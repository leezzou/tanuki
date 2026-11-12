#!/usr/bin/env python3
"""本机合并发布队列：plan/status 只读；init/once/watch 显式执行，不依赖 Agent。"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import threading
import time
import zipfile

CERTIFICATE = "371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f"
PACKAGE = "com.tanuki.modern.workspacecheck"
SHA = re.compile(r"[0-9a-f]{40}")
VERSION = re.compile(r"0\.1\.(0|[1-9][0-9]*)")
STOP = threading.Event()


class ReleaseError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


def version_key(value):
    require(isinstance(value, str) and VERSION.fullmatch(value), "仅支持整数 0.1.patch 版本")
    return int(value.split(".")[2])


def next_version(versions):
    return "0.1.%d" % (max(map(version_key, versions)) + 1)


def digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def read_json(path):
    require(not path.is_symlink() and path.stat().st_size <= 16 * 1024 * 1024, "JSON 路径或大小无效")
    return json.loads(path.read_text())


def atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=".pending-", dir=str(path.parent))
    temporary = Path(name)
    try:
        with os.fdopen(fd, "w") as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        require(not path.is_symlink(), "状态文件不能是符号链接")
        os.replace(temporary, path)
        directory_fd = os.open(str(path.parent), os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    finally:
        if temporary.exists():
            require(temporary.parent.resolve() == path.parent.resolve(), "临时状态文件越界")
            temporary.unlink()


@contextmanager
def writer(directory, label="队列"):
    directory.mkdir(parents=True, exist_ok=True)
    fd = os.open(str(directory / "writer.lock"), os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "a+") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ReleaseError("已有" + label + "写入者；本次未执行") from None
        yield


class Runner:
    def __init__(self, env=None):
        self.env = env

    def run(self, command, cwd=None, binary=False, timeout=120):
        # 外部工具的输出不直接转发，防止异常回显凭据；日志只记录受控阶段。
        try:
            process = subprocess.Popen(list(map(str, command)), cwd=cwd, env=self.env,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                       text=not binary, start_new_session=True)
            try:
                output, _ = process.communicate(timeout=timeout)
            except subprocess.TimeoutExpired:
                # 仅终止本 runner 创建的进程组；不能留下重任务却提前归还共享锁。
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.communicate(timeout=30)
                except subprocess.TimeoutExpired:
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    process.communicate()
                raise ReleaseError("命令超时且本次进程组已停止：" + Path(str(command[0])).name) from None
        except OSError:
            raise ReleaseError("命令无法执行：" + Path(str(command[0])).name) from None
        require(process.returncode == 0, "命令失败：%s（退出码 %d）" % (Path(str(command[0])).name, process.returncode))
        return output


class GitHub:
    def __init__(self, repo, runner):
        require(re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo), "仓库标识无效")
        self.repo, self.runner = repo, runner
        self.identity_cache = {}

    def api(self, endpoint):
        return json.loads(self.runner.run(["gh", "api", "repos/" + self.repo + "/" + endpoint]))

    def pages(self, endpoint):
        result = []
        for page in range(1, 1001):
            items = self.api(endpoint + ("&" if "?" in endpoint else "?") + "per_page=100&page=%d" % page)
            require(isinstance(items, list), "GitHub 分页格式错误")
            result.extend(items)
            if len(items) < 100:
                return result
        raise ReleaseError("GitHub 分页超过上限")

    def releases(self):
        return self.pages("releases")

    def assets(self, release):
        return self.pages("releases/%d/assets" % release["id"])

    def asset_bytes(self, asset):
        return self.runner.run(["gh", "api", "-H", "Accept: application/octet-stream",
                                "repos/%s/releases/assets/%d" % (self.repo, asset["id"])], binary=True)

    def tag_sha(self, tag):
        require(re.fullmatch(r"v0\.1\.[0-9]+", tag), "tag 格式无效")
        ref = self.api("git/ref/tags/" + tag)["object"]
        for _ in range(8):
            if ref["type"] == "commit":
                require(SHA.fullmatch(ref["sha"]), "tag SHA 无效")
                return ref["sha"]
            require(ref["type"] == "tag", "tag 不是提交")
            ref = self.api("git/tags/" + ref["sha"])["object"]
        raise ReleaseError("tag 嵌套超过上限")

    def identities(self, exclude_tag=None):
        versions, codes = [], []
        for release in self.releases():
            tag = release["tag_name"]
            if tag == exclude_tag:
                continue
            if not tag.startswith("v0.1."):
                continue
            version_key(tag[1:])
            versions.append(tag[1:])
            entries = [a for a in self.assets(release) if a["name"] == "app-update.json"]
            require(len(entries) <= 1, "重复更新清单")
            if entries:
                require(entries[0]["size"] <= 1024 * 1024, "公开更新清单过大")
                asset = entries[0]
                cache_key = (asset["id"], asset["size"], asset.get("digest"), asset.get("updated_at"))
                if cache_key not in self.identity_cache:
                    self.identity_cache[cache_key] = json.loads(self.asset_bytes(asset))
                document = self.identity_cache[cache_key]
                require(document.get("schemaVersion") == 1, "公开更新清单 schema 错误")
                for entry in document["packages"]:
                    if entry["packageName"] == PACKAGE:
                        require(entry["versionName"] == tag[1:] and type(entry["versionCode"]) is int,
                                "公开版本身份不一致")
                        codes.append(entry["versionCode"])
            elif not release["draft"] and version_key(tag[1:]) >= 2:
                raise ReleaseError("已发布版本缺更新清单，不能推断 code 上限")
        # 即使手工预留 tag 尚无 Release，也不能重复使用其版本。
        for tag in self.pages("tags"):
            if tag["name"].startswith("v0.1."):
                version_key(tag["name"][1:])
                versions.append(tag["name"][1:])
        require(versions and codes, "缺少可确认的公开发布身份")
        return versions, codes

    def find(self, tag):
        found = [r for r in self.releases() if r["tag_name"] == tag]
        require(len(found) <= 1, "同一 tag 有多个 Release")
        return found[0] if found else None

    def create(self, job, notes):
        tag = "v" + job["version"]
        tags = {t["name"] for t in self.pages("tags")}
        if tag in tags:
            require(self.tag_sha(tag) == job["sha"], "已存在 tag 指向另一提交")
        else:
            self.runner.run(["gh", "api", "--method", "POST", "repos/" + self.repo + "/git/refs",
                             "-f", "ref=refs/tags/" + tag, "-f", "sha=" + job["sha"]])
        self.runner.run(["gh", "release", "create", tag, "--repo", self.repo, "--target", job["sha"],
                         "--title", "Tanuki " + tag, "--notes-file", notes, "--draft", "--prerelease"])

    def upload(self, tag, path):
        self.runner.run(["gh", "release", "upload", tag, path, "--repo", self.repo], timeout=900)

    def publish(self, tag):
        self.runner.run(["gh", "release", "edit", tag, "--repo", self.repo, "--draft=false", "--prerelease"])

    def association(self, sha):
        pulls = self.pages("commits/%s/pulls" % sha)
        matches = [p for p in pulls if p.get("merged_at") and p["base"]["ref"] == "main"
                   and p["base"]["repo"]["full_name"] == self.repo]
        require(len(matches) == 1, "main 提交不能关联唯一已合并 PR；停止，不跳过")
        return matches[0]

    def merge(self, sha):
        pull = self.association(sha)
        require(pull.get("merge_commit_sha") == sha, "提交不是 PR 的最终合并边界")
        return pull

    def checks(self, sha):
        checks = self.api("commits/%s/check-runs?per_page=100" % sha)
        require(0 < checks["total_count"] <= 100 and len(checks["check_runs"]) == checks["total_count"],
                "CI 为空或超出单页上限")
        require(all(c["status"] == "completed" and c["conclusion"] == "success" for c in checks["check_runs"]),
                "对应 SHA 的 CI 尚未全部成功")
        status = self.api("commits/%s/status" % sha)
        require(status["total_count"] == 0 or status["state"] == "success", "对应 SHA 的 commit status 尚未成功")


def resolve_boundaries(provider, commits, base):
    positions = {sha: index for index, sha in enumerate(commits)}
    boundaries, cache, index = [], {}, 0
    def association(sha):
        if sha not in cache:
            cache[sha] = provider.association(sha)
        return cache[sha]
    try:
        while index < len(commits):
            pull = association(commits[index])
            end = pull["merge_commit_sha"]
            require(end in positions and positions[end] >= index, "PR 最终合并边界尚不在已扫描 main 范围")
            members = commits[index:positions[end] + 1]
            require(all(association(sha)["number"] == pull["number"] and association(sha)["merge_commit_sha"] == end
                        for sha in members), "rebase 合并区间混入其他 PR，拒绝猜测边界")
            boundaries.append({"sha": end, "base": base, "members": members, "pr": pull["number"]})
            base, index = end, positions[end] + 1
    except ReleaseError as error:
        return boundaries, str(error)
    return boundaries, None


def enqueue(state, boundaries):
    known = {j["sha"]: j for j in state["jobs"]}
    for item in boundaries:
        detail = {"sha": item} if isinstance(item, str) else item
        sha = detail["sha"]
        require(SHA.fullmatch(sha), "队列 SHA 无效")
        if sha not in known:
            job = dict(detail, status="queued")
            state["jobs"].append(job)
            known[sha] = job
        else:
            for key in ("base", "members", "pr"):
                if key in detail:
                    require(key not in known[sha] or known[sha][key] == detail[key], "已保留合并边界发生变化")
                    known[sha][key] = detail[key]
        state["cursor"] = sha


def reserve(job, state, versions, codes):
    if "version" in job:
        return
    previous = [j for j in state["jobs"] if "version" in j]
    job["version"] = next_version(versions + [state["baseline_version"]] + [j["version"] for j in previous])
    job["code"] = max(codes + [state["code_floor"]] + [j["code"] for j in previous]) + 1
    require(job["code"] <= 2_100_000_000, "versionCode 已耗尽")
    job["status"] = "reserved"


def known_floor(home, state):
    config_path, floor_path = home / "config.json", home / "floor/value.json"
    configured = read_json(config_path).get("installed_code_floor", 0) if config_path.exists() else 0
    data = read_json(floor_path) if floor_path.exists() else {"minimum": 0, "history": []}
    require(type(configured) is int and type(data["minimum"]) is int
            and 0 <= configured <= 2100000000 and 0 <= data["minimum"] <= 2100000000, "安装 versionCode floor 格式无效")
    return max(state["code_floor"], configured, data["minimum"]), data


def check_floor(job, minimum, data):
    if job["code"] > minimum:
        return
    candidate = data.get("candidate") or {}
    apk = "Tanuki-%s-workspacecheck-arm64-release.apk" % job["version"]
    require(job["code"] == minimum == data["minimum"] and candidate.get("sha") == job["sha"]
            and candidate.get("apk_sha256") == job.get("inventory", {}).get(apk, {}).get("sha256")
            and bool(candidate.get("apk_sha256")),
            "已知安装 floor 已达到或超过候选 code；需确认同候选 SHA/APK 哈希或明确迁移，身份不自动加号")


def set_floor(home, state, code, reason, candidate_sha=None, apk_sha256=None):
    require(type(code) is int and 0 <= code <= 2100000000 and reason and len(reason) <= 300,
            "set-floor 需要合法整数 code 与简短公开依据")
    require((candidate_sha is None) == (apk_sha256 is None), "同候选 floor 必须成对提供 SHA 与实际 APK SHA-256")
    candidate = None
    if candidate_sha is not None:
        require(SHA.fullmatch(candidate_sha) and re.fullmatch(r"[0-9a-f]{64}", apk_sha256), "同候选身份格式无效")
        matches = [j for j in state["jobs"] if j["sha"] == candidate_sha and j.get("code") == code]
        require(len(matches) == 1, "没有对应已保留候选，不能声明同候选安装")
        name = "Tanuki-%s-workspacecheck-arm64-release.apk" % matches[0]["version"]
        require(matches[0].get("inventory", {}).get(name, {}).get("sha256") == apk_sha256, "floor 的实际 APK 哈希不属于固定候选")
        candidate = {"sha": candidate_sha, "apk_sha256": apk_sha256}
    with writer(home / "floor", "versionCode floor/发布"):
        minimum, previous = known_floor(home, state)
        require(code >= minimum, "安装 floor 不能降低")
        require(not (code == previous["minimum"] and previous.get("candidate") and previous["candidate"] != candidate),
                "同 code 的已知候选归属不能重写")
        history = previous["history"] + [{"minimum": code, "reason": reason, "at": int(time.time()), "candidate": candidate}]
        atomic_json(home / "floor/value.json", {"minimum": code, "candidate": candidate, "history": history})


def attachment_names(version):
    return {"Tanuki-%s-workspacecheck-arm64-release.apk" % version, "app-update.json",
            "Tanuki-%s-source.zip" % version, "Tanuki-native-corresponding-sources.tar.gz",
            "native-source-SHA256SUMS", "RELEASE-SHA256SUMS"}


def checksum_entries(path):
    entries = {}
    for line in path.read_text().splitlines():
        require(re.fullmatch(r"[0-9a-f]{64}  [A-Za-z0-9][A-Za-z0-9._-]*", line), "散列清单格式错误")
        value, name = line.split("  ")
        require(name not in entries, "散列清单重名")
        entries[name] = value
    return entries


def bundle_inventory(directory, job):
    names = attachment_names(job["version"])
    require({p.name for p in directory.iterdir()} == names, "发布目录必须恰好包含六附件")
    require(all(p.is_file() and not p.is_symlink() and p.stat().st_size > 0 for p in directory.iterdir()),
            "附件不是非空普通文件")
    checks = checksum_entries(directory / "RELEASE-SHA256SUMS")
    require(set(checks) == names - {"RELEASE-SHA256SUMS"}, "发布散列必须覆盖另五附件")
    for name, value in checks.items():
        require(digest(directory / name) == value, "附件散列不匹配：" + name)
    native = "Tanuki-native-corresponding-sources.tar.gz"
    require(checksum_entries(directory / "native-source-SHA256SUMS") == {native: checks[native]}, "原生散列不匹配")
    document = read_json(directory / "app-update.json")
    require(document.get("schemaVersion") == 1 and len(document.get("packages", [])) == 1, "清单必须只含本渠道")
    entry = document["packages"][0]
    apk = "Tanuki-%s-workspacecheck-arm64-release.apk" % job["version"]
    require(entry["packageName"] == PACKAGE and entry["versionName"] == job["version"]
            and entry["versionCode"] == job["code"] and entry["certificateSha256"] == CERTIFICATE
            and entry["minSdk"] == 26 and entry["assetName"] == apk and entry["sha256"] == checks[apk]
            and entry["bytes"] == (directory / apk).stat().st_size, "APK 清单身份不匹配")
    require(document.get("sourceCommit") == job["sha"] and document.get("sourceTree") == job["tree"],
            "六附件未绑定本次精确源码 SHA/tree")
    with zipfile.ZipFile(directory / ("Tanuki-%s-source.zip" % job["version"])) as source:
        name = "Tanuki-%s/RELEASE-SOURCE.json" % job["version"]
        require(source.getinfo(name).file_size <= 16 * 1024 * 1024, "源码发布证明过大")
        identity = json.loads(source.read(name))["identity"]
    require(identity["commit"] == job["sha"] and identity["tree"] == job["tree"]
            and identity["version"] == job["version"] and identity["code"] == job["code"]
            and identity["certificateSha256"] == CERTIFICATE and identity["apkSha256"] == entry["sha256"],
            "源码证明与实际 APK/版本/code/证书不一致")
    if "base" in job:
        require(identity["base"] == job["base"] and document.get("sourceBase") == job["base"], "源码合并基线不匹配")
    return {name: {"sha256": digest(directory / name), "bytes": (directory / name).stat().st_size} for name in sorted(names)}


def publish_bundle(provider, job, directory, notes, stopped=lambda: False):
    inventory = bundle_inventory(directory, job)
    require(inventory == job["inventory"], "固定附件字节发生变化，拒绝重新签发")
    tag = "v" + job["version"]
    release = provider.find(tag)
    if release is None:
        require(not stopped(), "发布已停止")
        provider.create(job, notes)
        release = provider.find(tag)
    require(release is not None and release["prerelease"], "Release 不存在或不是 Pre-release")
    require(provider.tag_sha(tag) == job["sha"], "远端 tag 未指向本次 SHA")

    def verify_remote(current, complete):
        remote = provider.assets(current)
        names = [a["name"] for a in remote]
        require(len(names) == len(set(names)) and set(names) <= set(inventory), "远端包含重名或额外附件")
        if complete:
            require(set(names) == set(inventory), "远端六附件不完整")
        for asset in remote:
            expected = inventory[asset["name"]]
            require(asset["size"] == expected["bytes"], "远端同名附件大小冲突：" + asset["name"])
            # 不仅相信 API digest；实际下载比较所有字节，涵盖旧 API 无 digest 的情况。
            content = provider.asset_bytes(asset)
            require(len(content) == expected["bytes"] and hashlib.sha256(content).hexdigest() == expected["sha256"],
                    "远端同名附件字节冲突：" + asset["name"])
        return set(names)

    existing = verify_remote(release, not release["draft"])
    if not release["draft"]:
        return
    for name in sorted(set(inventory) - existing):
        require(not stopped(), "上传已停止，重启将从远端事实恢复")
        started = time.monotonic()
        print("[上传附件] %s · %d 字节" % (name, inventory[name]["bytes"]), flush=True)
        provider.upload(tag, directory / name)
        print("[附件完成] %s · %.1f 秒" % (name, time.monotonic() - started), flush=True)
    verify_remote(provider.find(tag), True)
    require(not stopped(), "发布前停止，完整 draft 已保留")
    provider.publish(tag)
    actual = provider.find(tag)
    require(actual and not actual["draft"] and actual["prerelease"], "发布状态尚未远端确认")
    require(provider.tag_sha(tag) == job["sha"], "发布后 tag 身份改变")
    verify_remote(actual, True)


class Executor:
    def __init__(self, config, home, runner, provider):
        self.config, self.home, self.runner, self.provider = config, home, runner, provider
        self.checkout = home / "checkout"

    def git(self, *args):
        return self.runner.run(["git", "-C", self.checkout, *args]).strip()

    def sync(self):
        require(self.checkout.resolve() == self.home.resolve() / "checkout", "构建 checkout 必须是本服务专用 clone，不能链接其他工作树")
        if not self.checkout.exists():
            self.runner.run(["git", "clone", "--branch", "main", "https://github.com/" + self.config["repo"] + ".git", self.checkout], timeout=900)
        require((self.checkout / ".git").is_dir() and not (self.checkout / ".git").is_symlink(),
                "构建 checkout 必须是独立 clone，不能使用共享 worktree Git 元数据")
        require(self.git("remote", "get-url", "origin") == "https://github.com/" + self.config["repo"] + ".git", "服务仓库来源改变")
        self.git("fetch", "origin", "+refs/heads/main:refs/remotes/origin/main", "+refs/tags/*:refs/tags/*")

    def commits(self, cursor):
        # 不能仅检查 ancestor；cursor 必须就在 main 的 first-parent 链上。
        history = self.git("rev-list", "--first-parent", "origin/main").splitlines()
        require(cursor in history, "main 历史改写或游标不在 first-parent 链，拒绝跳过")
        return list(reversed(history[:history.index(cursor)]))

    def discover(self, cursor):
        return resolve_boundaries(self.provider, self.commits(cursor), cursor)

    def pin(self, sha):
        require(SHA.fullmatch(sha), "构建 SHA 无效")
        require(not self.git("status", "--porcelain", "--untracked-files=no"), "服务 checkout 有已跟踪修改，拒绝覆盖")
        try:
            self.git("-c", "core.hooksPath=/dev/null", "checkout", "--detach", sha)
        finally:
            # 成败都检查；失败时不继续另一个工作树。
            require(self.git("rev-parse", "HEAD") == sha and not self.git("status", "--porcelain", "--untracked-files=no"),
                    "checkout 后 SHA/状态异常")
        return self.git("rev-parse", "HEAD^{tree}")

    def build(self, job):
        slot = load_module("build_slot", Path(__file__).with_name("build-slot.py"))
        def stopped():
            control = self.home / "control.json"
            return STOP.is_set() or (control.exists() and read_json(control)["paused"])
        with slot.acquire(self.config["build_slot"], "release", stopped):
            return self._build(job)

    def _native_state(self):
        environment = self.runner.env or os.environ
        require(environment.get("ANDROID_SDK_ROOT"), "需要稳定 SDK 路径才能核 native 缓存身份")
        module = load_module("native_cache_identity", self.checkout / "scripts/native-cache-identity.py")
        value = module.identity(self.checkout, Path(environment["ANDROID_SDK_ROOT"]))
        path = self.home / "native-cache.json"
        receipt = read_json(path) if path.exists() else None
        return module, value, module.matches(self.checkout, value, receipt)

    def _record_native(self, module, value, apk):
        # 构建后再核源码/工具链；依赖首次取齐可变化，避免下一次无谓重编。
        after = module.identity(self.checkout, Path(value["sdkRoot"]))
        require(all(after[key] == value[key] for key in ("sourceSha256", "toolchainSha256", "ndkVersion", "ndkRoot", "abi", "minSdk")),
                "构建期间 native 源码/工具链输入变化，拒绝固定候选")
        value = after
        atomic_json(self.home / "native-cache.json", module.receipt(self.checkout, value, apk))
        return value

    def _build(self, job):
        job["tree"] = self.pin(job["sha"])
        require(all((self.checkout / "scripts" / name).is_file()
                    for name in ("release-source.py", "native-cache-identity.py", "native-cache.init.gradle")),
                "目标合并 SHA 缺少自动发布源码协议；需先合入发布工具 PR，不能混入较新脚本或跳过该提交")
        parent = job.get("base") or self.git("rev-parse", "HEAD^1")
        directory = self.checkout / "artifacts/releases" / job["version"]
        command = [sys.executable, "scripts/prepare-app-release.py", "--version", job["version"],
                   "--version-code", str(job["code"]), "--channel", "workspacecheck", "--source-commit", job["sha"],
                   "--merge-base", parent]
        # 已封装完成的候选由 inventory 固定，失败后的完整 APK 通过固定字节模式复用。
        candidate = self.checkout / "app/build/outputs/apk/modern/release/app-modern-release.apk"
        receipt = self.home / "candidates" / (job["sha"] + ".json")
        module, native_identity, native_reused = self._native_state()
        fixed = receipt.exists()
        if fixed:
            tested = read_json(receipt)
            require(tested["sha"] == job["sha"] and tested["version"] == job["version"] and tested["code"] == job["code"]
                    and candidate.is_file() and digest(candidate) == tested["apk_sha256"], "重试 APK 来源或字节改变")
            require(tested.get("native_input_sha256") == native_identity["sha256"], "固定候选的 native 输入/工具链身份改变，需明确迁移，不能重签同一候选")
            command += ["--verified-apk", str(candidate), "--verified-apk-sha256", tested["apk_sha256"]]
        elif not native_reused:
            command += ["--force-native"]
        print("[native-cache] " + ("复用固定 APK" if fixed else "输入/输出匹配，允许增量" if native_reused else "输入或输出不匹配，仅强制 native task"), flush=True)
        snapshot = read_json(self.home / "state.json") if (self.home / "state.json").exists() else {"code_floor": 0}
        minimum, floor_data = known_floor(self.home, snapshot)
        check_floor(job, minimum, floor_data)
        self.runner.run(command, cwd=self.checkout, timeout=7200)
        inventory = bundle_inventory(directory, job)
        if not fixed:
            native_identity = self._record_native(module, native_identity, candidate)
        job["native_input_sha256"] = native_identity["sha256"]
        atomic_json(receipt, {"sha": job["sha"], "version": job["version"], "code": job["code"],
                              "apk_sha256": digest(candidate), "native_input_sha256": native_identity["sha256"]})
        for path in directory.iterdir():
            self.runner.run([sys.executable, "scripts/check-publication.py", "--archive", path], cwd=self.checkout)
        destination = self.home / "bundles" / job["sha"]
        destination.mkdir(parents=True, exist_ok=True)
        import shutil
        for name in attachment_names(job["version"]):
            target = destination / name
            require(not target.exists() or digest(target) == inventory[name]["sha256"], "持久候选同名冲突")
            if not target.exists():
                shutil.copyfile(directory / name, target)
        require(bundle_inventory(destination, job) == inventory, "持久候选副本不一致")
        job["inventory"] = inventory
        return destination

    def gate(self, job):
        pull = self.provider.merge(job["sha"])
        self.provider.checks(pull["head"]["sha"])
        self.provider.checks(job["sha"])
        # 合并前门禁由现有流程执行；首轮固定回执副本，发布重试不依赖AO在线。
        frozen = self.home / "receipts" / (job["sha"] + ".json")
        if job.get("gate_receipt_sha256"):
            require(frozen.is_file() and not frozen.is_symlink() and digest(frozen) == job["gate_receipt_sha256"],
                    "候选冻结门禁回执丢失或字节改变")
            receipt = read_json(frozen)
        else:
            source = Path(self.config["gate_receipts"]) / (pull["head"]["sha"] + ".json")
            require(source.is_file() and not source.is_symlink(),
                    "缺少 PR #%d head %s 的合并前门禁回执；先运行 record-release-gate.py，队列不会跳过" %
                    (pull["number"], pull["head"]["sha"]))
            before = digest(source)
            receipt = read_json(source)
            require(digest(source) == before, "读取期间门禁回执发生变化")
        require(receipt.get("schema") == 1 and receipt.get("repo") == self.config["repo"]
                and receipt.get("pr") == pull["number"] and receipt.get("head_sha") == pull["head"]["sha"]
                and receipt.get("development_gates") == "passed", "缺少绑定本次合并的审查/适用 QA 门禁回执")
        require("pr" not in job or job["pr"] == pull["number"], "队列的 PR 合并归属发生变化")
        if not job.get("gate_receipt_sha256"):
            require(not frozen.exists() or read_json(frozen) == receipt, "已有冻结回执身份冲突，拒绝覆盖")
            if not frozen.exists():
                atomic_json(frozen, receipt)
            job["gate_receipt_sha256"] = digest(frozen)
        job["pr"] = pull["number"]
        job["validation"] = receipt.get("validation", "Host CI 已核对；设备、真实模型与真机未提供本次证据。")


def run_once(state, save, executor, provider, home, stopped=lambda: False):
    executor.sync()
    boundaries, blocked = executor.discover(state["cursor"])
    enqueue(state, boundaries)
    state["discovery_error"] = blocked
    save()
    for job in state["jobs"]:
        if job["status"] == "published":
            continue
        if stopped():
            return
        try:
            versions, codes = provider.identities("v" + job["version"] if "version" in job else None)
            with writer(home / "floor", "versionCode floor/发布"):
                minimum, floor_data = known_floor(home, state)
                state["code_floor"] = minimum
                reserve(job, state, versions, codes)
                save()
                require(job["code"] > max(codes), "外部发布上限已超过保留 code；停止等待明确迁移，不自动反复加号")
                check_floor(job, minimum, floor_data)
            executor.gate(job)
            save()
            if "inventory" not in job:
                print("[构建] %s → %s/code%d" % (job["sha"], job["version"], job["code"]), flush=True)
                started = time.monotonic()
                directory = executor.build(job)
                print("[构建封装完成] %.1f 秒" % (time.monotonic() - started), flush=True)
                job["status"] = "prepared"
                save()
            else:
                directory = home / "bundles" / job["sha"]
            notes = home / "bundles" / (job["sha"] + "-notes.md")
            notes.write_text("Tanuki v%s · workspacecheck 开发签名 Pre-release\n\n"
                             "Source commit: `%s`\nSource tree: `%s`\nPR: #%d\n\n"
                             "ARM64 · minSdk26 / targetSdk34 · non-debuggable\nCertificate SHA-256: `%s`\n\n%s\n\n"
                             "完整源码、原生对应源码、更新清单和实际 APK 散列见六附件。\n" %
                             (job["version"], job["sha"], job["tree"], job["pr"], CERTIFICATE, job["validation"]))
            require(not stopped(), "执行器停止，候选保留")
            with writer(home / "floor", "versionCode floor/发布"):
                minimum, floor_data = known_floor(home, state)
                state["code_floor"] = minimum
                check_floor(job, minimum, floor_data)
                # 整个远端上传/公开期间拒绝并发 floor 改写，避免检查后身份下限变化。
                publish_bundle(provider, job, directory, notes, stopped)
            job["status"] = "published"
            job.pop("error", None)
            save()
            print("[发布完成] v" + job["version"], flush=True)
        except Exception as error:
            job["error"] = str(error) if isinstance(error, ReleaseError) else type(error).__name__
            job["failed_at"] = int(time.time())
            save()
            raise
    require(not blocked, blocked or "")


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=Path, help="稳定的专用服务目录，不能放在 AO worktree")
    parser.add_argument("command", choices=("init", "plan", "status", "once", "watch", "pause", "resume", "set-floor"))
    parser.add_argument("--baseline-tag", help="init 必须明确确认的已发布基线，例如 v0.1.14")
    parser.add_argument("--version-code", type=int, help="set-floor 的已核已安装 code 下限")
    parser.add_argument("--reason", help="floor 的简短公开事实依据，不能放凭据或原始日志")
    parser.add_argument("--candidate-sha", help="仅同一固定候选安装时提供")
    parser.add_argument("--apk-sha256", help="与 --candidate-sha 成对的实际 APK SHA-256")
    args = parser.parse_args()
    home = args.home.expanduser().resolve()
    config = read_json(home / "config.json")
    allowed_env = {"PATH", "TANUKI_JDK", "ANDROID_SDK_ROOT", "ANDROID_HOME", "GRADLE_USER_HOME", "TANUKI_WORKSPACECHECK_KEYSTORE"}
    require(set(config.get("environment", {})) <= allowed_env, "服务环境配置包含不允许的字段")
    runner = Runner(dict(os.environ, **config.get("environment", {})))
    provider = GitHub(config["repo"], runner)
    path = home / "state.json"
    if args.command == "status":
        snapshot = read_json(path)
        if (home / "control.json").exists():
            snapshot["paused"] = read_json(home / "control.json")["paused"]
        minimum, floor_data = known_floor(home, snapshot)
        snapshot["installed_floor"] = dict(floor_data, effective_minimum=minimum)
        print(json.dumps(snapshot, ensure_ascii=False, indent=2))
        return 0
    if args.command == "plan":
        state = read_json(path)
        # plan 从 provider 只读遍历，不 fetch、不建目录、不占版本。
        current = provider.api("commits/main")
        pending = []
        while current["sha"] != state["cursor"]:
            require(len(pending) < 10000 and current["parents"], "游标不在 main first-parent 链")
            pending.append(current["sha"])
            current = provider.api("commits/" + current["parents"][0]["sha"])
        versions, codes = provider.identities()
        boundaries, blocked = resolve_boundaries(provider, list(reversed(pending)), state["cursor"])
        minimum, floor_data = known_floor(home, state)
        next_code = max(codes + [minimum] + [j["code"] for j in state["jobs"] if "code" in j]) + 1
        print(json.dumps({"queued": state["jobs"], "new_commits": list(reversed(pending)),
                          "merge_boundaries": boundaries, "blocked": blocked,
                          "next_unreserved_version": next_version(versions + [j["version"] for j in state["jobs"] if "version" in j]),
                          "public_code_max": max(codes), "installed_floor": dict(floor_data, effective_minimum=minimum),
                          "next_unreserved_code": next_code, "paused": state["paused"]}, ensure_ascii=False, indent=2))
        return 0
    if args.command == "set-floor":
        state = read_json(path)
        require(state["schema"] == 1 and state["repo"] == config["repo"], "floor 不能修改其他仓库/格式的队列")
        set_floor(home, state, args.version_code, args.reason, args.candidate_sha, args.apk_sha256)
        print("versionCode floor 已持久记录；服务未访问设备。")
        return 0
    if args.command in ("pause", "resume"):
        atomic_json(home / "control.json", {"paused": args.command == "pause"})
        return 0
    with writer(home):
        if args.command == "init":
            require(not path.exists(), "已有状态，拒绝重新初始化或丢弃失败候选")
            require(args.baseline_tag is not None, "init 必须提供已确认的 --baseline-tag")
            baseline = provider.find(args.baseline_tag)
            require(baseline and not baseline["draft"], "基线必须是已发布 Release")
            versions, codes = provider.identities()
            state = {"schema": 1, "repo": config["repo"], "baseline_version": args.baseline_tag[1:],
                     "cursor": provider.tag_sha(args.baseline_tag), "code_floor": max(config["installed_code_floor"], *codes),
                     "paused": False, "jobs": []}
            Executor(config, home, runner, provider).sync()
            Executor(config, home, runner, provider).commits(state["cursor"])
            atomic_json(path, state)
            return 0
        state = read_json(path)
        require(state["schema"] == 1 and state["repo"] == config["repo"], "队列 schema/仓库不匹配")
        save = lambda: atomic_json(path, state)
        for signum in (signal.SIGINT, signal.SIGTERM):
            signal.signal(signum, lambda *_: STOP.set())
        def stopped():
            return STOP.is_set() or ((home / "control.json").exists() and read_json(home / "control.json")["paused"])

        while not STOP.is_set():
            state["paused"] = stopped()
            save()
            if not state["paused"]:
                try:
                    run_once(state, save, Executor(config, home, runner, provider), provider, home, stopped)
                except Exception as error:
                    print("[停止/重试] " + (str(error) if isinstance(error, ReleaseError) else type(error).__name__), file=sys.stderr, flush=True)
                    if args.command == "once":
                        return 1
            if args.command == "once":
                return 0
            STOP.wait(max(10, config.get("poll_seconds", 30)))
        return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print("发布队列拒绝执行：" + (str(error) if isinstance(error, ReleaseError) else type(error).__name__), file=sys.stderr)
        sys.exit(1)

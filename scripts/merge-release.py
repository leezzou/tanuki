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
def writer(directory):
    directory.mkdir(parents=True, exist_ok=True)
    fd = os.open(str(directory / "writer.lock"), os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "a+") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ReleaseError("已有队列写入者；本次未执行") from None
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

    def merge(self, sha):
        pulls = self.pages("commits/%s/pulls" % sha)
        matches = [p for p in pulls if p.get("merged_at") and p["base"]["ref"] == "main"
                   and p["base"]["repo"]["full_name"] == self.repo and p.get("merge_commit_sha") == sha]
        require(len(matches) == 1, "main 提交不是唯一已合并 PR 边界；停止，不跳过")
        return matches[0]

    def checks(self, sha):
        checks = self.api("commits/%s/check-runs?per_page=100" % sha)
        require(0 < checks["total_count"] <= 100 and len(checks["check_runs"]) == checks["total_count"],
                "CI 为空或超出单页上限")
        require(all(c["status"] == "completed" and c["conclusion"] == "success" for c in checks["check_runs"]),
                "对应 SHA 的 CI 尚未全部成功")
        status = self.api("commits/%s/status" % sha)
        require(status["total_count"] == 0 or status["state"] == "success", "对应 SHA 的 commit status 尚未成功")


def enqueue(state, commits):
    # first-parent 范围包括 squash/rebase 的每个 main 提交；不是 PR 边界则显式阻塞。
    known = {j["sha"] for j in state["jobs"]}
    for sha in commits:
        require(SHA.fullmatch(sha), "队列 SHA 无效")
        if sha not in known:
            state["jobs"].append({"sha": sha, "status": "queued"})
            known.add(sha)
    if commits:
        state["cursor"] = commits[-1]


def reserve(job, state, versions, codes):
    if "version" in job:
        return
    previous = [j for j in state["jobs"] if "version" in j]
    job["version"] = next_version(versions + [state["baseline_version"]] + [j["version"] for j in previous])
    job["code"] = max(codes + [state["code_floor"]] + [j["code"] for j in previous]) + 1
    require(job["code"] <= 2_100_000_000, "versionCode 已耗尽")
    job["status"] = "reserved"


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
        if not self.checkout.exists():
            self.runner.run(["git", "clone", "--branch", "main", "https://github.com/" + self.config["repo"] + ".git", self.checkout], timeout=900)
        require(self.git("remote", "get-url", "origin") == "https://github.com/" + self.config["repo"] + ".git", "服务仓库来源改变")
        self.git("fetch", "origin", "+refs/heads/main:refs/remotes/origin/main", "+refs/tags/*:refs/tags/*")

    def commits(self, cursor):
        # 不能仅检查 ancestor；cursor 必须就在 main 的 first-parent 链上。
        history = self.git("rev-list", "--first-parent", "origin/main").splitlines()
        require(cursor in history, "main 历史改写或游标不在 first-parent 链，拒绝跳过")
        return list(reversed(history[:history.index(cursor)]))

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

    def _build(self, job):
        job["tree"] = self.pin(job["sha"])
        parent = self.git("rev-parse", "HEAD^1")
        directory = self.checkout / "artifacts/releases" / job["version"]
        command = [sys.executable, "scripts/prepare-app-release.py", "--version", job["version"],
                   "--version-code", str(job["code"]), "--channel", "workspacecheck", "--source-commit", job["sha"],
                   "--merge-base", parent]
        # 已封装完成的候选由 inventory 固定，失败后的完整 APK 通过固定字节模式复用。
        candidate = self.checkout / "app/build/outputs/apk/modern/release/app-modern-release.apk"
        receipt = self.home / "candidates" / (job["sha"] + ".json")
        if receipt.exists():
            tested = read_json(receipt)
            require(tested["sha"] == job["sha"] and tested["version"] == job["version"] and tested["code"] == job["code"]
                    and candidate.is_file() and digest(candidate) == tested["apk_sha256"], "重试 APK 来源或字节改变")
            command += ["--verified-apk", str(candidate), "--verified-apk-sha256", tested["apk_sha256"]]
        self.runner.run(command, cwd=self.checkout, timeout=7200)
        inventory = bundle_inventory(directory, job)
        atomic_json(receipt, {"sha": job["sha"], "version": job["version"], "code": job["code"], "apk_sha256": digest(candidate)})
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
        # 合并前门禁由编排执行；持久回执绑定合并 SHA 和 PR head，不能拿旧 QA 替代。
        receipt = read_json(Path(self.config["gate_receipts"]) / (pull["head"]["sha"] + ".json"))
        require(receipt.get("schema") == 1 and receipt.get("repo") == self.config["repo"]
                and receipt.get("pr") == pull["number"] and receipt.get("head_sha") == pull["head"]["sha"]
                and receipt.get("development_gates") == "passed", "缺少绑定本次合并的审查/适用 QA 门禁回执")
        job["pr"] = pull["number"]
        job["validation"] = receipt.get("validation", "Host CI 已核对；设备、真实模型与真机未提供本次证据。")


def run_once(state, save, executor, provider, home, stopped=lambda: False):
    executor.sync()
    enqueue(state, executor.commits(state["cursor"]))
    save()
    for job in state["jobs"]:
        if job["status"] == "published":
            continue
        if stopped():
            return
        try:
            versions, codes = provider.identities("v" + job["version"] if "version" in job else None)
            reserve(job, state, versions, codes)
            save()
            require(job["code"] > max(codes + [state["code_floor"]]),
                    "外部发布/安装上限已超过保留 code；停止等待明确迁移，不自动反复加号")
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


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=Path, help="稳定的专用服务目录，不能放在 AO worktree")
    parser.add_argument("command", choices=("init", "plan", "status", "once", "watch", "pause", "resume"))
    parser.add_argument("--baseline-tag", help="init 必须明确确认的已发布基线，例如 v0.1.14")
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
        print(json.dumps({"queued": state["jobs"], "new_commits": list(reversed(pending)),
                          "next_unreserved_version": next_version(versions + [j["version"] for j in state["jobs"] if "version" in j]),
                          "public_code_max": max(codes), "paused": state["paused"]}, ensure_ascii=False, indent=2))
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

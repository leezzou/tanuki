#!/usr/bin/env python3
"""验证生成发布源码包的提交/tree/文件证明；无需 .git，可计划或执行同身份重建。"""
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tarfile
import time

ROOT = Path(__file__).resolve().parent.parent
METADATA = "RELEASE-SOURCE.json"
SHA = re.compile(r"[0-9a-f]{40}")
CERTIFICATE = "371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f"


def require(value, message):
    if not value:
        raise ValueError(message)


def load(root, filename):
    spec = importlib.util.spec_from_file_location("release_source_" + filename.replace("-", "_"), root / "scripts" / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def object_hash(kind, content):
    return hashlib.sha1(kind.encode() + b" " + str(len(content)).encode() + b"\0" + content).hexdigest()


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def selected_paths(root):
    package = load(root, "package-source.py")
    package.ROOT = root
    for path in root.iterdir():
        if path.is_file() and package.public_name(Path(path.name)):
            require(path.name in package.FILES or path.name == METADATA, "根目录文件不在公开源码清单：" + path.name)
    paths = set()
    for name in package.FILES:
        path = root / name
        require(path.is_file() and package.public_file(path), "发布源码缺少必需公开文件")
        paths.add(path)
    for name in package.TREES:
        directory = root / name
        require(directory.is_dir(), "发布源码缺少必需目录")
        paths.update(p for p in directory.rglob("*") if package.public_file(p))
    extras = [root / name for name in package.EXTRAS]
    if any(p.exists() for p in extras):
        require(all(p.is_file() and not p.is_symlink() for p in extras), "原生源码附件不完整")
        paths.update(extras)
    return paths, set(package.EXTRAS)


def parse_tree(content):
    entries, position = [], 0
    while position < len(content):
        space = content.index(b" ", position)
        end = content.index(b"\0", space)
        mode, name = content[position:space].decode("ascii"), content[space + 1:end].decode("utf-8")
        require(name not in ("", ".", "..") and "/" not in name and len(content) >= end + 21, "Git tree 条目无效")
        oid = content[end + 1:end + 21].hex()
        entries.append((mode, name, oid))
        position = end + 21
    return entries


def flatten(trees, tree):
    result, pending = {}, [("", tree)]
    while pending:
        prefix, oid = pending.pop()
        require(oid in trees and len(result) < 100000, "Git tree 证明缺失或过大")
        for mode, name, child in parse_tree(trees[oid]):
            path = prefix + name
            if mode in ("40000", "040000"):
                pending.append((path + "/", child))
            else:
                require(path not in result and mode in ("100644", "100755", "120000", "160000"), "Git tree 模式/路径重复无效")
                result[path] = (mode, child)
    return result


def commit_header(content):
    lines = content.split(b"\n\n", 1)[0].decode("utf-8").splitlines()
    tree = [line[5:] for line in lines if line.startswith("tree ")]
    parents = [line[7:] for line in lines if line.startswith("parent ")]
    require(len(tree) == 1 and SHA.fullmatch(tree[0]) and all(SHA.fullmatch(p) for p in parents), "Git commit 证明无效")
    return tree[0], parents


def changed_paths(before, after):
    return sorted(name for name in set(before) | set(after) if before.get(name) != after.get(name))


def validate_document(root, document):
    require(document.get("schema") == 1 and document.get("kind") == "generated-release-source-proof", "发布源码元数据 schema/生成类型无效")
    identity = document["identity"]
    require(re.fullmatch(r"0\.1\.(0|[1-9][0-9]*)", identity["version"])
            and type(identity["code"]) is int and 2 <= identity["code"] <= 2100000000
            and identity["channel"] == "workspacecheck" and identity["certificateSha256"] == CERTIFICATE
            and re.fullmatch(r"[0-9a-f]{64}", identity["apkSha256"]),
            "发布源码版本/code/渠道/证书身份无效")
    objects = {}
    for oid, encoded in document["commits"].items():
        content = encoded.encode("utf-8")
        require(SHA.fullmatch(oid) and object_hash("commit", content) == oid, "Git commit 对象散列不匹配")
        objects[oid] = content
    trees = {}
    for oid, encoded in document["trees"].items():
        content = base64.b64decode(encoded, validate=True)
        require(SHA.fullmatch(oid) and object_hash("tree", content) == oid, "Git tree 对象散列不匹配")
        trees[oid] = content
    commit, base = identity["commit"], identity["base"]
    require(commit != base and commit in objects and base in objects, "发布合并提交或基线证明缺失/没有新合并")
    cursor, chain = commit, []
    while cursor != base:
        require(cursor in objects and len(chain) < 10000 and cursor not in chain, "发布合并范围不连续")
        chain.append(cursor)
        _, parents = commit_header(objects[cursor])
        require(parents, "发布基线不是 first-parent 祖先")
        cursor = parents[0]
    tree, _ = commit_header(objects[commit])
    base_tree, _ = commit_header(objects[base])
    require(tree == identity["tree"], "提交声明的源码 tree 不匹配")
    before, after = flatten(trees, base_tree), flatten(trees, tree)
    changed = changed_paths(before, after)
    require(changed == document["changedFiles"] and list(reversed(chain)) == document["mergeCommits"], "合并改动/提交索引不匹配")
    paths, extras = selected_paths(root)
    relative = {p.relative_to(root).as_posix(): p for p in paths}
    package = load(root, "package-source.py")
    # 应分发集合来自已核Git tree和真实打包白名单，不能由磁盘/可变清单自证完整。
    expected = set(package.FILES)
    expected.update(name for name in after
                    if (name in package.FILES or any(name.startswith(tree + "/") for tree in package.TREES))
                    and package.public_name(PurePosixPath(name)))
    require(all(name in after and after[name][0] in ("100644", "100755") for name in expected),
            "Git tree 缺少必需公开源码或模式不受支持")
    require(set(relative) - extras == expected, "Git tree 公开源码文件集合不完整或有额外文件")
    require(set(relative) == set(document["files"]), "源码包公开文件集合发生变化")
    for name, path in relative.items():
        require(not path.is_symlink() and sha256(path) == document["files"][name], "源码包文件散列不匹配：" + name)
        if name not in extras:
            require(name in after and after[name][0] in ("100644", "100755")
                    and object_hash("blob", path.read_bytes()) == after[name][1], "源码内容不属于声明的 Git tree：" + name)
    handoff = load(root, "check-handoff.py")
    handoff.ROOT, handoff.STAMP, handoff.STATE, handoff.RECORD_DIR = root, root / "docs/handoffs/CURRENT.json", root / "docs/AGENT-STATE.md", root / "docs/handoffs"
    changed_objects = [handoff.PurePosixPath(p) for p in changed]
    records = sorted(p.as_posix() for p in changed_objects if handoff.is_record_change(p))
    require(records == document["handoffs"], "本次交接索引不匹配")
    handoff.pr_check(None, changed_objects)
    return identity


def make_document(root, paths, manifest):
    entry = manifest["packages"][0]
    identity = {"commit": manifest["sourceCommit"], "tree": manifest["sourceTree"], "base": manifest["sourceBase"],
                "version": entry["versionName"], "code": entry["versionCode"], "channel": "workspacecheck",
                "certificateSha256": entry["certificateSha256"], "apkSha256": entry["sha256"]}
    def git(*args):
        result = subprocess.run(["git", *args], cwd=root, capture_output=True, check=True)
        return result.stdout
    require(git("rev-parse", "HEAD").decode().strip() == identity["commit"] and
            not git("status", "--porcelain", "--untracked-files=no").strip(), "生成发布证明需要精确干净 Git checkout")
    commits, cursor, trees = {}, identity["commit"], {}
    while True:
        require(cursor not in commits and len(commits) < 10000, "发布提交范围无效")
        content = git("cat-file", "commit", cursor)
        commits[cursor] = content.decode("utf-8")
        tree, parents = commit_header(content)
        pending = [tree]
        while pending:
            oid = pending.pop()
            if oid in trees:
                continue
            raw = git("cat-file", "tree", oid)
            trees[oid] = base64.b64encode(raw).decode("ascii")
            pending.extend(child for mode, _, child in parse_tree(raw) if mode in ("40000", "040000"))
        if cursor == identity["base"]:
            break
        require(parents, "发布基线不在 first-parent 链")
        cursor = parents[0]
    decoded = {key: base64.b64decode(value) for key, value in trees.items()}
    before = flatten(decoded, commit_header(commits[identity["base"]].encode("utf-8"))[0])
    after = flatten(decoded, identity["tree"])
    changed = changed_paths(before, after)
    chain = git("rev-list", "--first-parent", "--reverse", identity["base"] + ".." + identity["commit"]).decode().splitlines()
    document = {"schema": 1, "kind": "generated-release-source-proof",
                "validationScope": "生成的发布来源/公开源码一致性证明；不表示 Android、设备或真实模型 QA。",
                "identity": identity, "commits": commits, "trees": trees, "mergeCommits": chain,
                "changedFiles": changed, "handoffs": sorted(p for p in changed if p.startswith("docs/handoffs/") and p.endswith(".md")
                and p != "docs/handoffs/TEMPLATE.md" and (root / p).is_file()),
                "files": {p.relative_to(root).as_posix(): sha256(p) for p in sorted(paths)}}
    validate_document(root, document)
    return document


def verify(root):
    path = root / METADATA
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 16 * 1024 * 1024, "缺少有效生成发布源码元数据")
    return validate_document(root, json.loads(path.read_text()))


def restore_native_sources(root):
    archive_path = root / "artifacts/sources/native/Tanuki-native-corresponding-sources.tar.gz"
    require(archive_path.is_file() and not archive_path.is_symlink(), "重建需要包内已验证的原生对应源码归档")
    anchor = root / ".build/sources"
    start, count, byte_count = time.monotonic(), 0, 0
    print("[native-source] 恢复包内已验证上游源码；已有不同字节将拒绝覆盖", flush=True)
    with tarfile.open(archive_path, "r:gz") as archive:
        members, links = [], []
        for member in archive:
            relative = PurePosixPath(member.name)
            require(not relative.is_absolute() and ".." not in relative.parts, "原生源码归档路径越界")
            if relative.parts[:2] != (".build", "sources") or len(relative.parts) < 3:
                continue
            require(relative.parts[2] == "termux-proot" or re.fullmatch(r"talloc-[0-9.]+", relative.parts[2]), "原生源码缓存目录不受支持")
            if ".git" in relative.parts:
                index = relative.parts.index(".git")
                git_name = PurePosixPath(*relative.parts[index + 1:]).as_posix()
                require(member.isdir() or git_name in ("HEAD", "config", "index", "shallow")
                        or re.fullmatch(r"refs/tags/[A-Za-z0-9._-]+", git_name)
                        or re.fullmatch(r"objects/pack/pack-[0-9a-f]{40}\.(pack|idx|rev|bitmap)", git_name), "原生源码包含非公开最小 Git 元数据")
            require(member.isdir() or member.isfile() or (member.issym() and ".git" not in relative.parts), "原生源码归档含不受支持的特殊条目")
            byte_count += member.size
            require(len(members) < 50000 and member.size <= 256 * 1024 ** 2 and byte_count <= 512 * 1024 ** 2,
                    "原生源码归档超过恢复上限")
            members.append((member, relative))
        names = [relative.as_posix() for _, relative in members]
        require(len(names) == len(set(names)), "原生源码归档路径重复")
        for member, relative in members:
            destination = root.joinpath(*relative.parts)
            if member.issym():
                links.append((member, destination))
                continue
            require(destination.resolve() == destination, "已有原生源码缓存含路径链接")
            if member.isdir():
                require(not destination.exists() or destination.is_dir(), "原生缓存目录冲突")
                destination.mkdir(parents=True, exist_ok=True)
                continue
            source = archive.extractfile(member)
            require(source is not None, "原生源码文件缺失")
            with source:
                data = source.read()
            if ".git" in relative.parts and relative.name == "config":
                require(data.decode().strip() == "[core]\nrepositoryformatversion = 0\nbare = false\nfilemode = true", "原生 Git 配置不属于公开最小格式")
            if destination.exists():
                require(destination.is_file() and not destination.is_symlink()
                        and hashlib.sha256(data).hexdigest() == sha256(destination), "已有原生缓存字节不同；请使用全新解压目录")
            else:
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("xb") as output:
                    output.write(data)
                destination.chmod(member.mode & 0o777)
            count += 1
        while links:
            remaining = []
            for member, destination in links:
                require(not Path(member.linkname).is_absolute() and destination.parent.resolve() == destination.parent,
                        "原生源码链接路径越界")
                target = (destination.parent / member.linkname).resolve()
                require(anchor in target.parents, "原生源码链接目标越界")
                if not target.exists():
                    remaining.append((member, destination))
                    continue
                if destination.exists() or destination.is_symlink():
                    require(destination.is_symlink() and destination.resolve() == target, "已有原生源码链接冲突")
                else:
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.symlink_to(member.linkname)
                count += 1
            require(len(remaining) < len(links), "原生源码链接缺目标或成环")
            links = remaining
        require((anchor / "termux-proot/.git/HEAD").is_file() and
                any((directory / "talloc.c").is_file() for directory in anchor.glob("talloc-*")), "归档缺少完整上游源码/公开版本元数据")
    print("[native-source] 已恢复/核对 %d 文件 / %d 字节 / %.1f 秒" % (count, byte_count, time.monotonic() - start), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("verify", "rebuild"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--plan", action="store_true", help="输出构建命令，不运行 Gradle/native")
    args = parser.parse_args()
    identity = verify(ROOT)
    require((args.version, args.version_code) == (identity["version"], identity["code"]), "重建请求的版本/code不属于本源码包")
    release = load(ROOT, "prepare-app-release.py")
    command = release.build_command("workspacecheck", args.version, args.version_code)
    if "scripts/development-signing.init.gradle" not in command:
        command[1:1] = ["-I", "scripts/development-signing.init.gradle"]
    if args.command == "verify":
        print("生成发布源码证明通过；未声明设备或真实模型 QA。")
    elif args.plan:
        print(json.dumps({"identity": identity, "command": command}, ensure_ascii=False))
    else:
        location = os.environ.get("TANUKI_WORKSPACECHECK_KEYSTORE")
        require(location, "重建需要 TANUKI_WORKSPACECHECK_KEYSTORE 引用既有开发证书；不会生成替代 key")
        keystore = Path(location).expanduser().resolve()
        require(keystore.is_file() and ROOT not in keystore.parents, "既有开发证书必须位于源码树外")
        restore_native_sources(ROOT)
        release.run(command, "按源码包身份重建 native-inclusive workspacecheck")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print("发布源码验证/重建停止：" + (str(error) if isinstance(error, ValueError) else type(error).__name__), file=sys.stderr)
        sys.exit(1)

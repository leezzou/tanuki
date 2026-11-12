#!/usr/bin/env python3
"""计算发布用 native 缓存身份；实际内容散列覆盖源码/配置/NDK，不依赖 mtime。"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import zipfile

LIBRARIES = ("libproot.so", "libproot-loader.so", "libsupervisor.so")


def require(value, message):
    if not value:
        raise ValueError(message)


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def canonical(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def dependency_sources(root):
    result = {}
    source = root / ".build/sources"
    proot = source / "termux-proot"
    if proot.exists():
        require(proot.is_dir() and not proot.is_symlink() and (proot / ".git").is_dir(), "PRoot 缓存来源无效")
        def git(*args):
            return subprocess.run(["git", "-c", "core.hooksPath=/dev/null", *args], cwd=proot,
                                  capture_output=True, check=True, timeout=30).stdout
        result["prootHead"] = git("rev-parse", "HEAD").decode().strip()
        names = git("ls-files", "--cached", "--others", "--exclude-standard", "-z").decode().split("\0")
        for name in sorted(set(n for n in names if n)):
            path = proot / name
            require(not Path(name).is_absolute() and ".." not in Path(name).parts, "PRoot 源码缓存路径无效")
            if path.is_symlink():
                target = path.resolve()
                require(target.exists() and proot.resolve() in target.parents, "PRoot 源码链接越界")
                result["proot/" + name] = {"link": target.relative_to(proot.resolve()).as_posix(),
                                          "sha256": digest(target) if target.is_file() else None}
            else:
                result["proot/" + name] = digest(path) if path.is_file() else None
    versions = set(re.findall(r"\$SRC/talloc-([0-9.]+)", (root / "runtime/build-native-runtime.sh").read_text()))
    require(len(versions) == 1, "无法识别固定 talloc 输入协议，拒绝复用缓存")
    talloc = source / ("talloc-" + next(iter(versions)))
    for directory in ([talloc] if talloc.exists() else []):
        require(directory.is_dir() and not directory.is_symlink(), "talloc 缓存路径无效")
        for path in sorted(directory.rglob("*")):
            name = path.relative_to(source).as_posix()
            if path.is_symlink():
                target = path.resolve()
                require(target.exists() and directory.resolve() in target.parents, "talloc 源码链接越界")
                result[name] = {"link": target.relative_to(directory.resolve()).as_posix(),
                                "sha256": digest(target) if target.is_file() else None}
            elif path.is_file():
                result[name] = digest(path)
    archive = root / ".build/downloads/talloc.tar.gz"
    if archive.exists():
        require(not archive.is_symlink(), "talloc 下载缓存不能为符号链接")
        result["tallocArchive"] = digest(archive)
    return canonical(result)


def refresh_dependencies(root, value):
    changed = {key: item for key, item in value.items() if key != "sha256"}
    changed["dependencySha256"] = dependency_sources(root)
    return dict(changed, sha256=canonical(changed))


def validate_output_paths(root):
    directory = root / "app/src/main/jniLibs/arm64-v8a"
    require(directory.resolve() == directory and not any((directory / name).is_symlink() for name in LIBRARIES),
            "native 输出路径含符号链接；拒绝重建以免写入其他工作树")


def identity(root, sdk):
    root, sdk = root.resolve(), sdk.resolve()
    validate_output_paths(root)
    script = (root / "runtime/build-native-runtime.sh").read_text()
    match = re.search(r"\$SDK/ndk/([0-9.]+)/toolchains/llvm/prebuilt/([A-Za-z0-9_-]+)", script)
    require(match, "无法识别 native 脚本的固定 NDK 协议，拒绝复用缓存")
    ndk = (sdk / "ndk" / match[1]).resolve()
    require(ndk.is_dir() and (ndk / "source.properties").is_file()
            and (ndk / "toolchains/llvm/prebuilt" / match[2]).is_dir(), "缺少本次源码要求的固定 NDK")
    source = {}
    for directory in ("runtime", "app/src/main/cpp", "gradle"):
        for path in sorted((root / directory).rglob("*")):
            if path.is_file():
                require(not path.is_symlink(), "native 源码缓存输入不能含符号链接")
                source[path.relative_to(root).as_posix()] = digest(path)
    for filename in ("app/build.gradle.kts", "build.gradle.kts", "gradle.properties", "settings.gradle.kts",
                     "scripts/native-cache.init.gradle", "scripts/native-cache-identity.py"):
        path = root / filename
        require(path.is_file() and not path.is_symlink(), "native 构建配置输入缺失")
        source[filename] = digest(path)
    print("[native-cache] 校验完整 NDK 内容身份（非 mtime），正在读取输入", flush=True)
    start, toolchain, byte_count = time.monotonic(), {}, 0
    entries = sorted(ndk.rglob("*"))
    for index, path in enumerate(entries):
        if index and index % 1000 == 0:
            print("[native-cache] 已校验 %d/%d 条输入" % (index, len(entries)), flush=True)
        name = path.relative_to(ndk).as_posix()
        if path.is_symlink():
            target = path.resolve()
            require(target.exists() and ndk in target.parents, "NDK 链接越过固定工具链目录")
            toolchain[name] = {"link": target.relative_to(ndk).as_posix()}
        elif path.is_file():
            byte_count += path.stat().st_size
            require(len(toolchain) < 100000 and byte_count <= 16 * 1024 ** 3, "NDK 输入超过缓存校验上限")
            toolchain[name] = {"sha256": digest(path), "mode": path.stat().st_mode & 0o777}
    print("[native-cache] 已核 %d 项 / %d 字节 / %.1f 秒" % (len(toolchain), byte_count, time.monotonic() - start), flush=True)
    value = {"schema": 1, "abi": "arm64-v8a", "minSdk": 26, "ndkVersion": match[1],
             "sdkRoot": str(sdk), "ndkRoot": str(ndk), "sourceSha256": canonical(source), "toolchainSha256": canonical(toolchain),
             "dependencySha256": dependency_sources(root)}
    return dict(value, sha256=canonical(value))


def outputs(root):
    validate_output_paths(root)
    result = {}
    for filename in LIBRARIES:
        path = root / "app/src/main/jniLibs/arm64-v8a" / filename
        require(path.is_file() and not path.is_symlink(), "native 缓存输出缺失或为符号链接")
        result[filename] = digest(path)
    return result


def matches(root, value, receipt):
    validate_output_paths(root)
    if not receipt or receipt.get("identity") != value:
        return False
    try:
        return outputs(root) == receipt["outputs"]
    except (OSError, ValueError):
        return False


def receipt(root, value, apk):
    actual = outputs(root)
    with zipfile.ZipFile(apk) as archive:
        for name, sha in actual.items():
            require(hashlib.sha256(archive.read("lib/arm64-v8a/" + name)).hexdigest() == sha,
                    "APK 实际 native 字节不属于已核构建输出")
    return {"schema": 1, "identity": value, "outputs": actual}

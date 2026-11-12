#!/usr/bin/env python3
"""按明确版本生成指定渠道 APK、更新清单和对应源码；不创建 tag、不上传。"""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parent.parent
VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?")
PACKAGES = {"workspacecheck": "com.tanuki.modern.workspacecheck", "production": "com.tanuki.modern"}


def validate_version(version, code):
    if len(version) > 80 or not VERSION.fullmatch(version) or not isinstance(code, int) or not 2 <= code <= 2_100_000_000:
        raise ValueError("先确认形如 0.1.1 或 0.1.1-rc.1 的版本，并提供高于所有已安装/已发布版本的 versionCode（至少 2）。")


def build_command(channel, version, code):
    validate_version(version, code)
    if channel == "workspacecheck":
        signing = ["-I", "scripts/development-signing.init.gradle"] if os.environ.get("TANUKI_WORKSPACECHECK_KEYSTORE") else []
        return ["./gradlew", "--no-daemon", "--max-workers=2", *signing, "-I", "scripts/workspace-verification.init.gradle",
                "-PdevelopmentReleaseSigning=true", f"-PtanukiVersionName={version}", f"-PtanukiVersionCode={code}",
                ":app:assembleModernRelease"]
    if channel == "production":
        return ["bash", "scripts/sign-release.sh"]
    raise ValueError("必须明确 workspacecheck 或 production 渠道")


def digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def verified_options(apk, sha256):
    if (apk is None) != (sha256 is None):
        raise ValueError("--verified-apk 和 --verified-apk-sha256 必须同时提供")
    if sha256 is not None and not re.fullmatch(r"[0-9a-fA-F]{64}", sha256):
        raise ValueError("已验证 APK 必须提供完整的 SHA-256")
    return sha256.lower() if sha256 is not None else None


@contextmanager
def verified_copy(apk, sha256):
    expected = verified_options(apk, sha256)
    if apk is None:
        yield None
        return
    source = Path(apk)
    if not source.is_file() or not 1 <= source.stat().st_size <= 256 * 1024 * 1024:
        raise ValueError("已验证 APK 必须是 1 字节至 256MiB 的文件")
    # 先固定待封装字节，再核对哈希和 APK 身份；后续构建改写原路径不会替换已测包。
    with tempfile.TemporaryDirectory(prefix="tanuki-verified-apk-") as temporary:
        staged = Path(temporary) / "verified.apk"
        shutil.copyfile(source, staged)
        if not 1 <= staged.stat().st_size <= 256 * 1024 * 1024 or digest(staged) != expected:
            raise ValueError("已验证 APK 的实际 SHA-256 与指定值不符")
        yield staged


def native_checksum(directory):
    # 发布目录扁平分发，清单不能沿用源码树中的 artifacts/ 相对路径。
    native = directory / "Tanuki-native-corresponding-sources.tar.gz"
    (directory / "native-source-SHA256SUMS").write_text(f"{digest(native)}  {native.name}\n")


def build_apk_path(root, channel, version):
    if channel == "workspacecheck":
        return root / "app/build/outputs/apk/modern/release/app-modern-release.apk"
    return root / "artifacts/builds/production" / version / f"Tanuki-{version}-arm64-release.apk"


def copy_source_attachments(root, directory, version):
    # 构建中间产物按类别保存，完整发布目录仍保持可直接上传的扁平附件结构。
    sources = root / "artifacts/sources"
    archive = f"Tanuki-{version}-source.zip"
    shutil.copy2(sources / archive, directory / archive)
    native = "Tanuki-native-corresponding-sources.tar.gz"
    shutil.copy2(sources / "native" / native, directory / native)
    native_checksum(directory)


def certificate_digest(text):
    values = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", text, re.MULTILINE)
    if len(values) != 1:
        raise ValueError("APK 必须通过签名验证，并且仅有一个当前签名者")
    return values[0].lower()


def entry_for(apk, channel, version, code, *, actual_package, actual_version, actual_code, min_sdk, certificate):
    validate_version(version, code)
    if (actual_package != PACKAGES[channel] or actual_version != version or actual_code != code or
            not 26 <= min_sdk <= 34 or not re.fullmatch(r"[0-9a-f]{64}", certificate) or
            not apk.is_file() or not 1 <= apk.stat().st_size <= 256 * 1024 * 1024):
        raise ValueError("实际 APK 的包名、版本、Android 要求、证书或大小不符合发布请求")
    return dict(packageName=actual_package, versionName=version, versionCode=code, minSdk=min_sdk,
                assetName=f"Tanuki-{version}-{channel}-arm64-release.apk", sha256=digest(apk),
                bytes=apk.stat().st_size, certificateSha256=certificate)


def prior_entries(directory, version, code, document):
    entries = document.get("packages", [])
    if document.get("schemaVersion") != 1 or not isinstance(entries, list) or len(entries) > 8:
        raise ValueError("已有发布清单无效，不覆盖它")
    names = set()
    for entry in entries:
        name = entry.get("assetName", "")
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,159}", name) or entry.get("packageName") in names:
            raise ValueError("已有发布附件名称或包名重复无效")
        names.add(entry.get("packageName"))
        apk = directory / name
        if (entry.get("versionName") != version or entry.get("versionCode") != code or not apk.is_file() or
                apk.stat().st_size != entry.get("bytes") or digest(apk) != entry.get("sha256")):
            raise ValueError("已有发布与请求版本不一致，或附件丢失/已改变；请检查，不自动删除")
    return entries


def run(command, label, *, capture=False):
    print(f"[{label}] 开始", flush=True)
    started = time.monotonic()
    # 仅加载构建环境，不读取/打印整个环境变量，也不把密码放进命令行。
    result = subprocess.run(["bash", "-c", 'source scripts/env.sh; exec "$@"', "--", *map(str, command)],
                            cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE if capture else None)
    print(f"[{label}] 完成 · {time.monotonic() - started:.1f} 秒", flush=True)
    return result.stdout or ""


def inspect_archive(apk, sdk):
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    actual = {}
    for name, command in (("actual_package", "application-id"), ("actual_version", "version-name"),
                          ("actual_code", "version-code"), ("min_sdk", "min-sdk")):
        value = run([analyzer, "manifest", command, apk], f"核对 {command}", capture=True).strip()
        actual[name] = int(value) if name in {"actual_code", "min_sdk"} else value
    signing = run([sdk / "build-tools/34.0.0/apksigner", "verify", "--verbose", "--print-certs", apk], "验证 APK 签名", capture=True)
    actual["certificate"] = certificate_digest(signing)
    return actual


def inspect_workspace_payload(apk, sdk):
    # 实核渠道约束，不能把无原生库的 Host CI APK 当作可运行发布包。
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    target = run([analyzer, "manifest", "target-sdk", apk], "核对 target-sdk", capture=True).strip()
    debuggable = run([analyzer, "manifest", "debuggable", apk], "核对 debuggable", capture=True).strip()
    minimum = run([analyzer, "manifest", "min-sdk", apk], "核对 min-sdk", capture=True).strip()
    if target != "34" or minimum != "26" or debuggable != "false":
        raise ValueError("workspacecheck 必须 min26/target34/non-debuggable")
    with zipfile.ZipFile(apk) as archive:
        libs = {n for n in archive.namelist() if n.startswith("lib/") and n.endswith(".so")}
        required = {"lib/arm64-v8a/" + n for n in ("libproot.so", "libproot-loader.so", "libsupervisor.so")}
        if not required <= libs or any(not n.startswith("lib/arm64-v8a/") for n in libs):
            raise ValueError("workspacecheck 必须仅 ARM64，且包含完整原生运行时")
        for name in required:
            if not 20 <= archive.getinfo(name).file_size <= 64 * 1024 * 1024:
                raise ValueError("原生运行时为空或过大")
            with archive.open(name) as library:
                header = library.read(20)
            if header[:6] != b"\x7fELF\x02\x01" or header[18:20] != b"\xb7\x00":
                raise ValueError("原生运行时必须是 Android ARM64 ELF64")


def pinned_source(commit):
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("源码绑定需要完整 SHA")
    head = run(["git", "rev-parse", "HEAD"], "核对源码提交", capture=True).strip()
    dirty = run(["git", "status", "--porcelain", "--untracked-files=no"], "核对源码状态", capture=True).strip()
    if head != commit or dirty:
        raise ValueError("源码提交不匹配或有已跟踪修改")
    return run(["git", "rev-parse", "HEAD^{tree}"], "核对源码树", capture=True).strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="明确版本；自动合并发布由持久队列按整数递增")
    parser.add_argument("--source-commit", help="绑定干净 checkout 的完整提交 SHA")
    parser.add_argument("--merge-base", help="自动合并模式的上一 first-parent SHA；核对该变更中的 PR 交接记录")
    parser.add_argument("--version-code", required=True, type=int, help="高于所有已发布/安装的版本代码")
    parser.add_argument("--channel", required=True, choices=tuple(PACKAGES))
    parser.add_argument("--verified-apk", type=Path, help="已完成测试的 APK；只封装固定字节，不重新构建")
    parser.add_argument("--verified-apk-sha256", help="已测 APK 的完整 SHA-256，必须与 --verified-apk 成对提供")
    options = parser.parse_args()
    validate_version(options.version, options.version_code)
    pinned = verified_options(options.verified_apk, options.verified_apk_sha256)
    if options.channel == "production" and pinned is None and ("TANUKI_KEYSTORE" not in os.environ or "TANUKI_STORE_PASSWORD" not in os.environ):
        raise ValueError("正式包需要仓库外已有密钥及环境密码；不能生成临时密钥冒充正式更新")
    with verified_copy(options.verified_apk, pinned) as tested_apk:
        prepare(options, tested_apk, pinned)


def prepare(options, tested_apk, pinned):
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", Path.home() / "Library/Android/sdk"))
    directory = ROOT / "artifacts/releases" / options.version
    manifest = directory / "app-update.json"
    prior = prior_entries(directory, options.version, options.version_code, json.loads(manifest.read_text())) if manifest.exists() else []
    source_tree = pinned_source(options.source_commit) if options.source_commit else None
    if options.merge_base:
        if options.channel != "workspacecheck":
            raise ValueError("自动合并交接模式仅适用于 workspacecheck，production 仍需严格发布检查")
        if not options.source_commit or not re.fullmatch(r"[0-9a-f]{40}", options.merge_base):
            raise ValueError("自动合并模式必须绑定提交和上一 first-parent")
        parent = run(["git", "rev-parse", "HEAD^1"], "核对合并父提交", capture=True).strip()
        if parent != options.merge_base:
            raise ValueError("自动合并模式父提交不匹配")
        changed = run(["git", "diff", "--name-only", options.merge_base, options.source_commit], "核对合并变更", capture=True)
        command = [sys.executable, "scripts/check-handoff.py", "--pr"]
        for name in changed.splitlines():
            command += ["--changed-file", name]
        run(command, "确认本次合并交接")
    else:
        run([sys.executable, "scripts/check-handoff.py", "--release"], "确认最新交接")
    if tested_apk is None:
        command = ["env", f"TANUKI_VERSION_NAME={options.version}", f"TANUKI_VERSION_CODE={options.version_code}",
                   *build_command(options.channel, options.version, options.version_code)]
        run(command, "构建指定渠道")
    else:
        print("[复用已测 APK] 哈希已核对，继续验证实际身份与签名", flush=True)
    source_apk = tested_apk or build_apk_path(ROOT, options.channel, options.version)
    entry = entry_for(source_apk, options.channel, options.version, options.version_code, **inspect_archive(source_apk, sdk))
    if options.channel == "workspacecheck":
        inspect_workspace_payload(source_apk, sdk)
        expected_certificate = "371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f"
        if entry["certificateSha256"] != expected_certificate:
            raise ValueError("workspacecheck 必须沿用既有开发证书")
    if pinned is not None and entry["sha256"] != pinned:
        raise ValueError("已验证 APK 在身份核验期间发生变化")
    for old in prior:
        if old["packageName"] != entry["packageName"]:
            old_channel = next((key for key, package in PACKAGES.items() if package == old["packageName"]), None)
            if old_channel is None or entry_for(directory / old["assetName"], old_channel, options.version, options.version_code,
                                                **inspect_archive(directory / old["assetName"], sdk)) != old:
                raise ValueError("已有其他渠道 APK 的实际身份与清单不符")
    directory.mkdir(parents=True, exist_ok=True)
    destination = directory / entry["assetName"]
    shutil.copy2(source_apk, destination)
    if digest(destination) != entry["sha256"]:
        raise ValueError("发布 APK 副本与已核验字节不一致")
    entries = [item for item in prior if item["packageName"] != entry["packageName"]] + [entry]
    document = dict(schemaVersion=1, packages=entries)
    if options.source_commit:
        document.update(sourceCommit=options.source_commit, sourceTree=source_tree)
    manifest.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n")
    run(["bash", "scripts/package-native-sources.sh"], "打包原生对应源码")
    run([sys.executable, "scripts/package-source.py", "--version", options.version], "打包本次完整源码")
    source_zip = f"Tanuki-{options.version}-source.zip"
    copy_source_attachments(ROOT, directory, options.version)
    selected = [directory / item["assetName"] for item in entries] + [manifest, directory / source_zip,
                directory / "Tanuki-native-corresponding-sources.tar.gz", directory / "native-source-SHA256SUMS"]
    (directory / "RELEASE-SHA256SUMS").write_text("".join(f"{digest(path)}  {path.name}\n" for path in selected))
    if options.source_commit and pinned_source(options.source_commit) != source_tree:
        raise ValueError("打包期间源码树发生变化")
    print(f"完成：{directory.relative_to(ROOT)}；未创建 tag、上传 Release 或操作设备。")
    print("发布时把该目录附件上传到同一 Release；workspacecheck 必须标为 Pre-release。")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"打包停止：{error}", file=sys.stderr)
        raise SystemExit(64)

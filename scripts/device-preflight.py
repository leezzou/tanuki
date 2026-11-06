#!/usr/bin/env python3
"""设备验收前置门禁：只读核验 APK 身份、设备能力与忙碌状态。

设计边界：
- 只读取普通构建/设备 metadata，不读取 App 私有文件、Key、Cookie 或 token。
- 不安装、不启动、不修改设备；安装与仪器仍由 verify-workspace.sh 负责。
- 失败即失败，不跳过；`--phase post` 用于确认 install -r 后实际生效的版本。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
BUSY_PROCESS = re.compile(
    r"^(apt|apt-get|dpkg|dpkg-deb|node|proot|supervisor|libproot\.so|libsupervisor\.so)$",
    re.IGNORECASE,
)
REQUIRED_LIBS = (
    "lib/arm64-v8a/libproot.so",
    "lib/arm64-v8a/libproot-loader.so",
    "lib/arm64-v8a/libsupervisor.so",
)


class GateError(Exception):
    """门禁失败；消息只包含普通 metadata。"""


def fail(message: str) -> None:
    raise GateError(message)


def run(argv: list[str]) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(argv, capture_output=True, text=True, check=False)
    except OSError as error:
        fail(f"无法执行 {Path(argv[0]).name}: {error}")


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def apkanalyzer_tool(sdk: Path) -> Path:
    tool = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    if not tool.is_file():
        fail(f"缺少 apkanalyzer: {tool}")
    return tool


def apkanalyzer(sdk: Path, apk: Path, field: str) -> str:
    result = run([str(apkanalyzer_tool(sdk)), "manifest", field, str(apk)])
    if result.returncode != 0:
        fail(f"apkanalyzer {field} 失败: {result.stderr.strip()[:300]}")
    value = result.stdout.strip()
    if not value:
        fail(f"apkanalyzer {field} 未返回值")
    return value


def apkanalyzer_optional(sdk: Path, apk: Path, field: str) -> str | None:
    # androidTest manifest 通常不继承应用的 versionCode/versionName；
    # 缺失是合法状态，不能因此阻断设备验收。
    result = run([str(apkanalyzer_tool(sdk)), "manifest", field, str(apk)])
    if result.returncode != 0:
        return None
    value = result.stdout.strip()
    return value or None


def instrumentation_target(sdk: Path, apk: Path) -> str:
    text = apkanalyzer(sdk, apk, "print")
    try:
        root = ET.fromstring(text)
    except ET.ParseError as error:
        fail(f"无法解析 apkanalyzer manifest print: {error}")
    items = root.findall("instrumentation")
    if len(items) != 1:
        fail(f"测试 APK 必须恰好有一个 instrumentation，实际 {len(items)}")
    return items[0].get(ANDROID_NS + "targetPackage", "")


def apksigner_path(sdk: Path) -> Path:
    build_tools = sdk / "build-tools"
    preferred = [build_tools / "35.0.0/apksigner", build_tools / "34.0.0/apksigner"]
    for candidate in preferred:
        if candidate.is_file():
            return candidate
    candidates = sorted(build_tools.glob("*/apksigner"), reverse=True) if build_tools.is_dir() else []
    if not candidates:
        fail(f"缺少 apksigner: {build_tools}")
    return candidates[0]


def certificate_sha256(sdk: Path, apk: Path) -> str:
    tool = apksigner_path(sdk)
    result = run([str(tool), "verify", "--verbose", "--print-certs", str(apk)])
    if result.returncode != 0:
        fail(f"APK 签名校验失败: {apk.name}: {result.stderr.strip()[:300]}")
    values = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$",
        result.stdout,
        re.MULTILINE,
    )
    if len(values) != 1:
        fail(f"{apk.name} 必须只有一个签名者，实际 {len(values)}")
    return values[0].lower()


def apk_abis(apk: Path) -> tuple[list[str], set[str]]:
    try:
        with zipfile.ZipFile(apk) as archive:
            names = set(archive.namelist())
    except (OSError, zipfile.BadZipFile) as error:
        fail(f"无法读取 APK {apk.name}: {error}")
    abis = sorted({name.split("/")[1] for name in names if name.startswith("lib/") and name.count("/") >= 2})
    return abis, names


def adb(adb_path: Path, serial: str, *args: str) -> subprocess.CompletedProcess[str]:
    return run([str(adb_path), "-s", serial, *args])


def device_prop(adb_path: Path, serial: str, name: str) -> str:
    result = adb(adb_path, serial, "shell", "getprop", name)
    if result.returncode != 0:
        fail(f"读取 {name} 失败: {result.stderr.strip()[:200]}")
    return result.stdout.strip()


def device_webview(adb_path: Path, serial: str, minimum: int) -> str:
    result = adb(adb_path, serial, "shell", "dumpsys", "webviewupdate")
    if result.returncode != 0:
        fail(f"读取 WebView 状态失败: {result.stderr.strip()[:200]}")
    match = re.search(
        r"Current WebView package \(name, version\):\s*\(([^,]+),\s*([^)]+)\)",
        result.stdout,
    )
    if not match:
        fail("无法确认当前 WebView 包与版本")
    version = match.group(2).strip()
    try:
        major = int(version.split(".")[0])
    except ValueError:
        fail(f"WebView 版本格式异常: {version}")
    if major < minimum:
        fail(f"WebView {version} 低于要求的最低主版本 {minimum}")
    return version


def busy_processes(adb_path: Path, serial: str) -> list[str]:
    result = adb(adb_path, serial, "shell", "ps -A -o NAME")
    if result.returncode != 0:
        fail(f"无法读取设备进程名: {result.stderr.strip()[:200]}")
    names = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if names and names[0].upper() == "NAME":
        names = names[1:]
    return sorted({name for name in names if BUSY_PROCESS.match(name)})


def active_install_sessions(adb_path: Path, serial: str) -> list[str]:
    staged = adb(adb_path, serial, "shell", "pm list staged-sessions")
    if staged.returncode == 0:
        text = staged.stdout.strip()
        if text and not text.lower().startswith("error"):
            entries = [line.strip() for line in text.splitlines() if re.search(r"Session \d+", line)]
            if entries:
                return entries
    # fallback：部分镜像的 pm 子命令不可用，改读 dumpsys 的 Active install sessions 段。
    dump = adb(adb_path, serial, "shell", "dumpsys package")
    if dump.returncode != 0:
        fail("无法确认在途安装会话：pm 与 dumpsys 均失败")
    lines = dump.stdout.splitlines()
    try:
        start = next(index for index, line in enumerate(lines) if line.strip() == "Active install sessions:")
    except StopIteration:
        fail("无法确认在途安装会话：dumpsys 缺少 Active install sessions 段")
    sessions: list[str] = []
    for line in lines[start + 1:]:
        if line and not line.startswith((" ", "\t")):
            break
        # Android 14 输出 "Active Session 7:" / "Active Child Session 7:"；
        # 旧格式可能是 "Session 7:"，两者都只在 Active 段内计数。
        if re.match(r"^\s+(?:Active\s+(?:Child\s+)?Session|Session)\s+\d+:", line):
            sessions.append(line.strip())
    return sessions


def installed(adb_path: Path, serial: str, package: str) -> dict[str, object] | None:
    result = adb(adb_path, serial, "shell", "dumpsys", "package", package)
    if result.returncode != 0:
        fail(f"读取已安装包 {package} 失败: {result.stderr.strip()[:200]}")
    text = result.stdout
    if "Unable to find package" in text:
        return None
    code = re.search(r"\bversionCode=(\d+)", text)
    name = re.search(r"\bversionName=(\S+)", text)
    if not code or not name:
        fail(f"无法解析已安装包 {package} 的版本信息")
    return {"versionCode": int(code.group(1)), "versionName": name.group(1)}


def optional_int(value: str | None, label: str) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except ValueError:
        fail(f"{label} 格式异常: {value}")


def app_fields(sdk: Path, apk: Path) -> dict[str, object]:
    version_name = apkanalyzer(sdk, apk, "version-name")
    return {
        "path": str(apk),
        "sha256": digest(apk),
        "packageName": apkanalyzer(sdk, apk, "application-id"),
        "versionName": version_name,
        "versionCode": optional_int(apkanalyzer(sdk, apk, "version-code"), "应用 versionCode"),
        "minSdk": optional_int(apkanalyzer(sdk, apk, "min-sdk"), "应用 minSdk"),
    }


def test_fields(sdk: Path, apk: Path) -> dict[str, object]:
    return {
        "path": str(apk),
        "sha256": digest(apk),
        "packageName": apkanalyzer(sdk, apk, "application-id"),
        "versionName": apkanalyzer_optional(sdk, apk, "version-name"),
        "versionCode": optional_int(apkanalyzer_optional(sdk, apk, "version-code"), "测试 APK versionCode"),
        "minSdk": optional_int(apkanalyzer_optional(sdk, apk, "min-sdk"), "测试 APK minSdk"),
    }


def check_identity(sdk: Path, app_apk: Path, test_apk: Path, package: str,
                   expected_sha: str | None, expected_cert: str | None) -> dict[str, object]:
    for apk in (app_apk, test_apk):
        if not apk.is_file():
            fail(f"APK 不存在: {apk}")
    app = app_fields(sdk, app_apk)
    test = test_fields(sdk, test_apk)
    if app["packageName"] != package:
        fail(f"应用 APK 包名不符: 期望 {package}，实际 {app['packageName']}")
    if test["packageName"] != package + ".test":
        fail(f"测试 APK 包名不符: 期望 {package}.test，实际 {test['packageName']}")
    # AGP 的 androidTest manifest 不继承应用 versionCode/versionName；
    # 测试 APK 允许缺失或独立版本，分别记录，不做相等要求。
    if app["minSdk"] is None or int(app["minSdk"]) < 26:
        fail(f"APK minSdk={app['minSdk']} 低于项目要求 26")
    if instrumentation_target(sdk, test_apk) != package:
        fail("instrumentation target 与应用包名不一致")
    if expected_sha and app["sha256"] != expected_sha.lower():
        fail(f"APK SHA-256 与预期不符: {app['sha256']}")
    app_cert = certificate_sha256(sdk, app_apk)
    test_cert = certificate_sha256(sdk, test_apk)
    if app_cert != test_cert:
        fail("应用/测试 APK 签名证书不一致")
    if expected_cert and app_cert != expected_cert.lower():
        fail(f"APK 证书 SHA-256 与预期不符: {app_cert}")
    abis, names = apk_abis(app_apk)
    if "arm64-v8a" not in abis:
        fail(f"APK 缺少 arm64-v8a: {abis}")
    missing = [name for name in REQUIRED_LIBS if name not in names]
    if missing:
        fail("APK 缺少原生运行时 " + ", ".join(missing) + "；CI 编译包不能用于设备验收")
    app["abis"] = abis
    app["certificateSha256"] = app_cert
    test["certificateSha256"] = test_cert
    return {"app": app, "test": test}


def evidence_path(explicit: str | None, phase: str) -> Path:
    if explicit:
        path = Path(explicit)
    else:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        path = Path(".build/logs") / f"device-preflight-{phase}-{stamp}.json"
    if not path.is_absolute():
        path = Path.cwd() / path
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def write_evidence(path: Path, payload: dict[str, object]) -> None:
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def commit_sha() -> str:
    result = run(["git", "rev-parse", "HEAD"])
    return result.stdout.strip() if result.returncode == 0 else "unknown"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--phase", choices=("pre", "post"), required=True)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--app-apk", required=True)
    parser.add_argument("--test-apk", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--expect-apk-sha256")
    parser.add_argument("--expect-cert-sha256")
    parser.add_argument("--min-webview-major", type=int, default=123)
    parser.add_argument("--evidence")
    options = parser.parse_args()

    adb_path = Path(options.adb)
    sdk = Path(options.sdk)
    app_apk = Path(options.app_apk).resolve()
    test_apk = Path(options.test_apk).resolve()
    output = evidence_path(options.evidence, options.phase)
    payload: dict[str, object] = {
        "schema": 1,
        "phase": options.phase,
        "status": "pass",
        "commit": commit_sha(),
        "serial": options.serial,
        "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "package": options.package,
        "checks": {},
    }
    try:
        identity = check_identity(
            sdk, app_apk, test_apk, options.package,
            options.expect_apk_sha256, options.expect_cert_sha256,
        )
    except GateError as error:
        payload["status"] = "fail"
        payload["error"] = str(error)
        write_evidence(output, payload)
        print(f"设备验收门禁失败: {error}", file=sys.stderr)
        return 2

    app = identity["app"]
    test = identity["test"]
    payload["app"] = app
    payload["test"] = test

    if options.phase == "post":
        try:
            installed_app = installed(adb_path, options.serial, options.package)
            installed_test = installed(adb_path, options.serial, options.package + ".test")
            if installed_app is None or installed_test is None:
                fail("install -r 后未找到应用或测试包")
            if (installed_app["versionCode"] != app["versionCode"]
                    or installed_app["versionName"] != app["versionName"]):
                fail(
                    "install -r 后已安装版本与 APK 不一致: "
                    f"{installed_app['versionName']}/{installed_app['versionCode']} != "
                    f"{app['versionName']}/{app['versionCode']}"
                )
        except GateError as error:
            payload["status"] = "fail"
            payload["error"] = str(error)
            write_evidence(output, payload)
            print(f"设备验收门禁失败: {error}", file=sys.stderr)
            return 2
        payload["installed"] = installed_app
        payload["checks"] = {"installedVersion": "ok", "testPackage": "ok"}
        write_evidence(output, payload)
        print(f"设备验收门禁通过（post）: {installed_app['versionName']}/{installed_app['versionCode']}；证据: {output}")
        return 0

    try:
        abi = device_prop(adb_path, options.serial, "ro.product.cpu.abi")
        api = device_prop(adb_path, options.serial, "ro.build.version.sdk")
        release = device_prop(adb_path, options.serial, "ro.build.version.release")
        webview = device_webview(adb_path, options.serial, options.min_webview_major)
        if abi != "arm64-v8a":
            fail(f"设备 ABI 为 {abi}，需要 arm64-v8a")
        before = installed(adb_path, options.serial, options.package)
        if before is not None and int(before["versionCode"]) > int(app["versionCode"]):
            fail(
                "APK versionCode 低于已安装版本，install -r 会因降级失败: "
                f"APK {app['versionCode']} < 已安装 {before['versionCode']}；"
                "请用 -PtanukiVersionCode 传入更高值构建"
            )
        busy = busy_processes(adb_path, options.serial)
        if busy:
            fail("设备存在 apt/dpkg/Node/PRoot/supervisor 活动进程: " + ", ".join(busy))
        sessions = active_install_sessions(adb_path, options.serial)
        if sessions:
            fail(f"设备存在活动安装会话: {len(sessions)} 个")
    except GateError as error:
        payload["status"] = "fail"
        payload["error"] = str(error)
        write_evidence(output, payload)
        print(f"设备验收门禁失败: {error}", file=sys.stderr)
        return 2

    payload["device"] = {"api": api, "release": release, "abi": abi, "webview": webview}
    payload["installedBefore"] = before
    payload["busyProcesses"] = busy
    payload["activeInstallSessions"] = len(sessions)
    payload["checks"] = {
        "package": "ok",
        "instrumentationTarget": "ok",
        "version": "ok",
        "signature": "ok",
        "abi": "ok",
        "nativeLibraries": "ok",
        "webview": "ok",
        "busyProcesses": "ok",
        "activeInstallSessions": "ok",
    }
    write_evidence(output, payload)
    print(
        f"设备验收门禁通过（pre）: {app['versionName']}/{app['versionCode']} "
        f"{app['sha256'][:12]}… API{api} {abi} WebView {webview}；证据: {output}"
    )
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except GateError as error:
        print(f"设备验收门禁失败: {error}", file=sys.stderr)
        sys.exit(2)

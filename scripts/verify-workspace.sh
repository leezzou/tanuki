#!/bin/bash
# Safe default: build only. Device tests require an explicit device and test-data acknowledgement.
set -euo pipefail
usage() {
  printf '%s\n' \
    'Usage: bash scripts/verify-workspace.sh [--run-device-tests --confirm-test-data --device SERIAL]' \
    '       [--expect-apk-sha256 SHA256] [--expect-cert-sha256 SHA256] [--min-webview-major N]' \
    'Default: build isolated workspacecheck app/test APKs, JVM tests and lint; no device changes.' \
    'Device tests install in place and may change the test environment/settings. Use ONLY test data.' \
    'Prepare Ubuntu -> Node -> tools -> DSH first; stop DSH/installers before device tests.' \
    'Device runs verify APK hash/package/version/signature/ABI/native libs and WebView, and refuse' \
    'when apt/dpkg/Node/PRoot/supervisor or an active install session is present. Device runs are' \
    'serialized by a per-serial process lock; direct adb or TANUKI_DEVICE_LOCK_HELD=1 bypasses it.'
}
run_device=false
confirmed=false
explicit_device=false
device="${TANUKI_DEVICE:-}"
expect_apk_sha256=""
expect_cert_sha256=""
min_webview_major=123
original_args=("$@")
while [ "$#" -gt 0 ]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --run-device-tests) run_device=true; shift ;;
    --confirm-test-data) confirmed=true; shift ;;
    --device)
      [ "$#" -ge 2 ] && [ -n "$2" ] && [[ "$2" != --* ]] || { usage >&2; exit 2; }
      device="$2"; explicit_device=true; shift 2 ;;
    --expect-apk-sha256)
      [ "$#" -ge 2 ] && [ -n "$2" ] || { usage >&2; exit 2; }
      expect_apk_sha256="$2"; shift 2 ;;
    --expect-cert-sha256)
      [ "$#" -ge 2 ] && [ -n "$2" ] || { usage >&2; exit 2; }
      expect_cert_sha256="$2"; shift 2 ;;
    --min-webview-major)
      [ "$#" -ge 2 ] && [ -n "$2" ] || { usage >&2; exit 2; }
      min_webview_major="$2"; shift 2 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done
if [ "$run_device" = true ]; then
  [ "$confirmed" = true ] && [ -n "$device" ] || {
    printf '%s\n' 'Refusing device changes: pass --confirm-test-data and --device SERIAL (or TANUKI_DEVICE).' >&2
    exit 2
  }
  for value in "$expect_apk_sha256" "$expect_cert_sha256"; do
    [ -z "$value" ] || [[ "$value" =~ ^[0-9a-fA-F]{64}$ ]] || {
      printf 'Expected SHA-256 must be 64 hex characters: %s\n' "$value" >&2; exit 2;
    }
  done
  [[ "$min_webview_major" =~ ^[1-9][0-9]{0,3}$ ]] || {
    printf 'Invalid --min-webview-major: %s\n' "$min_webview_major" >&2; exit 2;
  }
elif [ "$confirmed" = true ] || [ "$explicit_device" = true ] || [ -n "$expect_apk_sha256" ] \
    || [ -n "$expect_cert_sha256" ] || [ "$min_webview_major" != 123 ]; then
  printf '%s\n' 'Device options require --run-device-tests; omit them for a build-only run.' >&2
  exit 2
fi
# 设备任务从构建前就持有 per-serial 进程锁；锁 fd 传给子任务，父进程退出也不提前释放。
if [ "$run_device" = true ] && [ "${TANUKI_DEVICE_LOCK_HELD:-}" != "1" ]; then
  export TANUKI_DEVICE_LOCK_HELD=1
  script="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
  exec python3 "$(dirname "$0")/device-lock.py" --serial "$device" -- bash "$script" "${original_args[@]}"
fi
source "$(dirname "$0")/env.sh"
cd "$TANUKI_PROJECT"
# 构建/设备门禁按普通 PR 模式校验本分支交接；共享状态由集成方刷新。
python3 scripts/check-handoff.py --pr
app_id=com.tanuki.modern.workspacecheck
app_apk=app/build/outputs/apk/modern/debug/app-modern-debug.apk
test_apk=app/build/outputs/apk/androidTest/modern/debug/app-modern-debug-androidTest.apk
if [ "$run_device" = true ]; then
  export TANUKI_DEVICE="$device"
  [ -x "$TANUKI_SDK/cmdline-tools/latest/bin/apkanalyzer" ] || {
    printf '%s\n' 'Install Android SDK cmdline-tools/latest (apkanalyzer required).' >&2; exit 2;
  }
  select_device
  abi="$("$TANUKI_ADB" -s "$TANUKI_DEVICE" shell getprop ro.product.cpu.abi | tr -d '\r')"
  [ "$abi" = arm64-v8a ] || { printf 'Unsupported test device ABI: %s (arm64-v8a required).\n' "$abi" >&2; exit 2; }
fi
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest :app:lintModernDebug
if [ "$run_device" != true ]; then
  printf '\nBuild-only complete; no APK was installed.\nApp: %s\nTests: %s\n' "$app_apk" "$test_apk"
  exit 0
fi
# Standard and isolated builds share output paths. Never trust a filename as package identity.
# 门禁在安装前核验 APK 身份/签名/ABI/原生库与设备忙碌状态；失败即中止，不安装。
preflight=(python3 scripts/device-preflight.py --phase pre
  --adb "$TANUKI_ADB" --sdk "$TANUKI_SDK" --serial "$TANUKI_DEVICE"
  --app-apk "$app_apk" --test-apk "$test_apk" --package "$app_id"
  --min-webview-major "$min_webview_major")
[ -z "$expect_apk_sha256" ] || preflight+=(--expect-apk-sha256 "$expect_apk_sha256")
[ -z "$expect_cert_sha256" ] || preflight+=(--expect-cert-sha256 "$expect_cert_sha256")
"${preflight[@]}"
classes='com.tanuki.RuntimeTreeCleanupTest,com.tanuki.WorkspaceFileManagerTest,com.tanuki.BrowserIntegrationTest,com.tanuki.AgentBrowserControlTest,com.tanuki.BrowserLibraryTest,com.tanuki.BrowserWorkspacePresentationTest,com.tanuki.FilesBrowserPresentationTest,com.tanuki.SetupPresentationTest,com.tanuki.WorkspaceStorageUiTest,com.tanuki.BrowserOverlayPresentationTest,com.tanuki.BrowserTabLifecycleTest,com.tanuki.DshViewportTest,com.tanuki.WebAuthenticationTest,com.tanuki.RuntimeFoundationToolsTest'
mkdir -p .build/logs
log=".build/logs/workspace-regression-$(date -u +%Y%m%dT%H%M%SZ)-$$.txt"
printf 'Running on explicitly selected test device %s; log: %s\n' "$TANUKI_DEVICE" "$log"
# No uninstall, pm clear, force-stop, system-image reset, permission bypass or model invocation.
"$TANUKI_ADB" -s "$TANUKI_DEVICE" install -r "$app_apk"
"$TANUKI_ADB" -s "$TANUKI_DEVICE" install -r "$test_apk"
# install -r 返回 0 仍须确认实际生效版本，防止旧包/错误包被继续测试。
python3 scripts/device-preflight.py --phase post \
  --adb "$TANUKI_ADB" --sdk "$TANUKI_SDK" --serial "$TANUKI_DEVICE" \
  --app-apk "$app_apk" --test-apk "$test_apk" --package "$app_id"
if ! "$TANUKI_ADB" -s "$TANUKI_DEVICE" shell am instrument -w -r -e class "$classes" \
  "$app_id.test/androidx.test.runner.AndroidJUnitRunner" > "$log" 2>&1; then
  printf 'ADB/instrumentation command failed; inspect private log %s\n' "$log" >&2
  exit 1
fi
# am instrument can exit zero even when JUnit fails. Skipped prerequisites are NOT a clean pass.
python3 - "$log" <<'PY'
import pathlib, re, sys
path = pathlib.Path(sys.argv[1])
text = path.read_text(errors="replace").replace("\r", "")
summary = re.findall(r"^OK \((\d+) tests?\)\s*$", text, flags=re.M)
codes = re.findall(r"^INSTRUMENTATION_STATUS_CODE: (-?\d+)\s*$", text, flags=re.M)
if (len(summary) != 1 or int(summary[0]) <= 0
        or re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed", text)
        or any(code in {"-1", "-2", "-3", "-4"} for code in codes)
        or codes.count("0") != int(summary[0])):
    print(f"FAIL/incomplete/skipped: inspect {path}; verify runtime/WebView prerequisites.", file=sys.stderr)
    raise SystemExit(1)
print(f"PASS: {summary[0]} Android tests; detailed private log: {path}")
PY

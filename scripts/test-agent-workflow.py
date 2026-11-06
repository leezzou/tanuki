#!/usr/bin/env python3
"""离线宿主回归：交接有效性与模拟验证保护，不调用真实ADB/Gradle。"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent.parent
APP = "com.tanuki.modern.workspacecheck"
RECORD = "docs/handoffs/test-session.md"


class WorkflowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tanuki-agent-workflow-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "project with spaces"
        (self.root / "scripts").mkdir(parents=True)
        self.env = {**os.environ, "TANUKI_DEVICE": "", "VERIFIER_EVENTS": str(self.root / "events.jsonl"),
                    "TANUKI_DEVICE_LOCK_DIR": str(self.root / "device-locks")}

    def run_cmd(self, *args, **env):
        return subprocess.run(args, cwd=self.root, env={**self.env, **env}, capture_output=True, text=True)

    def put(self, name, text, executable=False):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        if executable:
            path.chmod(0o755)
        return path

    def handoff_fixture(self):
        shutil.copy2(ROOT / "scripts/check-handoff.py", self.root / "scripts/check-handoff.py")
        self.put("scripts/package-source.py", '''from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
FILES = ("AGENTS.md", "README.md")
TREES = ("scripts", "docs", "app/src")
def public_file(path):
    return path.is_file() and "__pycache__" not in path.parts
''')
        self.put("AGENTS.md", "Agent rules\n")
        self.put("README.md", "Public project\n")
        self.put("app/src/main/example.kt", "// original\n")
        self.put("docs/AGENT-STATE.md", "[Latest](handoffs/test-session.md)\n")
        headings = ("用户目标与范围", "当前状态", "本次改动", "验证记录", "未完成与暂停项", "下一步", "约束与风险", "产物与校验")
        self.put(RECORD, "# Synthetic handoff\n" + "\n".join("## " + h + "\nSynthetic evidence.\n" for h in headings))
        result = self.checker("--record", RECORD)
        self.assertEqual(0, result.returncode, result.stderr)

    def checker(self, *args):
        return self.run_cmd(sys.executable, "scripts/check-handoff.py", *args)

    def test_workspace_isolation_applies_to_debug_and_release(self):
        script = (ROOT / "scripts/workspace-verification.init.gradle").read_text()
        for variant in ("debug", "release"):
            self.assertIn(f"getByName('{variant}').applicationIdSuffix = '.workspacecheck'", script)

    def test_release_signing_requires_keystore_outside_repository(self):
        self.runner_fixture()
        shutil.copy2(ROOT / "scripts/sign-release.sh", self.root / "scripts/sign-release.sh")
        self.put("docs/ARTIFACTS.md", "合成产物目录说明\n")
        inside = self.put("keys/release.jks", "synthetic-keystore")
        outside = Path(self.temp.name) / "release.jks"
        outside.write_text("synthetic-keystore")
        link = Path(self.temp.name) / "linked.jks"
        link.symlink_to(inside)
        for value in ("keys/release.jks", "./keys/release.jks", str(inside), str(link), "missing.jks"):
            result = self.run_cmd("bash", "scripts/sign-release.sh", TANUKI_KEYSTORE=value,
                                  TANUKI_STORE_PASSWORD="synthetic-password")
            self.assertEqual(64, result.returncode, result.stderr)
            self.assertFalse((self.root / "events.jsonl").exists())
        self.put("sdk/build-tools/34.0.0/apksigner", '''#!/usr/bin/env python3
import pathlib, sys
args = sys.argv[1:]
if args[0] == 'sign':
    pathlib.Path(args[args.index('--out') + 1]).write_bytes(b'synthetic-apk')
''', True)
        result = self.run_cmd("bash", "scripts/sign-release.sh", TANUKI_KEYSTORE=str(outside),
                              TANUKI_STORE_PASSWORD="synthetic-password", TANUKI_VERSION_NAME="0.1.1", TANUKI_VERSION_CODE="2")
        self.assertEqual(0, result.returncode, result.stderr)
        events = [json.loads(line) for line in (self.root / "events.jsonl").read_text().splitlines()]
        self.assertIn(["gradlew", "-PdevelopmentReleaseSigning=false", "-PtanukiVersionName=0.1.1", "-PtanukiVersionCode=2", ":app:assembleModernRelease"], events)
        folder = self.root / "artifacts/builds/production/0.1.1"
        self.assertEqual(b"synthetic-apk", (folder / "Tanuki-0.1.1-arm64-release.apk").read_bytes())
        self.assertTrue((folder / "release-signature.txt").is_file())
        self.assertTrue((folder / "release-SHA256SUMS").read_text().endswith("  Tanuki-0.1.1-arm64-release.apk\n"))
        self.assertFalse(list((self.root / "artifacts").glob("*.apk")))

    def test_clean_handoff_and_move_are_portable(self):
        self.handoff_fixture()
        self.assertEqual(0, self.checker().returncode)
        moved = self.root.with_name("moved project")
        shutil.copytree(self.root, moved)
        result = subprocess.run([sys.executable, "scripts/check-handoff.py"], cwd=moved, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_code_change_requires_written_handoff_not_just_restamping(self):
        self.handoff_fixture()
        self.put("app/src/main/example.kt", "// changed\n")
        self.assertNotEqual(0, self.checker().returncode)
        self.assertNotEqual(0, self.checker("--record", RECORD).returncode)
        with (self.root / RECORD).open("a") as output:
            output.write("\nUpdated: actual code change and verification.\n")
        self.assertEqual(0, self.checker("--record", RECORD).returncode)
        self.assertEqual(0, self.checker().returncode)

    def test_missing_section_and_wrong_current_link_are_rejected(self):
        self.handoff_fixture()
        self.put("docs/AGENT-STATE.md", "No current record link\n")
        self.assertNotEqual(0, self.checker().returncode)
        self.put("docs/AGENT-STATE.md", "[Latest](handoffs/test-session.md)\n")
        record = self.root / RECORD
        record.write_text(record.read_text().replace("## 验证记录", "## Removed"))
        self.assertNotEqual(0, self.checker("--record", RECORD).returncode)

    def test_generated_jni_does_not_invalidate_source_handoff(self):
        self.handoff_fixture()
        self.put("app/src/main/jniLibs/arm64-v8a/libsynthetic.so", "generated ELF placeholder")
        self.assertEqual(0, self.checker().returncode)

    def test_record_traversal_template_and_symlink_are_rejected(self):
        self.handoff_fixture()
        for bad in ("../record.md", "/tmp/record.md", "docs/handoffs/TEMPLATE.md"):
            with self.subTest(path=bad):
                self.assertNotEqual(0, self.checker("--record", bad).returncode)
        (self.root / "docs/handoffs/link.md").symlink_to("test-session.md")
        self.put("docs/AGENT-STATE.md", "[Latest](handoffs/link.md)\n")
        self.assertNotEqual(0, self.checker("--record", "docs/handoffs/link.md").returncode)

    def apk_paths(self):
        return (self.root / "app/build/outputs/apk/modern/debug/app-modern-debug.apk",
                self.root / "app/build/outputs/apk/androidTest/modern/debug/app-modern-debug-androidTest.apk")

    def make_apk(self, path, libs=True):
        path.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("AndroidManifest.xml", "synthetic")
            if libs:
                for name in ("libproot.so", "libproot-loader.so", "libsupervisor.so"):
                    archive.writestr(f"lib/arm64-v8a/{name}", "synthetic")

    def runner_fixture(self):
        shutil.copy2(ROOT / "scripts/verify-workspace.sh", self.root / "scripts/verify-workspace.sh")
        shutil.copy2(ROOT / "scripts/device-preflight.py", self.root / "scripts/device-preflight.py")
        shutil.copy2(ROOT / "scripts/device-lock.py", self.root / "scripts/device-lock.py")
        self.put("scripts/check-handoff.py", "print('Synthetic handoff guard')\n")
        self.put("scripts/env.sh", '''set -euo pipefail
TANUKI_PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TANUKI_SDK="$TANUKI_PROJECT/sdk"
TANUKI_ADB="$TANUKI_SDK/platform-tools/adb"
select_device() { "$TANUKI_ADB" -s "$TANUKI_DEVICE" get-state >/dev/null; }
''')
        app_apk, test_apk = self.apk_paths()
        self.make_apk(app_apk)
        self.make_apk(test_apk)
        common = '''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ['VERIFIER_EVENTS'], 'a') as f:
    f.write(json.dumps([pathlib.Path(sys.argv[0]).name, *args]) + '\\n')
'''
        self.put("gradlew", common + "import time; time.sleep(float(os.environ.get('FAKE_BUILD_SLEEP', '0')))\nraise SystemExit(int(os.environ.get('FAKE_BUILD_EXIT', '0')))\n", True)
        self.put("sdk/cmdline-tools/latest/bin/apkanalyzer", common + f'''
app = {APP!r}
apk = args[-1]
if args[:2] == ['manifest', 'application-id']:
    print(app + '.test' if 'androidTest' in apk else os.environ.get('FAKE_APP', app))
elif args[:2] == ['manifest', 'version-name']:
    if 'androidTest' in apk:
        if os.environ.get('FAKE_TEST_VERSION_NAME'): print(os.environ['FAKE_TEST_VERSION_NAME'])
        else: raise SystemExit(2)
    else:
        print(os.environ.get('FAKE_VERSION_NAME', '0.1.7'))
elif args[:2] == ['manifest', 'version-code']:
    if 'androidTest' in apk:
        if os.environ.get('FAKE_TEST_VERSION_CODE'): print(os.environ['FAKE_TEST_VERSION_CODE'])
        else: raise SystemExit(2)
    else:
        print(os.environ.get('FAKE_VERSION_CODE', '9'))
elif args[:2] == ['manifest', 'min-sdk']:
    if 'androidTest' in apk:
        if os.environ.get('FAKE_TEST_MIN_SDK'): print(os.environ['FAKE_TEST_MIN_SDK'])
        else: raise SystemExit(2)
    else:
        print(os.environ.get('FAKE_MIN_SDK', '26'))
elif args[:2] == ['manifest', 'print']:
    target = os.environ.get('FAKE_TARGET', app)
    print('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><instrumentation android:targetPackage="' + target + '"/></manifest>')
else:
    raise SystemExit(2)
''', True)
        self.put("sdk/build-tools/35.0.0/apksigner", common + '''
if os.environ.get('FAKE_SIGN_BAD') == '1':
    print('ERROR: synthetic bad signature')
    raise SystemExit(1)
apk = args[-1]
cert = os.environ.get('FAKE_TEST_CERT' if 'androidTest' in apk else 'FAKE_CERT', '1' * 64)
print('Verifies')
print('Signer #1 certificate SHA-256 digest: ' + cert)
''', True)
        self.put("sdk/platform-tools/adb", common + '''
argv = args[2:] if len(args) >= 2 and args[0] == '-s' else args
def out(text=''): print(text)
if argv == ['get-state']:
    out('device')
elif argv and argv[0] == 'shell':
    command = ' '.join(argv[1:])
    if 'getprop' in command:
        prop = command.split()[-1]
        values = {'ro.product.cpu.abi': os.environ.get('FAKE_ABI', 'arm64-v8a'),
                  'ro.build.version.sdk': os.environ.get('FAKE_API', '34'),
                  'ro.build.version.release': os.environ.get('FAKE_RELEASE', '14')}
        out(values.get(prop, ''))
    elif 'ps -A -o NAME' in command:
        out('NAME')
        for name in os.environ.get('FAKE_PROCESSES', 'init,system_server').split(','):
            if name.strip(): out(name.strip())
    elif 'pm list staged-sessions' in command:
        if os.environ.get('FAKE_STAGED_UNSUPPORTED') == '1': raise SystemExit(1)
        if os.environ.get('FAKE_SESSIONS'): out(os.environ['FAKE_SESSIONS'])
    elif 'dumpsys webviewupdate' in command:
        out('  Current WebView package (name, version): (com.android.webview, ' + os.environ.get('FAKE_WEBVIEW', '159.0.8133.0') + ')')
    elif command.startswith('dumpsys package'):
        parts = command.split()
        if len(parts) == 2:
            out('Active install sessions:')
            if os.environ.get('FAKE_ACTIVE_SESSIONS'):
                prefix = '  Active Child Session 7:' if os.environ.get('FAKE_ACTIVE_CHILD') else '  Active Session 7:'
                out(prefix)
                out('    package=com.example')
            out('Finalized install sessions:')
        else:
            package = parts[-1]
            if os.environ.get('FAKE_NO_INSTALL') == '1':
                out('Unable to find package: ' + package)
            else:
                post = (pathlib.Path.cwd() / 'install-done').exists()
                code = os.environ.get('FAKE_POST_CODE') if post and os.environ.get('FAKE_POST_CODE') else os.environ.get('FAKE_INSTALLED_CODE', '9')
                name = os.environ.get('FAKE_POST_NAME') if post and os.environ.get('FAKE_POST_NAME') else os.environ.get('FAKE_INSTALLED_NAME', '0.1.7')
                out('    versionCode=' + code + ' minSdk=26 targetSdk=34')
                out('    versionName=' + name)
    elif 'am instrument' in command:
        mode = os.environ.get('FAKE_RESULT', 'pass')
        print('INSTRUMENTATION_STATUS_CODE: 1')
        if mode == 'fail':
            print('INSTRUMENTATION_STATUS_CODE: -2\\nFAILURES!!!')
        elif mode == 'skip':
            print('INSTRUMENTATION_STATUS_CODE: -3\\nOK (1 test)')
        elif mode == 'incomplete':
            print('INSTRUMENTATION_STATUS_CODE: 0')
        else:
            print('INSTRUMENTATION_STATUS_CODE: 0\\nOK (1 test)')
        if mode == 'transport': raise SystemExit(7)
    else:
        raise SystemExit(2)
elif 'install' in argv:
    (pathlib.Path.cwd() / 'install-done').write_text('1')
    out('Success (mock only)')
else:
    raise SystemExit(2)
''', True)

    def runner(self, *args, **env):
        return self.run_cmd("bash", "scripts/verify-workspace.sh", *args, **env)

    def events(self):
        path = self.root / "events.jsonl"
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def device_args(self):
        return ("--run-device-tests", "--confirm-test-data", "--device", "synthetic-device")

    def test_help_and_invalid_flags_touch_no_tools(self):
        self.runner_fixture()
        self.assertEqual(0, self.runner("--help").returncode)
        for args in (("--unknown",), ("--device",), ("--run-device-tests",), ("--confirm-test-data",),
                     ("--device", "synthetic-device"), ("--expect-apk-sha256", "0" * 64),
                     ("--min-webview-major", "120")):
            with self.subTest(args=args):
                self.assertNotEqual(0, self.runner(*args).returncode)
        self.assertEqual([], self.events())

    def test_default_only_builds_even_with_device_in_environment(self):
        self.runner_fixture()
        result = self.runner(TANUKI_DEVICE="configured-but-not-used")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["gradlew"], [event[0] for event in self.events()])
        self.assertIn("-PtestBuildType=debug", self.events()[0])
        self.assertIn("scripts/workspace-verification.init.gradle", self.events()[0])

    def test_package_target_and_abi_guards_block_installation(self):
        self.runner_fixture()
        for env in ({"FAKE_APP": "com.tanuki.modern"}, {"FAKE_TARGET": "wrong.target"}, {"FAKE_ABI": "x86_64"}):
            with self.subTest(env=env):
                result = self.runner(*self.device_args(), **env)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertFalse(any("install" in event for event in self.events()))
                (self.root / "events.jsonl").unlink()

    def test_device_run_uses_explicit_serial_and_never_clears_data(self):
        self.runner_fixture()
        result = self.runner(*self.device_args())
        self.assertEqual(0, result.returncode, result.stderr)
        commands = [event for event in self.events() if event[0] == 'adb']
        self.assertTrue(commands)
        self.assertTrue(all(command[1:3] == ['-s', 'synthetic-device'] for command in commands))
        self.assertEqual(2, sum('install' in command for command in commands))
        self.assertFalse(any(item in {'uninstall', 'clear', 'force-stop'} for command in commands for item in command))
        self.assertIn('PASS: 1 Android tests', result.stdout)

    def test_device_lock_rejects_same_serial_and_releases_after_exit(self):
        self.runner_fixture()
        env = {"TANUKI_DEVICE_LOCK_DIR": str(self.root / "device-locks")}
        holder = subprocess.Popen(
            [sys.executable, "scripts/device-lock.py", "--serial", "lock-a", "--",
             sys.executable, "-c", "import time; time.sleep(30)"],
            cwd=self.root, env={**self.env, **env}, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.addCleanup(lambda: holder.poll() is None and holder.kill())
        self.assertIn("DEVICE_LOCK_ACQUIRED", holder.stdout.readline())
        second = self.run_cmd(sys.executable, "scripts/device-lock.py", "--serial", "lock-a",
                              "--", sys.executable, "-c", "pass", **env)
        self.assertEqual(3, second.returncode, second.stderr)
        self.assertIn("已被", second.stderr)
        other = self.run_cmd(sys.executable, "scripts/device-lock.py", "--serial", "lock-b",
                             "--", sys.executable, "-c", "pass", **env)
        self.assertEqual(0, other.returncode, other.stderr)
        holder.terminate()
        holder.wait(timeout=10)
        released = self.run_cmd(sys.executable, "scripts/device-lock.py", "--serial", "lock-a",
                                "--", sys.executable, "-c", "pass", **env)
        self.assertEqual(0, released.returncode, released.stderr)

    def test_device_lock_survives_helper_death_until_child_exits(self):
        self.runner_fixture()
        lock_dir = self.root / "device-locks"
        env = {"TANUKI_DEVICE_LOCK_DIR": str(lock_dir)}
        holder = subprocess.Popen(
            [sys.executable, "scripts/device-lock.py", "--serial", "lock-c", "--",
             sys.executable, "-c", "import time; time.sleep(30)"],
            cwd=self.root, env={**self.env, **env}, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.assertIn("DEVICE_LOCK_ACQUIRED", holder.stdout.readline())
        owner = json.loads((lock_dir / "tanuki-device-lock-c.lock").read_text())
        child_pid = int(owner["childPid"])
        holder.kill()
        holder.wait(timeout=10)
        still_held = self.run_cmd(sys.executable, "scripts/device-lock.py", "--serial", "lock-c",
                                  "--", sys.executable, "-c", "pass", **env)
        self.assertEqual(3, still_held.returncode, still_held.stderr)
        try:
            os.kill(child_pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        deadline = time.time() + 10
        attempt = None
        while time.time() < deadline:
            attempt = self.run_cmd(sys.executable, "scripts/device-lock.py", "--serial", "lock-c",
                                   "--", sys.executable, "-c", "pass", **env)
            if attempt.returncode == 0:
                break
            time.sleep(0.1)
        self.assertEqual(0, attempt.returncode, attempt.stderr)

    def test_verify_workspace_device_runs_are_serialized_per_serial(self):
        self.runner_fixture()
        env = {"FAKE_BUILD_SLEEP": "3"}
        first = subprocess.Popen(
            ["bash", "scripts/verify-workspace.sh", *self.device_args()],
            cwd=self.root, env={**self.env, **env}, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.addCleanup(lambda: first.poll() is None and first.kill())
        # 锁行与子进程输出共用 stdout，子进程可能先于锁行被读取；循环等待锁标记出现。
        deadline = time.time() + 30
        lock_line = ""
        while time.time() < deadline and "DEVICE_LOCK_ACQUIRED" not in lock_line:
            line = first.stdout.readline()
            if not line:
                break
            lock_line = line
            if "DEVICE_LOCK_ACQUIRED" not in lock_line:
                time.sleep(0.1)
        self.assertIn("DEVICE_LOCK_ACQUIRED", lock_line, "设备锁未在 30 秒内获取")
        second = self.runner(*self.device_args(), **env)
        self.assertEqual(3, second.returncode, second.stderr)
        self.assertIn("设备任务被拒绝", second.stderr)
        _, stderr = first.communicate(timeout=90)
        self.assertEqual(0, first.returncode, stderr)
        self.assertEqual(1, sum(event[0] == "gradlew" for event in self.events()))

    def test_device_preflight_identity_and_busy_gates_block_installation(self):
        self.runner_fixture()
        cases = (
            ({"FAKE_SIGN_BAD": "1"}, "bad archive signature"),
            ({"FAKE_TEST_CERT": "2" * 64}, "app/test certificate mismatch"),
            ({"FAKE_INSTALLED_CODE": "10"}, "installed versionCode is higher"),
            ({"FAKE_WEBVIEW": "120.0.0.0"}, "WebView below minimum"),
            ({"FAKE_PROCESSES": "init,node"}, "live Node process"),
            ({"FAKE_SESSIONS": "Session 7: package=com.example"}, "active install session"),
            ({"FAKE_STAGED_UNSUPPORTED": "1", "FAKE_ACTIVE_SESSIONS": "1"}, "dumpsys fallback active session"),
            ({"FAKE_STAGED_UNSUPPORTED": "1", "FAKE_ACTIVE_SESSIONS": "1", "FAKE_ACTIVE_CHILD": "1"},
             "dumpsys fallback active child session"),
        )
        for env, label in cases:
            with self.subTest(label=label):
                result = self.runner(*self.device_args(), **env)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertFalse(any("install" in event for event in self.events()))
                (self.root / "events.jsonl").unlink()
        app_apk, _ = self.apk_paths()
        self.make_apk(app_apk, libs=False)
        result = self.runner(*self.device_args())
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertFalse(any("install" in event for event in self.events()))

    def test_device_preflight_accepts_missing_test_version_and_empty_sessions(self):
        self.runner_fixture()
        result = self.runner(*self.device_args())
        self.assertEqual(0, result.returncode, result.stderr)
        evidence = sorted((self.root / ".build/logs").glob("device-preflight-pre-*.json"))[-1]
        payload = json.loads(evidence.read_text())
        self.assertIsNone(payload["test"]["versionName"])
        self.assertIsNone(payload["test"]["versionCode"])
        self.assertEqual(0, payload["activeInstallSessions"])

    def test_device_preflight_accepts_independent_androidtest_version(self):
        self.runner_fixture()
        result = self.runner(*self.device_args(), FAKE_TEST_VERSION_NAME="1.0-test",
                             FAKE_TEST_VERSION_CODE="1", FAKE_TEST_MIN_SDK="26")
        self.assertEqual(0, result.returncode, result.stderr)
        evidence = sorted((self.root / ".build/logs").glob("device-preflight-pre-*.json"))[-1]
        payload = json.loads(evidence.read_text())
        self.assertEqual(9, payload["app"]["versionCode"])
        self.assertEqual(1, payload["test"]["versionCode"])

    def test_device_preflight_pins_and_post_install_version(self):
        self.runner_fixture()
        app_apk, _ = self.apk_paths()
        app_sha = hashlib.sha256(app_apk.read_bytes()).hexdigest()
        result = self.runner(*self.device_args(), "--expect-apk-sha256", app_sha,
                             "--expect-cert-sha256", "1" * 64)
        self.assertEqual(0, result.returncode, result.stderr)
        preflight = sorted((self.root / ".build/logs").glob("device-preflight-pre-*.json"))
        self.assertEqual(1, len(preflight))
        self.assertIn('"status": "pass"', preflight[0].read_text())
        (self.root / "events.jsonl").unlink()
        (self.root / "install-done").unlink(missing_ok=True)

        for args, env in (
            (("--expect-apk-sha256", "0" * 64), {}),
            (("--expect-cert-sha256", "3" * 64), {}),
        ):
            with self.subTest(args=args):
                result = self.runner(*self.device_args(), *args, **env)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertFalse(any("install" in event for event in self.events()))
                (self.root / "events.jsonl").unlink()
                (self.root / "install-done").unlink(missing_ok=True)

        result = self.runner(*self.device_args(), FAKE_POST_CODE="10")
        self.assertNotEqual(0, result.returncode, result.stdout)
        commands = self.events()
        self.assertEqual(2, sum("install" in event for event in commands))
        self.assertFalse(any("instrument" in event for event in commands))

    def test_failure_skip_incomplete_and_transport_are_not_passes(self):
        self.runner_fixture()
        for mode in ('fail', 'skip', 'incomplete', 'transport'):
            with self.subTest(mode=mode):
                result = self.runner(*self.device_args(), FAKE_RESULT=mode)
                self.assertNotEqual(0, result.returncode, result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)

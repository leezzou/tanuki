#!/usr/bin/env python3
"""离线合成发布回归，不联网、不运行 Gradle、不创建真实 Release。"""
from __future__ import annotations

import contextlib
import io
import hashlib
import importlib.util
import json
import multiprocessing
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("merge_release", ROOT / "scripts/merge-release.py")
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)
A, B, C = "a" * 40, "b" * 40, "c" * 40


def fixture(directory, job):
    directory.mkdir(parents=True, exist_ok=True)
    names = r.attachment_names(job["version"])
    apk = "Tanuki-%s-workspacecheck-arm64-release.apk" % job["version"]
    for name in names - {"app-update.json", "RELEASE-SHA256SUMS", "native-source-SHA256SUMS"}:
        (directory / name).write_bytes(("synthetic-" + name + job["sha"]).encode())
    entry = {"packageName": r.PACKAGE, "versionName": job["version"], "versionCode": job["code"],
             "minSdk": 26, "assetName": apk, "sha256": r.digest(directory / apk),
             "bytes": (directory / apk).stat().st_size, "certificateSha256": r.CERTIFICATE}
    metadata = {"identity": {"commit": job["sha"], "tree": job["tree"], "base": job.get("base", A),
                 "version": job["version"], "code": job["code"], "certificateSha256": r.CERTIFICATE, "apkSha256": entry["sha256"]}}
    with zipfile.ZipFile(directory / ("Tanuki-%s-source.zip" % job["version"]), "w") as archive:
        archive.writestr(zipfile.ZipInfo("Tanuki-%s/RELEASE-SOURCE.json" % job["version"], (1980, 1, 1, 0, 0, 0)), json.dumps(metadata))
    (directory / "app-update.json").write_text(json.dumps({"schemaVersion": 1, "packages": [entry],
        "sourceCommit": job["sha"], "sourceTree": job["tree"], "sourceBase": job.get("base", A)}))
    native = "Tanuki-native-corresponding-sources.tar.gz"
    (directory / "native-source-SHA256SUMS").write_text(r.digest(directory / native) + "  " + native + "\n")
    (directory / "RELEASE-SHA256SUMS").write_text("".join(r.digest(directory / n) + "  " + n + "\n" for n in sorted(names - {"RELEASE-SHA256SUMS"})))
    return directory


class Provider:
    def __init__(self):
        self.release = None
        self.content = {}
        self.target = None
        self.fail_upload = None
        self.fail_publish_response = False
        self.uploads = []
        self.creates = 0
        self.publishes = 0

    def identities(self, exclude_tag=None):
        return ["0.1.14"] + ([self.release["tag_name"][1:]] if self.release and self.release["tag_name"] != exclude_tag else []), [19]

    def find(self, tag):
        return self.release if self.release and self.release["tag_name"] == tag else None

    def create(self, job, notes):
        self.creates += 1
        self.target = job["sha"]
        self.release = {"tag_name": "v" + job["version"], "draft": True, "prerelease": True}

    def assets(self, release):
        return [{"id": n, "name": n, "size": len(b)} for n, b in self.content.items()]

    def asset_bytes(self, asset):
        return self.content[asset["name"]]

    def tag_sha(self, tag):
        return self.target

    def upload(self, tag, path):
        if path.name == self.fail_upload:
            self.fail_upload = None
            raise r.ReleaseError("synthetic upload failure")
        self.uploads.append(path.name)
        self.content[path.name] = path.read_bytes()

    def publish(self, tag):
        self.publishes += 1
        self.release["draft"] = False
        if self.fail_publish_response:
            self.fail_publish_response = False
            raise r.ReleaseError("synthetic lost response after publish")


class Executor:
    def __init__(self, home):
        self.home = home
        self.committed = [A, B, C]
        self.built = []
        self.fail = None
        self.provider = None

    def sync(self):
        pass

    def commits(self, cursor):
        return self.committed[self.committed.index(cursor) + 1:]

    def discover(self, cursor):
        return self.commits(cursor), None

    def gate(self, job):
        job["pr"] = 1
        job["validation"] = "Host synthetic only; Android/model unverified."
        if self.fail == job["sha"]:
            raise r.ReleaseError("synthetic gate failure")

    def build(self, job):
        self.built.append((job["sha"], job["version"], job["code"]))
        job["tree"] = job["sha"]
        directory = fixture(self.home / "bundles" / job["sha"], job)
        job["inventory"] = r.bundle_inventory(directory, job)
        return directory


def lock_child(home, result):
    try:
        with r.writer(Path(home)):
            result.put("acquired")
    except r.ReleaseError:
        result.put("blocked")


def slot_child(directory, acquired, stop, result):
    slot = r.load_module("slot_child", ROOT / "scripts/build-slot.py")
    try:
        with slot.acquire(directory, "release", stop.is_set):
            acquired.set()
            result.put("acquired")
    except InterruptedError:
        result.put("stopped")


class MergeReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.home = Path(self.temp.name).resolve()
        self.job = {"sha": B, "tree": B, "version": "0.1.15", "code": 20, "pr": 1}
        self.directory = fixture(self.home / "bundle", self.job)
        self.job["inventory"] = r.bundle_inventory(self.directory, self.job)
        self.provider = Provider()

    def state(self):
        return {"schema": 1, "cursor": A, "baseline_version": "0.1.14", "code_floor": 19, "jobs": []}

    def publish(self, stopped=lambda: False):
        r.publish_bundle(self.provider, self.job, self.directory, self.home / "notes.md", stopped)

    def test_integer_boundaries_and_reserved_versions(self):
        for start, end in (("0.1.9", "0.1.10"), ("0.1.99", "0.1.100"), ("0.1.100", "0.1.101")):
            self.assertEqual(end, r.next_version([start]))
        self.assertEqual("0.1.101", r.next_version(["0.1.9", "0.1.100", "0.1.99"]))
        for invalid in ("0.1.01", "0.2.0", "0.1.99-rc", "0.1.1\n"):
            with self.assertRaises(r.ReleaseError):
                r.next_version([invalid])

    def test_same_candidate_does_not_increase_itself_forever(self):
        state = self.state()
        r.enqueue(state, [B])
        job = state["jobs"][0]
        r.reserve(job, state, ["0.1.99"], [99])
        self.assertEqual(("0.1.100", 100), (job["version"], job["code"]))
        for _ in range(5):
            r.reserve(job, state, ["0.1.100"], [100])
        self.assertEqual(("0.1.100", 100), (job["version"], job["code"]))

    def test_failure_keeps_fifo_and_reservation_across_restart(self):
        state = self.state()
        executor = Executor(self.home)
        executor.fail = B
        path = self.home / "state.json"
        save = lambda: r.atomic_json(path, state)
        with self.assertRaises(r.ReleaseError):
            r.run_once(state, save, executor, self.provider, self.home)
        state = r.read_json(path)
        self.assertEqual([B, C], [j["sha"] for j in state["jobs"]])
        self.assertEqual("0.1.15", state["jobs"][0]["version"])
        self.assertNotIn("version", state["jobs"][1])
        executor.fail = None
        # 模拟每个 tag 分别保存的提供者。
        def create(job, notes):
            Provider.create(self.provider, job, notes)
            self.provider.content = {}
        self.provider.create = create
        save = lambda: r.atomic_json(path, state)
        r.run_once(state, save, executor, self.provider, self.home)
        self.assertEqual([(B, "0.1.15", 20), (C, "0.1.16", 21)], executor.built)
        self.assertTrue(all(j["status"] == "published" for j in state["jobs"]))
        self.assertEqual(C, state["cursor"])

    def test_atomic_writer_excludes_second_process(self):
        context = multiprocessing.get_context("fork")
        result = context.Queue()
        with r.writer(self.home):
            child = context.Process(target=lock_child, args=(str(self.home), result))
            child.start()
            child.join(5)
            self.assertFalse(child.is_alive())
            self.assertEqual("blocked", result.get(timeout=1))
        child = context.Process(target=lock_child, args=(str(self.home), result))
        child.start()
        child.join(5)
        self.assertEqual("acquired", result.get(timeout=1))

    def test_multi_merge_discovery_deduplicates_without_losing_middle(self):
        state = self.state()
        r.enqueue(state, [B, C])
        r.enqueue(state, [B, C])
        self.assertEqual([B, C], [j["sha"] for j in state["jobs"]])
        self.assertEqual(C, state["cursor"])

    def test_partial_upload_resumes_and_never_clobbers(self):
        self.provider.fail_upload = "Tanuki-native-corresponding-sources.tar.gz"
        with self.assertRaises(r.ReleaseError):
            self.publish()
        prior = list(self.provider.uploads)
        self.assertTrue(prior)
        self.assertTrue(self.provider.release["draft"])
        self.publish()
        self.assertEqual(6, len(self.provider.content))
        self.assertEqual(6, len(self.provider.uploads))
        self.assertEqual(1, self.provider.creates)
        self.assertFalse(self.provider.release["draft"])
        self.publish()
        self.assertEqual(6, len(self.provider.uploads))
        self.assertEqual(1, self.provider.publishes)

    def test_remote_same_name_different_bytes_is_rejected(self):
        self.provider.create(self.job, None)
        name = "app-update.json"
        self.provider.content[name] = b"x" * (self.directory / name).stat().st_size
        with self.assertRaisesRegex(r.ReleaseError, "字节冲突"):
            self.publish()
        self.assertFalse(self.provider.uploads)
        self.assertTrue(self.provider.release["draft"])

    def test_remote_extra_asset_and_wrong_tag_are_rejected(self):
        self.provider.create(self.job, None)
        self.provider.target = C
        with self.assertRaisesRegex(r.ReleaseError, "tag"):
            self.publish()
        self.provider.target = B
        self.provider.content["unexpected.txt"] = b"synthetic"
        with self.assertRaisesRegex(r.ReleaseError, "额外"):
            self.publish()

    def test_lost_publish_response_reconciles_actual_remote_state(self):
        self.provider.fail_publish_response = True
        with self.assertRaises(r.ReleaseError):
            self.publish()
        self.assertFalse(self.provider.release["draft"])
        self.publish()
        self.assertEqual(1, self.provider.publishes)

    def test_stop_before_upload_and_resume(self):
        self.provider.create(self.job, None)
        with self.assertRaises(r.ReleaseError):
            self.publish(lambda: True)
        self.assertTrue(self.provider.release["draft"])
        self.assertFalse(self.provider.uploads)
        self.publish()
        self.assertFalse(self.provider.release["draft"])

    def test_published_incomplete_release_is_never_filled_or_overwritten(self):
        self.provider.create(self.job, None)
        self.provider.release["draft"] = False
        with self.assertRaisesRegex(r.ReleaseError, "不完整"):
            self.publish()
        self.assertFalse(self.provider.uploads)

    def test_local_identity_hash_and_exact_six_are_required(self):
        extra = self.directory / "extra.txt"
        extra.write_text("synthetic")
        with self.assertRaises(r.ReleaseError):
            self.publish()
        extra.unlink()
        (self.directory / "app-update.json").write_text("corrupt")
        with self.assertRaises(r.ReleaseError):
            self.publish()
        self.assertEqual(0, self.provider.creates)

    def test_commit_binding_cannot_follow_newer_head(self):
        executor = r.Executor({"repo": "example/repository"}, self.home, r.Runner(), self.provider)
        with mock.patch.object(executor, "git") as git:
            git.side_effect = lambda *args: "" if args[:1] == ("status",) else C
            with self.assertRaisesRegex(r.ReleaseError, "SHA/状态"):
                executor.pin(B)
        wrong = self.job | {"sha": C}
        with self.assertRaisesRegex(r.ReleaseError, "源码 SHA/tree"):
            r.bundle_inventory(self.directory, wrong)

    def test_first_parent_history_rewrite_is_rejected(self):
        executor = r.Executor({}, self.home, r.Runner(), self.provider)
        with mock.patch.object(executor, "git", return_value=C + "\n" + B + "\n" + A):
            self.assertEqual([B, C], executor.commits(A))
            with self.assertRaisesRegex(r.ReleaseError, "历史改写"):
                executor.commits("d" * 40)

    def test_init_uses_confirmed_published_baseline_without_replaying_history(self):
        r.atomic_json(self.home / "config.json", {"repo": "example/repository", "installed_code_floor": 19})
        provider = mock.Mock()
        provider.find.return_value = {"draft": False}
        provider.identities.return_value = (["0.1.14"], [19])
        provider.tag_sha.return_value = A
        executor = mock.Mock()
        executor.commits.return_value = [B, C]
        args = ["queue", "--home", str(self.home), "init", "--baseline-tag", "v0.1.14"]
        with mock.patch.object(sys, "argv", args), mock.patch.object(r, "GitHub", return_value=provider), \
                mock.patch.object(r, "Executor", return_value=executor):
            self.assertEqual(0, r.main())
            initial = r.read_json(self.home / "state.json")
            self.assertEqual(A, initial["cursor"])
            self.assertEqual([], initial["jobs"])
            self.assertEqual(19, initial["code_floor"])
            with self.assertRaisesRegex(r.ReleaseError, "已有状态"):
                r.main()
            self.assertEqual(initial, r.read_json(self.home / "state.json"))
        provider.create.assert_not_called()

    def test_build_runner_receives_exact_commit_parent_version_and_retry_hash(self):
        runner = mock.Mock()
        executor = r.Executor({}, self.home, runner, self.provider)
        calls = []
        job = self.job.copy()
        candidate = executor.checkout / "app/build/outputs/apk/modern/release/app-modern-release.apk"
        (executor.checkout / "scripts").mkdir(parents=True)
        for name in ("release-source.py", "native-cache-identity.py", "native-cache.init.gradle"):
            (executor.checkout / "scripts" / name).write_text("synthetic-protocol-fixture")
        def run(command, **kwargs):
            calls.append(command)
            if "scripts/prepare-app-release.py" in command:
                directory = fixture(executor.checkout / "artifacts/releases" / job["version"], job)
                candidate.parent.mkdir(parents=True, exist_ok=True)
                candidate.write_bytes((directory / ("Tanuki-%s-workspacecheck-arm64-release.apk" % job["version"])).read_bytes())
            return ""
        runner.run.side_effect = run
        native = {"sha256": "e" * 64}
        with mock.patch.object(executor, "pin", return_value=B), mock.patch.object(executor, "git", return_value=A), \
                mock.patch.object(executor, "_native_state", return_value=(None, native, False)) as state, \
                mock.patch.object(executor, "_record_native", return_value=native):
            result = executor._build(job)
            inventory = job["inventory"].copy()
            executor._build(job)
            state.return_value = (None, {"sha256": "f" * 64}, False)
            with self.assertRaisesRegex(r.ReleaseError, "工具链身份改变"):
                executor._build(job)
        commands = [c for c in calls if "scripts/prepare-app-release.py" in c]
        self.assertEqual(2, len(commands))
        for command in commands:
            self.assertEqual(B, command[command.index("--source-commit") + 1])
            self.assertEqual(A, command[command.index("--merge-base") + 1])
            self.assertEqual("0.1.15", command[command.index("--version") + 1])
            self.assertEqual("20", command[command.index("--version-code") + 1])
        self.assertNotIn("--verified-apk", commands[0])
        self.assertIn("--force-native", commands[0])
        self.assertNotIn("--force-native", commands[1])
        self.assertEqual(r.digest(candidate), commands[1][commands[1].index("--verified-apk-sha256") + 1])
        self.assertEqual(inventory, r.bundle_inventory(result, job))

    def test_plan_is_read_only_and_includes_every_new_merge(self):
        r.atomic_json(self.home / "config.json", {"repo": "example/repository"})
        r.atomic_json(self.home / "state.json", self.state() | {"paused": False})
        initial = {p.name: p.read_bytes() for p in self.home.iterdir() if p.is_file()}
        provider = mock.Mock()
        provider.api.side_effect = [{"sha": C, "parents": [{"sha": B}]},
                                    {"sha": B, "parents": [{"sha": A}]}, {"sha": A, "parents": []}]
        provider.identities.return_value = (["0.1.99"], [99])
        provider.association.side_effect = lambda sha: {"number": 1 if sha == B else 2, "merge_commit_sha": sha}
        output = io.StringIO()
        with mock.patch.object(sys, "argv", ["queue", "--home", str(self.home), "plan"]), \
                mock.patch.object(r, "GitHub", return_value=provider), contextlib.redirect_stdout(output):
            self.assertEqual(0, r.main())
        plan = json.loads(output.getvalue())
        self.assertEqual([B, C], plan["new_commits"])
        self.assertEqual("0.1.100", plan["next_unreserved_version"])
        self.assertEqual(initial, {p.name: p.read_bytes() for p in self.home.iterdir() if p.is_file()})
        self.assertFalse(provider.create.called)

    def test_plan_and_pause_are_separate_from_writer(self):
        # pause 必须在长驻 writer 持锁时仍能生效，不调用网络或构建。
        r.atomic_json(self.home / "config.json", {"repo": "example/repository"})
        with r.writer(self.home):
            for command, expected in (("pause", True), ("resume", False)):
                result = subprocess.run([sys.executable, str(ROOT / "scripts/merge-release.py"), "--home", str(self.home), command], capture_output=True)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(expected, r.read_json(self.home / "control.json")["paused"])

    def test_build_slot_excludes_parallel_build_and_can_stop_waiter(self):
        slot = r.load_module("test_slot", ROOT / "scripts/build-slot.py")
        context = multiprocessing.get_context("fork")
        acquired, stop, result = context.Event(), context.Event(), context.Queue()
        directory = self.home / "slot"
        with slot.acquire(directory):
            child = context.Process(target=slot_child, args=(directory, acquired, stop, result))
            child.start()
            self.assertFalse(acquired.wait(0.3))
            stop.set()
            child.join(5)
            self.assertFalse(child.is_alive())
            self.assertEqual("stopped", result.get(timeout=1))
        self.assertFalse(acquired.is_set())
        stop.clear()
        child = context.Process(target=slot_child, args=(directory, acquired, stop, result))
        child.start()
        self.assertTrue(acquired.wait(5))
        child.join(5)
        self.assertEqual("acquired", result.get(timeout=1))

    def test_background_build_yields_to_foreground_ticket(self):
        slot = r.load_module("test_slot_priority", ROOT / "scripts/build-slot.py")
        context = multiprocessing.get_context("fork")
        acquired, stop, result = context.Event(), context.Event(), context.Queue()
        directory = self.home / "slot"
        directory.mkdir()
        # 使用加锁票据模拟另一个工作树已经排队的前台任务。
        with slot.lock_file(directory / "development-synthetic") as ticket:
            slot.fcntl.flock(ticket, slot.fcntl.LOCK_EX)
            child = context.Process(target=slot_child, args=(directory, acquired, stop, result))
            child.start()
            self.assertFalse(acquired.wait(0.3))
            slot.fcntl.flock(ticket, slot.fcntl.LOCK_UN)
        self.assertTrue(acquired.wait(5))
        child.join(5)
        self.assertEqual("acquired", result.get(timeout=1))

    def test_service_prepare_copies_durable_code_without_launching(self):
        service = r.load_module("test_service", ROOT / "scripts/merge-release-service.py")
        jdk, sdk, home = self.home / "jdk", self.home / "sdk", self.home / "service"
        (jdk / "bin").mkdir(parents=True)
        (jdk / "bin/java").write_text("synthetic-tool")
        sdk.mkdir()
        key = self.home / "fixture.debug.keystore"
        key.write_bytes(b"synthetic-not-a-key")
        arguments = ["service", "prepare", "--home", str(home), "--jdk", str(jdk), "--sdk", str(sdk),
                     "--keystore", str(key), "--build-slot", str(self.home / "slot"), "--gate-receipts", str(self.home / "gates")]
        with mock.patch.object(sys, "argv", arguments), mock.patch.object(service.shutil, "which", return_value="/synthetic/bin/gh"), \
                mock.patch.object(service, "launch") as launch:
            self.assertEqual(0, service.main())
            launch.assert_not_called()
            config = r.read_json(home / "config.json")
            self.assertEqual(str(key), config["environment"]["TANUKI_WORKSPACECHECK_KEYSTORE"])
            self.assertNotIn("PASSWORD", json.dumps(config))
            self.assertEqual((ROOT / "scripts/merge-release.py").read_bytes(), (home / "bin/merge-release.py").read_bytes())
            plist = service.plistlib.loads((home / "com.tanuki.merge-release.plist").read_bytes())
            self.assertIn(str(home / "bin/merge-release.py"), plist["ProgramArguments"])
            self.assertNotIn(str(ROOT), json.dumps(plist))
            with self.assertRaises(ValueError):
                service.main()
        for path in (ROOT, ROOT / ".build/service", Path.home(), Path("/")):
            with self.assertRaises(ValueError):
                service.stable(path)

    def test_service_enable_persists_login_registration_and_stop_preserves_state(self):
        service = r.load_module("test_service_lifecycle", ROOT / "scripts/merge-release-service.py")
        home = self.home / "service"
        home.mkdir()
        r.atomic_json(home / "state.json", {"synthetic": True})
        plist = home / "com.tanuki.merge-release.plist"
        plist.write_bytes(service.plistlib.dumps({"Label": service.LABEL, "ProgramArguments": ["synthetic"]}))
        agents = self.home / "LaunchAgents"
        args = ["service", "enable", "--home", str(home), "--confirm-shared-slot"]
        with mock.patch.object(service, "AGENTS_DIRECTORY", agents), mock.patch.object(service.sys, "platform", "darwin"), \
                mock.patch.object(service, "launch") as launch:
            with mock.patch.object(sys, "argv", args):
                self.assertEqual(0, service.main())
            registered = agents / plist.name
            self.assertEqual(plist.read_bytes(), registered.read_bytes())
            self.assertEqual("bootstrap", launch.call_args[0][0][1])
            with mock.patch.object(sys, "argv", ["service", "stop", "--home", str(home)]):
                registered.write_bytes(b"synthetic-other-service")
                with self.assertRaises(ValueError):
                    service.main()
                self.assertTrue(registered.exists())
                registered.write_bytes(plist.read_bytes())
                self.assertEqual(0, service.main())
            self.assertFalse(registered.exists())
            self.assertTrue((home / "state.json").exists())
            self.assertTrue(plist.exists())

    def test_provider_requires_exact_merged_pr_boundary_and_complete_ci(self):
        provider = r.GitHub("example/repository", mock.Mock())
        pull = {"merged_at": "synthetic", "merge_commit_sha": B, "base": {"ref": "main", "repo": {"full_name": "example/repository"}}}
        with mock.patch.object(provider, "pages", return_value=[pull]):
            self.assertEqual(pull, provider.merge(B))
        for pulls in ([], [pull, pull], [pull | {"merge_commit_sha": C}]):
            with mock.patch.object(provider, "pages", return_value=pulls), self.assertRaises(r.ReleaseError):
                provider.merge(B)
        passed = {"total_count": 1, "check_runs": [{"status": "completed", "conclusion": "success"}]}
        with mock.patch.object(provider, "api", side_effect=[passed, {"total_count": 0, "state": "pending"}]):
            provider.checks(B)
        for checks, status in (({"total_count": 0, "check_runs": []}, {"total_count": 0}),
                               (passed, {"total_count": 1, "state": "failure"}),
                               ({"total_count": 1, "check_runs": [{"status": "completed", "conclusion": "skipped"}]}, {"total_count": 0})):
            with mock.patch.object(provider, "api", side_effect=[checks, status]), self.assertRaises(r.ReleaseError):
                provider.checks(B)

    def test_timeout_stops_only_owned_command_process_group(self):
        process = mock.Mock(pid=123456, returncode=-15)
        process.communicate.side_effect = [subprocess.TimeoutExpired("synthetic", 1), ("", "")]
        with mock.patch.object(r.subprocess, "Popen", return_value=process) as popen, mock.patch.object(r.os, "killpg") as kill:
            with self.assertRaisesRegex(r.ReleaseError, "本次进程组已停止"):
                r.Runner().run(["synthetic-command"], timeout=1)
            self.assertTrue(popen.call_args.kwargs["start_new_session"])
            kill.assert_called_once_with(123456, r.signal.SIGTERM)

    def test_runner_does_not_print_credential_like_child_output(self):
        with self.assertRaises(r.ReleaseError) as error:
            r.Runner().run([sys.executable, "-c", "import sys; print('synthetic-private-output'); sys.exit(7)"])
        self.assertNotIn("synthetic-private-output", str(error.exception))
        self.assertIn("7", str(error.exception))

    def test_real_git_checkout_pins_older_commit_and_keeps_cache(self):
        checkout = self.home / "checkout"
        runner = r.Runner()
        runner.run(["git", "init", "--initial-branch=main", checkout])
        def commit(text):
            (checkout / "source.txt").write_text(text)
            runner.run(["git", "-C", checkout, "add", "source.txt"])
            runner.run(["git", "-C", checkout, "-c", "core.hooksPath=/dev/null", "-c", "user.name=Fixture",
                        "-c", "user.email=fixture@example.invalid", "commit", "-m", "test: synthetic"])
            return runner.run(["git", "-C", checkout, "rev-parse", "HEAD"]).strip()
        first = commit("first")
        second = commit("second")
        (checkout / ".build").mkdir()
        (checkout / ".build/cache").write_text("synthetic-cache")
        executor = r.Executor({}, self.home, runner, self.provider)
        tree = executor.pin(first)
        self.assertRegex(tree, r"^[0-9a-f]{40}$")
        self.assertEqual("first", (checkout / "source.txt").read_text())
        self.assertEqual("synthetic-cache", (checkout / ".build/cache").read_text())
        executor.pin(second)
        self.assertEqual("second", (checkout / "source.txt").read_text())
        (checkout / "source.txt").write_text("local-edit")
        with self.assertRaisesRegex(r.ReleaseError, "已跟踪修改"):
            executor.pin(first)
        self.assertEqual("local-edit", (checkout / "source.txt").read_text())

    def test_external_code_conflict_blocks_without_allocating_another_version(self):
        state = self.state()
        r.enqueue(state, [B])
        r.reserve(state["jobs"][0], state, ["0.1.14"], [19])
        self.provider.identities = lambda *_: (["0.1.16"], [21])
        executor = Executor(self.home)
        with self.assertRaisesRegex(r.ReleaseError, "外部发布"):
            r.run_once(state, lambda: None, executor, self.provider, self.home)
        self.assertEqual("0.1.15", state["jobs"][0]["version"])
        self.assertEqual(20, state["jobs"][0]["code"])
        self.assertFalse(executor.built)

    def test_gate_binds_head_receipt_and_both_ci_shas(self):
        config = {"repo": "example/repository", "gate_receipts": str(self.home / "gates")}
        provider = mock.Mock()
        provider.merge.return_value = {"number": 7, "head": {"sha": C}}
        executor = r.Executor(config, self.home, r.Runner(), provider)
        self.job["pr"] = 7
        receipt = {"schema": 1, "repo": "example/repository", "pr": 7, "head_sha": C,
                   "development_gates": "passed", "validation": "Host synthetic only"}
        path = self.home / "gates" / (C + ".json")
        r.atomic_json(path, receipt)
        executor.gate(self.job)
        self.assertEqual([mock.call(C), mock.call(B)], provider.checks.call_args_list)
        frozen_hash = self.job["gate_receipt_sha256"]
        path.unlink()
        executor.gate(self.job)
        self.assertEqual(frozen_hash, self.job["gate_receipt_sha256"])
        for field, value in (("head_sha", A), ("pr", 8), ("repo", "other/repository"), ("development_gates", "failed")):
            r.atomic_json(path, receipt | {field: value})
            candidate = self.job.copy()
            candidate.pop("gate_receipt_sha256")
            with self.assertRaises(r.ReleaseError):
                executor.gate(candidate)

    def test_gate_recorder_writes_only_after_real_gate_success_and_stable_head(self):
        recorder = r.load_module("test_gate_recorder", ROOT / "scripts/record-release-gate.py")
        before = {"number": 7, "url": "https://github.com/example/repository/pull/7", "headRefOid": C, "state": "OPEN", "isDraft": False}
        args = ["record", "--pr", "7", "--session", "synthetic-worker", "--directory", str(self.home / "gates")]
        def runner(output):
            result = mock.Mock()
            result.run.side_effect = [json.dumps(before), json.dumps(output), json.dumps({"nameWithOwner": "example/repository"})]
            return result
        with mock.patch.object(sys, "argv", args), mock.patch.object(r, "Runner", return_value=runner(before)), \
                mock.patch.object(recorder.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, "", "")), \
                mock.patch.object(recorder.importlib.util, "module_from_spec", return_value=r), \
                mock.patch.object(recorder.importlib.util, "spec_from_file_location") as load:
            load.return_value.loader.exec_module = lambda *_: None
            self.assertEqual(0, recorder.main())
        receipt = r.read_json(self.home / "gates" / (C + ".json"))
        self.assertEqual("passed", receipt["development_gates"])
        self.assertEqual(C, receipt["head_sha"])
        for code, after in ((1, before), (0, before | {"headRefOid": B})):
            with mock.patch.object(sys, "argv", args), mock.patch.object(r, "Runner", return_value=runner(after)), \
                    mock.patch.object(recorder.subprocess, "run", return_value=subprocess.CompletedProcess([], code, "", "")), \
                    mock.patch.object(recorder.importlib.util, "module_from_spec", return_value=r), \
                    mock.patch.object(recorder.importlib.util, "spec_from_file_location") as load:
                load.return_value.loader.exec_module = lambda *_: None
                with self.assertRaises(r.ReleaseError):
                    recorder.main()
                self.assertFalse((self.home / "gates" / (C + ".json")).exists())

    def test_rebase_members_form_one_release_per_pr_and_keep_continuous_merges(self):
        d, e, f = "d" * 40, "e" * 40, "f" * 40
        provider = mock.Mock()
        mapping = {B: (1, C), C: (1, C), d: (2, e), e: (2, e), f: (3, f)}
        provider.association.side_effect = lambda sha: {"number": mapping[sha][0], "merge_commit_sha": mapping[sha][1]}
        groups, blocked = r.resolve_boundaries(provider, [B, C, d, e, f], A)
        self.assertIsNone(blocked)
        self.assertEqual([C, e, f], [g["sha"] for g in groups])
        self.assertEqual([[B, C], [d, e], [f]], [g["members"] for g in groups])
        self.assertEqual([A, C, e], [g["base"] for g in groups])
        state = self.state()
        r.enqueue(state, groups)
        r.enqueue(state, groups)
        for job in state["jobs"]:
            r.reserve(job, state, ["0.1.99"], [99])
        self.assertEqual(["0.1.100", "0.1.101", "0.1.102"], [j["version"] for j in state["jobs"]])
        self.assertEqual([100, 101, 102], [j["code"] for j in state["jobs"]])

    def test_uncertain_later_boundary_does_not_block_confirmed_prefix_or_skip_unknown(self):
        provider = mock.Mock()
        def associated(sha):
            if sha == C:
                raise r.ReleaseError("synthetic direct push boundary unknown")
            return {"number": 1, "merge_commit_sha": B}
        provider.association.side_effect = associated
        groups, blocked = r.resolve_boundaries(provider, [B, C], A)
        self.assertEqual([B], [g["sha"] for g in groups])
        state, executor = self.state(), Executor(self.home)
        executor.discover = lambda cursor: (groups, blocked)
        with self.assertRaisesRegex(r.ReleaseError, "boundary unknown"):
            r.run_once(state, lambda: None, executor, self.provider, self.home)
        self.assertEqual(B, state["cursor"])
        self.assertEqual("published", state["jobs"][0]["status"])
        self.assertEqual(1, len(state["jobs"]))

    def test_rebase_range_with_different_pr_is_refused(self):
        provider = mock.Mock()
        provider.association.side_effect = lambda sha: {"number": 1 if sha == B else 2, "merge_commit_sha": C}
        groups, blocked = r.resolve_boundaries(provider, [B, C], A)
        self.assertEqual([], groups)
        self.assertIn("其他 PR", blocked)

    def test_floor_raised_during_build_blocks_publish_without_reallocating(self):
        state, executor = self.state(), Executor(self.home)
        original = executor.build
        def build(job):
            result = original(job)
            r.set_floor(self.home, state, 25, "synthetic independent QA installed code25")
            return result
        executor.build = build
        with self.assertRaisesRegex(r.ReleaseError, "安装 floor"):
            r.run_once(state, lambda: None, executor, self.provider, self.home)
        self.assertEqual(("0.1.15", 20), (state["jobs"][0]["version"], state["jobs"][0]["code"]))
        self.assertEqual(0, self.provider.creates)
        self.assertEqual(25, r.read_json(self.home / "floor/value.json")["minimum"])
        with self.assertRaisesRegex(r.ReleaseError, "不能降低"):
            r.set_floor(self.home, state, 24, "synthetic lower floor")

    def test_same_candidate_installed_floor_is_bound_to_actual_apk_and_never_adds_code(self):
        state = self.state()
        job = self.job.copy()
        state["jobs"] = [job]
        name = "Tanuki-0.1.15-workspacecheck-arm64-release.apk"
        actual = job["inventory"][name]["sha256"]
        with self.assertRaisesRegex(r.ReleaseError, "哈希不属于"):
            r.set_floor(self.home, state, 20, "synthetic same candidate QA", B, "f" * 64)
        r.set_floor(self.home, state, 20, "synthetic same candidate QA", B, actual)
        for _ in range(4):
            minimum, data = r.known_floor(self.home, state)
            r.reserve(job, state, ["0.1.15"], [19])
            r.check_floor(job, minimum, data)
        self.assertEqual(("0.1.15", 20), (job["version"], job["code"]))
        with self.assertRaisesRegex(r.ReleaseError, "安装 floor"):
            r.check_floor(job | {"sha": C}, minimum, data)

    def test_publication_holds_floor_lock_and_concurrent_floor_update_is_explicitly_rejected(self):
        state, executor = self.state(), Executor(self.home)
        executor.committed = [A, B]
        previous = self.provider.publish
        def publish(tag):
            with self.assertRaisesRegex(r.ReleaseError, "floor/发布"):
                r.set_floor(self.home, state, 25, "synthetic concurrent installed fact")
            previous(tag)
        self.provider.publish = publish
        r.run_once(state, lambda: None, executor, self.provider, self.home)
        self.assertEqual("published", state["jobs"][0]["status"])
        self.assertFalse((self.home / "floor/value.json").exists())
        r.set_floor(self.home, state, 25, "synthetic fact retry after publication")
        self.assertEqual(25, r.known_floor(self.home, state)[0])

    def test_reservation_uses_live_configured_floor_instead_of_permanent_19(self):
        state, executor = self.state(), Executor(self.home)
        executor.committed = [A, B]
        r.atomic_json(self.home / "config.json", {"installed_code_floor": 31})
        r.run_once(state, lambda: None, executor, self.provider, self.home)
        self.assertEqual(32, state["jobs"][0]["code"])
        self.assertEqual(31, state["code_floor"])

    def test_floor_cli_updates_during_watch_writer_and_status_shows_persistent_fact(self):
        r.atomic_json(self.home / "config.json", {"repo": "example/repository", "installed_code_floor": 19})
        r.atomic_json(self.home / "state.json", self.state() | {"repo": "example/repository", "paused": False})
        with r.writer(self.home):
            result = subprocess.run([sys.executable, str(ROOT / "scripts/merge-release.py"), "--home", str(self.home),
                                     "set-floor", "--version-code", "24", "--reason", "synthetic independently confirmed installed code"], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
        result = subprocess.run([sys.executable, str(ROOT / "scripts/merge-release.py"), "--home", str(self.home), "status"], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        status = json.loads(result.stdout)
        self.assertEqual(24, status["installed_floor"]["effective_minimum"])
        self.assertEqual(1, len(status["installed_floor"]["history"]))
        self.assertFalse(status["jobs"])

    def test_missing_receipt_has_actionable_reason_and_frozen_copy_is_checked(self):
        provider = mock.Mock()
        provider.merge.return_value = {"number": 1, "head": {"sha": C}}
        executor = r.Executor({"repo": "example/repository", "gate_receipts": str(self.home / "missing")}, self.home, r.Runner(), provider)
        with self.assertRaisesRegex(r.ReleaseError, "record-release-gate.py"):
            executor.gate(self.job)
        self.job["gate_receipt_sha256"] = "f" * 64
        with self.assertRaisesRegex(r.ReleaseError, "冻结门禁回执"):
            executor.gate(self.job)

    def test_source_zip_identity_must_match_the_actual_apk(self):
        archive = self.directory / "Tanuki-0.1.15-source.zip"
        with zipfile.ZipFile(archive, "w") as source:
            source.writestr("Tanuki-0.1.15/RELEASE-SOURCE.json", json.dumps({"identity": {
                "commit": B, "tree": B, "version": "0.1.15", "code": 20,
                "certificateSha256": r.CERTIFICATE, "apkSha256": "f" * 64}}))
        sums = self.directory / "RELEASE-SHA256SUMS"
        entries = r.checksum_entries(sums)
        entries[archive.name] = r.digest(archive)
        sums.write_text("".join(value + "  " + name + "\n" for name, value in sorted(entries.items())))
        with self.assertRaisesRegex(r.ReleaseError, "实际 APK"):
            r.bundle_inventory(self.directory, self.job)

    def test_sync_refuses_linked_checkout_or_shared_worktree_metadata_before_git_mutation(self):
        runner = mock.Mock()
        executor = r.Executor({"repo": "example/repository"}, self.home, runner, self.provider)
        foreign = self.home / "another-worktree"
        foreign.mkdir()
        marker = foreign / "sentinel.txt"
        marker.write_text("preserve another worktree")
        executor.checkout.symlink_to(foreign)
        with self.assertRaisesRegex(r.ReleaseError, "专用 clone"):
            executor.sync()
        executor.checkout.unlink()
        executor.checkout.mkdir()
        (executor.checkout / ".git").write_text("gitdir: /synthetic/shared-git\n")
        with self.assertRaisesRegex(r.ReleaseError, "独立 clone"):
            executor.sync()
        runner.run.assert_not_called()
        self.assertEqual("preserve another worktree", marker.read_text())

    def test_old_source_without_protocol_is_rejected_before_any_build_command(self):
        runner = mock.Mock()
        executor = r.Executor({}, self.home, runner, self.provider)
        with mock.patch.object(executor, "pin", return_value=B), self.assertRaisesRegex(r.ReleaseError, "缺少自动发布源码协议"):
            executor._build(self.job.copy())
        runner.run.assert_not_called()

    def test_no_automatic_product_commits_or_pushes(self):
        commands = []
        runner = mock.Mock()
        runner.run.side_effect = lambda command, **_: commands.append(command) or ""
        provider = r.GitHub("example/repository", runner)
        with mock.patch.object(provider, "pages", return_value=[]):
            provider.create(self.job, self.home / "notes.md")
        self.assertFalse(any(command[0] == "git" and any(x in command for x in ("commit", "push")) for command in commands))
        self.assertTrue(any("sha=" + B in command for command in commands))


if __name__ == "__main__":
    unittest.main()

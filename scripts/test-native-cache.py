#!/usr/bin/env python3
"""合成 NDK/native 输出的缓存输入身份回归；不运行编译器或读取真实 SDK。"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("native_cache", ROOT / "scripts/native-cache-identity.py")
cache = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache)


class NativeCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name).resolve()
        self.root, self.sdk = self.directory / "source", self.directory / "sdk"
        self.root.mkdir()
        for filename in ("app/build.gradle.kts", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
                         "scripts/native-cache.init.gradle", "scripts/native-cache-identity.py", "gradle/wrapper/gradle-wrapper.properties",
                         "app/src/main/cpp/supervisor.c", "runtime/dependency.lock"):
            path = self.root / filename
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("synthetic-native-input\n")
        self.script = self.root / "runtime/build-native-runtime.sh"
        self.script.write_text('NDK="$SDK/ndk/27.2.12479018/toolchains/llvm/prebuilt/darwin-x86_64"\nTALLOC="$SRC/talloc-2.4.3"\n')
        self.ndk = self.sdk / "ndk/27.2.12479018"
        self.compiler = self.ndk / "toolchains/llvm/prebuilt/darwin-x86_64/bin/clang"
        self.compiler.parent.mkdir(parents=True)
        self.compiler.write_bytes(b"synthetic-compiler-not-an-executable")
        (self.ndk / "source.properties").write_text("Pkg.Revision = 27.2.12479018\n")
        self.apk = self.directory / "synthetic.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            for name in cache.LIBRARIES:
                path = self.root / "app/src/main/jniLibs/arm64-v8a" / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b"synthetic-ELF-" + name.encode())
                archive.writestr("lib/arm64-v8a/" + name, path.read_bytes())

    def identity(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return cache.identity(self.root, self.sdk)

    def test_identical_contents_ignore_mtime_and_match_actual_apk_outputs(self):
        value = self.identity()
        receipt = cache.receipt(self.root, value, self.apk)
        stat = self.compiler.stat()
        os.utime(self.compiler, (stat.st_atime + 60, stat.st_mtime + 60))
        self.assertEqual(value, self.identity())
        self.assertTrue(cache.matches(self.root, value, receipt))

    def test_toolchain_content_change_with_same_mtime_invalidates(self):
        value = self.identity()
        receipt = cache.receipt(self.root, value, self.apk)
        stat = self.compiler.stat()
        self.compiler.write_bytes(b"changed--compiler-not-an-executable")
        os.utime(self.compiler, (stat.st_atime, stat.st_mtime))
        changed = self.identity()
        self.assertNotEqual(value["toolchainSha256"], changed["toolchainSha256"])
        self.assertFalse(cache.matches(self.root, changed, receipt))

    def test_native_source_lock_abi_config_and_wrapper_changes_invalidate(self):
        value = self.identity()
        for filename in ("app/src/main/cpp/supervisor.c", "runtime/dependency.lock", "app/build.gradle.kts",
                         "gradle/wrapper/gradle-wrapper.properties"):
            path = self.root / filename
            original = path.read_bytes()
            path.write_bytes(original + b"synthetic-change")
            self.assertNotEqual(value["sourceSha256"], self.identity()["sourceSha256"])
            path.write_bytes(original)

    def test_newly_fetched_dependencies_refresh_once_then_warm_cache_matches(self):
        empty = self.identity()
        talloc = self.root / ".build/sources/talloc-2.4.3/talloc.c"
        talloc.parent.mkdir(parents=True)
        talloc.write_text("synthetic-upstream-source")
        proot = self.root / ".build/sources/termux-proot"
        proot.mkdir()
        (proot / "source.c").write_text("synthetic-proot-source")
        for args in (("init", "--initial-branch=main"), ("add", "."),
                     ("-c", "user.name=leezzou", "-c", "user.email=115924933+leezzou@users.noreply.github.com", "-c", "core.hooksPath=/dev/null", "commit", "-m", "test: synthetic dependency")):
            result = subprocess.run(["git", *args], cwd=proot, capture_output=True)
            self.assertEqual(0, result.returncode)
        populated = cache.refresh_dependencies(self.root, empty)
        self.assertNotEqual(empty["sha256"], populated["sha256"])
        receipt = cache.receipt(self.root, populated, self.apk)
        self.assertEqual(populated, self.identity())
        self.assertTrue(cache.matches(self.root, self.identity(), receipt))
        (proot / "source.c").write_text("changed cached upstream source")
        self.assertFalse(cache.matches(self.root, self.identity(), receipt))
        talloc.write_text("changed cached talloc source")
        self.assertNotEqual(populated["dependencySha256"], self.identity()["dependencySha256"])

    def test_output_change_and_apk_mismatch_refuse_reuse(self):
        value = self.identity()
        receipt = cache.receipt(self.root, value, self.apk)
        output = self.root / "app/src/main/jniLibs/arm64-v8a/libproot.so"
        output.write_bytes(b"changed output")
        self.assertFalse(cache.matches(self.root, value, receipt))
        with self.assertRaisesRegex(ValueError, "APK 实际 native"):
            cache.receipt(self.root, value, self.apk)
        output.unlink()
        self.assertFalse(cache.matches(self.root, value, receipt))

    def test_sdk_path_tool_mode_and_requested_ndk_change_refuse_stale_cache(self):
        value = self.identity()
        second = self.directory / "second-sdk"
        shutil.copytree(self.sdk, second)
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertNotEqual(value["sha256"], cache.identity(self.root, second)["sha256"])
        self.compiler.chmod(self.compiler.stat().st_mode ^ 0o100)
        self.assertNotEqual(value["toolchainSha256"], self.identity()["toolchainSha256"])
        self.script.write_text(self.script.read_text().replace("27.2.12479018", "28.0.0"))
        with self.assertRaisesRegex(ValueError, "固定 NDK"):
            self.identity()

    def test_linked_native_output_is_refused_instead_of_forced_foreign_write(self):
        value = self.identity()
        receipt = cache.receipt(self.root, value, self.apk)
        output = self.root / "app/src/main/jniLibs/arm64-v8a/libproot.so"
        outside = self.directory / "another-worktree-output"
        outside.write_bytes(output.read_bytes())
        output.unlink()
        output.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "拒绝重建"):
            cache.matches(self.root, value, receipt)
        with self.assertRaisesRegex(ValueError, "拒绝重建"):
            self.identity()
        self.assertEqual(b"synthetic-ELF-libproot.so", outside.read_bytes())

    def test_toolchain_mutation_during_build_refuses_final_cache_receipt(self):
        value = self.identity()
        self.compiler.write_bytes(b"changed during synthetic build")
        spec = importlib.util.spec_from_file_location("merge_release_cache_test", ROOT / "scripts/merge-release.py")
        release = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(release)
        executor = release.Executor({}, self.directory / "service", release.Runner(), None)
        executor.checkout = self.root
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(release.ReleaseError, "构建期间"):
            executor._record_native(cache, value, self.apk)
        self.assertFalse((executor.home / "native-cache.json").exists())

    def test_unknown_protocol_and_external_tool_link_are_rejected(self):
        original = self.script.read_text()
        self.script.write_text("synthetic-unknown-toolchain-selection")
        with self.assertRaisesRegex(ValueError, "固定 NDK 协议"):
            self.identity()
        self.script.write_text(original)
        outside = self.directory / "synthetic-outside-file"
        outside.write_text("synthetic-not-a-secret")
        (self.ndk / "external-link").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "链接越过"):
            self.identity()


if __name__ == "__main__":
    unittest.main()

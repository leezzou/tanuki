#!/usr/bin/env python3
"""无.git源码包的离线验证/重建回归；只运行合成Gradle命令，不构建Android。"""
from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def load(path):
    spec = importlib.util.spec_from_file_location("fixture_" + path.stem.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ReleaseSourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve() / "repo"
        self.root.mkdir()
        self.package = load(ROOT / "scripts/package-source.py")
        self.package.ROOT = self.root
        for name in self.package.FILES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("synthetic-public-source\n")
        for name in self.package.TREES:
            path = self.root / name
            path.mkdir(parents=True, exist_ok=True)
            (path / "fixture.txt").write_text("synthetic-public-source\n")
        for name in ("package-source.py", "release-source.py", "check-handoff.py", "prepare-app-release.py", "verify-workspace.sh"):
            shutil.copyfile(ROOT / "scripts" / name, self.root / "scripts" / name)
        (self.root / "scripts/env.sh").write_text('#!/bin/bash\n# 合成环境，不读取宿主配置。\nTANUKI_PROJECT="$PWD"\n')
        (self.root / "gradlew").write_text("#!/bin/bash\nmkdir -p .build\nprintf '%s\\n' \"$@\" > .build/rebuild-arguments.txt\n")
        (self.root / "gradlew").chmod(0o755)
        (self.root / ".gitignore").write_text(".build/\nartifacts/\n")
        self.record("20260101-0000-base.md")
        (self.root / "docs/AGENT-STATE.md").write_text("旧交接：handoffs/20260101-0000-base.md\n")
        (self.root / "docs/handoffs/CURRENT.json").write_text('{"schema":2,"record":"intentionally-stale"}\n')
        self.git("init", "--initial-branch=main")
        self.base = self.commit()
        self.record("20260102-0000-merge.md")
        (self.root / "app/src/fixture.txt").write_text("changed-public-source\n")
        self.end = self.commit()
        self.tree = self.git("rev-parse", "HEAD^{tree}").strip()
        self.manifest = {"schemaVersion": 1, "sourceCommit": self.end, "sourceTree": self.tree, "sourceBase": self.base,
                         "packages": [{"versionName": "0.1.100", "versionCode": 100, "sha256": "e" * 64,
                         "certificateSha256": "371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f"}]}
        (self.root / ".build").mkdir()
        self.manifest_path = self.root / ".build/manifest.json"
        self.manifest_path.write_text(json.dumps(self.manifest))
        archive = self.root / "artifacts/sources/native/Tanuki-native-corresponding-sources.tar.gz"
        archive.parent.mkdir(parents=True)
        files = {".build/sources/termux-proot/.git/HEAD": b"a" * 40 + b"\n",
                 ".build/sources/termux-proot/.git/config": b"[core]\nrepositoryformatversion = 0\nbare = false\nfilemode = true\n",
                 ".build/sources/termux-proot/source.c": b"synthetic-upstream-source",
                 ".build/sources/talloc-2.4.3/talloc.c": b"synthetic-talloc-source"}
        with tarfile.open(archive, "w:gz") as output:
            for name, data in files.items():
                entry = tarfile.TarInfo(name)
                entry.size, entry.mode = len(data), 0o644
                output.addfile(entry, io.BytesIO(data))
            link = tarfile.TarInfo(".build/sources/termux-proot/source-link.c")
            link.type, link.linkname = tarfile.SYMTYPE, "source.c"
            output.addfile(link)
        proof = load(ROOT / "scripts/release-source.py")
        (archive.parent / "native-source-SHA256SUMS").write_text(proof.sha256(archive) + "  " + archive.name + "\n")

    def git(self, *args):
        result = subprocess.run(["git", *args], cwd=self.root, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        return result.stdout

    def commit(self):
        self.git("add", ".")
        self.git("-c", "core.hooksPath=/dev/null", "-c", "user.name=leezzou", "-c", "user.email=115924933+leezzou@users.noreply.github.com", "commit", "-m", "test: synthetic public source")
        return self.git("rev-parse", "HEAD").strip()

    def record(self, name):
        path = self.root / "docs/handoffs" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        headings = ("用户目标与范围", "当前状态", "本次改动", "验证记录", "未完成与暂停项", "下一步", "约束与风险", "产物与校验")
        path.write_text("# 合成源码交接\n" + "".join("\n## " + h + "\n\n合成数据；未执行设备和真实模型。\n" for h in headings))

    def package_and_extract(self):
        with contextlib.redirect_stdout(io.StringIO()):
            self.package.main("0.1.100", self.manifest_path)
        archive = self.root / "artifacts/sources/Tanuki-0.1.100-source.zip"
        extracted = self.root.parent / "unpacked"
        with zipfile.ZipFile(archive) as source:
            source.extractall(extracted)
            # 模拟 Unix unzip 恢复归档内明确权限，不依赖宿主缓存或 .git。
            for entry in source.infolist():
                (extracted / entry.filename).chmod((entry.external_attr >> 16) & 0o777)
        result = extracted / "Tanuki-0.1.100"
        self.assertFalse((result / ".git").exists())
        return archive, result

    def test_no_git_package_verifies_stale_shared_state_and_rebuilds_exact_identity(self):
        _, root = self.package_and_extract()
        for args in (["scripts/check-handoff.py", "--pr"], ["scripts/check-handoff.py", "--release"],
                     ["scripts/release-source.py", "verify", "--version", "0.1.100", "--version-code", "100"]):
            result = subprocess.run([sys.executable, *args], cwd=root, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
        strict = subprocess.run([sys.executable, "scripts/check-handoff.py", "--release", "--shared-stamp"], cwd=root, capture_output=True)
        self.assertNotEqual(0, strict.returncode, "人工严格共享印章不能借生成证明通过")
        rebuild = [sys.executable, "scripts/release-source.py", "rebuild", "--version", "0.1.100", "--version-code", "100"]
        key = self.root.parent / "synthetic-not-a-key.keystore"
        key.write_bytes(b"synthetic-not-a-private-key")
        environment = dict(os.environ, TANUKI_WORKSPACECHECK_KEYSTORE=str(key))
        planned = subprocess.run(rebuild + ["--plan"], cwd=root, env=environment, capture_output=True, text=True)
        self.assertEqual(0, planned.returncode, planned.stderr)
        command = json.loads(planned.stdout.splitlines()[-1])["command"]
        self.assertIn("-PtanukiVersionName=0.1.100", command)
        self.assertIn("-PtanukiVersionCode=100", command)
        self.assertIn(":app:assembleModernRelease", command)
        self.assertNotIn("-x", command)
        result = subprocess.run(rebuild, cwd=root, env=environment, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        arguments = (root / ".build/rebuild-arguments.txt").read_text().splitlines()
        self.assertEqual(command[1:], arguments)
        self.assertEqual("synthetic-talloc-source", (root / ".build/sources/talloc-2.4.3/talloc.c").read_text())
        self.assertTrue((root / ".build/sources/termux-proot/source-link.c").is_symlink())
        workspace = subprocess.run(["bash", "scripts/verify-workspace.sh"], cwd=root, capture_output=True, text=True)
        self.assertEqual(0, workspace.returncode, workspace.stderr)
        self.assertIn("no APK was installed", workspace.stdout)

    def test_multi_commit_merge_range_has_offline_first_parent_proof(self):
        first = self.end
        (self.root / "app/src/fixture.txt").write_text("second commit in the same synthetic PR")
        end = self.commit()
        self.manifest.update(sourceCommit=end, sourceTree=self.git("rev-parse", "HEAD^{tree}").strip())
        self.manifest_path.write_text(json.dumps(self.manifest))
        _, root = self.package_and_extract()
        document = json.loads((root / "RELEASE-SOURCE.json").read_text())
        self.assertEqual([first, end], document["mergeCommits"])
        self.assertEqual(end, load(root / "scripts/release-source.py").verify(root)["commit"])

    def test_archive_is_reproducible_without_host_paths_or_mtime(self):
        archive, _ = self.package_and_extract()
        before = archive.read_bytes()
        with contextlib.redirect_stdout(io.StringIO()):
            self.package.main("0.1.100", self.manifest_path)
        self.assertEqual(before, archive.read_bytes())

    def test_source_edit_is_rejected_even_if_metadata_file_hash_is_rewritten(self):
        _, root = self.package_and_extract()
        proof = load(root / "scripts/release-source.py")
        source = root / "app/src/fixture.txt"
        source.write_text("unrecorded-source-change")
        with self.assertRaisesRegex(ValueError, "文件散列"):
            proof.verify(root)
        path = root / "RELEASE-SOURCE.json"
        metadata = json.loads(path.read_text())
        metadata["files"]["app/src/fixture.txt"] = proof.sha256(source)
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "Git tree"):
            proof.verify(root)

    def test_extra_public_source_is_rejected(self):
        _, root = self.package_and_extract()
        (root / "scripts/unrecorded.py").write_text("synthetic-unrecorded")
        with self.assertRaisesRegex(ValueError, "文件集合"):
            load(root / "scripts/release-source.py").verify(root)
        (root / "scripts/unrecorded.py").unlink()
        (root / "unexpected.txt").write_text("synthetic-unrecorded-root-file")
        with self.assertRaisesRegex(ValueError, "不在公开源码清单"):
            load(root / "scripts/release-source.py").verify(root)

    def test_commit_changed_index_and_handoff_claims_are_not_trusted(self):
        _, root = self.package_and_extract()
        proof = load(root / "scripts/release-source.py")
        path = root / "RELEASE-SOURCE.json"
        original = json.loads(path.read_text())
        import copy
        for mutate in (lambda d: d["commits"].__setitem__(self.end, "forged-commit"),
                       lambda d: d.__setitem__("changedFiles", []), lambda d: d.__setitem__("handoffs", []),
                       lambda d: d["identity"].__setitem__("tree", "f" * 40)):
            document = copy.deepcopy(original)
            mutate(document)
            path.write_text(json.dumps(document))
            with self.assertRaises(ValueError):
                proof.verify(root)

    def test_actual_new_source_change_without_changed_handoff_is_rejected(self):
        base = self.end
        (self.root / "app/src/fixture.txt").write_text("another source change without handoff")
        end = self.commit()
        self.manifest.update(sourceBase=base, sourceCommit=end, sourceTree=self.git("rev-parse", "HEAD^{tree}").strip())
        self.manifest_path.write_text(json.dumps(self.manifest))
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(ValueError):
            self.package.main("0.1.100", self.manifest_path)

    def test_untracked_public_source_cannot_enter_declared_tree(self):
        (self.root / "scripts/untracked.py").write_text("synthetic-untracked")
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "Git tree"):
            self.package.main("0.1.100", self.manifest_path)

    def test_restore_native_sources_is_idempotent_and_refuses_existing_different_bytes(self):
        _, root = self.package_and_extract()
        proof = load(root / "scripts/release-source.py")
        with contextlib.redirect_stdout(io.StringIO()):
            proof.restore_native_sources(root)
            proof.restore_native_sources(root)
        cached = root / ".build/sources/termux-proot/source.c"
        cached.write_text("existing independently changed cache")
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "已有原生缓存字节不同"):
            proof.restore_native_sources(root)
        self.assertEqual("existing independently changed cache", cached.read_text())

    def test_native_archive_traversal_and_external_link_never_write_outside_source_cache(self):
        _, root = self.package_and_extract()
        proof = load(root / "scripts/release-source.py")
        archive = root / "artifacts/sources/native/Tanuki-native-corresponding-sources.tar.gz"
        for link in (False, True):
            with tarfile.open(archive, "w:gz") as output:
                member = tarfile.TarInfo(".build/sources/../../../escaped" if not link else ".build/sources/termux-proot/external")
                if link:
                    member.type, member.linkname = tarfile.SYMTYPE, "../../../../escaped"
                    output.addfile(member)
                else:
                    member.size = 1
                    output.addfile(member, io.BytesIO(b"x"))
            with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(ValueError, "越界"):
                proof.restore_native_sources(root)
            self.assertFalse((root.parent / "escaped").exists())
            self.assertFalse((root / ".build/sources/termux-proot/external").is_symlink())

    def test_rebuild_without_existing_key_path_stops_before_gradle(self):
        _, root = self.package_and_extract()
        environment = dict(os.environ)
        environment.pop("TANUKI_WORKSPACECHECK_KEYSTORE", None)
        result = subprocess.run([sys.executable, "scripts/release-source.py", "rebuild", "--version", "0.1.100", "--version-code", "100"],
                                cwd=root, env=environment, capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("不会生成替代 key", result.stderr)
        self.assertFalse((root / ".build/rebuild-arguments.txt").exists())

    def test_rebuild_refuses_wrong_version_or_code(self):
        _, root = self.package_and_extract()
        for version, code in (("0.1.99", "100"), ("0.1.100", "101")):
            result = subprocess.run([sys.executable, "scripts/release-source.py", "rebuild", "--plan", "--version", version,
                                     "--version-code", code], cwd=root, capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse((root / ".build/rebuild-arguments.txt").exists())


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3
"""离线回归 check-handoff.py 的 PR / 集成 / 发布三种模式，不访问网络或设备。"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
RECORD = "docs/handoffs/20260101-0000-test-session.md"
NEWER = "docs/handoffs/20260102-0000-newer-session.md"
HEADINGS = ("用户目标与范围", "当前状态", "本次改动", "验证记录",
            "未完成与暂停项", "下一步", "约束与风险", "产物与校验")


def record_text(verification="`python3 scripts/check-handoff.py --pr` 通过。\n\n未测试：设备未运行。", extra=""):
    """生成结构完整的合成交接；用于证明检查器只判断结构而不依赖真实项目内容。"""
    body = "\n".join(f"## {heading}\n合成内容：{heading}。\n" for heading in HEADINGS)
    body = body.replace("## 验证记录\n合成内容：验证记录。", f"## 验证记录\n{verification}")
    return f"""# 交接：20260101-0000-test-session

- 状态：进行中
- 前一记录：无

{body}
{extra}
"""


class CheckHandoffTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="tanuki-handoff-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "project with spaces"
        (self.root / "scripts").mkdir(parents=True)
        shutil.copy2(ROOT / "scripts/check-handoff.py", self.root / "scripts/check-handoff.py")
        self.put("scripts/package-source.py", '''from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
FILES = ("AGENTS.md", "README.md")
TREES = ("scripts", "docs", "app/src")
def public_name(relative):
    if relative.parts[:4] == ("app", "src", "main", "jniLibs"):
        return False
    return bool(relative.parts) and relative.parts[0] not in {"artifacts", ".build", "__pycache__"} \
        and relative.suffix not in {".tmp"}
def public_file(path):
    relative = path.relative_to(ROOT)
    return path.is_file() and public_name(relative) and "__pycache__" not in path.parts
''')
        self.put("AGENTS.md", "Agent rules\n")
        self.put("README.md", "Public project\n")
        self.put("app/src/main/example.kt", "// original\n")
        self.put("docs/handoffs/TEMPLATE.md", "# 交接模板\n")
        self.put("docs/AGENT-STATE.md", "No current record link yet\n")
        self.put(RECORD, record_text())

    def put(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def checker(self, *args):
        env = {**os.environ, "PYTHONDONTWRITEBYTECODE": "1"}
        return subprocess.run([sys.executable, "scripts/check-handoff.py", *args],
                              cwd=self.root, env=env, capture_output=True, text=True)

    def assert_ok(self, *args):
        result = self.checker(*args)
        self.assertEqual(0, result.returncode, result.stderr)
        return result

    def assert_fail(self, *args):
        result = self.checker(*args)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("Handoff check failed", result.stderr)
        return result

    def link_state(self, name=RECORD):
        self.put("docs/AGENT-STATE.md", f"[Latest](handoffs/{Path(name).name})\n")

    def test_pr_mode_allows_stale_shared_state_and_never_writes(self):
        # 普通 PR：记录完整即可，AGENT-STATE 未链接、CURRENT.json 缺失都不失败。
        result = self.assert_ok("--pr", "--record", RECORD)
        self.assertIn("shared state may lag", result.stdout)
        self.assertFalse((self.root / "docs/handoffs/CURRENT.json").exists())
        self.assert_ok("--pr")

    def test_pr_mode_rejects_incomplete_record(self):
        missing = record_text().replace("## 下一步\n合成内容：下一步。\n", "")
        self.put(RECORD, missing)
        self.assert_fail("--pr", "--record", RECORD)
        empty = record_text().replace("## 验证记录\n`python3 scripts/check-handoff.py --pr` 通过。\n\n未测试：设备未运行。",
                                      "## 验证记录\n\n")
        self.put(RECORD, empty)
        self.assert_fail("--pr", "--record", RECORD)
        placeholder = record_text(extra="| <相对路径> | <为什么改> |")
        self.put(RECORD, placeholder)
        self.assert_fail("--pr", "--record", RECORD)
        no_gap = record_text(verification="所有命令均通过。")
        self.put(RECORD, no_gap)
        self.assert_fail("--pr", "--record", RECORD)

    def test_pr_mode_rejects_bad_paths_and_symlink(self):
        for bad in ("../record.md", "/tmp/record.md", "docs/handoffs/TEMPLATE.md",
                    "docs/handoffs/foo.md", "docs/handoffs/20260101-0000-test-session.txt"):
            with self.subTest(path=bad):
                self.assert_fail("--pr", "--record", bad)
        (self.root / "docs/handoffs/20260103-0000-link.md").symlink_to(
            self.root / "docs/handoffs/20260101-0000-test-session.md")
        self.assert_fail("--pr", "--record", "docs/handoffs/20260103-0000-link.md")

    def test_changed_source_requires_a_changed_record(self):
        # 公开源码/文档有改动时，本次变更集必须同时新增或更新交接记录。
        self.assert_fail("--pr", "--changed-file", "app/src/main/example.kt")
        self.assert_ok("--pr", "--changed-file", "app/src/main/example.kt", "--changed-file", RECORD)
        # 共享状态单独变化（集成方刷新）不要求新记录。
        self.assert_ok("--pr", "--changed-file", "docs/AGENT-STATE.md")
        self.assert_ok("--pr", "--changed-file", "docs/handoffs/CURRENT.json")
        # 模板属于机制文档，改动仍要求交接。
        self.assert_fail("--pr", "--changed-file", "docs/handoffs/TEMPLATE.md")
        # 生成的 jniLibs 不在公开指纹内，不触发交接要求。
        self.assert_ok("--pr", "--changed-file", "app/src/main/jniLibs/arm64-v8a/libsynthetic.so")
        # 显式传入的旧记录必须属于本次变更集，不能拿旧记录充当新证据。
        self.assert_fail("--pr", "--record", RECORD, "--changed-file", "app/src/main/example.kt")
        # 变更清单里的交接文件名必须符合 YYYYMMDD-HHMM-topic.md。
        self.assert_fail("--pr", "--changed-file", "docs/handoffs/not-a-record.md")

    def test_deleted_public_file_requires_record_but_handoff_deletion_is_exempt(self):
        # 删除公开源码/回归测试也必须附带本次交接记录。
        self.assert_fail("--pr", "--changed-file", "app/src/main/deleted.kt")
        self.assert_ok("--pr", "--changed-file", "app/src/main/deleted.kt", "--changed-file", RECORD)
        # 删除旧交接记录属于清理，不要求为删除本身再写一条记录。
        self.assert_ok("--pr", "--changed-file", "docs/handoffs/20260103-0000-old.md")
        self.assert_fail("--pr", "--changed-file", "app/src/main/deleted.kt",
                         "--changed-file", "docs/handoffs/20260103-0000-old.md")

    def test_ci_workflow_keeps_deletions_and_structural_fallback(self):
        # 防止 CI 回归为“排除删除”或“无基线时传空清单”。
        workflow = (ROOT / ".github/workflows/verify.yml").read_text()
        self.assertIn("--diff-filter=AMRD", workflow)
        self.assertRegex(workflow, r"python3 scripts/check-handoff\.py --pr\s*\n")

    def test_pr_mode_without_changed_list_checks_latest_record(self):
        # CI 拿不到基线时不传空清单；无清单的 --pr 必须实际校验最新记录结构。
        invalid = record_text().replace("## 下一步\n合成内容：下一步。\n", "")
        self.put(NEWER, invalid.replace("20260101-0000", "20260102-0000"))
        self.assert_fail("--pr")
        self.put(NEWER, record_text().replace("20260101-0000", "20260102-0000"))
        self.assert_ok("--pr")

    def test_integrate_requires_state_link_and_latest_record(self):
        self.assert_fail("--integrate", "--record", RECORD)
        self.link_state()
        self.assert_ok("--integrate", "--record", RECORD)
        stamp = json.loads((self.root / "docs/handoffs/CURRENT.json").read_text())
        self.assertEqual(2, stamp["schema"])
        self.assertEqual(RECORD, stamp["record"])
        self.assertEqual(hashlib.sha256((self.root / RECORD).read_bytes()).hexdigest(),
                         stamp["recordSha256"])
        self.assert_ok("--release")

        self.put(NEWER, record_text().replace("20260101-0000", "20260102-0000"))
        self.assert_fail("--integrate", "--record", RECORD)
        self.link_state(NEWER)
        self.assert_ok("--integrate", "--record", NEWER)
        self.assert_ok("--release")
        self.assert_ok("--integrate")

    def test_release_detects_code_and_record_changes_until_integrate(self):
        self.link_state()
        self.assert_ok("--integrate", "--record", RECORD)
        self.put("app/src/main/example.kt", "// changed\n")
        self.assert_fail("--release")
        # 源码变了但交接正文没变，不允许只重盖章。
        self.assert_fail("--integrate", "--record", RECORD)
        with (self.root / RECORD).open("a") as output:
            output.write("\n补充：源码改动后的验证与未测试范围。\n")
        self.assert_ok("--integrate", "--record", RECORD)
        self.assert_ok("--release")
        # 只改共享状态不触发证据变化，集成可以刷新印章。
        with (self.root / "docs/AGENT-STATE.md").open("a") as output:
            output.write("状态补充\n")
        self.assert_ok("--integrate", "--record", RECORD)
        self.assert_ok("--release")
        # 印章写好后篡改记录必须失败。
        with (self.root / RECORD).open("a") as output:
            output.write("tamper\n")
        self.assert_fail("--release")
        self.assert_fail("--release", "--record", RECORD)

    def test_release_requires_stamp(self):
        self.assert_fail("--release")

    def test_integrated_stamp_is_portable_across_directory_move(self):
        self.link_state()
        self.assert_ok("--integrate", "--record", RECORD)
        moved = self.root.with_name("moved project")
        shutil.copytree(self.root, moved)
        result = subprocess.run([sys.executable, "scripts/check-handoff.py", "--release"],
                                cwd=moved, env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
                                capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)

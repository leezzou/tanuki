#!/usr/bin/env python3
"""离线回归：用假 gh/ao 验证研发门禁的拒绝与豁免边界，不访问网络或真实 PR。"""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
CHECKER = ROOT / "scripts/check-development-gates.py"
PR = "https://github.com/leezzou/tanuki/pull/13"
HEAD = "a" * 40
OLD_SHA = "b" * 40
OTHER_SHA = "c" * 40

GH_SCRIPT = """#!/bin/sh
FIX="$TANUKI_FAKE_FIXTURES"
case "$1 $2" in
  "repo view")
    cat "$FIX/repo.json" ;;
  "pr view")
    count=0
    if [ -f "$FIX/view-count" ]; then count=$(cat "$FIX/view-count"); fi
    count=$((count + 1))
    printf '%s' "$count" > "$FIX/view-count"
    if [ -f "$FIX/pr3.json" ] && [ "$count" -ge 3 ]; then
      cat "$FIX/pr3.json"
    elif [ -f "$FIX/pr2.json" ] && [ "$count" -ge 2 ]; then
      cat "$FIX/pr2.json"
    else
      cat "$FIX/pr.json"
    fi ;;
  "pr diff")
    cat "$FIX/files.txt" ;;
  "pr checks")
    cat "$FIX/checks.json"
    exit "${FAKE_CHECKS_EXIT:-0}" ;;
  *)
    exit 3 ;;
esac
"""

AO_SCRIPT = """#!/bin/sh
FIX="$TANUKI_FAKE_FIXTURES"
case "$1 $2" in
  "session ls")
    cat "$FIX/sessions.json" ;;
  "project get")
    cat "$FIX/project.json" ;;
  "review ls")
    cat "$FIX/reviews.json"
    exit "${FAKE_REVIEW_EXIT:-0}" ;;
  *)
    exit 3 ;;
esac
"""


class DevelopmentGateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.fixtures = self.root / "fixtures"
        self.fixtures.mkdir()
        binaries = self.root / "bin"
        binaries.mkdir()
        self._write_executable(binaries / "gh", GH_SCRIPT)
        self._write_executable(binaries / "ao", AO_SCRIPT)
        self.environment = dict(os.environ)
        self.environment["PATH"] = str(binaries) + os.pathsep + self.environment.get("PATH", "")
        self.environment["TANUKI_FAKE_FIXTURES"] = str(self.fixtures)
        self.set_json("repo.json", {"nameWithOwner": "leezzou/tanuki",
                                    "url": "https://github.com/leezzou/tanuki"})
        self.set_json("project.json", {"status": "ok", "project": {
            "id": "android_agent",
            "config": {"defaultBranch": "main", "reviewers": [{"harness": "codex"}]},
        }})
        self.set_json("sessions.json", {"data": [
            {"id": "android_agent-7", "projectId": "android_agent",
             "harness": "deepseek-harness", "role": "worker", "status": "working"},
            {"id": "android_agent-8", "projectId": "android_agent",
             "harness": "deepseek-harness", "role": "worker", "status": "working"},
        ]})
        self.set_json("pr.json", {"url": PR, "number": 13, "state": "OPEN",
                                  "isDraft": False, "baseRefName": "main", "headRefOid": HEAD})
        self.set_files(["docs/AGENTS.md"])
        self.set_json("checks.json", [
            {"name": "verify / Host scripts", "state": "SUCCESS", "bucket": "pass", "link": "x"},
            {"name": "verify / Android build", "state": "SUCCESS", "bucket": "pass", "link": "x"},
        ])
        self.set_json("reviews.json", {"reviews": [self.review()]})
        self.qa_path = self.root / "qa.json"

    def tearDown(self):
        self.temporary.cleanup()

    def _write_executable(self, path: Path, content: str):
        path.write_text(content)
        path.chmod(0o755)

    def set_json(self, name: str, value):
        (self.fixtures / name).write_text(json.dumps(value))

    def set_files(self, paths):
        (self.fixtures / "files.txt").write_text("\n".join(paths) + "\n")

    def review(self, **overrides):
        run = {"targetSha": HEAD, "status": "complete", "verdict": "approved",
               "harness": "codex", "githubReviewId": "5795793779",
               "createdAt": "2026-11-06T04:36:13Z"}
        run.update(overrides)
        return {"prUrl": PR, "prNumber": 13, "title": "governance",
                "targetSha": run["targetSha"], "status": "needs_review", "latestRun": run}

    def evidence(self, **overrides):
        data = {
            "schema": 1, "pr": PR, "sha": HEAD, "qaSession": "android_agent-8",
            "device": {"avd": "Pixel_7_API_34_arm64", "serial": "emulator-5572",
                        "api": 34, "abi": "arm64-v8a"},
            "apk": {"package": "com.tanuki.modern.workspacecheck", "sha256": "d" * 64},
            "steps": [{"name": "device smoke", "command": "adb shell am instrument -w",
                        "result": "passed", "source": "android"}],
            "model": {"configured": False},
        }
        data.update(overrides)
        return data

    def run_gate(self, qa=None, environment=None):
        command = [sys.executable, str(CHECKER), "--pr", "13", "--session", "android_agent-7"]
        if qa is not None:
            self.qa_path.write_text(json.dumps(qa))
            command += ["--qa-evidence", str(self.qa_path)]
        env = dict(self.environment)
        env.update(environment or {})
        return subprocess.run(command, cwd=str(self.root), capture_output=True, text=True, env=env)

    def assert_passes(self, result):
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("GATE RESULT: PASS", result.stdout)
        self.assertNotIn("REJECT", result.stdout)

    def assert_rejected(self, result, reason=None):
        self.assertEqual(1, result.returncode, result.stdout)
        self.assertIn("GATE RESULT: REJECT", result.stderr)
        if reason is not None:
            self.assertIn(reason, result.stderr)

    def test_docs_only_pr_passes_without_qa(self):
        self.assert_passes(self.run_gate())

    def test_gate_own_files_are_exempt(self):
        self.set_files(["scripts/check-development-gates.py", "scripts/test-development-gates.py",
                        ".github/workflows/verify.yml", "AGENTS.md"])
        self.assert_passes(self.run_gate())

    def test_stale_review_sha_is_rejected(self):
        self.set_json("reviews.json", {"reviews": [self.review(targetSha=OLD_SHA)]})
        self.assert_rejected(self.run_gate(), "没有绑定当前 head")

    def test_missing_review_is_rejected(self):
        self.set_json("reviews.json", {"reviews": []})
        self.assert_rejected(self.run_gate(), "没有绑定当前 head")

    def test_review_query_failure_is_rejected(self):
        self.assert_rejected(self.run_gate(environment={"FAKE_REVIEW_EXIT": "1"}), "只读查询失败")

    def test_review_pr_mismatch_is_rejected(self):
        review = self.review()
        review["prUrl"] = "https://github.com/leezzou/tanuki/pull/999"
        self.set_json("reviews.json", {"reviews": [review]})
        self.assert_rejected(self.run_gate(), "没有绑定当前 head")

    def test_self_review_is_rejected(self):
        self.set_json("reviews.json", {"reviews": [self.review(harness="deepseek-harness")]})
        self.assert_rejected(self.run_gate(), "独立 reviewer")

    def test_changes_requested_is_rejected(self):
        self.set_json("reviews.json", {"reviews": [self.review(verdict="changes_requested")]})
        self.assert_rejected(self.run_gate(), "未被 AO 原生 reviewer 批准")

    def test_unconfigured_reviewer_is_rejected(self):
        self.set_json("project.json", {"status": "ok", "project": {
            "id": "android_agent",
            "config": {"defaultBranch": "main", "reviewers": [{"harness": "claude-code"}]},
        }})
        self.assert_rejected(self.run_gate(), "独立 reviewer")

    def test_empty_checks_are_rejected(self):
        self.set_json("checks.json", [])
        self.assert_rejected(self.run_gate(), "空 checks")

    def test_pending_check_is_rejected(self):
        self.set_json("checks.json", [{"name": "verify", "state": "QUEUED", "bucket": "pending"}])
        self.assert_rejected(self.run_gate(), "未通过、排队、失败或跳过")

    def test_failed_check_is_rejected(self):
        self.set_json("checks.json", [{"name": "verify", "state": "FAILURE", "bucket": "fail"}])
        self.assert_rejected(self.run_gate(), "未通过、排队、失败或跳过")

    def test_skipped_check_is_rejected(self):
        self.set_json("checks.json", [{"name": "verify", "state": "SKIPPED", "bucket": "skipping"}])
        self.assert_rejected(self.run_gate(), "未通过、排队、失败或跳过")

    def test_check_query_failure_is_rejected(self):
        self.assert_rejected(self.run_gate(environment={"FAKE_CHECKS_EXIT": "3"}), "无法读取 PR check")

    def test_android_change_requires_qa(self):
        self.set_files(["app/src/main/kotlin/com/tanuki/TanukiApp.kt"])
        self.assert_rejected(self.run_gate(), "缺少独立 QA 证据")

    def test_unknown_scope_requires_qa(self):
        self.set_files(["scripts/unclassified-tool.sh"])
        self.assert_rejected(self.run_gate(), "缺少独立 QA 证据")

    def test_product_skill_markdown_requires_qa(self):
        for path in ("app/src/main/assets/browser/SKILL.md",
                     "app/src/main/assets/phone/SKILL.md"):
            with self.subTest(path=path):
                self.set_files([path])
                self.assert_rejected(self.run_gate(), "缺少独立 QA 证据")

    def test_root_markdown_remains_exempt(self):
        self.set_files(["CHANGELOG.md"])
        result = self.run_gate()
        self.assert_passes(result)
        self.assertIn("qa=not_required", result.stdout)

    def test_valid_qa_with_skips_is_not_all_green(self):
        self.set_files(["app/src/main/kotlin/com/tanuki/TanukiApp.kt"])
        evidence = self.evidence(steps=[
            {"name": "device smoke", "command": "adb shell am instrument -w",
             "result": "passed", "source": "android"},
            {"name": "model task", "command": "synthetic model run", "result": "skipped",
             "source": "android", "reason": "real model/account not configured"},
        ])
        result = self.run_gate(qa=evidence)
        self.assert_passes(result)
        self.assertIn("qa=passed_with_unverified", result.stdout)
        self.assertIn("unverified=step:model task skipped", result.stdout)
        self.assertIn("真实模型/账号链路未配置", result.stdout)
        self.assertNotIn("qa=passed\n", result.stdout)

    def test_qa_wrong_sha_is_rejected(self):
        self.set_files(["runtime/build-native-runtime.sh"])
        self.assert_rejected(self.run_gate(qa=self.evidence(sha=OLD_SHA)), "不是当前 PR head")

    def test_qa_must_be_independent_session(self):
        self.set_files(["app/build.gradle.kts"])
        self.assert_rejected(self.run_gate(qa=self.evidence(qaSession="android_agent-7")),
                             "独立 QA session")

    def test_qa_session_must_exist(self):
        self.set_files(["app/build.gradle.kts"])
        self.assert_rejected(self.run_gate(qa=self.evidence(qaSession="android_agent-99")),
                             "不是当前 AO 已知 session")

    def test_qa_failed_step_is_rejected(self):
        self.set_files(["app/build.gradle.kts"])
        evidence = self.evidence(steps=[{"name": "device smoke", "command": "adb shell",
                                         "result": "failed", "source": "android"}])
        self.assert_rejected(self.run_gate(qa=evidence), "失败步骤")

    def test_qa_without_android_step_is_rejected(self):
        self.set_files(["app/build.gradle.kts"])
        evidence = self.evidence(steps=[{"name": "host build", "command": "./gradlew assemble",
                                         "result": "passed", "source": "host"}])
        self.assert_rejected(self.run_gate(qa=evidence), "Android 内实际执行")

    def test_pr_from_other_repository_is_rejected(self):
        self.set_json("pr.json", {"url": "https://github.com/other/repo/pull/13", "number": 13,
                                  "state": "OPEN", "isDraft": False, "baseRefName": "main",
                                  "headRefOid": HEAD})
        self.assert_rejected(self.run_gate(), "不属于当前仓库")

    def test_head_change_during_gate_is_rejected(self):
        self.set_json("pr2.json", {"url": PR, "number": 13, "state": "OPEN", "isDraft": False,
                                   "baseRefName": "main", "headRefOid": OTHER_SHA})
        self.assert_rejected(self.run_gate(), "head 发生变化")

    def test_head_change_during_ao_queries_is_rejected(self):
        self.set_json("pr3.json", {"url": PR, "number": 13, "state": "OPEN", "isDraft": False,
                                   "baseRefName": "main", "headRefOid": OTHER_SHA})
        self.assert_rejected(self.run_gate(), "head 或状态发生变化")

    def test_non_candidate_state_during_ao_queries_is_rejected(self):
        for label, changes in (("draft", {"isDraft": True}), ("closed", {"state": "CLOSED"})):
            with self.subTest(label=label):
                (self.fixtures / "view-count").unlink(missing_ok=True)
                payload = {"url": PR, "number": 13, "state": "OPEN", "isDraft": False,
                           "baseRefName": "main", "headRefOid": HEAD}
                payload.update(changes)
                self.set_json("pr3.json", payload)
                self.assert_rejected(self.run_gate(), "head 或状态发生变化")


if __name__ == "__main__":
    unittest.main(verbosity=2)

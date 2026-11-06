#!/usr/bin/env python3
"""只读研发门禁：合并前核对 PR 身份、最新 head、CI、独立 review 与 QA 证据。

本脚本只调用 gh/ao 的公开 CLI 读取事实，不执行证据中的命令、不写入任何文件，
也不输出凭据或完整日志。任何查询失败、字段缺失或无法判断的范围都按拒绝处理。
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
MAX_EVIDENCE_BYTES = 1024 * 1024
MAX_EVIDENCE_STEPS = 50
MAX_TEXT = 300
COMMAND_TIMEOUT = 60

# 已知不需要 Android 独立 QA 的范围：纯文档、本门禁自身和 CI 配置。
EXEMPT_PREFIXES = ("docs/", ".github/")
EXEMPT_FILES = {
    "AGENTS.md", "README.md", "LICENSE", "THIRD_PARTY_NOTICES", ".gitignore",
    "scripts/check-development-gates.py", "scripts/test-development-gates.py",
}
# 会改动 Android 构建、安装、运行或发布产物的路径，必须提供独立 QA 证据。
QA_REQUIRED_PREFIXES = ("app/", "runtime/", "gradle/")
QA_REQUIRED_FILES = {
    "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat",
    "scripts/build-debug.sh", "scripts/install-and-launch.sh", "scripts/verify-workspace.sh",
    "scripts/prepare-build-tools.sh", "scripts/env.sh", "scripts/ao-worktree-env.sh",
    "scripts/workspace-verification.init.gradle", "scripts/device-ui.py",
    "scripts/prepare-app-release.py", "scripts/collect-diagnostics.sh",
    "scripts/fetch-test-webview.py", "scripts/sign-release.sh",
}
CHECK_BUCKET_PASS = "pass"


class GateError(Exception):
    """查询或输入不满足门禁，保守拒绝。"""


def command(args: List[str]) -> Tuple[int, str]:
    """执行只读 CLI；不经过 shell，异常时只报告退出状态。"""
    try:
        completed = subprocess.run(args, capture_output=True, text=True, timeout=COMMAND_TIMEOUT)
    except (OSError, subprocess.SubprocessError):
        raise GateError("无法执行只读查询命令") from None
    return completed.returncode, completed.stdout


def command_json(args: List[str], allow_exit: Tuple[int, ...] = (0,)) -> object:
    code, output = command(args)
    if code not in allow_exit:
        raise GateError("只读查询失败（退出码 %d）" % code)
    try:
        return json.loads(output)
    except (TypeError, ValueError):
        raise GateError("只读查询未返回可解析的 JSON") from None


def normalize_pr_url(value: object) -> str:
    if not isinstance(value, str):
        return ""
    return value.strip().rstrip("/")


def repo_pull_prefix(repo: Dict[str, object]) -> str:
    name = repo.get("nameWithOwner")
    url = normalize_pr_url(repo.get("url"))
    if isinstance(name, str) and "/" in name:
        return "https://github.com/" + name
    if url.startswith("https://github.com/"):
        return url
    raise GateError("无法确认当前仓库身份")


def classify_paths(paths: List[str]) -> Tuple[List[str], List[str]]:
    """返回（必须 QA 的路径，范围未知而需要 QA 的路径）。"""
    required: List[str] = []
    unknown: List[str] = []
    for path in paths:
        if path in EXEMPT_FILES or path.startswith(EXEMPT_PREFIXES) or path.endswith(".md"):
            continue
        if path in QA_REQUIRED_FILES or path.startswith(QA_REQUIRED_PREFIXES):
            required.append(path)
        else:
            unknown.append(path)
    return required, unknown


def require_text(value: object, field: str, pattern: Optional[re.Pattern] = None) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > MAX_TEXT:
        raise GateError("QA 证据字段 %s 缺失或格式不正确" % field)
    text = value.strip()
    if pattern is not None and not pattern.fullmatch(text):
        raise GateError("QA 证据字段 %s 格式不正确" % field)
    return text


def require_object(value: object, field: str) -> Dict[str, object]:
    if not isinstance(value, dict):
        raise GateError("QA 证据字段 %s 缺失或格式不正确" % field)
    return value


def load_evidence(path: str) -> Dict[str, object]:
    evidence_path = Path(path)
    # 不接受符号链接，避免门禁读取未声明的文件。
    if evidence_path.is_symlink() or not evidence_path.is_file():
        raise GateError("QA 证据文件不存在或不是普通文件")
    try:
        if evidence_path.stat().st_size > MAX_EVIDENCE_BYTES:
            raise GateError("QA 证据文件超过大小上限")
        data = json.loads(evidence_path.read_text(encoding="utf-8"))
    except (OSError, ValueError, UnicodeError):
        raise GateError("QA 证据不是可解析的 JSON 文件") from None
    if not isinstance(data, dict):
        raise GateError("QA 证据必须是 JSON 对象")
    return data


def validate_evidence(data: Dict[str, object], pr_url: str, head: str, worker: str,
                      sessions: List[str]) -> List[str]:
    """校验 QA 证据结构并返回无法验证的项；不执行证据里的任何命令。"""
    if data.get("schema") != 1:
        raise GateError("QA 证据 schema 不受支持")
    if normalize_pr_url(data.get("pr")) != pr_url:
        raise GateError("QA 证据绑定的 PR 与当前 PR 不一致")
    if not isinstance(data.get("sha"), str) or data["sha"] != head:
        raise GateError("QA 证据绑定的 SHA 不是当前 PR head")
    qa_session = require_text(data.get("qaSession"), "qaSession")
    if qa_session == worker:
        raise GateError("QA 证据不能由实现 worker 自证，必须使用独立 QA session")
    if qa_session not in sessions:
        raise GateError("QA 证据中的 session 不是当前 AO 已知 session")

    device = require_object(data.get("device"), "device")
    for field in ("avd", "serial", "abi"):
        require_text(device.get(field), "device." + field)
    api = device.get("api")
    if not isinstance(api, int) or isinstance(api, bool) or api <= 0:
        raise GateError("QA 证据字段 device.api 缺失或格式不正确")

    apk = require_object(data.get("apk"), "apk")
    require_text(apk.get("package"), "apk.package")
    require_text(apk.get("sha256"), "apk.sha256", SHA256_PATTERN)

    steps = data.get("steps")
    if not isinstance(steps, list) or not steps or len(steps) > MAX_EVIDENCE_STEPS:
        raise GateError("QA 证据 steps 缺失或数量不合理")
    android_passed = False
    unverified: List[str] = []
    for index, raw in enumerate(steps):
        step = require_object(raw, "steps[%d]" % index)
        name = require_text(step.get("name"), "steps[%d].name" % index)
        require_text(step.get("command"), "steps[%d].command" % index)
        result = step.get("result")
        source = step.get("source")
        if result not in ("passed", "failed", "skipped"):
            raise GateError("QA 证据 steps[%d].result 不受支持" % index)
        if source not in ("android", "host"):
            raise GateError("QA 证据 steps[%d].source 不受支持" % index)
        if result == "failed":
            raise GateError("QA 存在失败步骤，不能作为合并依据")
        if result == "skipped":
            reason = require_text(step.get("reason"), "steps[%d].reason" % index)
            unverified.append("step:%s skipped: %s" % (name, reason))
        elif source == "android":
            android_passed = True
    if not android_passed:
        raise GateError("QA 证据缺少在 Android 内实际执行并通过的步骤")

    model = data.get("model")
    if model is None:
        unverified.append("真实模型/账号链路未提供证据")
    else:
        model_data = require_object(model, "model")
        if model_data.get("configured") is not True:
            unverified.append("真实模型/账号链路未配置或未验证")
    return unverified


def a0_get(data: object, *keys: str):
    current = data
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return None
        current = current[key]
    return current


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pr", required=True, help="PR 编号或当前仓库的 PR URL")
    parser.add_argument("--session", required=True, help="实现该 PR 的 AO worker session id")
    parser.add_argument("--qa-evidence", help="独立 QA 证据 JSON 路径；仅校验结构，不执行其中命令")
    args = parser.parse_args()

    try:
        repo = command_json(["gh", "repo", "view", "--json", "nameWithOwner,url"])
        if not isinstance(repo, dict):
            raise GateError("无法确认当前仓库身份")
        pull_prefix = repo_pull_prefix(repo)

        first = command_json(["gh", "pr", "view", args.pr, "--json",
                              "url,number,state,isDraft,baseRefName,headRefOid"])
        if not isinstance(first, dict):
            raise GateError("无法读取 PR 身份")
        pr_url = normalize_pr_url(first.get("url"))
        number = first.get("number")
        head = first.get("headRefOid")
        if not pr_url.startswith(pull_prefix + "/pull/"):
            raise GateError("PR 不属于当前仓库")
        if not isinstance(number, int) or isinstance(number, bool):
            raise GateError("PR 编号缺失")
        if normalize_pr_url(pr_url) != pull_prefix + "/pull/%d" % number:
            raise GateError("PR URL 与编号不一致")
        if first.get("state") != "OPEN":
            raise GateError("PR 不是打开状态")
        if first.get("isDraft") is True:
            raise GateError("草稿 PR 不能作为合并候选")
        if not isinstance(head, str) or not SHA_PATTERN.fullmatch(head):
            raise GateError("PR head SHA 格式不正确")
        if first.get("baseRefName") != "main":
            raise GateError("PR 目标分支不是 main")

        files_code, files_output = command(["gh", "pr", "diff", args.pr, "--name-only"])
        if files_code != 0:
            raise GateError("无法读取 PR 改动路径")
        changed = [line.strip() for line in files_output.splitlines() if line.strip()]
        if not changed:
            raise GateError("PR 没有可识别的改动路径")
        required, unknown = classify_paths(changed)

        checks_code, checks_output = command(["gh", "pr", "checks", args.pr, "--json",
                                              "name,state,bucket,link"])
        # gh pr checks 在失败/排队时也返回非零，但 stdout 仍带完整结果；只拒绝无法解析。
        if checks_code not in (0, 1, 8):
            raise GateError("无法读取 PR check 结果")
        try:
            checks = json.loads(checks_output)
        except (TypeError, ValueError):
            raise GateError("PR check 结果不可解析")
        if not isinstance(checks, list) or not checks:
            raise GateError("PR 没有任何 check 结果，空 checks 不能视为通过")
        for check in checks:
            if not isinstance(check, dict):
                raise GateError("PR check 条目格式不正确")
            if check.get("bucket") != CHECK_BUCKET_PASS:
                raise GateError("PR 存在未通过、排队、失败或跳过的 check")

        second = command_json(["gh", "pr", "view", args.pr, "--json", "headRefOid"])
        if not isinstance(second, dict) or second.get("headRefOid") != head:
            raise GateError("门禁执行期间 PR head 发生变化，必须重新运行")

        worker_data = command_json(["ao", "session", "ls", "--json"])
        sessions_raw = worker_data.get("data") if isinstance(worker_data, dict) else None
        if not isinstance(sessions_raw, list):
            raise GateError("无法读取 AO session 列表")
        session_ids: List[str] = []
        worker = None
        for entry in sessions_raw:
            if not isinstance(entry, dict):
                continue
            session_id = entry.get("id")
            if isinstance(session_id, str):
                session_ids.append(session_id)
            if session_id == args.session:
                worker = entry
        if worker is None:
            raise GateError("指定的 AO worker session 不存在")
        worker_harness = worker.get("harness")
        project_id = worker.get("projectId")
        if not isinstance(worker_harness, str) or not worker_harness:
            raise GateError("无法确认实现 worker 的 harness")
        if not isinstance(project_id, str) or not project_id:
            raise GateError("无法确认实现 worker 的项目")

        project_data = command_json(["ao", "project", "get", project_id, "--json"])
        project = a0_get(project_data, "project") or project_data
        config = a0_get(project, "config") if isinstance(project, dict) else None
        if not isinstance(config, dict):
            raise GateError("无法读取 AO 项目配置")
        default_branch = config.get("defaultBranch", "main")
        if default_branch != "main":
            raise GateError("项目默认分支与门禁约定不一致，需要人工确认")
        reviewers = config.get("reviewers")
        if not isinstance(reviewers, list) or not reviewers:
            raise GateError("项目未配置独立 reviewer")
        independent = set()
        for reviewer in reviewers:
            harness = reviewer.get("harness") if isinstance(reviewer, dict) else None
            if isinstance(harness, str) and harness:
                independent.add(harness)
        if not independent:
            raise GateError("项目 reviewer 配置无法识别")

        review_data = command_json(["ao", "review", "ls", args.session, "--json"])
        reviews = review_data.get("reviews") if isinstance(review_data, dict) else None
        if not isinstance(reviews, list):
            raise GateError("无法读取 AO 原生 review 记录")
        matching: List[Dict[str, object]] = []
        for review in reviews:
            if not isinstance(review, dict):
                continue
            if normalize_pr_url(review.get("prUrl")) != pr_url or review.get("prNumber") != number:
                continue
            run = review.get("latestRun")
            if isinstance(run, dict) and run.get("targetSha") == head:
                matching.append(run)
        if not matching:
            raise GateError("没有绑定当前 head 的 AO 原生 review")
        matching.sort(key=lambda run: str(run.get("createdAt", "")), reverse=True)
        latest = matching[0]
        if latest.get("status") != "complete":
            raise GateError("绑定当前 head 的 AO 原生 review 未完成")
        if latest.get("verdict") != "approved":
            raise GateError("当前 head 未被 AO 原生 reviewer 批准")
        harness = latest.get("harness")
        if harness not in independent:
            raise GateError("AO 原生 review 不是项目配置的独立 reviewer")
        if harness == worker_harness:
            raise GateError("实现 worker 的自评不能替代独立 review")
        if not isinstance(latest.get("githubReviewId"), str) or not latest["githubReviewId"]:
            raise GateError("AO 原生 review 未绑定已提交的 provider review")

        unverified: List[str] = []
        if required or unknown:
            if not args.qa_evidence:
                raise GateError("改动涉及 Android/构建/未知范围，缺少独立 QA 证据")
            evidence = load_evidence(args.qa_evidence)
            unverified = validate_evidence(evidence, pr_url, head, args.session, session_ids)
            qa_status = "passed_with_unverified" if unverified else "passed"
        else:
            qa_status = "not_required"

        print("GATE RESULT: PASS")
        print("pr=%s head=%s base=%s checks=%d" % (pr_url, head, default_branch, len(checks)))
        print("review=%s approved target=%s" % (harness, latest.get("targetSha")))
        print("qa=%s changed_files=%d" % (qa_status, len(changed)))
        for item in unverified:
            print("unverified=%s" % item)
        return 0
    except GateError as error:
        print("GATE RESULT: REJECT", file=sys.stderr)
        print("reason=%s" % error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""运行既有合并前门禁并保存 head 回执；不合并、不构建、不上传。"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pr", required=True)
    parser.add_argument("--session", required=True)
    parser.add_argument("--qa-evidence", type=Path)
    parser.add_argument("--directory", required=True, type=Path)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("merge_release", ROOT / "scripts/merge-release.py")
    release = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(release)
    runner = release.Runner()
    query = ["gh", "pr", "view", args.pr, "--json", "number,url,headRefOid,state,isDraft"]
    before = json.loads(runner.run(query, cwd=ROOT))
    head = before["headRefOid"]
    release.require(release.SHA.fullmatch(head), "PR head 无效")
    receipt_directory = args.directory.resolve()
    receipt_path = receipt_directory / (head + ".json")
    if receipt_path.exists() or receipt_path.is_symlink():
        # 同一 head 后续门禁失败时不能继续使用早先的通过回执。
        release.require(receipt_path.parent.resolve() == receipt_directory and receipt_path.is_file()
                        and not receipt_path.is_symlink(), "旧回执路径无效")
        receipt_path.unlink()
    command = [sys.executable, str(ROOT / "scripts/check-development-gates.py"), "--pr", args.pr, "--session", args.session]
    if args.qa_evidence:
        command += ["--qa-evidence", str(args.qa_evidence)]
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=900)
    release.require(result.returncode == 0, "合并前门禁未通过；未写发布回执")
    after = json.loads(runner.run(query, cwd=ROOT))
    release.require(before == after and after["state"] == "OPEN" and not after["isDraft"], "门禁期间 PR 身份改变")
    repo = json.loads(runner.run(["gh", "repo", "view", "--json", "nameWithOwner"], cwd=ROOT))["nameWithOwner"]
    release.require(after["url"] == "https://github.com/%s/pull/%d" % (repo, after["number"]), "PR 仓库不匹配")
    head = after["headRefOid"]
    release.require(release.SHA.fullmatch(head), "PR head 无效")
    # 只保留门禁实际输出的公开摘要；不读取或复制 QA 原始日志、账号或密钥。
    validation = "Host：合并前 CI/独立审查/适用 QA 门禁通过。\n"
    if args.qa_evidence:
        validation += "Android：提供了绑定本 PR head 的独立 QA 证据，范围以该 PR/交接为准；发布 APK 未另行设备复验。\n"
    else:
        validation += "Android：本 PR 范围门禁判定无需额外设备 QA；发布 APK 未设备复验。\n"
    validation += "真实模型/账号、真机：此发布器未执行或验证。"
    document = {"schema": 1, "repo": repo, "pr": after["number"], "head_sha": head,
                "development_gates": "passed", "validation": validation}
    release.atomic_json(args.directory.resolve() / (head + ".json"), document)
    print("门禁通过并保存 head 发布回执：" + head)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        # 外部输出不原样回显；失败不产生可用回执。
        print("回执记录失败：" + type(error).__name__, file=sys.stderr)
        sys.exit(1)

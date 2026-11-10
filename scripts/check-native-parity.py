#!/usr/bin/env python3
"""校验 docs/NATIVE-PARITY.md 的功能矩阵结构。

只做文档一致性检查，不连接设备、不启动 DSH、不读取用户数据：
- 14 个功能域齐全；
- 每行有唯一 ID、状态、优先级、至少一个验收用例；
- 验收用例引用在第 4 节有定义；
- 反引号中的仓库文件路径存在（可用 --no-path-check 关闭）。

用法：
    python3 scripts/check-native-parity.py
    python3 scripts/check-native-parity.py --self-test
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

CATEGORIES = {
    "3.1": "SESS",
    "3.2": "CHAT",
    "3.3": "INPUT",
    "3.4": "ATT",
    "3.5": "MODEL",
    "3.6": "APPROVAL",
    "3.7": "TOOL",
    "3.8": "FILE",
    "3.9": "SEARCH",
    "3.10": "SET",
    "3.11": "NOTIFY",
    "3.12": "SKILL",
    "3.13": "REMOTE",
    "3.14": "SAFE",
}

STATUSES = ("原生完整", "原生部分", "仅官方", "原生替代", "进行中", "不适用")
PRIORITIES = ("P0", "P1", "P2")
ID_RE = re.compile(r"^(SESS|CHAT|INPUT|ATT|MODEL|APPROVAL|TOOL|FILE|SEARCH|SET|NOTIFY|SKILL|REMOTE|SAFE)-\d{2}$")
CASE_RE = re.compile(r"A-[A-Z]+-\d{2}")
PATH_RE = re.compile(r"`([^`]+)`")
PATH_SUFFIXES = (".kt", ".kts", ".md", ".py", ".json", ".xml", ".yml", ".yaml", ".sh", ".mjs", ".cjs")


def parse_matrix(text: str) -> tuple[list[dict[str, str]], list[str]]:
    """返回矩阵行和错误列表。只解析第 3 节的表格。"""
    errors: list[str] = []
    rows: list[dict[str, str]] = []
    section = ""
    seen_ids: set[str] = set()
    category_counts: dict[str, int] = {code: 0 for code in CATEGORIES.values()}

    for line in text.splitlines():
        heading = re.match(r"^### (3\.\d+)", line)
        if heading:
            section = heading.group(1)
            continue
        if line.startswith("## 4."):
            section = ""
            continue
        if not section or not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) != 5:
            errors.append(f"{section}: 表格列数不是 5：{line[:80]}")
            continue
        if cells[0] in ("ID", "---") or set(cells[0]) <= {"-", ":"}:
            continue
        row_id = cells[0]
        if not ID_RE.match(row_id):
            errors.append(f"{section}: 非法行 ID：{row_id}")
            continue
        if row_id in seen_ids:
            errors.append(f"{section}: 重复行 ID：{row_id}")
            continue
        seen_ids.add(row_id)
        expected_prefix = CATEGORIES[section]
        if not row_id.startswith(expected_prefix + "-"):
            errors.append(f"{section}: 行 {row_id} 不属于 {expected_prefix} 域")
        status_cell = cells[3]
        if not any(status in status_cell for status in STATUSES):
            errors.append(f"{row_id}: 缺少合法状态（{'/'.join(STATUSES)}）")
        gap_cell = cells[4]
        if not any(priority in gap_cell for priority in PRIORITIES) and "—" not in gap_cell:
            errors.append(f"{row_id}: 缺少优先级（P0/P1/P2/—）")
        if not CASE_RE.search(gap_cell):
            errors.append(f"{row_id}: 缺少验收用例引用（A-*）")
        category_counts[expected_prefix] += 1
        rows.append({"id": row_id, "section": section, "cells": cells})

    for section, code in CATEGORIES.items():
        if category_counts[code] < 5:
            errors.append(f"{section}: 功能域 {code} 少于 5 行（实际 {category_counts[code]}）")
    return rows, errors


def parse_case_coverage(text: str) -> tuple[dict[str, set[str]], dict[str, str], list[str]]:
    """解析每个验收用例标题后的 `- 覆盖：...` 与可选 `- 跨域：...`。

    覆盖声明是“该用例真的验证了哪些矩阵行”的唯一依据；只写用例编号
    不再足以通过门禁，避免行引用了不覆盖该行的共享用例。跨功能域复用
    必须用 `- 跨域：` 说明理由，否则视为可疑复用。
    """
    errors: list[str] = []
    coverage: dict[str, set[str]] = {}
    cross_reasons: dict[str, str] = {}
    seen: set[str] = set()
    current: str | None = None
    for line in text.splitlines():
        heading = re.match(r"^\*\*(A-[A-Z]+-\d{2})", line)
        if heading:
            current = heading.group(1)
            coverage.setdefault(current, set())
            continue
        if current and line.startswith("- 覆盖："):
            raw = line[len("- 覆盖："):].strip()
            seen.add(current)
            if raw and raw != "—":
                for token in re.split(r"[、,，\s]+", raw):
                    if token:
                        coverage[current].add(token)
            continue
        if current and line.startswith("- 跨域："):
            reason = line[len("- 跨域："):].strip()
            if reason:
                cross_reasons[current] = reason
            current = None
    for case_id in sorted(set(coverage) - seen):
        errors.append(f"验收用例缺少覆盖声明：{case_id}")
    return coverage, cross_reasons, errors


def check_paths(text: str, root: Path) -> list[str]:
    errors: list[str] = []
    for token in PATH_RE.findall(text):
        if "|" in token or token.startswith(("http://", "https://", "ao://")):
            continue
        # 只检查仓库内相对路径；裸文件名可能是并行 worktree 的未提交文件，
        # 命令字符串（含空格）也不是路径。
        if " " in token or "/" not in token:
            continue
        if not token.endswith(PATH_SUFFIXES):
            continue
        candidate = root / token
        if candidate.exists():
            continue
        errors.append(f"引用的仓库路径不存在：{token}")
    return errors


def validate(text: str, root: Path, check_path_refs: bool = True) -> list[str]:
    rows, errors = parse_matrix(text)
    coverage, cross_reasons, case_errors = parse_case_coverage(text)
    errors.extend(case_errors)
    row_ids = {row["id"] for row in rows}

    for case_id, covered in sorted(coverage.items()):
        domains = sorted({row_id.split("-")[0] for row_id in covered})
        for row_id in sorted(covered):
            if row_id not in row_ids:
                errors.append(f"{case_id}: 覆盖声明含不存在的矩阵行 {row_id}")
        if len(domains) > 1 and not cross_reasons.get(case_id, "").strip():
            errors.append(
                f"{case_id}: 覆盖多个功能域（{'、'.join(domains)}），"
                "需拆分或在 `- 跨域：` 说明理由"
            )

    for row in rows:
        refs = sorted(set(CASE_RE.findall(row["cells"][4])))
        if not refs:
            errors.append(f"{row['id']}: 没有验收用例")
            continue
        covering = [case_id for case_id in refs if row["id"] in coverage.get(case_id, set())]
        if not covering:
            errors.append(
                f"{row['id']}: 引用的用例都不覆盖该行（{'、'.join(refs)}）"
            )
        for case_id in refs:
            if case_id not in coverage:
                errors.append(f"{row['id']}: 引用未定义或无覆盖声明的用例 {case_id}")

    if not rows:
        errors.append("没有解析到任何矩阵行")
    if check_path_refs:
        errors.extend(check_paths(text, root))
    return errors


def self_test() -> int:
    sample = """# 测试矩阵

### 3.1 会话（SESS）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| SESS-01 | 列表 | 有 | 原生完整 | — · `docs/NATIVE-PARITY.md` · A-SESS-01 |
| SESS-02 | 选择 | 有 | 原生部分 | P0 · A-SESS-01 |
| SESS-03 | 新建 | 有 | 仅官方 | P1 · A-SESS-01 |
| SESS-04 | 重命名 | 有 | 仅官方 | P1 · A-SESS-01 |
| SESS-05 | 分叉 | 有 | 仅官方 | P1 · A-SESS-01 |

### 3.2 对话（CHAT）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| CHAT-01 | 文本 | 有 | 原生完整 | — · A-CHAT-01 |
| CHAT-02 | Markdown | 有 | 仅官方 | P1 · A-CHAT-01 |
| CHAT-03 | 代码 | 有 | 仅官方 | P1 · A-CHAT-01 |
| CHAT-04 | 工具 | 有 | 仅官方 | P1 · A-CHAT-01 |
| CHAT-05 | 图片 | 有 | 仅官方 | P1 · A-CHAT-01 |

### 3.3 输入（INPUT）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| INPUT-01 | 文本 | 有 | 仅官方 | P0 · A-INPUT-01 |
| INPUT-02 | 发送 | 有 | 仅官方 | P0 · A-INPUT-01 |
| INPUT-03 | 停止 | 有 | 仅官方 | P0 · A-INPUT-01 |
| INPUT-04 | 命令 | 有 | 仅官方 | P1 · A-INPUT-01 |
| INPUT-05 | 引用 | 有 | 仅官方 | P1 · A-INPUT-01 |

### 3.4 附件（ATT）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| ATT-01 | 上传 | 有 | 仅官方 | P1 · A-ATT-01 |
| ATT-02 | 图片 | 有 | 仅官方 | P1 · A-ATT-01 |
| ATT-03 | 预览 | 有 | 仅官方 | P1 · A-ATT-01 |
| ATT-04 | 限制 | 有 | 仅官方 | P1 · A-ATT-01 |
| ATT-05 | 授权 | 有 | 仅官方 | P1 · A-ATT-01 |

### 3.5 模型/推理（MODEL）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| MODEL-01 | 目录 | 有 | 仅官方 | P1 · A-MODEL-01 |
| MODEL-02 | 选择 | 有 | 仅官方 | P1 · A-MODEL-01 |
| MODEL-03 | 推理 | 有 | 仅官方 | P1 · A-MODEL-01 |
| MODEL-04 | 密钥 | 有 | 仅官方 | P1 · A-MODEL-01 |
| MODEL-05 | 默认 | 有 | 仅官方 | P1 · A-MODEL-01 |

### 3.6 审批/权限（APPROVAL）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| APPROVAL-01 | 审批 | 有 | 进行中 | P0 · A-APPROVAL-01 |
| APPROVAL-02 | 回答 | 有 | 仅官方 | P0 · A-APPROVAL-01 |
| APPROVAL-03 | 预设 | 有 | 仅官方 | P1 · A-APPROVAL-01 |
| APPROVAL-04 | 沙箱 | 有 | 原生部分 | — · A-APPROVAL-01 |
| APPROVAL-05 | 提问 | 有 | 进行中 | P0 · A-APPROVAL-01 |

### 3.7 工具调用（TOOL）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| TOOL-01 | 卡片 | 有 | 仅官方 | P1 · A-TOOL-01 |
| TOOL-02 | 输出 | 有 | 仅官方 | P1 · A-TOOL-01 |
| TOOL-03 | 终端 | 有 | 仅官方 | P2 · A-TOOL-01 |
| TOOL-04 | 浏览器 | 有 | 原生替代 | P2 · A-TOOL-01 |
| TOOL-05 | 文件 | 有 | 原生部分 | P2 · A-TOOL-01 |

### 3.8 工作区/文件（FILE）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| FILE-01 | 工作区 | 有 | 仅官方 | P1 · A-FILE-01 |
| FILE-02 | 树 | 有 | 原生完整 | — · A-FILE-01 |
| FILE-03 | 预览 | 有 | 原生部分 | P2 · A-FILE-01 |
| FILE-04 | 编辑 | 有 | 原生部分 | P2 · A-FILE-01 |
| FILE-05 | 导入 | 有 | 原生完整 | — · A-FILE-01 |

### 3.9 搜索（SEARCH）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| SEARCH-01 | 会话 | 有 | 仅官方 | P1 · A-SEARCH-01 |
| SEARCH-02 | 内容 | 有 | 仅官方 | P1 · A-SEARCH-01 |
| SEARCH-03 | 文件 | 有 | 原生完整 | — · A-SEARCH-01 |
| SEARCH-04 | 页内 | 有 | 仅官方 | P2 · A-SEARCH-01 |
| SEARCH-05 | 命令 | 有 | 仅官方 | P2 · A-SEARCH-01 |

### 3.10 设置（SET）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| SET-01 | 入口 | 有 | 原生部分 | P1 · A-SET-01 |
| SET-02 | 模型 | 有 | 仅官方 | P1 · A-SET-01 |
| SET-03 | 权限 | 有 | 仅官方 | P1 · A-SET-01 |
| SET-04 | 插件 | 有 | 仅官方 | P2 · A-SET-01 |
| SET-05 | 快捷键 | 有 | 仅官方 | P2 · A-SET-01 |

### 3.11 通知（NOTIFY）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| NOTIFY-01 | 完成 | 有 | 原生替代 | — · A-NOTIFY-01 |
| NOTIFY-02 | 错误 | 有 | 原生替代 | — · A-NOTIFY-01 |
| NOTIFY-03 | 审批 | 有 | 原生替代 | — · A-NOTIFY-01 |
| NOTIFY-04 | 点击 | 有 | 原生完整 | — · A-NOTIFY-01 |
| NOTIFY-05 | 权限 | 有 | 原生完整 | — · A-NOTIFY-01 |

### 3.12 技能/插件（SKILL）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| SKILL-01 | 技能 | 有 | 仅官方 | P2 · A-SKILL-01 |
| SKILL-02 | 命令 | 有 | 仅官方 | P2 · A-SKILL-01 |
| SKILL-03 | 插件 | 有 | 仅官方 | P2 · A-SKILL-01 |
| SKILL-04 | 市场 | 有 | 仅官方 | P2 · A-SKILL-01 |
| SKILL-05 | 豁免 | 有 | 仅官方 | P2 · A-SKILL-01 |

### 3.13 远程（REMOTE）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| REMOTE-01 | 配置 | 有 | 原生完整 | — · A-REMOTE-01 |
| REMOTE-02 | 隧道 | 有 | 原生完整 | — · A-REMOTE-01 |
| REMOTE-03 | 回退 | 有 | 原生完整 | — · A-REMOTE-01 |
| REMOTE-04 | 隔离 | 有 | 原生完整 | — · A-REMOTE-01 |
| REMOTE-05 | 凭据 | 有 | 原生完整 | — · A-REMOTE-01 |

### 3.14 安全模式（SAFE）

| ID | 功能 | 官方 | 原生 | 缺口 |
|---|---|---|---|---|
| SAFE-01 | 分类 | 有 | 原生完整 | — · A-SAFE-01 |
| SAFE-02 | 救援 | 有 | 原生完整 | — · A-SAFE-01 |
| SAFE-03 | 试用 | 有 | 原生完整 | — · A-SAFE-01 |
| SAFE-04 | 重启 | 有 | 原生完整 | — · A-SAFE-01 |
| SAFE-05 | 隔离 | 有 | 原生完整 | — · A-SAFE-01 |

## 4. 验收用例

**A-SESS-01（JVM）**
- 覆盖：SESS-01、SESS-02、SESS-03、SESS-04、SESS-05
**A-CHAT-01（JVM）**
- 覆盖：CHAT-01、CHAT-02、CHAT-03、CHAT-04、CHAT-05
**A-INPUT-01（JVM）**
- 覆盖：INPUT-01、INPUT-02、INPUT-03、INPUT-04、INPUT-05
**A-ATT-01（JVM）**
- 覆盖：ATT-01、ATT-02、ATT-03、ATT-04、ATT-05
**A-MODEL-01（JVM）**
- 覆盖：MODEL-01、MODEL-02、MODEL-03、MODEL-04、MODEL-05
**A-APPROVAL-01（JVM）**
- 覆盖：APPROVAL-01、APPROVAL-02、APPROVAL-03、APPROVAL-04、APPROVAL-05
**A-TOOL-01（JVM）**
- 覆盖：TOOL-01、TOOL-02、TOOL-03、TOOL-04、TOOL-05
**A-FILE-01（JVM）**
- 覆盖：FILE-01、FILE-02、FILE-03、FILE-04、FILE-05
**A-SEARCH-01（JVM）**
- 覆盖：SEARCH-01、SEARCH-02、SEARCH-03、SEARCH-04、SEARCH-05
**A-SET-01（JVM）**
- 覆盖：SET-01、SET-02、SET-03、SET-04、SET-05
**A-NOTIFY-01（JVM）**
- 覆盖：NOTIFY-01、NOTIFY-02、NOTIFY-03、NOTIFY-04、NOTIFY-05
**A-SKILL-01（JVM）**
- 覆盖：SKILL-01、SKILL-02、SKILL-03、SKILL-04、SKILL-05
**A-REMOTE-01（JVM）**
- 覆盖：REMOTE-01、REMOTE-02、REMOTE-03、REMOTE-04、REMOTE-05
**A-SAFE-01（JVM）**
- 覆盖：SAFE-01、SAFE-02、SAFE-03、SAFE-04、SAFE-05
"""
    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)
        (root / "docs").mkdir()
        (root / "docs" / "NATIVE-PARITY.md").write_text(sample, encoding="utf-8")
        errors = validate(sample, root)
        if errors:
            print("SELF-TEST FAILED（有效样例被误判）")
            for error in errors:
                print(" -", error)
            return 1
        broken = sample.replace("| SESS-02 |", "| SESS-01 |", 1)
        broken = broken.replace("| CHAT-05 |", "| CHAT-05 | 多 |", 1)
        broken = broken.replace("A-INPUT-01", "A-INPUT-99", 1)
        broken = broken.replace(
            "| CHAT-02 | Markdown | 有 | 仅官方 | P1 · A-CHAT-01 |",
            "| CHAT-02 | Markdown | 有 | 仅官方 | P1 · A-SESS-01 |",
            1,
        )
        broken = broken + "\n**A-CROSS-01（JVM）**\n- 覆盖：SESS-01、CHAT-01\n"
        broken_errors = validate(broken, root)
        if not broken_errors:
            print("SELF-TEST FAILED（坏样例未被发现）")
            return 1
        if not any("CHAT-02" in error and "覆盖" in error for error in broken_errors):
            print("SELF-TEST FAILED（覆盖错配未被发现）")
            return 1
        if not any("A-CROSS-01" in error and "多个功能域" in error for error in broken_errors):
            print("SELF-TEST FAILED（跨域可疑复用未被发现）")
            return 1
        print(f"SELF-TEST PASS（有效样例通过；坏样例发现 {len(broken_errors)} 个问题）")
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--doc", default="docs/NATIVE-PARITY.md", help="矩阵文档路径")
    parser.add_argument("--root", default=".", help="仓库根目录")
    parser.add_argument("--no-path-check", action="store_true", help="跳过反引号路径存在性检查")
    parser.add_argument("--self-test", action="store_true", help="用内置样例自测检查器")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    root = Path(args.root).resolve()
    doc = (root / args.doc).resolve()
    if not doc.is_file():
        print(f"FAIL: 找不到矩阵文档 {args.doc}")
        return 1
    text = doc.read_text(encoding="utf-8")
    errors = validate(text, root, check_path_refs=not args.no_path_check)
    if errors:
        print(f"FAIL: {args.doc} 发现 {len(errors)} 个问题")
        for error in errors:
            print(" -", error)
        return 1
    rows, _ = parse_matrix(text)
    print(f"PASS: {args.doc} 共 {len(rows)} 行、14 个功能域，ID/状态/优先级/验收引用/覆盖/跨域/路径检查通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""检查交接记录：普通 PR 只校验自己的新记录，集成/发布才强制共享状态最新。

背景：docs/AGENT-STATE.md 的“当前记录”和 docs/handoffs/CURRENT.json 印章原先由每个 PR
改写，main 每合并一个 PR，其余 PR 立即冲突并反复 rebase。现在普通 PR 只新增
docs/handoffs/<日期>-<主题>.md，共享状态由集成阶段单一维护方刷新。

三种模式：
- 默认/--pr：普通 PR 模式。只校验本次新增/修改的交接记录自身完整；
  AGENT-STATE 链接、CURRENT.json 印章允许落后。可用 --changed-from/--changed-file
  传入本次变更清单，强制“公开文件有改动就必须同时新增/更新交接记录”。
- --integrate：集成维护模式。要求记录已被 AGENT-STATE 链接、且是最新记录，
  然后刷新 CURRENT.json 印章（源码指纹 + 记录哈希）。
- --release：发布严格模式（只读）。要求 CURRENT.json 为最新印章、记录哈希与
  公开源码指纹全部匹配、AGENT-STATE 指向最新记录；不写任何文件。

指纹不依赖 Git、mtime 或本机路径，复用 scripts/package-source.py 的公开清单。
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
STAMP = ROOT / "docs/handoffs/CURRENT.json"
STATE = ROOT / "docs/AGENT-STATE.md"
RECORD_DIR = ROOT / "docs/handoffs"
STATE_RELATIVE = "docs/AGENT-STATE.md"
STAMP_RELATIVE = "docs/handoffs/CURRENT.json"
REQUIRED = (
    "## 用户目标与范围", "## 当前状态", "## 本次改动", "## 验证记录",
    "## 未完成与暂停项", "## 下一步", "## 约束与风险", "## 产物与校验",
)
# 模板占位符必须全部替换，避免把未填写的模板当成真实交接。
PLACEHOLDERS = (
    "<YYYYMMDD-HHMM-topic>", "<非敏感标识即可>", "<UTC或明确时区>",
    "<进行中 / 已完成 / 部分完成 / 受阻 / 中断>", "<用户本次实际要求>",
    "<相对路径>", "<确实执行的命令>", "<通过 / 失败 / 跳过 / 未执行>",
    "<公开证据或脱敏结果>", "<下一任可直接执行的动作、文件与命令；无新工作则写等待用户明确任务>",
    "<前置条件、所需授权或验收方法>",
)
# 记录必须显式说明未验证范围，不能用“全部通过”掩盖未测项。
UNVERIFIED = ("未测试", "未验证", "未执行", "未跑", "跳过", "待验证", "无法验证")
RECORD_NAME = re.compile(r"^\d{8}-\d{4}-[a-z0-9][a-z0-9.-]*\.md$")
# 共享状态由集成方维护；普通 PR 允许它们落后。旧印章 schema=1 只用于迁移时读取。
LEGACY_SCHEMA = 1
SCHEMA = 2


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def distribution():
    """加载源码包白名单，保证校验与公开源码边界一致。"""
    spec = importlib.util.spec_from_file_location("public_distribution", ROOT / "scripts/package-source.py")
    if spec is None or spec.loader is None:
        raise ValueError("Cannot load the source distribution allowlist")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def latest_record() -> Path | None:
    """按 YYYYMMDD-HHMM 文件名排序取最新记录；排序即时间序，不依赖 mtime。"""
    if not RECORD_DIR.is_dir():
        return None
    records = sorted(
        path for path in RECORD_DIR.glob("*.md")
        if path.is_file() and not path.is_symlink() and RECORD_NAME.match(path.name)
    )
    return records[-1] if records else None


def record_sections(text: str) -> dict[str, str]:
    """按二级标题切分记录，返回每个标题下的正文（不含标题本身）。"""
    sections: dict[str, list[str]] = {}
    current = None
    for line in text.splitlines():
        if line.startswith("## "):
            current = line.strip()
            sections.setdefault(current, [])
        elif current is not None:
            sections[current].append(line)
    return {heading: "\n".join(lines).strip() for heading, lines in sections.items()}


def record_path(name: str, require_state_link: bool = False) -> Path:
    """校验记录路径、命名、章节完整性与可选的 AGENT-STATE 链接。"""
    relative = PurePosixPath(name)
    if (relative.is_absolute() or ".." in relative.parts or len(relative.parts) != 3
            or relative.parts[:2] != ("docs", "handoffs") or relative.suffix != ".md"
            or relative.name == "TEMPLATE.md" or not RECORD_NAME.match(relative.name)):
        raise ValueError("Record must be docs/handoffs/YYYYMMDD-HHMM-topic.md, not TEMPLATE.md")
    path = ROOT.joinpath(*relative.parts)
    if any(ROOT.joinpath(*relative.parts[:i]).is_symlink() for i in range(1, len(relative.parts) + 1)):
        raise ValueError("Handoff symlinks are not supported")
    if not path.is_file():
        raise ValueError(f"Handoff record not found: {name}")
    text = path.read_text()
    sections = record_sections(text)
    missing = [heading for heading in REQUIRED if heading not in sections]
    if missing:
        raise ValueError("Missing handoff sections: " + ", ".join(missing))
    empty = [heading for heading in REQUIRED if not sections[heading]]
    if empty:
        raise ValueError("Empty handoff sections: " + ", ".join(empty))
    remaining = [marker for marker in PLACEHOLDERS if marker in text]
    if remaining:
        raise ValueError("Handoff still contains template placeholders: " + ", ".join(remaining))
    if not any(marker in text for marker in UNVERIFIED):
        raise ValueError("Handoff must state unverified/untested scope explicitly "
                         "(含未测试/未验证/未执行/未跑/跳过/待验证之一)")
    if require_state_link and f"handoffs/{relative.name}" not in STATE.read_text():
        raise ValueError("docs/AGENT-STATE.md must link to the integrated handoff record")
    return path


def fingerprint(include_state: bool = True) -> tuple[str, int]:
    """计算公开源码指纹；集成模式另算不含共享状态的证据指纹。"""
    module = distribution()
    paths: set[Path] = set()
    for name in module.FILES:
        path = ROOT / name
        if not path.is_file():
            raise ValueError(f"Required public file missing: {name}")
        if module.public_file(path):
            paths.add(path)
    for tree in module.TREES:
        directory = ROOT / tree
        if not directory.is_dir():
            raise ValueError(f"Required public directory missing: {tree}")
        paths.update(p for p in directory.rglob("*") if module.public_file(p))
    selected = []
    for path in paths:
        relative = path.relative_to(ROOT).as_posix()
        # 印章不计算自身散列；原生 ELF 带有宿主诊断差异，需要重新构建。
        # 对应源码、构建脚本和补丁仍参与指纹计算。
        if path == STAMP or relative.startswith("app/src/main/jniLibs/"):
            continue
        # 证据指纹排除共享状态，集成时只改 AGENT-STATE 不强迫重写记录正文。
        if not include_state and relative == STATE_RELATIVE:
            continue
        selected.append((relative, path))
    hasher = hashlib.sha256()
    for relative, path in sorted(selected):
        hasher.update(relative.encode("utf-8") + b"\0" + digest(path).encode("ascii") + b"\n")
    return hasher.hexdigest(), len(selected)


def read_stamp(required_schema: int | None = None) -> dict:
    if not STAMP.exists():
        raise ValueError("No handoff stamp: run --integrate --record docs/handoffs/<latest>.md")
    try:
        stamp = json.loads(STAMP.read_text())
    except json.JSONDecodeError as error:
        raise ValueError(f"Invalid handoff stamp JSON: {error}") from None
    schema = stamp.get("schema")
    if schema not in (LEGACY_SCHEMA, SCHEMA):
        raise ValueError("Unsupported handoff stamp schema")
    if required_schema is not None and schema != required_schema:
        raise ValueError(f"Handoff stamp schema {schema} is not current; run --integrate to refresh it")
    return stamp


def validate_current() -> tuple[Path, int]:
    """严格校验：印章、记录哈希、公开指纹与最新记录全部一致。"""
    stamp = read_stamp(SCHEMA)
    name = stamp.get("record")
    if not isinstance(name, str):
        raise ValueError("Handoff stamp has no record")
    path = record_path(name, require_state_link=True)
    if digest(path) != stamp.get("recordSha256"):
        raise ValueError("Handoff record changed after the stamp was written; run --integrate")
    source_hash, count = fingerprint(True)
    if stamp.get("sourceFingerprint") != source_hash or stamp.get("fileCount") != count:
        raise ValueError("Stale handoff stamp: public files changed after integration; run --integrate")
    evidence_hash, _ = fingerprint(False)
    if stamp.get("evidenceFingerprint") != evidence_hash:
        raise ValueError("Stale handoff evidence fingerprint; run --integrate")
    latest = latest_record()
    if latest is None or path != latest:
        expected = latest.relative_to(ROOT).as_posix() if latest else "(none)"
        raise ValueError(f"CURRENT.json points to {name}, but the latest record is {expected}; "
                         "integrate the latest record first")
    return path, count


def integrate(name: str) -> tuple[Path, int]:
    """集成维护：记录必须已链接且为最新，然后刷新印章。"""
    path = record_path(name, require_state_link=True)
    latest = latest_record()
    if latest is None or path != latest:
        expected = latest.relative_to(ROOT).as_posix() if latest else "(none)"
        raise ValueError(f"Integration target must be the latest record {expected}, not {name}")
    source_hash, count = fingerprint(True)
    evidence_hash, _ = fingerprint(False)
    record_hash = digest(path)
    previous = read_stamp() if STAMP.exists() else None
    # 防止“源码变了但交接正文没更新”只靠重盖章混过。只对 schema 2 印章生效，
    # 迁移旧印章时允许重建；共享状态变化不算证据变化。
    if (previous is not None and previous.get("schema") == SCHEMA
            and previous.get("record") == name and previous.get("recordSha256") == record_hash
            and previous.get("evidenceFingerprint") != evidence_hash):
        raise ValueError("Public source changed but the handoff text did not; "
                         "update the record before integrating")
    stamp = {
        "schema": SCHEMA,
        "record": name,
        "recordSha256": record_hash,
        "sourceFingerprint": source_hash,
        "evidenceFingerprint": evidence_hash,
        "fileCount": count,
    }
    # 先写临时文件再原子替换，避免中断留下半个印章。
    temporary = STAMP.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(stamp, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(STAMP)
    # 写完立即回读严格校验，防止写入或并发覆盖留下不一致印章。
    path, count = validate_current()
    return path, count


def normalize_changed(value: str) -> PurePosixPath | None:
    value = value.strip()
    if not value or value.startswith("#"):
        return None
    posix = PurePosixPath(value.replace("\\", "/"))
    if posix.is_absolute():
        try:
            posix = PurePosixPath(Path(value).resolve().relative_to(ROOT.resolve()).as_posix())
        except ValueError:
            return None
    if ".." in posix.parts:
        return None
    return posix


def changed_paths(options) -> list[PurePosixPath] | None:
    """读取变更清单；未提供时返回 None（跳过变更集守卫）。"""
    if not options.changed_file and options.changed_from is None:
        return None
    values = list(options.changed_file)
    if options.changed_from is not None:
        if str(options.changed_from) == "-":
            values.extend(sys.stdin.read().splitlines())
        else:
            values.extend(options.changed_from.read_text().splitlines())
    return [path for path in (normalize_changed(value) for value in values) if path is not None]


def is_record_change(relative: PurePosixPath) -> bool:
    return (len(relative.parts) == 3 and relative.parts[:2] == ("docs", "handoffs")
            and relative.suffix == ".md" and relative.name != "TEMPLATE.md"
            and RECORD_NAME.match(relative.name) is not None
            and (ROOT / relative).is_file())


def is_public_change(relative: PurePosixPath, package) -> bool:
    """除交接记录与共享状态外，公开文件变化都要求本次同时更新交接记录。"""
    if is_record_change(relative):
        return False
    if relative.as_posix() in {STATE_RELATIVE, STAMP_RELATIVE}:
        return False
    return package.public_name(relative)


def pr_check(name: str | None, changed: list[PurePosixPath] | None) -> str:
    """普通 PR 模式：只校验记录自身，共享状态允许落后。"""
    package = distribution()
    records: list[PurePosixPath] = []
    if name is not None:
        path = record_path(name)
        relative = PurePosixPath(path.relative_to(ROOT).as_posix())
        if changed is not None:
            relevant = [item for item in changed if is_public_change(item, package)]
            if relevant and relative not in changed:
                # 显式记录也必须属于本次变更集，否则等于拿旧记录充当新证据。
                raise ValueError(f"{name} is not part of the changed file list; "
                                 "the PR must add or update its own handoff")
        records = [relative]
    elif changed is not None:
        for relative in changed:
            # 只允许规定命名，避免出现无法排序、无法迁移的交接文件名。
            if (len(relative.parts) == 3 and relative.parts[:2] == ("docs", "handoffs")
                    and relative.suffix == ".md" and relative.name != "TEMPLATE.md"
                    and not RECORD_NAME.match(relative.name)):
                raise ValueError(f"Invalid handoff filename: {relative} "
                                 "(expected docs/handoffs/YYYYMMDD-HHMM-topic.md)")
            if is_record_change(relative):
                records.append(relative)
        relevant = [relative for relative in changed if is_public_change(relative, package)]
        if relevant and not records:
            raise ValueError("Public files changed, but this change set adds/updates no "
                             "docs/handoffs/YYYYMMDD-HHMM-topic.md handoff record")
    else:
        latest = latest_record()
        if latest is None:
            raise ValueError("No handoff record found in docs/handoffs/")
        records = [PurePosixPath(latest.relative_to(ROOT).as_posix())]
    if not records:
        print("Handoff PR check passed: shared-state-only changes need no new record")
        return "shared-state-only"
    for relative in records:
        record_path(relative.as_posix())
    # 共享状态允许落后，只提示不失败；链接由集成阶段强制。
    state = STATE.read_text() if STATE.exists() else ""
    behind = [relative.name for relative in records if f"handoffs/{relative.name}" not in state]
    note = " (shared state may lag; integration refreshes it)" if behind else ""
    print(f"Handoff PR check passed: {', '.join(relative.as_posix() for relative in records)}{note}")
    return records[0].as_posix()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--pr", dest="mode", action="store_const", const="pr",
                      help="普通 PR 模式（默认）：只校验本次交接记录，共享状态可落后")
    mode.add_argument("--integrate", dest="mode", action="store_const", const="integrate",
                      help="集成模式：记录须已链接且为最新，并刷新 CURRENT.json 印章")
    mode.add_argument("--release", dest="mode", action="store_const", const="release",
                      help="发布严格模式：只读校验印章、记录与公开源码指纹全部最新")
    parser.set_defaults(mode="pr")
    parser.add_argument("--record", metavar="PATH",
                        help="指定本次交接记录；PR 模式只读，集成模式用于刷新印章")
    parser.add_argument("--changed-file", action="append", default=[], metavar="PATH",
                        help="变更文件（可重复）；用于 PR 模式强制源码变化必须有交接记录")
    parser.add_argument("--changed-from", type=Path, metavar="FILE",
                        help="从文件读取变更清单（每行一个路径，- 表示标准输入）")
    args = parser.parse_args()
    try:
        if args.mode == "release" and args.record:
            raise ValueError("--release is read-only; run --integrate --record first")
        if args.mode == "release":
            path, count = validate_current()
            print(f"Handoff release check passed: {path.relative_to(ROOT).as_posix()} ({count} public files)")
        elif args.mode == "integrate":
            if args.record:
                path, count = integrate(args.record)
                print(f"Handoff integrated: {path.relative_to(ROOT).as_posix()} ({count} public files)")
            else:
                path, count = validate_current()
                print(f"Handoff integrate check passed: {path.relative_to(ROOT).as_posix()} "
                      f"({count} public files; pass --record to refresh the stamp)")
        else:
            pr_check(args.record, changed_paths(args))
    except (OSError, ValueError, TypeError, AttributeError, KeyError) as error:
        print(f"Handoff check failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

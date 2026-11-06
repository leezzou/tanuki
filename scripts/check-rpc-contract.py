#!/usr/bin/env python3
"""校验 Browser/Phone RPC 客户端与原生实现的方法集、传输常量一致。

只读源码，不修改协议或产品代码；任何一侧扩展、删减或常量漂移都会输出差异并以非零退出。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

BROWSER_CLIENT = "app/src/main/assets/browser/tanuki-browser.mjs"
BROWSER_NATIVE = "app/src/main/kotlin/com/tanuki/browser/AgentBrowserController.kt"
PHONE_CLIENT = "app/src/main/assets/phone/tanuki-phone.mjs"
PHONE_NATIVE = "app/src/main/kotlin/com/tanuki/storage/PhoneFileService.kt"

# 冻结的对外方法集。并行 Worker 不得只改一侧；扩协议必须先更新这里的显式决定。
BROWSER_METHODS = frozenset({
    "status", "request_control", "navigate", "snapshot", "click", "type", "scroll",
    "screenshot", "back", "forward", "reload", "tabs", "new_tab", "select_tab",
    "close_tab", "find", "zoom",
})
PHONE_METHODS = frozenset({
    "status", "list", "stat", "read", "write_begin", "write_chunk", "write_commit",
    "write_cancel", "mkdir", "rename", "delete",
})

TOKEN_PATTERN = r"/^[A-Za-z0-9_-]{43}$/"
PHONE_ERROR_PATTERN = r"/^[a-z_]{1,64}$/"


class ContractError(Exception):
    """源码缺失或无法解析为约定的契约结构。"""


def read(relative: str) -> str:
    path = ROOT / relative
    try:
        return path.read_text(encoding="utf-8")
    except OSError as error:
        raise ContractError("无法读取 {}: {}".format(relative, error)) from error


def quoted_set(text: str, pattern: str, label: str) -> set[str]:
    match = re.search(pattern, text, re.S)
    if not match:
        raise ContractError("{}: 找不到方法集声明".format(label))
    values = re.findall(r"['\"]([^'\"]+)['\"]", match.group(1))
    if not values:
        raise ContractError("{}: 方法集为空或无法解析".format(label))
    duplicates = sorted({value for value in values if values.count(value) > 1})
    if duplicates:
        raise ContractError("{}: 方法重复: {}".format(label, ", ".join(duplicates)))
    return set(values)


def describe_difference(name: str, expected: set[str], client: set[str], native: set[str]) -> list[str]:
    problems = []
    for side, actual in (("客户端", client), ("原生", native)):
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        if missing:
            problems.append("{} {}缺少方法: {}".format(name, side, ", ".join(missing)))
        if extra:
            problems.append("{} {}多出方法: {}".format(name, side, ", ".join(extra)))
    if client != native:
        only_client = sorted(client - native)
        only_native = sorted(native - client)
        detail = []
        if only_client:
            detail.append("仅客户端: " + ", ".join(only_client))
        if only_native:
            detail.append("仅原生: " + ", ".join(only_native))
        problems.append("{} 客户端与原生不一致（{}）".format(name, "；".join(detail)))
    return problems


def require_contains(text: str, needle: str, label: str, problems: list[str]) -> None:
    if needle not in text:
        problems.append("{}: 缺少关键常量或结构 `{}`".format(label, needle))


def require_error_codes(text: str, label: str, problems: list[str]) -> None:
    codes = set(re.findall(r'RpcFailure\("([^"]+)"\)', text))
    codes.update(set(re.findall(r'error\("([^"]+)"\)', text)))
    invalid = sorted(code for code in codes if not re.fullmatch(r"[a-z][a-z0-9_]*", code))
    if invalid:
        problems.append("{}: 错误码格式不是小写短码: {}".format(label, ", ".join(invalid)))


def main() -> int:
    problems: list[str] = []
    try:
        browser_client = read(BROWSER_CLIENT)
        browser_native = read(BROWSER_NATIVE)
        phone_client = read(PHONE_CLIENT)
        phone_native = read(PHONE_NATIVE)

        browser_client_methods = quoted_set(
            browser_client, r"const methods = new Set\(\[(.*?)\]\);", "Browser 客户端")
        browser_native_methods = quoted_set(
            browser_native,
            r'if \(method !in setOf\((.*?)\)\) throw RpcFailure\("unknown_method"\)',
            "Browser 原生")
        phone_client_methods = quoted_set(
            phone_client, r"const methods = new Set\(\[(.*?)\]\);", "Phone 客户端")
        phone_native_methods = quoted_set(
            phone_native, r"private val methods = setOf\((.*?)\)", "Phone 原生")
    except ContractError as error:
        print("RPC contract check failed: {}".format(error), file=sys.stderr)
        return 1

    problems.extend(describe_difference(
        "Browser", BROWSER_METHODS, browser_client_methods, browser_native_methods))
    problems.extend(describe_difference(
        "Phone", PHONE_METHODS, phone_client_methods, phone_native_methods))

    # Browser 客户端：本机端口、/rpc、Bearer、43 字符 token、请求/响应上限与超时。
    for needle in (
        "path: '/rpc'",
        "Authorization: `Bearer ${connection.token}`",
        TOKEN_PATTERN,
        "if (body.length > 65536)",
        "size > 3 * 1024 * 1024",
        "req.destroy(new Error('browser_timeout')), 12000",
        "agent: false",
    ):
        require_contains(browser_client, needle, "Browser 客户端", problems)

    # Browser 原生：只监听回环地址、仅接受 POST /rpc、Bearer 比较、请求上限与错误结构。
    for needle in (
        'InetAddress.getByName("127.0.0.1")',
        '"POST /rpc HTTP/1.1"',
        '"POST /rpc HTTP/1.0"',
        '"Bearer ${connection.token}"',
        "MessageDigest.isEqual",
        "if (length !in 1..65536)",
        "ByteArray(32)",
        "android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING",
        '.put("ok", false).put("error", JSONObject().put("code", code).put("message", code))',
    ):
        require_contains(browser_native, needle, "Browser 原生", problems)
    require_error_codes(browser_native, "Browser 原生", problems)

    # Phone 客户端：/rpc、Bearer、token、300000 字节请求上限、1 MiB 响应上限与错误码格式。
    for needle in (
        "path: '/rpc'",
        "Authorization: `Bearer ${connection.token}`",
        TOKEN_PATTERN,
        "if (body.length > 300000)",
        "if (received > 1048576)",
        "if (response.statusCode !== 200)",
        "if (text.includes(connection.token))",
        PHONE_ERROR_PATTERN,
        "longIoMethods.has(method) ? 120000 : 20000",
    ):
        require_contains(phone_client, needle, "Phone 客户端", problems)

    # Phone 原生：回环监听、POST /rpc、Bearer、请求/响应上限、43 字符 token 与错误码格式。
    for needle in (
        'InetAddress.getByName("127.0.0.1")',
        '"POST /rpc HTTP/1.1"',
        '"Bearer $token"',
        "MessageDigest.isEqual",
        "require(length in 1..300_000)",
        "require(data.size <= 1_048_576)",
        "Base64.getUrlEncoder().withoutPadding()",
        "ByteArray(32)",
        'matches(Regex("[a-z_]{1,64}"))',
        '.put("ok", false).put("error", code)',
    ):
        require_contains(phone_native, needle, "Phone 原生", problems)

    print("RPC contract check")
    print("  Browser methods: client={} native={} expected={}".format(
        len(browser_client_methods), len(browser_native_methods), len(BROWSER_METHODS)))
    print("  Phone methods:   client={} native={} expected={}".format(
        len(phone_client_methods), len(phone_native_methods), len(PHONE_METHODS)))

    if problems:
        print("\nRPC contract check failed:", file=sys.stderr)
        for problem in problems:
            print("- {}".format(problem), file=sys.stderr)
        return 1
    print("\nRPC contract OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Tanuki 中继配对协议 v1 的主机端参考实现。

手机端 Kotlin 与主机端 Python 共用这份线协议：
- 帧：4 字节大端总长（含 1 字节类型 + 4 字节流号）+ 负载，最大 64 KiB；
- 握手：HELLO -> CHALLENGE -> AUTH -> QUEUED/REGISTERED -> OPEN/OPEN_OK；
- 数据：OPEN_OK 之后同一 TLS 流只承载 DATA/CLOSE/PING/PONG；
- 认证：HMAC-SHA256(token, "tanuki-relay-v1|<role>|<room>|...")，token 从不在客户端控制帧里出现；
- 中继只做字节转发，SSH 仍由手机与远程 sshd 端到端协商。

本文件只使用 Python 3.9 标准库；不记录 token、配对链接或 SSH 明文。
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import re
import struct
import urllib.parse
from typing import Dict, List, Optional, Tuple

VERSION = 1
DOMAIN = "tanuki-relay-v1"
URI_PREFIX = "tanuki-relay://v1?"
MAX_FRAME = 64 * 1024
MAX_CONTROL = 1024

CONTROL = 1
OPEN = 2
OPEN_OK = 3
DATA = 4
CLOSE = 5
PING = 6
PONG = 7
ERROR = 8

BASE64URL_RE = re.compile(r"^[A-Za-z0-9_-]+$")
HEX_32_RE = re.compile(r"^[0-9a-f]{32}$")


class ProtocolError(Exception):
    """线协议格式错误；调用方应关闭连接，不回显原始载荷。"""


class RelayError(Exception):
    """中继返回的业务错误码。"""

    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


def b64u_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def b64u_decode(text: str) -> bytes:
    if not text or not BASE64URL_RE.match(text):
        raise ValueError("base64url 格式无效")
    padding = "=" * (-len(text) % 4)
    raw = base64.urlsafe_b64decode(text + padding)
    if b64u_encode(raw) != text:
        raise ValueError("base64url 不是规范形式")
    return raw


def new_room() -> str:
    return b64u_encode(_random_bytes(16))


def new_token() -> str:
    return b64u_encode(_random_bytes(32))


def new_host_id() -> str:
    return _random_bytes(16).hex()


def new_nonce() -> str:
    return b64u_encode(_random_bytes(16))


def _random_bytes(size: int) -> bytes:
    import secrets

    return secrets.token_bytes(size)


def validate_room(room: str) -> None:
    if len(b64u_decode(room)) != 16:
        raise ValueError("房间号必须是 16 字节 base64url")


def validate_token(token: str) -> None:
    if len(b64u_decode(token)) != 32:
        raise ValueError("配对密钥必须是 32 字节 base64url")


def validate_host_id(host_id: str) -> None:
    if not HEX_32_RE.match(host_id):
        raise ValueError("主机标识必须是 32 位小写十六进制")


def _read_exact(sock, size: int) -> bytes:
    chunks: List[bytes] = []
    remaining = size
    while remaining > 0:
        chunk = sock.recv(remaining)
        if not chunk:
            raise EOFError("连接已关闭")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def read_frame(sock) -> Optional[Tuple[int, int, bytes]]:
    """读取一帧；正常 EOF 返回 None，截断/非法帧抛 ProtocolError/EOFError。"""
    first = sock.recv(4)
    if not first:
        return None
    header = first + _read_exact(sock, 4 - len(first)) if len(first) < 4 else first
    length = struct.unpack(">I", header)[0]
    if length < 5 or length > MAX_FRAME:
        raise ProtocolError("帧长度无效")
    body = _read_exact(sock, length)
    frame_type = body[0]
    stream_id = struct.unpack(">I", body[1:5])[0]
    if stream_id > 0x7FFFFFFF:
        raise ProtocolError("流号无效")
    payload = body[5:]
    if frame_type == CONTROL and len(payload) > MAX_CONTROL:
        raise ProtocolError("控制帧过大")
    return frame_type, stream_id, payload


def write_frame(sock, frame_type: int, stream_id: int, payload: bytes = b"") -> None:
    if frame_type not in (CONTROL, OPEN, OPEN_OK, DATA, CLOSE, PING, PONG, ERROR):
        raise ProtocolError("帧类型未知")
    if stream_id < 0 or stream_id > 0x7FFFFFFF:
        raise ProtocolError("流号无效")
    if len(payload) + 5 > MAX_FRAME:
        raise ProtocolError("帧过大")
    if frame_type == CONTROL and len(payload) > MAX_CONTROL:
        raise ProtocolError("控制帧过大")
    body = bytes([frame_type]) + struct.pack(">I", stream_id) + payload
    sock.sendall(struct.pack(">I", len(body)) + body)


def send_control(sock, text: str) -> None:
    if not text or "\n" in text or "\r" in text:
        raise ValueError("控制帧必须是非空单行文本")
    write_frame(sock, CONTROL, 0, text.encode("utf-8"))


def parse_control(payload: bytes) -> List[str]:
    try:
        text = payload.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ProtocolError("控制帧不是 UTF-8") from error
    if not text or "\n" in text or "\r" in text:
        raise ProtocolError("控制帧格式无效")
    parts = text.split(" ")
    if not parts or len(parts) > 8 or any(not part for part in parts):
        raise ProtocolError("控制帧字段无效")
    return parts


def read_control(sock) -> List[str]:
    frame = read_frame(sock)
    if frame is None:
        raise EOFError("连接已关闭")
    frame_type, stream_id, payload = frame
    if frame_type == ERROR:
        raise RelayError(payload.decode("utf-8", "replace")[:64])
    if frame_type != CONTROL or stream_id != 0:
        raise ProtocolError("预期控制帧")
    return parse_control(payload)


def _hmac(token: str, fields: List[str]) -> str:
    key = b64u_decode(token)
    message = "|".join([DOMAIN] + fields).encode("utf-8")
    return b64u_encode(hmac.new(key, message, hashlib.sha256).digest())


def client_proof(token: str, room: str, client_nonce: str, server_nonce: str) -> str:
    return _hmac(token, ["client", room, client_nonce, server_nonce])


def host_proof(token: str, room: str, host_id: str, host_nonce: str, server_nonce: str) -> str:
    return _hmac(token, ["host", room, host_id, host_nonce, server_nonce])


def verify(expected: str, actual: str) -> bool:
    return hmac.compare_digest(expected.encode("utf-8"), actual.encode("utf-8"))


def normalize_host(value: str) -> str:
    text = value.strip()
    if not text:
        raise ValueError("主机为空")
    if text.startswith("[") and text.endswith("]"):
        inner = text[1:-1]
        if not re.match(r"^[0-9A-Fa-f:.]+$", inner) or ":" not in inner or ":::" in inner:
            raise ValueError("IPv6 地址无效")
        return inner.lower()
    if text.count(":") >= 2:
        if not re.match(r"^[0-9A-Fa-f:.]+$", text) or ":::" in text:
            raise ValueError("IPv6 地址无效")
        return text.lower()
    if len(text) > 253 or not re.match(r"^[A-Za-z0-9](?:[A-Za-z0-9._-]{0,251}[A-Za-z0-9])?$", text):
        raise ValueError("主机名无效")
    if ".." in text:
        raise ValueError("主机名无效")
    return text.lower()


def split_endpoint(value: str) -> Tuple[str, int]:
    text = value.strip()
    if text.startswith("["):
        close = text.find("]")
        if close <= 1 or close + 1 >= len(text) or text[close + 1] != ":":
            raise ValueError("中继地址格式无效")
        host = text[1:close]
        port_text = text[close + 2:]
    else:
        separator = text.rfind(":")
        if separator <= 0:
            raise ValueError("中继地址必须包含端口")
        host = text[:separator]
        port_text = text[separator + 1:]
        if ":" in host:
            raise ValueError("IPv6 中继地址必须使用方括号")
    if ":" in port_text or not port_text.isdigit():
        raise ValueError("中继端口无效")
    port = int(port_text)
    if port < 1 or port > 65535:
        raise ValueError("中继端口必须在 1-65535 之间")
    return normalize_host(host), port


def build_pairing_uri(host: str, port: int, room: str, token: str, host_id: str, pin: str) -> str:
    validate_room(room)
    validate_token(token)
    validate_host_id(host_id)
    normalized = normalize_host(host)
    shown = "[{}]".format(normalized) if ":" in normalized else normalized
    relay = "{}:{}".format(shown, port)
    parts = [
        "relay=" + urllib.parse.quote(relay, safe=""),
        "room=" + room,
        "token=" + token,
        "host=" + host_id,
    ]
    if pin:
        if not pin.startswith("sha256-") or len(pin) != 50:
            raise ValueError("证书钉扎格式无效")
        parts.append("pin=" + pin)
    return URI_PREFIX + "&".join(parts)


def pairing_code(uri: str) -> str:
    """与手机端 RemotePairingCode 共用：SHA-256(规范链接) 前 8 字节 mod 1e6。

    6 位码只用于人工核对“链接和码来自同一次配对”，不是新的传输秘密；
    单独输入 6 位码无法恢复中继地址/房间/token。
    """
    digest = hashlib.sha256(uri.strip().encode("utf-8")).digest()
    value = int.from_bytes(digest[:8], "big")
    return "{:06d}".format(value % 1000000)


def parse_pairing_uri(uri: str) -> Dict[str, object]:
    text = uri.strip()
    if not text.startswith(URI_PREFIX) or "#" in text:
        raise ValueError("配对链接格式无效")
    values: Dict[str, str] = {}
    allowed = {"relay", "room", "token", "host", "pin"}
    for part in text[len(URI_PREFIX):].split("&"):
        if not part or "=" not in part:
            raise ValueError("配对链接字段格式无效")
        key, value = part.split("=", 1)
        key = urllib.parse.unquote(key)
        value = urllib.parse.unquote(value)
        if key not in allowed or key in values:
            raise ValueError("配对链接包含未知或重复字段")
        values[key] = value
    for required in ("relay", "room", "token", "host"):
        if required not in values:
            raise ValueError("配对链接缺少字段")
    host, port = split_endpoint(values["relay"])
    validate_room(values["room"])
    validate_token(values["token"])
    validate_host_id(values["host"])
    pin = values.get("pin", "")
    if pin and (not pin.startswith("sha256-") or len(pin) != 50):
        raise ValueError("证书钉扎格式无效")
    return {
        "relay_host": host,
        "relay_port": port,
        "room": values["room"],
        "token": values["token"],
        "host_id": values["host"],
        "pin": pin,
    }


def cert_pin(certificate_der: bytes) -> str:
    return "sha256-" + b64u_encode(hashlib.sha256(certificate_der).digest())


def matches_pin(pin: str, certificate_der: bytes) -> bool:
    if not pin.startswith("sha256-"):
        return False
    try:
        expected = b64u_decode(pin[len("sha256-"):])
    except ValueError:
        return False
    return hmac.compare_digest(expected, hashlib.sha256(certificate_der).digest())

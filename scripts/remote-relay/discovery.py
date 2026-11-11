#!/usr/bin/env python3
"""Tanuki 局域网自动发现：最小 mDNS 广播与报文编解码。

只做主机端“让手机看到这台电脑”所需的最小集合，不实现完整 mDNS 栈：

- 服务类型固定为 `_tanuki-remote._tcp.local`；
- 优先调用系统 `avahi-publish-service`（Linux）或 `dns-sd`（macOS）；
- 两者都不可用或启动失败时，用标准库 UDP 组播应答 PTR/SRV/TXT/A；
- TXT 只包含显示名、SSH/DSH 端口、hostId、协议版本和指纹摘要，绝不包含
  配对 token、房间号或 SSH 凭据；
- 广播失败只记录日志，不影响中继模式。

手机端仍必须通过 SSH 主机密钥确认和用户认证；发现信息只用于“看到并点选”，
不是新的信任来源。
"""
from __future__ import annotations

import hashlib
import ipaddress
import re
import shutil
import socket
import struct
import subprocess
import threading
import time
import unicodedata
from dataclasses import dataclass
from typing import Callable, Dict, List, Optional, Sequence, Tuple

import protocol as p

SERVICE_LABEL = "_tanuki-remote._tcp"
LOCAL_DOMAIN = "local"
SERVICE_TYPE = SERVICE_LABEL + "." + LOCAL_DOMAIN
PROTOCOL_VERSION = 1
DEFAULT_DSH_PORT = 3080

MDNS_ADDRESS = "224.0.0.251"
MDNS_PORT = 5353
RECORD_TTL = 120
MAX_LABEL_BYTES = 63
MAX_PACKET = 9000
MAX_TXT_BYTES = 1300
FINGERPRINT_RE = re.compile(r"^[0-9a-f]{16}$")

TYPE_A = 1
TYPE_PTR = 12
TYPE_TXT = 16
TYPE_SRV = 33
TYPE_ANY = 255
CLASS_IN = 1
CLASS_CACHE_FLUSH = 0x8001


class DiscoveryError(Exception):
    """发现配置/报文错误；调用方记录后继续中继模式。"""


@dataclass(frozen=True)
class Question:
    name: str
    qtype: int
    qclass: int = CLASS_IN


@dataclass(frozen=True)
class Record:
    name: str
    rtype: int
    rclass: int
    ttl: int
    rdata: bytes


def sanitize_name(value: str, fallback: str = "tanuki-host") -> str:
    """清洗 mDNS 实例名：去掉控制字符，点/斜杠转连字符，限制 63 字节。"""
    raw = unicodedata.normalize("NFKC", str(value or ""))
    chars: List[str] = []
    for char in raw:
        if char in ".。":
            chars.append("-")
        elif char in "\\/":
            chars.append("-")
        elif char.isspace():
            chars.append(" ")
        elif unicodedata.category(char).startswith("C"):
            chars.append(" ")
        else:
            chars.append(char)
    text = " ".join("".join(chars).split()).strip(" -")
    if not text:
        text = fallback
    encoded = text.encode("utf-8")
    if len(encoded) > MAX_LABEL_BYTES:
        # 按 UTF-8 字符边界截断，避免切出半个汉字。
        text = encoded[:MAX_LABEL_BYTES].decode("utf-8", "ignore").rstrip(" -")
    return text or fallback


def default_display_name(hostname: Optional[str] = None) -> str:
    """默认显示名：主机名第一段，去掉 .local/域名后缀。"""
    raw = (hostname if hostname is not None else socket.gethostname()).strip()
    first = raw.split(".")[0] if raw else ""
    return sanitize_name(first, "tanuki-host")


def default_host_label(hostname: Optional[str] = None) -> str:
    """A/SRV 目标标签：显示名里的空格换成连字符，避免 DNS 标签含空格。"""
    return sanitize_name(default_display_name(hostname), "tanuki-host").replace(" ", "-")


def dns_host_label(host_id: str, hostname: Optional[str] = None) -> str:
    """A/SRV 目标标签：优先用 ASCII 主机名，中文等非 ASCII 时回退 hostId 前缀。"""
    label = default_host_label(hostname)
    if re.fullmatch(r"[A-Za-z0-9-]{1,63}", label):
        return label
    return "tanuki-" + host_id[:8]


def fingerprint_digest(host_id: str) -> str:
    """hostId 的短摘要，仅用于界面显示/人工对照，不是认证凭据。"""
    p.validate_host_id(host_id)
    return hashlib.sha256(host_id.encode("ascii")).hexdigest()[:16]


def txt_records(
    name: str,
    ssh_port: int,
    dsh_port: int,
    host_id: str,
    fingerprint: str,
    version: int = PROTOCOL_VERSION,
) -> List[str]:
    """拼装 TXT 键值；这里刻意不接收 token/room，避免误广播。"""
    clean = sanitize_name(name)
    p.validate_host_id(host_id)
    if not 1 <= int(ssh_port) <= 65535:
        raise DiscoveryError("SSH 端口必须在 1-65535 之间")
    if not 1 <= int(dsh_port) <= 65535:
        raise DiscoveryError("DSH 端口必须在 1-65535 之间")
    if not 1 <= int(version) <= 65535:
        raise DiscoveryError("协议版本必须在 1-65535 之间")
    if not FINGERPRINT_RE.match(fingerprint):
        raise DiscoveryError("指纹摘要必须是 16 位小写十六进制")
    return [
        "name=" + clean,
        "ssh=" + str(int(ssh_port)),
        "dsh=" + str(int(dsh_port)),
        "host=" + host_id,
        "ver=" + str(int(version)),
        "fp=" + fingerprint,
    ]


def encode_txt(records: Sequence[str]) -> bytes:
    """TXT rdata：每条一个长度字节 + UTF-8 内容，总长不超过 mDNS 常规上限。"""
    out = bytearray()
    for record in records:
        raw = str(record).encode("utf-8")
        if not raw or len(raw) > 255:
            raise DiscoveryError("TXT 单条记录长度无效")
        out.append(len(raw))
        out.extend(raw)
    if len(out) > MAX_TXT_BYTES:
        raise DiscoveryError("TXT 总长度过大")
    return bytes(out)


def parse_txt(rdata: bytes) -> List[str]:
    records: List[str] = []
    offset = 0
    while offset < len(rdata):
        length = rdata[offset]
        offset += 1
        if length == 0 or offset + length > len(rdata):
            raise DiscoveryError("TXT 记录格式无效")
        records.append(rdata[offset:offset + length].decode("utf-8"))
        offset += length
    return records


def txt_map(records: Sequence[str]) -> Dict[str, str]:
    values: Dict[str, str] = {}
    for record in records:
        key, separator, value = str(record).partition("=")
        if not separator or not key or key in values:
            raise DiscoveryError("TXT 键值格式无效")
        values[key] = value
    return values


def encode_name(name: str) -> bytes:
    """编码 DNS 名称；mDNS 服务名允许 UTF-8，按字节长度校验。"""
    text = str(name).strip().rstrip(".")
    if not text:
        return b"\x00"
    out = bytearray()
    for label in text.split("."):
        raw = label.encode("utf-8")
        if not raw or len(raw) > MAX_LABEL_BYTES:
            raise DiscoveryError("DNS 标签长度无效")
        out.append(len(raw))
        out.extend(raw)
    out.append(0)
    if len(out) > 255:
        raise DiscoveryError("DNS 名称过长")
    return bytes(out)


def decode_name(data: bytes, offset: int) -> Tuple[str, int]:
    """解码 DNS 名称，支持 mDNS 常见的压缩指针。"""
    labels: List[str] = []
    next_offset: Optional[int] = None
    jumps = 0
    while True:
        if offset < 0 or offset >= len(data):
            raise DiscoveryError("DNS 名称越界")
        length = data[offset]
        if length == 0:
            offset += 1
            break
        if length & 0xC0 == 0xC0:
            if offset + 1 >= len(data):
                raise DiscoveryError("DNS 压缩指针越界")
            pointer = ((length & 0x3F) << 8) | data[offset + 1]
            if next_offset is None:
                next_offset = offset + 2
            if pointer >= len(data) or jumps >= 32:
                raise DiscoveryError("DNS 压缩指针无效")
            offset = pointer
            jumps += 1
            continue
        if length & 0xC0:
            raise DiscoveryError("不支持的 DNS 标签类型")
        offset += 1
        if offset + length > len(data):
            raise DiscoveryError("DNS 标签越界")
        try:
            labels.append(data[offset:offset + length].decode("utf-8"))
        except UnicodeDecodeError as error:
            raise DiscoveryError("DNS 标签不是 UTF-8") from error
        offset += length
    return ".".join(labels), next_offset if next_offset is not None else offset


def build_query(name: str, qtype: int = TYPE_PTR, qclass: int = CLASS_IN) -> bytes:
    header = struct.pack(">HHHHHH", 0, 0, 1, 0, 0, 0)
    return header + encode_name(name) + struct.pack(">HH", qtype, qclass)


def parse_query(data: bytes) -> List[Question]:
    """解析查询；响应包（QR=1）返回空列表，避免应答自己的广播。"""
    if len(data) < 12:
        raise DiscoveryError("mDNS 报文过短")
    _ident, flags, qdcount, _ancount, _nscount, _arcount = struct.unpack(">HHHHHH", data[:12])
    if flags & 0x8000:
        return []
    if qdcount > 32:
        raise DiscoveryError("mDNS 问题数量过多")
    offset = 12
    questions: List[Question] = []
    for _ in range(qdcount):
        name, offset = decode_name(data, offset)
        if offset + 4 > len(data):
            raise DiscoveryError("mDNS 问题越界")
        qtype, qclass = struct.unpack(">HH", data[offset:offset + 4])
        offset += 4
        questions.append(Question(name.lower().rstrip("."), qtype, qclass & 0x7FFF))
    return questions


def _encode_record(record: Record) -> bytes:
    return (
        encode_name(record.name)
        + struct.pack(">HHIH", record.rtype, record.rclass, record.ttl, len(record.rdata))
        + record.rdata
    )


def parse_response(data: bytes) -> List[Record]:
    """解析响应中的全部记录（answer/authority/additional）。"""
    if len(data) < 12:
        raise DiscoveryError("mDNS 报文过短")
    _ident, flags, qdcount, ancount, nscount, arcount = struct.unpack(">HHHHHH", data[:12])
    if not flags & 0x8000:
        raise DiscoveryError("不是 mDNS 响应")
    offset = 12
    for _ in range(qdcount):
        _name, offset = decode_name(data, offset)
        if offset + 4 > len(data):
            raise DiscoveryError("mDNS 问题越界")
        offset += 4
    records: List[Record] = []
    for count in (ancount, nscount, arcount):
        if count > 128:
            raise DiscoveryError("mDNS 记录数量过多")
        for _ in range(count):
            name, offset = decode_name(data, offset)
            if offset + 10 > len(data):
                raise DiscoveryError("mDNS 记录头越界")
            rtype, rclass, ttl, rdlength = struct.unpack(">HHIH", data[offset:offset + 10])
            offset += 10
            if offset + rdlength > len(data):
                raise DiscoveryError("mDNS 记录数据越界")
            records.append(Record(name, rtype, rclass, ttl, data[offset:offset + rdlength]))
            offset += rdlength
    return records


def parse_ptr_rdata(rdata: bytes) -> str:
    return decode_name(rdata, 0)[0]


def parse_srv_rdata(rdata: bytes) -> Tuple[int, int, int, str]:
    if len(rdata) < 7:
        raise DiscoveryError("SRV 记录过短")
    priority, weight, port = struct.unpack(">HHH", rdata[:6])
    target, _offset = decode_name(rdata, 6)
    return priority, weight, port, target


def parse_a_rdata(rdata: bytes) -> str:
    if len(rdata) != 4:
        raise DiscoveryError("A 记录长度无效")
    return socket.inet_ntoa(rdata)


def _answer_key(record: Record) -> Tuple[str, int, bytes]:
    return record.name.lower(), record.rtype, record.rdata


def build_response(
    name: str,
    host_id: str,
    ssh_port: int,
    dsh_port: int,
    fingerprint: str,
    addresses: Sequence[str],
    questions: Sequence[Question],
    version: int = PROTOCOL_VERSION,
    target: Optional[str] = None,
    ttl: int = RECORD_TTL,
) -> bytes:
    """按查询构造最小应答；只回答本服务类型/实例/目标主机，其余返回空包。"""
    clean = sanitize_name(name)
    target_fqdn = (sanitize_name(target, "tanuki-host").replace(" ", "-") if target else default_host_label()) + "." + LOCAL_DOMAIN
    instance = clean + "." + SERVICE_TYPE
    txt = txt_records(clean, ssh_port, dsh_port, host_id, fingerprint, version)
    valid_addresses: List[str] = []
    for address in addresses:
        try:
            parsed = ipaddress.ip_address(str(address))
        except ValueError:
            continue
        if parsed.version == 4 and str(parsed) not in valid_addresses:
            valid_addresses.append(str(parsed))

    ptr = Record(SERVICE_TYPE, TYPE_PTR, CLASS_IN, ttl, encode_name(instance))
    srv = Record(
        instance,
        TYPE_SRV,
        CLASS_CACHE_FLUSH,
        ttl,
        struct.pack(">HHH", 0, 0, int(ssh_port)) + encode_name(target_fqdn),
    )
    txt_record = Record(instance, TYPE_TXT, CLASS_CACHE_FLUSH, ttl, encode_txt(txt))
    a_records = [
        Record(target_fqdn, TYPE_A, CLASS_CACHE_FLUSH, ttl, socket.inet_aton(address))
        for address in valid_addresses
    ]

    answers: List[Record] = []
    additionals: List[Record] = []
    wanted = set()
    service = SERVICE_TYPE.lower()
    instance_key = instance.lower()
    target_key = target_fqdn.lower()
    for question in questions:
        query_name = question.name.lower().rstrip(".")
        if query_name == service and question.qtype in (TYPE_PTR, TYPE_ANY):
            wanted.add("ptr")
        elif query_name == instance_key:
            if question.qtype in (TYPE_SRV, TYPE_ANY):
                wanted.add("srv")
            if question.qtype in (TYPE_TXT, TYPE_ANY):
                wanted.add("txt")
        elif query_name == target_key and question.qtype in (TYPE_A, TYPE_ANY):
            wanted.add("a")
    if not wanted:
        return b""

    if "ptr" in wanted:
        answers.append(ptr)
        additionals.extend([srv, txt_record] + a_records)
    if "srv" in wanted:
        answers.append(srv)
        additionals.extend(a_records)
    if "txt" in wanted:
        answers.append(txt_record)
    if "a" in wanted:
        answers.extend(a_records)

    unique_answers = list({_answer_key(record): record for record in answers}.values())
    unique_additionals = [
        record
        for record in {_answer_key(record): record for record in additionals}.values()
        if _answer_key(record) not in {_answer_key(answer) for answer in unique_answers}
    ]
    header = struct.pack(
        ">HHHHHH", 0, 0x8400, 0, len(unique_answers), 0, len(unique_additionals)
    )
    return header + b"".join(_encode_record(record) for record in unique_answers + unique_additionals)


def _usable_ipv4(text: str) -> bool:
    try:
        address = ipaddress.ip_address(text)
    except ValueError:
        return False
    return (
        address.version == 4
        and not address.is_loopback
        and not address.is_link_local
        and not address.is_unspecified
    )


def detect_lan_ip(run: Callable = subprocess.run) -> str:
    """尽力探测本机非回环 IPv4；失败返回空串。"""
    candidates: Sequence[Sequence[str]] = (
        ("ipconfig", "getifaddr", "en0"),
        ("ipconfig", "getifaddr", "en1"),
        ("hostname", "-I"),
    )
    for args in candidates:
        try:
            completed = run(list(args), capture_output=True, text=True, timeout=2)
        except (OSError, subprocess.SubprocessError):
            continue
        if completed.returncode != 0:
            continue
        for token in str(completed.stdout).split():
            if _usable_ipv4(token):
                return token
    return ""


def local_ipv4_addresses(run: Callable = subprocess.run) -> List[str]:
    """收集可广播的局域网 IPv4（去重、过滤回环/链路本地）。"""
    found: List[str] = []
    primary = detect_lan_ip(run)
    if primary:
        found.append(primary)
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET, socket.SOCK_STREAM):
            address = info[4][0]
            if _usable_ipv4(address) and address not in found:
                found.append(address)
    except OSError:
        pass
    try:
        probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            # UDP connect 只选路由，不发包；用于拿到默认出口地址。
            probe.connect(("192.0.2.1", 9))
            address = probe.getsockname()[0]
        finally:
            probe.close()
        if _usable_ipv4(address) and address not in found:
            found.append(address)
    except OSError:
        pass
    return found


def system_publish_command(tool: str, name: str, ssh_port: int, txt: Sequence[str]) -> List[str]:
    """拼装系统广播命令；只使用固定参数，不经过 shell。"""
    clean = sanitize_name(name)
    if tool == "avahi-publish-service":
        return [tool, "--no-reverse", clean, SERVICE_LABEL, str(int(ssh_port))] + list(txt)
    if tool == "dns-sd":
        return [tool, "-R", clean, SERVICE_LABEL, LOCAL_DOMAIN, str(int(ssh_port))] + list(txt)
    raise DiscoveryError("不支持的系统 mDNS 工具：{}".format(tool))


class MdnsResponder:
    """标准库最小应答器：只处理本服务类型的 PTR/SRV/TXT/A 查询。"""

    def __init__(
        self,
        name: str,
        host_id: str,
        ssh_port: int,
        dsh_port: int,
        fingerprint: str,
        addresses: Sequence[str],
        target: Optional[str] = None,
    ) -> None:
        self.name = name
        self.host_id = host_id
        self.ssh_port = ssh_port
        self.dsh_port = dsh_port
        self.fingerprint = fingerprint
        self.addresses = list(addresses)
        self.target = target
        self._socket: Optional[socket.socket] = None
        self._thread: Optional[threading.Thread] = None
        self._stop = threading.Event()

    def start(self) -> None:
        if not self.addresses:
            raise DiscoveryError("没有可广播的局域网 IPv4")
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        try:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            if hasattr(socket, "SO_REUSEPORT"):
                try:
                    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
                except OSError:
                    pass
            sock.bind(("", MDNS_PORT))
            membership = struct.pack(
                "=4s4s", socket.inet_aton(MDNS_ADDRESS), socket.inet_aton("0.0.0.0")
            )
            sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, membership)
            sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 255)
            sock.settimeout(0.5)
        except OSError as error:
            sock.close()
            raise DiscoveryError("无法加入 mDNS 组播：{}".format(error)) from error
        self._socket = sock
        self._thread = threading.Thread(target=self._loop, name="tanuki-mdns", daemon=True)
        self._thread.start()

    def _loop(self) -> None:
        while not self._stop.is_set():
            sock = self._socket
            if sock is None:
                return
            try:
                data, _address = sock.recvfrom(MAX_PACKET)
            except socket.timeout:
                continue
            except OSError:
                return
            try:
                questions = parse_query(data)
                response = build_response(
                    self.name,
                    self.host_id,
                    self.ssh_port,
                    self.dsh_port,
                    self.fingerprint,
                    self.addresses,
                    questions,
                    target=self.target,
                )
            except (DiscoveryError, ValueError, OSError):
                continue
            if not response:
                continue
            try:
                sock.sendto(response, (MDNS_ADDRESS, MDNS_PORT))
            except OSError:
                continue

    def stop(self) -> None:
        self._stop.set()
        sock = self._socket
        self._socket = None
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass
        thread = self._thread
        self._thread = None
        if thread is not None and thread.is_alive():
            thread.join(timeout=2.0)


class Advertiser:
    """优先系统工具，失败时回退标准库应答；start/stop 均不抛到中继主循环。"""

    def __init__(
        self,
        name: str,
        ssh_port: int,
        dsh_port: int,
        host_id: str,
        fingerprint: Optional[str] = None,
        addresses: Optional[Sequence[str]] = None,
        target: Optional[str] = None,
        which: Callable[[str], Optional[str]] = shutil.which,
        popen: Callable = subprocess.Popen,
        responder_factory: Optional[Callable] = None,
    ) -> None:
        self.name = sanitize_name(name)
        self.ssh_port = int(ssh_port)
        self.dsh_port = int(dsh_port)
        self.host_id = host_id
        self.fingerprint = fingerprint or fingerprint_digest(host_id)
        self.txt = txt_records(
            self.name, self.ssh_port, self.dsh_port, host_id, self.fingerprint
        )
        self.addresses = list(addresses) if addresses is not None else None
        self.target = target
        self._which = which
        self._popen = popen
        self._responder_factory = responder_factory or MdnsResponder
        self._process = None
        self._responder = None
        self.backend = ""
        self.notes: List[str] = []

    def start(self) -> str:
        for tool in ("avahi-publish-service", "dns-sd"):
            path = self._which(tool)
            if not path:
                continue
            command = system_publish_command(tool, self.name, self.ssh_port, self.txt)
            command[0] = path
            try:
                process = self._popen(
                    command,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.PIPE,
                    text=True,
                )
                time.sleep(0.2)
                if process.poll() is None:
                    self._process = process
                    self.backend = tool
                    return tool
                stderr = ""
                if getattr(process, "stderr", None) is not None:
                    stderr = process.stderr.read() or ""
                self.notes.append("{} 启动失败：{}".format(tool, stderr.strip()[:160]))
            except (OSError, subprocess.SubprocessError) as error:
                self.notes.append("{} 不可用：{}".format(tool, error))

        addresses = self.addresses
        if addresses is None:
            addresses = local_ipv4_addresses()
            self.addresses = addresses
        if not addresses:
            raise DiscoveryError("没有可广播的局域网 IPv4；可改用 --no-mdns")
        responder = self._responder_factory(
            self.name,
            self.host_id,
            self.ssh_port,
            self.dsh_port,
            self.fingerprint,
            addresses,
            self.target,
        )
        responder.start()
        self._responder = responder
        self.backend = "python"
        return "python"

    def stop(self) -> None:
        process = self._process
        self._process = None
        if process is not None:
            try:
                process.terminate()
            except OSError:
                pass
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                try:
                    process.kill()
                    process.wait(timeout=3)
                except (OSError, subprocess.TimeoutExpired):
                    pass
            except OSError:
                pass
            stderr = getattr(process, "stderr", None)
            if stderr is not None:
                try:
                    stderr.close()
                except OSError:
                    pass
        responder = self._responder
        self._responder = None
        if responder is not None:
            try:
                responder.stop()
            except OSError:
                pass

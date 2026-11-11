#!/usr/bin/env python3
"""Tanuki 远程中继一键启动（quickstart.sh）的辅助逻辑。

quickstart.sh 只负责终端交互、进程编排和 trap 清理；下面这些容易出错、
又不需要真实网络的逻辑集中在 Python 里，既让 shell 保持简单，也能离线单测：

- 从证书 PEM 计算 `sha256-<base64url(SHA-256(DER))>` 钉扎值；
- 拼装 relay.py / host_agent.py 的命令行参数；
- 读取并校验配对文件，计算 6 位核对码；
- 端口是否被占用、等待端口监听、探测本机局域网地址。

安全边界：不读取/输出 SSH 凭据；配对链接含一次性房间 token，只写入 0600
配对文件或用户终端；不访问外网，局域网地址探测只调用本机命令。
"""
from __future__ import annotations

import argparse
import ipaddress
import os
import shlex
import socket
import ssl
import subprocess
import sys
import time
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

import protocol as p

HERE = Path(__file__).resolve().parent
RELAY_SCRIPT = HERE / "relay.py"
AGENT_SCRIPT = HERE / "host_agent.py"
DEFAULT_STATE_DIR = Path.home() / ".tanuki-relay"
PIN_PREFIX = "sha256-"
PIN_LENGTH = 50  # "sha256-" + 43 个 base64url 字符


class QuickstartError(Exception):
    """可直接展示给用户的中文错误；quickstart.sh 据此提示并退出。"""


def validate_cert_pin(pin: str) -> None:
    """钉扎必须与 protocol.build_pairing_uri 的格式一致，避免启动后才失败。"""
    if not pin.startswith(PIN_PREFIX) or len(pin) != PIN_LENGTH:
        raise QuickstartError("证书钉扎格式无效：应为 sha256- 加 43 个 base64url 字符")


def certificate_pin(cert_path: Path) -> str:
    """读取 PEM 证书，按 DER 的 SHA-256 计算钉扎（与 relay.py/手机端一致）。"""
    path = Path(cert_path).expanduser()
    try:
        pem = path.read_text(encoding="utf-8")
    except OSError as error:
        raise QuickstartError("读取证书失败：{}（{}）".format(path, error.strerror or error)) from error
    try:
        der = ssl.PEM_cert_to_DER_cert(pem)
    except ValueError as error:
        raise QuickstartError("证书不是有效的 PEM：{}".format(path)) from error
    return p.cert_pin(der)


def validate_port(port: int, label: str = "端口") -> None:
    if not 1 <= int(port) <= 65535:
        raise QuickstartError("{}必须在 1-65535 之间".format(label))


def relay_command(python: str, listen: str, port: int, cert: Path, key: Path) -> List[str]:
    """拼装 relay.py 参数；`--cert/--key` 必须是绝对或用户给定路径。"""
    validate_port(port)
    if not str(listen).strip():
        raise QuickstartError("中继监听地址不能为空")
    return [
        python,
        str(RELAY_SCRIPT),
        "--listen",
        str(listen),
        "--port",
        str(port),
        "--cert",
        str(cert),
        "--key",
        str(key),
    ]


def agent_command(
    python: str,
    relay: str,
    ssh_host: str,
    ssh_port: int,
    state_dir: Path,
    cert_pin: str,
    pairing_relay: str = "",
) -> List[str]:
    """拼装 host_agent.py 参数，确保钉扎不会漏传。"""
    if not cert_pin.startswith(PIN_PREFIX):
        raise QuickstartError("缺少 --cert-pin，拒绝启动主机端 agent")
    validate_port(ssh_port, "SSH 端口")
    if not str(relay).strip():
        raise QuickstartError("中继连接地址不能为空")
    try:
        p.split_endpoint(str(relay))
        if pairing_relay:
            p.split_endpoint(str(pairing_relay))
    except ValueError as error:
        raise QuickstartError("中继地址无效：{}".format(error)) from error
    command = [
        python,
        str(AGENT_SCRIPT),
        "--relay",
        str(relay),
        "--ssh-host",
        str(ssh_host),
        "--ssh-port",
        str(ssh_port),
        "--state-dir",
        str(state_dir),
        "--cert-pin",
        str(cert_pin),
    ]
    if pairing_relay:
        # 连接地址与手机可达地址可能不同（例如主机连 127.0.0.1、手机走公网地址）。
        command += ["--pairing-relay", str(pairing_relay)]
    return command


def format_endpoint(host: str, port: int) -> str:
    """把 host:port 格式化为 protocol.split_endpoint 能解析的形式（IPv6 加方括号）。"""
    text = str(host).strip()
    if not text:
        raise QuickstartError("中继地址不能为空")
    if text.startswith("[") and text.endswith("]"):
        text = text[1:-1]
    if ":" in text:
        return "[{}]:{}".format(text, port)
    return "{}:{}".format(text, port)


def validate_public_endpoint(host: str, port: int) -> None:
    """提前校验手机可达地址，避免 agent 启动后才因非法主机名崩溃。"""
    validate_port(port)
    try:
        p.split_endpoint(format_endpoint(host, port))
    except ValueError as error:
        raise QuickstartError("手机可达地址无效：{}".format(error)) from error


def read_pairing(path: Path) -> Tuple[str, str]:
    """读取配对文件，返回 (配对链接, 6 位核对码)；格式非法时拒绝。"""
    pairing_path = Path(path).expanduser()
    try:
        if pairing_path.stat().st_size > 8192:
            raise QuickstartError("配对文件过大，拒绝读取：{}".format(pairing_path))
        uri = pairing_path.read_text(encoding="utf-8").strip()
    except OSError as error:
        raise QuickstartError("读取配对文件失败：{}（{}）".format(pairing_path, error.strerror or error)) from error
    if not uri:
        raise QuickstartError("配对文件为空：{}".format(pairing_path))
    try:
        p.parse_pairing_uri(uri)
    except ValueError as error:
        raise QuickstartError("配对文件格式无效：{}（{}）".format(pairing_path, error)) from error
    return uri, p.pairing_code(uri)


def probe_host(host: str) -> str:
    """把通配监听地址换成可连接的探测地址，避免 0.0.0.0/:: 无法 connect。"""
    text = str(host).strip()
    if text in ("", "0.0.0.0", "*"):
        return "127.0.0.1"
    if text in ("::", "[::]"):
        return "::1"
    return text


def port_in_use(host: str, port: int, timeout: float = 0.5) -> bool:
    """尝试建立 TCP 连接；能连上就认为端口已被监听。"""
    try:
        with socket.create_connection((probe_host(host), int(port)), timeout=timeout):
            return True
    except OSError:
        return False


def wait_port(host: str, port: int, timeout: float = 10.0, interval: float = 0.1) -> bool:
    """在超时内等待端口可连接；返回是否成功。"""
    deadline = time.monotonic() + max(0.0, float(timeout))
    while True:
        if port_in_use(host, port, timeout=min(0.5, max(0.05, interval * 5))):
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(interval)


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


def detect_lan_ip(run=subprocess.run) -> str:
    """尽力探测本机非回环 IPv4；失败返回空串，交给调用方提示 --public-host。

    只调用本机固定命令（macOS ipconfig / Linux hostname），不访问外网，
    因此结果只是“局域网可用”的最好猜测，跨网络仍需用户显式传公网地址。
    """
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
            # hostname -I 可能返回多个地址（含 IPv6），只取第一个可用 IPv4。
            if _usable_ipv4(token):
                return token
    return ""


def plan_text(
    python: str,
    state_dir: Path,
    cert: Path,
    key: Path,
    listen: str,
    port: int,
    public_host: str,
    ssh_host: str,
    ssh_port: int,
    pairing_file: Path,
    cert_pin: Optional[str],
) -> str:
    """`--print-only` 的输出：展示解析后的参数与将要执行的命令，不启动进程。"""
    cert_exists = Path(cert).is_file()
    key_exists = Path(key).is_file()
    endpoint = format_endpoint(public_host, port)
    lines = [
        "# Tanuki 远程连接 quickstart（--print-only，未启动任何进程）",
        "state_dir={}".format(state_dir),
        "cert={}（{}）".format(cert, "存在" if cert_exists else "不存在，首次运行会生成"),
        "key={}（{}）".format(key, "存在" if key_exists else "不存在，首次运行会生成"),
        "listen={}".format(listen),
        "port={}".format(port),
        "public_endpoint={}".format(endpoint),
        "ssh_target={}:{}".format(ssh_host, ssh_port),
        "pairing_file={}".format(pairing_file),
        "cert_pin={}".format(cert_pin if cert_pin else "（证书不存在，生成后自动计算）"),
    ]
    relay_cmd = shlex.join(relay_command(python, listen, port, cert, key))
    agent_cmd = shlex.join(
        agent_command(
            python,
            "127.0.0.1:{}".format(port),
            ssh_host,
            ssh_port,
            state_dir,
            cert_pin or "sha256-<自动计算>",
            endpoint,
        )
    )
    lines.append("relay_command=" + relay_cmd)
    lines.append("agent_command=" + agent_cmd)
    if Path(pairing_file).is_file():
        try:
            uri, code = read_pairing(pairing_file)
        except QuickstartError as error:
            lines.append("pairing_file_status=无效（{}）".format(error))
        else:
            # 链接含一次性 token；只在用户主动执行的排查输出里显示。
            lines.append("pairing_file_status=存在（可能来自上次运行）")
            lines.append("pairing_code={}".format(code))
            lines.append("pairing_link={}".format(uri))
    else:
        lines.append("pairing_file_status=不存在（启动后生成）")
    return "\n".join(lines)


def _run_relay(args: argparse.Namespace) -> int:
    command = relay_command(args.python, args.listen, args.port, Path(args.cert), Path(args.key))
    os.execvp(command[0], command)
    return 1  # execvp 成功时不会返回


def _run_agent(args: argparse.Namespace) -> int:
    validate_cert_pin(args.cert_pin)
    command = agent_command(
        args.python,
        args.relay,
        args.ssh_host,
        args.ssh_port,
        Path(args.state_dir),
        args.cert_pin,
        args.pairing_relay or "",
    )
    os.execvp(command[0], command)
    return 1


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Tanuki quickstart 辅助工具（由 quickstart.sh 调用）")
    sub = parser.add_subparsers(dest="command", required=True)

    cert = sub.add_parser("cert-pin", help="计算证书 DER SHA-256 钉扎")
    cert.add_argument("--cert", required=True)

    relay = sub.add_parser("run-relay", help="校验并 exec relay.py")
    relay.add_argument("--python", default=sys.executable)
    relay.add_argument("--listen", default="0.0.0.0")
    relay.add_argument("--port", type=int, default=8443)
    relay.add_argument("--cert", required=True)
    relay.add_argument("--key", required=True)

    agent = sub.add_parser("run-agent", help="校验并 exec host_agent.py")
    agent.add_argument("--python", default=sys.executable)
    agent.add_argument("--relay", required=True)
    agent.add_argument("--ssh-host", default="127.0.0.1")
    agent.add_argument("--ssh-port", type=int, default=22)
    agent.add_argument("--state-dir", required=True)
    agent.add_argument("--cert-pin", required=True)
    agent.add_argument("--pairing-relay", default="")

    pairing = sub.add_parser("read-pairing", help="读取配对文件")
    pairing.add_argument("--path", required=True)
    pairing.add_argument("--field", choices=("link", "code"), default="")

    wait = sub.add_parser("wait-port", help="等待端口可连接")
    wait.add_argument("--host", default="127.0.0.1")
    wait.add_argument("--port", type=int, required=True)
    wait.add_argument("--timeout", type=float, default=10.0)

    free = sub.add_parser("port-free", help="检查端口是否空闲")
    free.add_argument("--host", default="127.0.0.1")
    free.add_argument("--port", type=int, required=True)

    endpoint = sub.add_parser("check-endpoint", help="校验手机可达的中继 host:port")
    endpoint.add_argument("--host", required=True)
    endpoint.add_argument("--port", type=int, required=True)

    endpoint_fmt = sub.add_parser("format-endpoint", help="格式化中继 host:port")
    endpoint_fmt.add_argument("--host", required=True)
    endpoint_fmt.add_argument("--port", type=int, required=True)

    sub.add_parser("detect-lan-ip", help="尽力探测本机局域网 IPv4")

    plan = sub.add_parser("plan", help="打印将执行的参数与命令（不启动）")
    plan.add_argument("--python", default=sys.executable)
    plan.add_argument("--state-dir", required=True)
    plan.add_argument("--cert", required=True)
    plan.add_argument("--key", required=True)
    plan.add_argument("--listen", default="0.0.0.0")
    plan.add_argument("--port", type=int, default=8443)
    plan.add_argument("--public-host", default="127.0.0.1")
    plan.add_argument("--ssh-host", default="127.0.0.1")
    plan.add_argument("--ssh-port", type=int, default=22)
    plan.add_argument("--pairing-file", required=True)
    plan.add_argument("--cert-pin", default="")

    args = parser.parse_args(argv)
    try:
        if args.command == "cert-pin":
            print(certificate_pin(Path(args.cert)))
        elif args.command == "run-relay":
            return _run_relay(args)
        elif args.command == "run-agent":
            return _run_agent(args)
        elif args.command == "read-pairing":
            uri, code = read_pairing(Path(args.path))
            if args.field == "link":
                print(uri)
            elif args.field == "code":
                print(code)
            else:
                print("link=" + uri)
                print("code=" + code)
        elif args.command == "wait-port":
            if not wait_port(args.host, args.port, args.timeout):
                raise QuickstartError("端口 {}:{} 在 {} 秒内未进入监听".format(args.host, args.port, args.timeout))
        elif args.command == "port-free":
            if port_in_use(args.host, args.port):
                raise QuickstartError("端口 {}:{} 已被占用".format(args.host, args.port))
        elif args.command == "check-endpoint":
            validate_public_endpoint(args.host, args.port)
        elif args.command == "format-endpoint":
            print(format_endpoint(args.host, args.port))
        elif args.command == "detect-lan-ip":
            print(detect_lan_ip())
        elif args.command == "plan":
            pin = ""
            if args.cert_pin:
                pin = args.cert_pin
            elif Path(args.cert).is_file():
                pin = certificate_pin(Path(args.cert))
            print(
                plan_text(
                    args.python,
                    Path(args.state_dir),
                    Path(args.cert),
                    Path(args.key),
                    args.listen,
                    args.port,
                    args.public_host,
                    args.ssh_host,
                    args.ssh_port,
                    Path(args.pairing_file),
                    pin or None,
                )
            )
    except QuickstartError as error:
        print("错误：{}".format(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

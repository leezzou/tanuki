#!/usr/bin/env python3
"""Tanuki 远程主机端 agent：向中继发起出站 TLS，配对后把字节转发到本机 sshd。

安全边界：
- 不监听任何端口；只主动连接中继与本机回环 sshd；
- 中继连接必须 TLS 1.2+，使用系统 CA 或显式 `--cert-pin`；不存在关闭校验的开关；
- 配对 token 只在内存/0600 配对文件中，不写日志；SSH 凭据不经过本 agent；
- 只转发 TCP 字节，不执行命令、不读取项目文件、不修改 sshd 配置。

手机端仍通过 SSH 完成主机密钥确认、用户认证和本地端口转发；中继与 agent 都不能
替代这些检查。断线后 agent 会重连中继并重新注册同一房间，手机端需重新建立 SSH 会话。
"""
from __future__ import annotations

import argparse
import logging
import os
import socket
import ssl
import threading
import time
from pathlib import Path
from typing import Optional

import protocol as p

MAX_SSHD_BUFFER = 8 * 1024


class PinMismatch(Exception):
    """中继证书与配对链接钉扎不一致；必须立即停止，不重试。"""


def tls_connect(host: str, port: int, pin: str, ca_file: Optional[str], timeout: float) -> ssl.SSLSocket:
    if pin:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE
        context.minimum_version = ssl.TLSVersion.TLSv1_2
    else:
        context = ssl.create_default_context(cafile=ca_file)
        context.check_hostname = True
        context.minimum_version = ssl.TLSVersion.TLSv1_2
    raw = socket.create_connection((host, port), timeout=timeout)
    raw.settimeout(timeout)
    try:
        tls = context.wrap_socket(raw, server_hostname=host)
    except (ssl.SSLError, OSError):
        raw.close()
        raise
    if pin:
        der = tls.getpeercert(binary_form=True)
        if der is None or not p.matches_pin(pin, der):
            tls.close()
            raise PinMismatch("relay certificate pin mismatch")
    return tls


class HostAgent:
    def __init__(self, args) -> None:
        self.args = args
        self._send_lock = threading.Lock()
        self._sshd_lock = threading.Lock()
        self._sshd: Optional[socket.socket] = None
        self._stream: Optional[int] = None
        self._reader: Optional[threading.Thread] = None
        self._reader_stop = threading.Event()
        self._relay: Optional[socket.socket] = None

    def run_forever(self) -> None:
        delay = 1.0
        while True:
            try:
                self._run_once()
                delay = 1.0
            except (OSError, ssl.SSLError, EOFError, p.ProtocolError, p.RelayError, ValueError) as error:
                self._close_sshd()
                logging.warning("relay connection lost (%s); retrying in %.0fs", type(error).__name__, delay)
                time.sleep(delay)
                delay = min(delay * 2, 30.0)

    def _run_once(self) -> None:
        host, port = p.split_endpoint(self.args.relay)
        relay = tls_connect(host, port, self.args.cert_pin or "", self.args.ca_file, 15.0)
        self._relay = relay
        try:
            relay.settimeout(30.0)
            host_nonce = p.new_nonce()
            p.send_control(
                relay,
                "HELLO {} host {} {} {} {}".format(
                    p.VERSION, self.args.room, self.args.host_id, self.args.token, host_nonce
                ),
            )
            challenge = p.read_control(relay)
            if len(challenge) != 2 or challenge[0] != "CHALLENGE":
                raise p.ProtocolError("CHALLENGE 格式无效")
            proof = p.host_proof(self.args.token, self.args.room, self.args.host_id, host_nonce, challenge[1])
            p.send_control(relay, "AUTH " + proof)
            registered = p.read_control(relay)
            if not registered or registered[0] != "REGISTERED":
                raise p.ProtocolError("REGISTERED 格式无效")
            relay.settimeout(None)
            logging.info("registered room %s… via relay", self.args.room[:8])
            self._serve(relay)
        finally:
            self._close_sshd()
            self._relay = None
            try:
                relay.close()
            except OSError:
                pass

    def _send(self, relay: socket.socket, frame_type: int, stream_id: int, payload: bytes = b"") -> None:
        with self._send_lock:
            p.write_frame(relay, frame_type, stream_id, payload)

    def _serve(self, relay: socket.socket) -> None:
        while True:
            frame = p.read_frame(relay)
            if frame is None:
                return
            frame_type, stream_id, payload = frame
            if frame_type == p.OPEN:
                self._open_sshd(relay, stream_id)
            elif frame_type == p.DATA:
                with self._sshd_lock:
                    sshd = self._sshd
                if sshd is not None and self._stream == stream_id:
                    try:
                        sshd.sendall(payload)
                    except OSError:
                        self._close_sshd(notify=True)
            elif frame_type == p.CLOSE:
                if self._stream == stream_id:
                    self._close_sshd(notify=False)
            elif frame_type == p.PING:
                self._send(relay, p.PONG, 0)
            elif frame_type == p.ERROR:
                logging.warning("relay error: %s", payload[:64].decode("utf-8", "replace"))
                return
            elif frame_type in (p.PONG, p.OPEN_OK, p.CONTROL):
                continue
            else:
                return

    def _open_sshd(self, relay: socket.socket, stream_id: int) -> None:
        self._close_sshd(notify=False)
        try:
            sshd = socket.create_connection((self.args.ssh_host, self.args.ssh_port), timeout=5.0)
        except OSError:
            self._send(relay, p.ERROR, 0, b"host_timeout")
            return
        sshd.settimeout(None)
        with self._sshd_lock:
            self._sshd = sshd
            self._stream = stream_id
        self._send(relay, p.OPEN_OK, stream_id)
        self._reader_stop.clear()
        reader = threading.Thread(
            target=self._pump_sshd,
            args=(relay, sshd, stream_id),
            name="tanuki-agent-sshd",
            daemon=True,
        )
        with self._sshd_lock:
            self._reader = reader
        reader.start()

    def _pump_sshd(self, relay: socket.socket, sshd: socket.socket, stream_id: int) -> None:
        try:
            while not self._reader_stop.is_set():
                data = sshd.recv(MAX_SSHD_BUFFER)
                if not data:
                    break
                self._send(relay, p.DATA, stream_id, data)
        except OSError:
            pass
        finally:
            if self._stream == stream_id:
                self._close_sshd(notify=True)

    def _close_sshd(self, notify: bool = False) -> None:
        self._reader_stop.set()
        with self._sshd_lock:
            sshd = self._sshd
            stream = self._stream
            self._sshd = None
            self._stream = None
            self._reader = None
        if sshd is not None:
            try:
                sshd.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            try:
                sshd.close()
            except OSError:
                pass
        if notify and stream is not None and self._relay is not None:
            try:
                self._send(self._relay, p.CLOSE, stream)
            except OSError:
                pass


def load_or_create_host_id(state_dir: Path) -> str:
    state_dir.mkdir(parents=True, exist_ok=True)
    try:
        os.chmod(state_dir, 0o700)
    except OSError:
        pass
    path = state_dir / "host-id"
    if path.is_file():
        text = path.read_text(encoding="utf-8").strip()
        try:
            p.validate_host_id(text)
            return text
        except ValueError:
            pass
    host_id = p.new_host_id()
    path.write_text(host_id + "\n", encoding="utf-8")
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass
    return host_id


def write_pairing(path: Path, uri: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(uri + "\n", encoding="utf-8")
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


def main() -> int:
    parser = argparse.ArgumentParser(description="Tanuki 远程主机端中继 agent")
    parser.add_argument("--relay", required=True, help="中继 host:port（TLS）")
    parser.add_argument("--ssh-host", default="127.0.0.1", help="本机 sshd 地址；默认 127.0.0.1")
    parser.add_argument("--ssh-port", type=int, default=22, help="本机 sshd 端口；默认 22")
    parser.add_argument("--state-dir", default=str(Path.home() / ".tanuki-relay"), help="保存 host-id 与配对文件的目录")
    parser.add_argument("--pairing-out", default="", help="配对链接输出文件；默认 <state-dir>/pairing.txt")
    parser.add_argument("--room", default="", help="已有房间号（默认自动生成）")
    parser.add_argument("--token", default="", help="已有配对密钥（默认自动生成；命令行会进 shell 历史，仅测试用）")
    parser.add_argument("--host-id", default="", help="已有稳定主机标识（默认从 state-dir 读取或生成）")
    parser.add_argument("--cert-pin", default="", help="中继证书钉扎 sha256-...；自签名中继必须提供")
    parser.add_argument("--ca-file", default="", help="使用系统 CA 之外的私有 CA 证书文件")
    parser.add_argument("--print-pairing", action="store_true", help="把配对链接打印到 stdout；链接含一次性密钥")
    args = parser.parse_args()

    host, port = p.split_endpoint(args.relay)
    if not 1 <= port <= 65535:
        parser.error("--relay 端口无效")
    if not 1 <= args.ssh_port <= 65535:
        parser.error("--ssh-port 必须在 1-65535")
    if args.cert_pin and not args.cert_pin.startswith("sha256-"):
        parser.error("--cert-pin 必须以 sha256- 开头")
    if not args.cert_pin and not args.ca_file:
        logging.warning("未提供 --cert-pin/--ca-file；将使用系统 CA 校验中继证书")

    state_dir = Path(args.state_dir).expanduser()
    host_id = args.host_id or load_or_create_host_id(state_dir)
    p.validate_host_id(host_id)
    room = args.room or p.new_room()
    token = args.token or p.new_token()
    p.validate_room(room)
    p.validate_token(token)
    args.room = room
    args.token = token
    args.host_id = host_id

    pairing_path = Path(args.pairing_out).expanduser() if args.pairing_out else state_dir / "pairing.txt"
    uri = p.build_pairing_uri(host, port, room, token, host_id, args.cert_pin)
    code = p.pairing_code(uri)
    write_pairing(pairing_path, uri)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    logging.info("pairing file written: %s", pairing_path)
    # 6 位码只用于人工核对链接，和链接一起输入手机；单独输入无法恢复中继/房间/token。
    logging.info("pairing code: %s (请输入到手机，并与配对链接一起使用)", code)
    logging.info("ssh target: %s:%s", args.ssh_host, args.ssh_port)
    if args.print_pairing:
        print(uri, flush=True)
        print("pairing_code=" + code, flush=True)

    agent = HostAgent(args)
    try:
        agent.run_forever()
    except PinMismatch:
        logging.error("relay certificate pin mismatch; refusing to continue")
        return 2
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""中继参考实现的离线端到端自测（仅回环、合成证书与合成字节，不访问外网）。

覆盖：
- 共享 HMAC 向量与 Kotlin/Rust 无关的线协议一致性；
- 自签名 TLS + 证书钉扎；
- 主机端注册、客户端配对、OPEN/OPEN_OK、DATA 回声；
- 慢消费者下 2 MiB 完整传输（验证中继使用背压而非有损队列）；
- 错误 token、未知房间、明文连接拒绝；
- 中继输出不包含配对 token。

需要本机 openssl 生成临时自签名证书；缺少时明确跳过，不伪装通过。
"""
from __future__ import annotations

import json
import os
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from typing import Optional, Tuple

import protocol as p

ROOT = Path(__file__).resolve().parents[2]
VECTORS = ROOT / "app/src/test/resources/relay-protocol-vectors.json"
OPENSSL = shutil.which("openssl")
SLOW_CONSUMER_BYTES = 2 * 1024 * 1024


class EchoServer:
    """最小回显服务，冒充本机 sshd；只用于证明字节通道可双向透传。"""

    def __init__(self) -> None:
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.socket.bind(("127.0.0.1", 0))
        self.socket.listen(8)
        self.port = self.socket.getsockname()[1]
        self._stop = threading.Event()
        self._threads = []
        self._thread = threading.Thread(target=self._accept, daemon=True)
        self._thread.start()

    def _accept(self) -> None:
        while not self._stop.is_set():
            try:
                conn, _ = self.socket.accept()
            except OSError:
                return
            worker = threading.Thread(target=self._echo, args=(conn,), daemon=True)
            worker.start()
            self._threads.append(worker)

    @staticmethod
    def _echo(conn: socket.socket) -> None:
        try:
            while True:
                data = conn.recv(8192)
                if not data:
                    return
                if data == b"BIG\n":
                    # 模拟主机快速返回大块 DSH 资源；客户端稍后才读取，验证中继背压。
                    conn.sendall(b"x" * SLOW_CONSUMER_BYTES)
                else:
                    conn.sendall(data)
        except OSError:
            return
        finally:
            try:
                conn.close()
            except OSError:
                pass

    def close(self) -> None:
        self._stop.set()
        try:
            self.socket.close()
        except OSError:
            pass


def free_port() -> int:
    probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]
    finally:
        probe.close()


def generate_cert(directory: Path) -> Tuple[Path, Path]:
    cert = directory / "relay-cert.pem"
    key = directory / "relay-key.pem"
    config = directory / "openssl.cnf"
    config.write_text(
        "[req]\n"
        "distinguished_name=dn\n"
        "x509_extensions=v3\n"
        "prompt=no\n"
        "[dn]\n"
        "CN=localhost\n"
        "[v3]\n"
        "subjectAltName=DNS:localhost,IP:127.0.0.1\n",
        encoding="utf-8",
    )
    subprocess.run(
        [
            OPENSSL,
            "req",
            "-x509",
            "-newkey",
            "ec",
            "-pkeyopt",
            "ec_paramgen_curve:prime256v1",
            "-nodes",
            "-days",
            "1",
            "-config",
            str(config),
            "-keyout",
            str(key),
            "-out",
            str(cert),
        ],
        check=True,
        capture_output=True,
    )
    os.chmod(key, 0o600)
    return cert, key


def cert_pin(cert: Path) -> str:
    pem = cert.read_text(encoding="utf-8")
    return p.cert_pin(ssl.PEM_cert_to_DER_cert(pem))


def wait_port(port: int, seconds: float = 8.0) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("relay port did not become ready")


def wait_file(path: Path, seconds: float = 8.0) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if path.is_file() and path.stat().st_size > 0:
            return
        time.sleep(0.1)
    raise RuntimeError("pairing file was not created")


def tls_connect(pairing, timeout: float = 5.0) -> ssl.SSLSocket:
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    raw = socket.create_connection((pairing["relay_host"], pairing["relay_port"]), timeout=timeout)
    tls = context.wrap_socket(raw, server_hostname=pairing["relay_host"])
    der = tls.getpeercert(binary_form=True)
    if der is None or not p.matches_pin(pairing["pin"], der):
        tls.close()
        raise AssertionError("relay certificate pin mismatch")
    tls.settimeout(timeout)
    return tls


def join_client(pairing, token: str) -> Tuple[ssl.SSLSocket, int]:
    tls = tls_connect(pairing)
    try:
        nonce = p.new_nonce()
        p.send_control(tls, "HELLO {} client {} - {}".format(p.VERSION, pairing["room"], nonce))
        challenge = p.read_control(tls)
        if len(challenge) != 2 or challenge[0] != "CHALLENGE":
            raise AssertionError("bad challenge")
        proof = p.client_proof(token, pairing["room"], nonce, challenge[1])
        p.send_control(tls, "AUTH " + proof)
        queued = p.read_control(tls)
        if queued != ["QUEUED"]:
            raise AssertionError("not queued: {}".format(queued))
        while True:
            frame = p.read_frame(tls)
            if frame is None:
                raise AssertionError("relay closed before OPEN_OK")
            frame_type, stream_id, payload = frame
            if frame_type == p.ERROR:
                raise p.RelayError(payload.decode("utf-8", "replace")[:64])
            if frame_type == p.OPEN_OK:
                tls.settimeout(5.0)
                return tls, stream_id
            if frame_type == p.PING:
                p.write_frame(tls, p.PONG, 0)
                continue
            raise AssertionError("unexpected frame before OPEN_OK: {}".format(frame_type))
    except BaseException:
        tls.close()
        raise


def wait_until_registered(pairing, seconds: float = 8.0) -> None:
    """主机端先写配对文件再连中继；反复尝试 JOIN，直到房间注册完成。"""
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            tls, stream_id = join_client(pairing, str(pairing["token"]))
            try:
                p.write_frame(tls, p.CLOSE, stream_id)
            finally:
                tls.close()
            return
        except p.RelayError as error:
            if error.code not in ("room_not_found", "host_offline", "room_busy"):
                raise
            time.sleep(0.2)
    raise RuntimeError("host agent did not register in time")


def read_exact(sock: socket.socket, size: int) -> bytes:
    chunks = []
    remaining = size
    while remaining > 0:
        chunk = sock.recv(remaining)
        if not chunk:
            raise EOFError("unexpected EOF")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


class RelayReferenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        if OPENSSL is None:
            raise unittest.SkipTest("openssl not available")
        cls.temp = tempfile.TemporaryDirectory(prefix="tanuki-relay-test-")
        cls.root = Path(cls.temp.name)
        cls.cert, cls.key = generate_cert(cls.root)
        cls.pin = cert_pin(cls.cert)
        cls.echo = EchoServer()
        cls.relay_port = free_port()
        cls.relay = subprocess.Popen(
            [
                sys.executable,
                str(Path(__file__).resolve().parent / "relay.py"),
                "--listen",
                "127.0.0.1",
                "--port",
                str(cls.relay_port),
                "--cert",
                str(cls.cert),
                "--key",
                str(cls.key),
                "--room-ttl",
                "60",
                "--max-rooms",
                "8",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        wait_port(cls.relay_port)
        cls.state = cls.root / "state"
        cls.pairing_path = cls.root / "pairing.txt"
        cls.agent = subprocess.Popen(
            [
                sys.executable,
                str(Path(__file__).resolve().parent / "host_agent.py"),
                "--relay",
                "127.0.0.1:{}".format(cls.relay_port),
                "--ssh-host",
                "127.0.0.1",
                "--ssh-port",
                str(cls.echo.port),
                "--cert-pin",
                cls.pin,
                "--state-dir",
                str(cls.state),
                "--pairing-out",
                str(cls.pairing_path),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        wait_file(cls.pairing_path)
        cls.pairing = p.parse_pairing_uri(cls.pairing_path.read_text(encoding="utf-8"))
        wait_until_registered(cls.pairing)

    @classmethod
    def tearDownClass(cls) -> None:
        for process in (cls.agent, cls.relay):
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
        relay_out = ""
        relay_err = ""
        agent_out = ""
        agent_err = ""
        if cls.relay.stdout is not None:
            relay_out = cls.relay.stdout.read()
        if cls.relay.stderr is not None:
            relay_err = cls.relay.stderr.read()
        if cls.agent.stdout is not None:
            agent_out = cls.agent.stdout.read()
        if cls.agent.stderr is not None:
            agent_err = cls.agent.stderr.read()
        for stream in (cls.agent.stdout, cls.agent.stderr, cls.relay.stdout, cls.relay.stderr):
            if stream is not None:
                stream.close()
        token = str(cls.pairing["token"])
        if token in relay_out or token in relay_err:
            raise AssertionError("relay logs leaked pairing token")
        if token in agent_err:
            raise AssertionError("host agent stderr leaked pairing token")
        cls.echo.close()
        cls.temp.cleanup()

    def test_shared_hmac_vectors_match_kotlin_fixture(self) -> None:
        vectors = json.loads(VECTORS.read_text(encoding="utf-8"))
        self.assertEqual(
            vectors["clientProof"],
            p.client_proof(vectors["token"], vectors["room"], vectors["clientNonce"], vectors["serverNonce"]),
        )
        self.assertEqual(
            vectors["hostProof"],
            p.host_proof(vectors["token"], vectors["room"], vectors["hostId"], vectors["hostNonce"], vectors["serverNonce"]),
        )

    def test_pairing_code_matches_shared_vector(self) -> None:
        uri = (
            "tanuki-relay://v1?relay=relay.example.test%3A8443"
            "&room=ICEiIyQlJicoKSorLC0uLw"
            "&token=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
            "&host=303132333435363738393a3b3c3d3e3f"
        )
        self.assertEqual("308002", p.pairing_code(uri))
        self.assertEqual(6, len(p.pairing_code(uri)))

    def test_pairing_uri_round_trips_with_pin(self) -> None:
        uri = p.build_pairing_uri(
            "127.0.0.1", self.relay_port, self.pairing["room"], self.pairing["token"], self.pairing["host_id"], self.pin
        )
        parsed = p.parse_pairing_uri(uri)
        self.assertEqual(parsed["pin"], self.pin)
        self.assertEqual(parsed["room"], self.pairing["room"])
        self.assertEqual(parsed["relay_port"], self.relay_port)

    def test_client_joins_and_passes_bytes_end_to_end(self) -> None:
        tls, stream_id = join_client(self.pairing, str(self.pairing["token"]))
        try:
            payload = b"SSH-2.0-synthetic\r\n"
            p.write_frame(tls, p.DATA, stream_id, payload)
            frame = p.read_frame(tls)
            self.assertIsNotNone(frame)
            frame_type, read_stream, echoed = frame
            self.assertEqual(p.DATA, frame_type)
            self.assertEqual(stream_id, read_stream)
            self.assertEqual(payload, echoed)
        finally:
            try:
                p.write_frame(tls, p.CLOSE, stream_id)
            except OSError:
                pass
            tls.close()

    def test_slow_consumer_receives_large_transfer_without_disconnect(self) -> None:
        tls, stream_id = join_client(self.pairing, str(self.pairing["token"]))
        expected = b"x" * SLOW_CONSUMER_BYTES
        try:
            p.write_frame(tls, p.DATA, stream_id, b"BIG\n")
            # 先不读，让中继的下行缓冲被填满；旧实现会在 32 帧队列满时直接断开。
            time.sleep(1.0)
            received = bytearray()
            deadline = time.monotonic() + 30
            while len(received) < len(expected) and time.monotonic() < deadline:
                frame = p.read_frame(tls)
                if frame is None:
                    self.fail("relay closed the slow consumer before transfer completed")
                frame_type, read_stream, payload = frame
                if frame_type == p.DATA:
                    self.assertEqual(stream_id, read_stream)
                    received.extend(payload)
                elif frame_type == p.PING:
                    p.write_frame(tls, p.PONG, 0)
                elif frame_type == p.CLOSE:
                    self.fail("relay sent CLOSE to slow consumer")
                elif frame_type == p.ERROR:
                    self.fail("relay error: {}".format(payload[:64].decode("utf-8", "replace")))
            self.assertEqual(len(expected), len(received))
            self.assertEqual(expected, bytes(received))
        finally:
            try:
                p.write_frame(tls, p.CLOSE, stream_id)
            except OSError:
                pass
            tls.close()

    def test_wrong_token_is_rejected(self) -> None:
        with self.assertRaises(p.RelayError) as error:
            join_client(self.pairing, p.new_token())
        self.assertEqual("bad_token", error.exception.code)

    def test_unknown_room_is_rejected(self) -> None:
        other = dict(self.pairing)
        other["room"] = p.new_room()
        with self.assertRaises(p.RelayError) as error:
            join_client(other, str(self.pairing["token"]))
        self.assertEqual("room_not_found", error.exception.code)

    def test_plaintext_connection_does_not_get_a_protocol_response(self) -> None:
        raw = socket.create_connection(("127.0.0.1", self.relay_port), timeout=3)
        try:
            p.write_frame(raw, p.CONTROL, 0, b"HELLO 1 client " + str(self.pairing["room"]).encode() + b" - x")
            raw.settimeout(2)
            try:
                frame = p.read_frame(raw)
                self.assertIsNone(frame)
            except (EOFError, OSError, p.ProtocolError):
                pass
        finally:
            raw.close()


if __name__ == "__main__":
    unittest.main(verbosity=2)

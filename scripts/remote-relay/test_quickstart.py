#!/usr/bin/env python3
"""quickstart 一键启动脚本的离线自测。

不依赖真实网络、真实手机或用户目录：
- 用临时自签名证书验证钉扎计算（DER SHA-256 base64url）；
- 验证 relay/agent 命令拼装、配对文件解析和端口探测；
- 用回环端口真实启动 quickstart，验证 SIGINT 后 relay 与 agent 都被清理；
- 验证 --print-only 不启动进程、端口被占用时快速失败。

openssl 不可用时整组跳过并明确报告，不伪装通过。
"""
from __future__ import annotations

import os
import shutil
import signal
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from typing import Callable, Dict, Optional, Tuple

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import protocol as p  # noqa: E402
import quickstart_lib as q  # noqa: E402

QUICKSTART = HERE / "quickstart.sh"
GEN_CERT = HERE / "gen-cert.sh"
OPENSSL = shutil.which("openssl")
BASH = shutil.which("bash")


def free_port() -> int:
    probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]
    finally:
        probe.close()


def wait_until(predicate: Callable[[], bool], timeout: float = 10.0, interval: float = 0.1) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(interval)
    return predicate()


def pid_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def read_text_safe(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def generate_cert(directory: Path) -> Tuple[Path, Path]:
    subprocess.run(
        [BASH, str(GEN_CERT), str(directory)],
        check=True,
        capture_output=True,
        timeout=30,
    )
    return directory / "relay-cert.pem", directory / "relay-key.pem"


class QuickstartUnitTest(unittest.TestCase):
    """纯逻辑与命令行拼装，不启动 quickstart 主流程。"""

    @classmethod
    def setUpClass(cls) -> None:
        if OPENSSL is None:
            raise unittest.SkipTest("openssl not available")
        if BASH is None:
            raise unittest.SkipTest("bash not available")
        cls.temp = tempfile.TemporaryDirectory(prefix="tanuki-quickstart-unit-")
        cls.root = Path(cls.temp.name)
        cls.cert, cls.key = generate_cert(cls.root / "cert-a")
        cls.other_cert, cls.other_key = generate_cert(cls.root / "cert-b")
        cls.pin = q.certificate_pin(cls.cert)

    @classmethod
    def tearDownClass(cls) -> None:
        cls.temp.cleanup()

    def test_cert_pin_matches_protocol_and_format(self) -> None:
        expected = p.cert_pin(ssl.PEM_cert_to_DER_cert(self.cert.read_text(encoding="utf-8")))
        self.assertEqual(expected, self.pin)
        self.assertTrue(self.pin.startswith("sha256-"))
        self.assertEqual(50, len(self.pin))
        q.validate_cert_pin(self.pin)

    def test_cert_pin_changes_for_different_certificate(self) -> None:
        self.assertNotEqual(self.pin, q.certificate_pin(self.other_cert))

    def test_cert_pin_rejects_invalid_or_missing_file(self) -> None:
        broken = self.root / "broken.pem"
        broken.write_text("not a certificate\n", encoding="utf-8")
        with self.assertRaises(q.QuickstartError):
            q.certificate_pin(broken)
        with self.assertRaises(q.QuickstartError):
            q.certificate_pin(self.root / "missing.pem")

    def test_relay_command_contains_required_flags(self) -> None:
        command = q.relay_command(sys.executable, "0.0.0.0", 8443, self.cert, self.key)
        self.assertEqual(
            [
                sys.executable,
                str(HERE / "relay.py"),
                "--listen",
                "0.0.0.0",
                "--port",
                "8443",
                "--cert",
                str(self.cert),
                "--key",
                str(self.key),
            ],
            command,
        )

    def test_relay_command_rejects_invalid_port(self) -> None:
        with self.assertRaises(q.QuickstartError):
            q.relay_command(sys.executable, "0.0.0.0", 0, self.cert, self.key)
        with self.assertRaises(q.QuickstartError):
            q.relay_command(sys.executable, "0.0.0.0", 70000, self.cert, self.key)

    def test_agent_command_includes_pin_and_pairing_relay(self) -> None:
        command = q.agent_command(
            sys.executable,
            "127.0.0.1:8443",
            "127.0.0.1",
            22,
            self.root / "state",
            self.pin,
            "relay.example.test:9443",
        )
        self.assertEqual(self.pin, command[command.index("--cert-pin") + 1])
        self.assertEqual("127.0.0.1:8443", command[command.index("--relay") + 1])
        self.assertEqual("relay.example.test:9443", command[command.index("--pairing-relay") + 1])
        self.assertIn("--state-dir", command)

    def test_agent_command_rejects_missing_pin(self) -> None:
        with self.assertRaises(q.QuickstartError):
            q.agent_command(sys.executable, "127.0.0.1:8443", "127.0.0.1", 22, self.root, "")
        with self.assertRaises(q.QuickstartError):
            q.agent_command(sys.executable, "127.0.0.1:8443", "127.0.0.1", 22, self.root, "sha1-xxx")

    def test_agent_command_rejects_invalid_endpoints(self) -> None:
        with self.assertRaises(q.QuickstartError):
            q.agent_command(sys.executable, "no-port", "127.0.0.1", 22, self.root, self.pin)
        with self.assertRaises(q.QuickstartError):
            q.agent_command(
                sys.executable,
                "127.0.0.1:8443",
                "127.0.0.1",
                22,
                self.root,
                self.pin,
                "bad host!:8443",
            )

    def test_format_endpoint_brackets_ipv6(self) -> None:
        self.assertEqual("[2001:db8::1]:8443", q.format_endpoint("2001:db8::1", 8443))
        self.assertEqual("[2001:db8::1]:8443", q.format_endpoint("[2001:db8::1]", 8443))
        self.assertEqual("relay.example.test:8443", q.format_endpoint("relay.example.test", 8443))

    def test_validate_public_endpoint_rejects_bad_host(self) -> None:
        q.validate_public_endpoint("relay.example.test", 8443)
        with self.assertRaises(q.QuickstartError):
            q.validate_public_endpoint("bad host!", 8443)

    def test_read_pairing_returns_link_and_code(self) -> None:
        uri = p.build_pairing_uri(
            "127.0.0.1", 8443, p.new_room(), p.new_token(), p.new_host_id(), self.pin
        )
        path = self.root / "pairing.txt"
        path.write_text(uri + "\n", encoding="utf-8")
        link, code = q.read_pairing(path)
        self.assertEqual(uri, link)
        self.assertEqual(p.pairing_code(uri), code)
        self.assertEqual(6, len(code))

    def test_read_pairing_rejects_empty_or_garbage(self) -> None:
        path = self.root / "pairing-bad.txt"
        path.write_text("", encoding="utf-8")
        with self.assertRaises(q.QuickstartError):
            q.read_pairing(path)
        path.write_text("tanuki-relay://v1?relay=only\n", encoding="utf-8")
        with self.assertRaises(q.QuickstartError):
            q.read_pairing(path)
        with self.assertRaises(q.QuickstartError):
            q.read_pairing(self.root / "pairing-missing.txt")

    def test_port_detection_and_wait(self) -> None:
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        port = listener.getsockname()[1]
        try:
            self.assertTrue(q.port_in_use("127.0.0.1", port))
            self.assertTrue(q.wait_port("127.0.0.1", port, timeout=1.0))
            # 通配监听地址应被映射为回环探测地址。
            self.assertTrue(q.port_in_use("0.0.0.0", port))
        finally:
            listener.close()
        self.assertTrue(wait_until(lambda: not q.port_in_use("127.0.0.1", port, timeout=0.1), 2.0))

    def test_detect_lan_ip_uses_first_usable_ipv4(self) -> None:
        def fake_run(args, **kwargs):
            if args == ["ipconfig", "getifaddr", "en0"]:
                return SimpleNamespace(returncode=0, stdout="192.0.2.10\n")
            return SimpleNamespace(returncode=1, stdout="")

        self.assertEqual("192.0.2.10", q.detect_lan_ip(fake_run))

        def loopback_run(args, **kwargs):
            return SimpleNamespace(returncode=0, stdout="127.0.0.1\n::1\n")

        self.assertEqual("", q.detect_lan_ip(loopback_run))

        def broken_run(args, **kwargs):
            raise OSError("command missing")

        self.assertEqual("", q.detect_lan_ip(broken_run))

    def test_plan_text_contains_commands_and_pin(self) -> None:
        text = q.plan_text(
            sys.executable,
            self.root / "state",
            self.cert,
            self.key,
            "0.0.0.0",
            8443,
            "relay.example.test",
            "127.0.0.1",
            22,
            self.root / "state" / "pairing.txt",
            self.pin,
        )
        self.assertIn("cert_pin=" + self.pin, text)
        self.assertIn("relay_command=", text)
        self.assertIn("agent_command=", text)
        self.assertIn("--pairing-relay relay.example.test:8443", text)
        self.assertIn("pairing_file_status=不存在", text)


class HostAgentPairingTest(unittest.TestCase):
    """--pairing-relay 让连接地址与手机可达地址解耦（quickstart 依赖该行为）。"""

    def test_pairing_relay_overrides_advertised_endpoint(self) -> None:
        with tempfile.TemporaryDirectory(prefix="tanuki-agent-pairing-") as tmp:
            root = Path(tmp)
            state = root / "state"
            pairing = state / "pairing.txt"
            pin = p.cert_pin(b"synthetic-der-for-pairing-test")
            proc = subprocess.Popen(
                [
                    sys.executable,
                    str(HERE / "host_agent.py"),
                    "--relay",
                    "127.0.0.1:1",
                    "--pairing-relay",
                    "203.0.113.9:9443",
                    "--state-dir",
                    str(state),
                    "--pairing-out",
                    str(pairing),
                    "--cert-pin",
                    pin,
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            try:
                self.assertTrue(wait_until(lambda: pairing.is_file() and pairing.stat().st_size > 0, 8.0))
                parsed = p.parse_pairing_uri(pairing.read_text(encoding="utf-8"))
                self.assertEqual("203.0.113.9", parsed["relay_host"])
                self.assertEqual(9443, parsed["relay_port"])
                self.assertEqual(pin, parsed["pin"])
            finally:
                proc.terminate()
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=5)


class QuickstartProcessTest(unittest.TestCase):
    """真实启动 quickstart（仅回环端口），验证横幅、SIGINT 清理与快速失败。"""

    @classmethod
    def setUpClass(cls) -> None:
        if OPENSSL is None:
            raise unittest.SkipTest("openssl not available")
        if BASH is None:
            raise unittest.SkipTest("bash not available")
        cls.temp = tempfile.TemporaryDirectory(prefix="tanuki-quickstart-process-")
        cls.root = Path(cls.temp.name)

    @classmethod
    def tearDownClass(cls) -> None:
        cls.temp.cleanup()

    def _quickstart_args(self, state: Path, port: int, extra: Optional[list] = None) -> list:
        args = [
            BASH,
            str(QUICKSTART),
            "--state-dir",
            str(state),
            "--port",
            str(port),
            "--listen",
            "127.0.0.1",
            "--public-host",
            "127.0.0.1",
            "--ssh-port",
            str(free_port()),
            "--no-clipboard",
            "--ready-timeout",
            "10",
        ]
        if extra:
            args.extend(extra)
        return args

    def test_banner_and_sigint_stop_both_children(self) -> None:
        state = self.root / "run-state"
        port = free_port()
        stdout_path = self.root / "run-stdout.log"
        stderr_path = self.root / "run-stderr.log"
        proc = None
        with stdout_path.open("w", encoding="utf-8") as out, stderr_path.open("w", encoding="utf-8") as err:
            proc = subprocess.Popen(
                self._quickstart_args(state, port),
                stdout=out,
                stderr=err,
                start_new_session=True,
            )
            try:
                self.assertTrue(
                    wait_until(lambda: "远程连接已就绪" in read_text_safe(stdout_path), 25.0),
                    "quickstart banner did not appear",
                )
                pairing_path = state / "pairing.txt"
                pids_path = state / "quickstart.pids"
                self.assertTrue(pairing_path.is_file())
                self.assertTrue(pids_path.is_file())
                link, code = q.read_pairing(pairing_path)
                parsed = p.parse_pairing_uri(link)
                self.assertEqual("127.0.0.1", parsed["relay_host"])
                self.assertEqual(port, parsed["relay_port"])
                self.assertEqual(q.certificate_pin(state / "relay-cert.pem"), parsed["pin"])
                self.assertIn("pin=sha256-", link)

                stdout_text = read_text_safe(stdout_path)
                self.assertIn(link, stdout_text)
                self.assertIn(code, stdout_text)
                self.assertIn("手机端下一步", stdout_text)
                self.assertIn("Ctrl+C", stdout_text)

                pids: Dict[str, int] = {}
                for line in pids_path.read_text(encoding="utf-8").splitlines():
                    key, _, value = line.partition("=")
                    if key in ("relay", "agent") and value.isdigit():
                        pids[key] = int(value)
                self.assertEqual({"relay", "agent"}, set(pids))
                self.assertTrue(all(pid_alive(pid) for pid in pids.values()))

                proc.send_signal(signal.SIGINT)
                proc.wait(timeout=20)
                self.assertEqual(130, proc.returncode)
                self.assertTrue(
                    wait_until(lambda: all(not pid_alive(pid) for pid in pids.values()), 5.0),
                    "child processes still alive after SIGINT",
                )
                self.assertTrue(
                    wait_until(lambda: not q.port_in_use("127.0.0.1", port, timeout=0.1), 5.0),
                    "relay port still open after SIGINT",
                )
                self.assertFalse(pids_path.exists())
                self.assertIn("已停止", read_text_safe(stderr_path))
            finally:
                if proc.poll() is None:
                    proc.kill()
                    proc.wait(timeout=5)

    def test_print_only_does_not_start_or_create_state(self) -> None:
        state = self.root / "print-only-state"
        port = free_port()
        completed = subprocess.run(
            self._quickstart_args(state, port, ["--print-only"]),
            capture_output=True,
            text=True,
            timeout=30,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("cert_pin=", completed.stdout)
        self.assertIn("relay_command=", completed.stdout)
        self.assertIn("agent_command=", completed.stdout)
        self.assertIn("--cert-pin", completed.stdout)
        self.assertFalse(state.exists(), "--print-only must not create the state directory")
        self.assertFalse(q.port_in_use("127.0.0.1", port, timeout=0.2))

    def test_port_in_use_fails_without_creating_state(self) -> None:
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        port = listener.getsockname()[1]
        state = self.root / "busy-state"
        try:
            completed = subprocess.run(
                self._quickstart_args(state, port),
                capture_output=True,
                text=True,
                timeout=30,
            )
        finally:
            listener.close()
        self.assertEqual(1, completed.returncode)
        self.assertIn("已被占用", completed.stderr)
        self.assertFalse(state.exists(), "port conflict must fail before creating state")
        self.assertNotIn("启动中继", completed.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)

#!/usr/bin/env python3
"""discovery.py 离线自测：名称清洗、TXT、mDNS 编解码和系统工具选择。

不依赖真实网络：报文用固定十六进制向量，系统命令用假进程/假 responder，
不绑定 5353、不启动 avahi/dns-sd。
"""
from __future__ import annotations

import io
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import discovery as d  # noqa: E402
import protocol as proto  # noqa: E402

HOST_ID = "00112233445566778899aabbccddeeff"
FINGERPRINT = "0123456789abcdef"
QUERY_HEX = (
    "0000000000010000000000000e5f74616e756b692d72656d6f7465045f746370"
    "056c6f63616c00000c0001"
)
RESPONSE_HEX = (
    "0000840000000001000000030e5f74616e756b692d72656d6f7465045f746370"
    "056c6f63616c00000c00010000007800270b54616e756b6920486f73740e5f74"
    "616e756b692d72656d6f7465045f746370056c6f63616c000b54616e756b6920"
    "486f73740e5f74616e756b692d72656d6f7465045f746370056c6f63616c0000"
    "2180010000007800190000000000160b74616e756b692d686f7374056c6f6361"
    "6c000b54616e756b6920486f73740e5f74616e756b692d72656d6f7465045f74"
    "6370056c6f63616c0000108001000000780061106e616d653d54616e756b6920"
    "486f7374067373683d3232086473683d3330383025686f73743d303031313232"
    "3333343435353636373738383939616162626363646465656666057665723d31"
    "1366703d303132333435363738396162636465660b74616e756b692d686f7374"
    "056c6f63616c0000018001000000780004c000020a"
)


class NameTest(unittest.TestCase):
    def test_sanitize_removes_control_and_dns_breaking_characters(self) -> None:
        self.assertEqual("My Host--local", d.sanitize_name("  My\nHost..local  "))
        self.assertEqual("我的 Mac-local", d.sanitize_name("我的 Mac。local"))
        self.assertEqual("tanuki-host", d.sanitize_name("\n\t"))
        self.assertEqual("a-b", d.sanitize_name("a/b"))

    def test_sanitize_limits_utf8_bytes_without_splitting_characters(self) -> None:
        cleaned = d.sanitize_name("汉" * 100)
        self.assertLessEqual(len(cleaned.encode("utf-8")), d.MAX_LABEL_BYTES)
        self.assertTrue(cleaned)
        self.assertEqual(cleaned, cleaned.encode("utf-8").decode("utf-8"))

    def test_default_display_name_uses_first_hostname_label(self) -> None:
        self.assertEqual("MacBook-Pro", d.default_display_name("MacBook-Pro.local"))
        self.assertEqual("host", d.default_display_name("host.example.com"))
        self.assertEqual("我的-Mac", d.default_host_label("我的 Mac.local"))

    def test_dns_host_label_falls_back_for_non_ascii(self) -> None:
        self.assertEqual("MacBook-Pro", d.dns_host_label(HOST_ID, "MacBook-Pro.local"))
        self.assertEqual("tanuki-00112233", d.dns_host_label(HOST_ID, "我的 Mac.local"))

    def test_fingerprint_is_short_stable_digest(self) -> None:
        self.assertEqual("5947d7c33d783f94", d.fingerprint_digest(HOST_ID))
        self.assertEqual(16, len(d.fingerprint_digest(HOST_ID)))
        self.assertNotEqual(d.fingerprint_digest(HOST_ID), HOST_ID)


class TxtTest(unittest.TestCase):
    def test_txt_contains_required_fields_and_never_token(self) -> None:
        records = d.txt_records("Tanuki Host", 22, 3080, HOST_ID, FINGERPRINT)
        self.assertEqual(
            [
                "name=Tanuki Host",
                "ssh=22",
                "dsh=3080",
                "host=" + HOST_ID,
                "ver=1",
                "fp=" + FINGERPRINT,
            ],
            records,
        )
        self.assertFalse(any("token" in record.lower() for record in records))
        self.assertEqual("Tanuki Host", d.txt_map(records)["name"])

    def test_txt_round_trips_through_wire_format(self) -> None:
        records = d.txt_records("我的 Mac", 2222, 3080, HOST_ID, FINGERPRINT)
        self.assertEqual(records, d.parse_txt(d.encode_txt(records)))

    def test_txt_rejects_invalid_values(self) -> None:
        with self.assertRaises(d.DiscoveryError):
            d.txt_records("host", 0, 3080, HOST_ID, FINGERPRINT)
        with self.assertRaises(d.DiscoveryError):
            d.txt_records("host", 22, 70000, HOST_ID, FINGERPRINT)
        with self.assertRaises(d.DiscoveryError):
            d.txt_records("host", 22, 3080, HOST_ID, "not-a-fingerprint")
        with self.assertRaises(d.DiscoveryError):
            d.txt_map(["broken"])


class PacketTest(unittest.TestCase):
    def test_fixed_query_vector_parses(self) -> None:
        questions = d.parse_query(bytes.fromhex(QUERY_HEX))
        self.assertEqual(1, len(questions))
        self.assertEqual(d.SERVICE_TYPE, questions[0].name)
        self.assertEqual(d.TYPE_PTR, questions[0].qtype)
        self.assertEqual(d.CLASS_IN, questions[0].qclass)

    def test_build_query_matches_fixed_vector(self) -> None:
        self.assertEqual(QUERY_HEX, d.build_query(d.SERVICE_TYPE, d.TYPE_PTR).hex())

    def test_fixed_response_vector_parses(self) -> None:
        records = d.parse_response(bytes.fromhex(RESPONSE_HEX))
        self.assertEqual(4, len(records))
        by_type = {record.rtype: record for record in records}
        self.assertEqual(
            "Tanuki Host." + d.SERVICE_TYPE, d.parse_ptr_rdata(by_type[d.TYPE_PTR].rdata)
        )
        _priority, _weight, port, target = d.parse_srv_rdata(by_type[d.TYPE_SRV].rdata)
        self.assertEqual(22, port)
        self.assertEqual("tanuki-host.local", target)
        txt = d.txt_map(d.parse_txt(by_type[d.TYPE_TXT].rdata))
        self.assertEqual("Tanuki Host", txt["name"])
        self.assertEqual("3080", txt["dsh"])
        self.assertEqual(HOST_ID, txt["host"])
        self.assertEqual(FINGERPRINT, txt["fp"])
        self.assertEqual("192.0.2.10", d.parse_a_rdata(by_type[d.TYPE_A].rdata))

    def test_build_response_matches_fixed_vector(self) -> None:
        response = d.build_response(
            "Tanuki Host",
            HOST_ID,
            22,
            3080,
            FINGERPRINT,
            ["192.0.2.10"],
            [d.Question(d.SERVICE_TYPE, d.TYPE_PTR, d.CLASS_IN)],
            target="tanuki-host",
        )
        self.assertEqual(RESPONSE_HEX, response.hex())

    def test_response_ignores_unrelated_queries(self) -> None:
        response = d.build_response(
            "Tanuki Host",
            HOST_ID,
            22,
            3080,
            FINGERPRINT,
            ["192.0.2.10"],
            [d.Question("_http._tcp.local", d.TYPE_PTR, d.CLASS_IN)],
            target="tanuki-host",
        )
        self.assertEqual(b"", response)

    def test_compressed_name_pointer_decodes(self) -> None:
        packet = (
            struct.pack(">HHHHHH", 0, 0, 2, 0, 0, 0)
            + d.encode_name(d.SERVICE_TYPE)
            + struct.pack(">HH", d.TYPE_PTR, d.CLASS_IN)
            + b"\xc0\x0c"
            + struct.pack(">HH", d.TYPE_TXT, d.CLASS_IN)
        )
        questions = d.parse_query(packet)
        self.assertEqual([d.SERVICE_TYPE, d.SERVICE_TYPE], [question.name for question in questions])

    def test_response_flag_is_not_treated_as_query(self) -> None:
        self.assertEqual([], d.parse_query(bytes.fromhex(RESPONSE_HEX)))


class FakeProcess:
    def __init__(self, command, returncode=None, stderr_text="", **_kwargs) -> None:
        self.command = command
        self._returncode = returncode
        self.stderr = io.StringIO(stderr_text)
        self.terminated = False
        self.killed = False

    def poll(self):
        return self._returncode

    def terminate(self) -> None:
        self.terminated = True
        self._returncode = 0

    def wait(self, timeout=None):
        return self._returncode

    def kill(self) -> None:
        self.killed = True
        self._returncode = -9


class FakeResponder:
    def __init__(self, *args) -> None:
        self.args = args
        self.started = False
        self.stopped = False

    def start(self) -> None:
        self.started = True

    def stop(self) -> None:
        self.stopped = True


class AdvertiserTest(unittest.TestCase):
    def test_system_commands_are_exact_and_shell_free(self) -> None:
        txt = d.txt_records("Tanuki Host", 22, 3080, HOST_ID, FINGERPRINT)
        self.assertEqual(
            ["avahi-publish-service", "--no-reverse", "Tanuki Host", d.SERVICE_LABEL, "22"] + txt,
            d.system_publish_command("avahi-publish-service", "Tanuki Host", 22, txt),
        )
        self.assertEqual(
            ["dns-sd", "-R", "Tanuki Host", d.SERVICE_LABEL, "local", "22"] + txt,
            d.system_publish_command("dns-sd", "Tanuki Host", 22, txt),
        )
        with self.assertRaises(d.DiscoveryError):
            d.system_publish_command("unknown", "Tanuki Host", 22, txt)

    def test_prefers_avahi_and_stops_process(self) -> None:
        processes = []

        def popen(command, **kwargs):
            process = FakeProcess(command, **kwargs)
            processes.append(process)
            return process

        advertiser = d.Advertiser(
            "Tanuki Host",
            22,
            3080,
            HOST_ID,
            FINGERPRINT,
            addresses=["192.0.2.10"],
            which=lambda tool: "/usr/bin/avahi-publish-service" if tool == "avahi-publish-service" else None,
            popen=popen,
        )
        self.assertEqual("avahi-publish-service", advertiser.start())
        self.assertEqual(1, len(processes))
        self.assertEqual("/usr/bin/avahi-publish-service", processes[0].command[0])
        self.assertIn("--no-reverse", processes[0].command)
        self.assertFalse(any("token" in arg.lower() for arg in processes[0].command))
        advertiser.stop()
        self.assertTrue(processes[0].terminated)

    def test_falls_back_to_python_responder_when_tools_missing(self) -> None:
        advertiser = d.Advertiser(
            "Tanuki Host",
            22,
            3080,
            HOST_ID,
            FINGERPRINT,
            addresses=["192.0.2.10"],
            which=lambda _tool: None,
            responder_factory=FakeResponder,
        )
        self.assertEqual("python", advertiser.start())
        responder = advertiser._responder
        self.assertTrue(responder.started)
        advertiser.stop()
        self.assertTrue(responder.stopped)

    def test_tool_failure_falls_back_and_records_note(self) -> None:
        advertiser = d.Advertiser(
            "Tanuki Host",
            22,
            3080,
            HOST_ID,
            FINGERPRINT,
            addresses=["192.0.2.10"],
            which=lambda tool: "/usr/bin/" + tool if tool == "avahi-publish-service" else None,
            popen=lambda command, **kwargs: FakeProcess(command, returncode=1, stderr_text="boom", **kwargs),
            responder_factory=FakeResponder,
        )
        self.assertEqual("python", advertiser.start())
        self.assertTrue(any("boom" in note for note in advertiser.notes))
        advertiser.stop()


class HostAgentNoMdnsTest(unittest.TestCase):
    """--no-mdns 只关闭广播，不影响配对文件和中继启动。"""

    def test_no_mdns_still_writes_pairing(self) -> None:
        with tempfile.TemporaryDirectory(prefix="tanuki-mdns-agent-") as tmp:
            root = Path(tmp)
            pairing = root / "pairing.txt"
            pin = proto.cert_pin(b"synthetic-der")
            proc = subprocess.Popen(
                [
                    sys.executable,
                    str(HERE / "host_agent.py"),
                    "--relay",
                    "127.0.0.1:1",
                    "--state-dir",
                    str(root),
                    "--pairing-out",
                    str(pairing),
                    "--cert-pin",
                    pin,
                    "--name",
                    "测试主机",
                    "--dsh-port",
                    "3080",
                    "--no-mdns",
                ],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                text=True,
            )
            try:
                deadline = time.monotonic() + 8
                while time.monotonic() < deadline and not pairing.is_file():
                    if proc.poll() is not None:
                        break
                    time.sleep(0.1)
                self.assertTrue(pairing.is_file())
                parsed = proto.parse_pairing_uri(pairing.read_text(encoding="utf-8"))
                self.assertEqual(pin, parsed["pin"])
            finally:
                proc.terminate()
                try:
                    _out, err = proc.communicate(timeout=5)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    _out, err = proc.communicate(timeout=5)
            self.assertIn("mDNS 广播已关闭（--no-mdns）", err)


if __name__ == "__main__":
    unittest.main(verbosity=2)

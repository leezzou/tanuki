#!/usr/bin/env python3
"""Tanuki 中继参考实现：TLS 房间配对 + SSH 密文字节转发。

资源需求很小：一个有公网 IPv4/IPv6、可被手机和主机访问的端口即可；每个房间只保留
两个 TCP/TLS 连接和少量内存。中继看不到 SSH 明文，也不能替代主机密钥校验。

限制：
- 这是参考实现，不是高可用生产服务；没有持久化、水平扩展、限流计费和审计存储。
- 房间 token 只保存在内存，中继进程重启后房间消失，主机端会重新注册。
- 不记录 token、配对链接、SSH 数据或请求载荷；只记录房间短前缀和错误码。
"""
from __future__ import annotations

import argparse
import logging
import queue
import socket
import socketserver
import ssl
import threading
import time
from dataclasses import dataclass, field
from typing import Dict, Optional

import protocol as p


class Peer:
    """一个已认证连接；写操作串行化，读仍由各自 handler 线程负责。"""

    def __init__(self, sock: socket.socket, label: str) -> None:
        self.sock = sock
        self.label = label
        self._queue: "queue.Queue[Optional[tuple]]" = queue.Queue(maxsize=32)
        self._closed = threading.Event()
        self._writer = threading.Thread(target=self._write_loop, name="relay-writer", daemon=True)
        self._writer.start()

    def send(self, frame_type: int, stream_id: int, payload: bytes = b"") -> None:
        if self._closed.is_set():
            return
        try:
            self._queue.put_nowait((frame_type, stream_id, payload))
        except queue.Full:
            self.close()

    def _write_loop(self) -> None:
        while not self._closed.is_set():
            item = self._queue.get()
            if item is None:
                break
            try:
                p.write_frame(self.sock, item[0], item[1], item[2])
            except (OSError, p.ProtocolError):
                break
        self.close()

    def close(self) -> None:
        if self._closed.is_set():
            return
        self._closed.set()
        try:
            self._queue.put_nowait(None)
        except queue.Full:
            pass
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        try:
            self.sock.close()
        except OSError:
            pass


@dataclass
class Room:
    room: str
    token: str
    host_id: str
    expires_at: float
    host: Optional[Peer] = None
    client: Optional[Peer] = None
    stream_id: int = 0
    next_stream: int = 1
    lock: threading.Lock = field(default_factory=threading.Lock)


class RoomManager:
    def __init__(self, ttl_seconds: int, max_rooms: int) -> None:
        self.ttl_seconds = ttl_seconds
        self.max_rooms = max_rooms
        self._rooms: Dict[str, Room] = {}
        self._lock = threading.Lock()

    def register(self, room_id: str, token: str, host_id: str, peer: Peer) -> Room:
        now = time.monotonic()
        with self._lock:
            self._purge_locked(now)
            existing = self._rooms.get(room_id)
            if existing is not None:
                if not p.verify(existing.token, token):
                    raise p.RelayError("bad_token")
                if existing.host is not None and existing.host is not peer:
                    raise p.RelayError("room_busy")
                if existing.client is not None:
                    existing.client.send(p.ERROR, 0, b"host_reconnected")
                    existing.client.close()
                    existing.client = None
                    existing.stream_id = 0
                existing.host = peer
                existing.host_id = host_id
                existing.expires_at = now + self.ttl_seconds
                return existing
            if len(self._rooms) >= self.max_rooms:
                raise p.RelayError("room_busy")
            created = Room(
                room=room_id,
                token=token,
                host_id=host_id,
                expires_at=now + self.ttl_seconds,
                host=peer,
            )
            self._rooms[room_id] = created
            return created

    def get(self, room_id: str) -> Room:
        now = time.monotonic()
        with self._lock:
            self._purge_locked(now)
            room = self._rooms.get(room_id)
            if room is None:
                raise p.RelayError("room_not_found")
            if room.host is None:
                raise p.RelayError("host_offline")
            if room.client is not None:
                raise p.RelayError("room_busy")
            return room

    def attach_client(self, room: Room, peer: Peer) -> int:
        with room.lock:
            if room.host is None:
                raise p.RelayError("host_offline")
            if room.client is not None:
                raise p.RelayError("room_busy")
            room.client = peer
            room.stream_id = room.next_stream
            room.next_stream += 1
            room.host.send(p.OPEN, room.stream_id)
            return room.stream_id

    def detach_client(self, room: Room, peer: Peer) -> None:
        with room.lock:
            if room.client is not peer:
                return
            room.client = None
            if room.host is not None and room.stream_id:
                room.host.send(p.CLOSE, room.stream_id)
            room.stream_id = 0

    def detach_host(self, room: Room, peer: Peer) -> None:
        with room.lock:
            if room.host is not peer:
                return
            room.host = None
            if room.client is not None:
                room.client.send(p.ERROR, 0, b"host_offline")
                room.client.close()
                room.client = None
            room.stream_id = 0
            room.expires_at = time.monotonic() + self.ttl_seconds

    def purge(self) -> None:
        with self._lock:
            self._purge_locked(time.monotonic())

    def _purge_locked(self, now: float) -> None:
        expired = [
            room_id
            for room_id, room in self._rooms.items()
            if room.host is None and room.expires_at <= now
        ]
        for room_id in expired:
            room = self._rooms.pop(room_id)
            if room.client is not None:
                room.client.send(p.ERROR, 0, b"expired")
                room.client.close()
            logging.info("room expired: %s…", room_id[:8])


def _send_error(sock: socket.socket, code: str) -> None:
    try:
        p.write_frame(sock, p.ERROR, 0, code.encode("utf-8"))
    except (OSError, p.ProtocolError):
        pass


class RelayHandler(socketserver.BaseRequestHandler):
    manager: RoomManager  # 由服务器实例注入

    def handle(self) -> None:
        sock: socket.socket = self.request
        sock.settimeout(15.0)
        peer: Optional[Peer] = None
        room: Optional[Room] = None
        try:
            frame = p.read_frame(sock)
            if frame is None:
                return
            frame_type, stream_id, payload = frame
            if frame_type != p.CONTROL or stream_id != 0:
                _send_error(sock, "protocol")
                return
            hello = p.parse_control(payload)
            if len(hello) >= 3 and hello[0] == "HELLO" and hello[1] == str(p.VERSION) and hello[2] == "host":
                peer, room = self._host_handshake(sock, hello)
                self._serve_host(peer, room)
            elif len(hello) >= 3 and hello[0] == "HELLO" and hello[1] == str(p.VERSION) and hello[2] == "client":
                peer, room = self._client_handshake(sock, hello)
                self._serve_client(peer, room)
            else:
                _send_error(sock, "protocol")
        except p.RelayError as error:
            if peer is not None:
                peer.send(p.ERROR, 0, error.code.encode("utf-8"))
            else:
                _send_error(sock, error.code)
        except (p.ProtocolError, EOFError, ValueError, ssl.SSLError, OSError):
            if peer is not None:
                peer.send(p.ERROR, 0, b"protocol")
        finally:
            if peer is not None:
                if room is not None:
                    if peer.label == "host":
                        self.manager.detach_host(room, peer)
                    else:
                        self.manager.detach_client(room, peer)
                peer.close()
            else:
                try:
                    sock.close()
                except OSError:
                    pass

    def _host_handshake(self, sock: socket.socket, hello) -> "tuple[Peer, Room]":
        if len(hello) != 7:
            raise p.ProtocolError("host HELLO 字段数无效")
        room_id, host_id, token, host_nonce = hello[3], hello[4], hello[5], hello[6]
        p.validate_room(room_id)
        p.validate_host_id(host_id)
        p.validate_token(token)
        server_nonce = p.new_nonce()
        p.send_control(sock, "CHALLENGE " + server_nonce)
        auth = p.read_control(sock)
        if len(auth) != 2 or auth[0] != "AUTH":
            raise p.ProtocolError("host AUTH 格式无效")
        expected = p.host_proof(token, room_id, host_id, host_nonce, server_nonce)
        if not p.verify(expected, auth[1]):
            raise p.RelayError("bad_token")
        peer = Peer(sock, "host")
        try:
            room = self.manager.register(room_id, token, host_id, peer)
        except p.RelayError:
            peer.close()
            raise
        sock.settimeout(None)
        peer.send(p.CONTROL, 0, ("REGISTERED " + str(int(time.time() + self.manager.ttl_seconds))).encode("utf-8"))
        logging.info("host registered: %s…", room_id[:8])
        return peer, room

    def _client_handshake(self, sock: socket.socket, hello) -> "tuple[Peer, Room]":
        if len(hello) != 6:
            raise p.ProtocolError("client HELLO 字段数无效")
        room_id, client_nonce = hello[3], hello[5]
        p.validate_room(room_id)
        room = self.manager.get(room_id)
        server_nonce = p.new_nonce()
        p.send_control(sock, "CHALLENGE " + server_nonce)
        auth = p.read_control(sock)
        if len(auth) != 2 or auth[0] != "AUTH":
            raise p.ProtocolError("client AUTH 格式无效")
        expected = p.client_proof(room.token, room_id, client_nonce, server_nonce)
        if not p.verify(expected, auth[1]):
            raise p.RelayError("bad_token")
        p.send_control(sock, "QUEUED")
        peer = Peer(sock, "client")
        try:
            stream_id = self.manager.attach_client(room, peer)
        except p.RelayError as error:
            peer.send(p.ERROR, 0, error.code.encode("utf-8"))
            peer.close()
            raise
        sock.settimeout(None)
        logging.info("client joined: %s… stream=%s", room_id[:8], stream_id)
        return peer, room

    def _serve_host(self, peer: Peer, room: Room) -> None:
        while True:
            frame = p.read_frame(peer.sock)
            if frame is None:
                return
            frame_type, stream_id, payload = frame
            if frame_type == p.OPEN_OK:
                with room.lock:
                    if room.client is not None and room.stream_id == stream_id:
                        room.client.send(p.OPEN_OK, stream_id)
            elif frame_type == p.DATA:
                with room.lock:
                    if room.client is not None and room.stream_id == stream_id:
                        room.client.send(p.DATA, stream_id, payload)
            elif frame_type == p.CLOSE:
                with room.lock:
                    if room.client is not None and room.stream_id == stream_id:
                        room.client.send(p.CLOSE, stream_id)
                        room.client.close()
                        room.client = None
                        room.stream_id = 0
            elif frame_type == p.ERROR:
                with room.lock:
                    if room.client is not None:
                        room.client.send(p.ERROR, 0, payload[:64])
                        room.client.close()
                        room.client = None
                        room.stream_id = 0
            elif frame_type == p.PING:
                peer.send(p.PONG, 0)
            elif frame_type in (p.PONG, p.OPEN, p.CONTROL):
                continue
            else:
                return

    def _serve_client(self, peer: Peer, room: Room) -> None:
        while True:
            frame = p.read_frame(peer.sock)
            if frame is None:
                return
            frame_type, stream_id, payload = frame
            if frame_type == p.DATA:
                with room.lock:
                    if room.host is not None and room.stream_id == stream_id:
                        room.host.send(p.DATA, stream_id, payload)
            elif frame_type == p.CLOSE:
                self.manager.detach_client(room, peer)
            elif frame_type == p.PING:
                peer.send(p.PONG, 0)
            elif frame_type in (p.PONG, p.OPEN_OK, p.CONTROL):
                continue
            else:
                return


class RelayServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, handler, context: ssl.SSLContext, manager: RoomManager) -> None:
        self.context = context
        self.manager = manager
        super().__init__(address, handler)

    def get_request(self):
        raw, address = self.socket.accept()
        try:
            tls = self.context.wrap_socket(raw, server_side=True)
        except (ssl.SSLError, OSError):
            try:
                raw.close()
            except OSError:
                pass
            raise
        return tls, address

    def handle_error(self, request, client_address) -> None:
        # 只记录“有连接失败”，不打印 TLS 载荷、token 或地址相关的敏感信息。
        logging.warning("connection failed during TLS/handshake")


def _certificate_pin(cert_path: str) -> str:
    with open(cert_path, "r", encoding="utf-8") as handle:
        pem = handle.read()
    der = ssl.PEM_cert_to_DER_cert(pem)
    return p.cert_pin(der)


def main() -> int:
    parser = argparse.ArgumentParser(description="Tanuki TLS 中继参考实现")
    parser.add_argument("--listen", default="0.0.0.0", help="监听地址；默认 0.0.0.0")
    parser.add_argument("--port", type=int, default=8443, help="监听端口；默认 8443")
    parser.add_argument("--cert", required=True, help="TLS 证书 PEM 路径")
    parser.add_argument("--key", required=True, help="TLS 私钥 PEM 路径（仅主机可读）")
    parser.add_argument("--room-ttl", type=int, default=600, help="主机掉线后的房间保留秒数")
    parser.add_argument("--max-rooms", type=int, default=64, help="内存中最大房间数")
    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error("--port 必须在 1-65535")
    if args.room_ttl < 30:
        parser.error("--room-ttl 不能小于 30 秒")
    if args.max_rooms < 1:
        parser.error("--max-rooms 必须为正数")

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(certfile=args.cert, keyfile=args.key)
    manager = RoomManager(args.room_ttl, args.max_rooms)
    RelayHandler.manager = manager
    server = RelayServer((args.listen, args.port), RelayHandler, context, manager)

    def purge_loop() -> None:
        while True:
            time.sleep(15)
            manager.purge()

    threading.Thread(target=purge_loop, name="relay-purge", daemon=True).start()
    print("relay_pin=" + _certificate_pin(args.cert), flush=True)
    logging.info("relay listening on %s:%s", args.listen, args.port)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

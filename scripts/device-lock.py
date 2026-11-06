#!/usr/bin/env python3
"""按设备 serial 的进程级独占锁，覆盖子任务的整个生命周期。

- 使用 Python 标准库 fcntl.flock，macOS 与 Linux 可用，无需额外依赖。
- 锁 fd 通过 pass_fds 传给子任务：父进程被意外杀死时，只要子任务仍存活，
  锁仍由继承的 fd 持有，直到安装/仪器等子任务真正结束。
- 锁文件只记录 serial、pid、session、时间与命令名，不记录 token 或设备内容。
- 只保障遵循本入口的任务；人工直接 adb、其他脚本或手动设置
  TANUKI_DEVICE_LOCK_HELD=1 均可绕过，不是安全边界。

用法：
  python3 scripts/device-lock.py --serial emulator-5554 -- bash <command...>
"""
from __future__ import annotations

import argparse
import fcntl
import json
import os
import re
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path


def lock_path(serial: str, directory: str | None) -> Path:
    safe = re.sub(r"[^A-Za-z0-9._-]", "_", serial) or "unknown"
    base = Path(directory) if directory else Path(tempfile.gettempdir())
    return base / f"tanuki-device-{safe}.lock"


def read_owner(path: Path) -> dict[str, object]:
    # 竞争时赢家可能还没写入元数据，短暂重试避免误报“未知持有者”。
    for _ in range(5):
        try:
            text = path.read_text().strip()
            if text:
                return json.loads(text)
        except (OSError, ValueError):
            pass
        time.sleep(0.05)
    return {}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--lock-dir", default=os.environ.get("TANUKI_DEVICE_LOCK_DIR"))
    parser.add_argument("command", nargs=argparse.REMAINDER)
    options = parser.parse_args()
    command = options.command
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        print("缺少要执行的命令", file=sys.stderr)
        return 2

    path = lock_path(options.serial, options.lock_dir)
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
    except OSError as error:
        print(f"无法创建设备锁 {path}: {error}", file=sys.stderr)
        return 2

    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        owner = read_owner(path)
        who = " ".join(
            f"{key}={owner.get(key)}"
            for key in ("session", "pid", "childPid", "since")
            if owner.get(key)
        ) or "未知持有者"
        print(
            f"设备任务被拒绝: serial={options.serial} 已被 {who} 持有；"
            "等待其结束或改用其他设备。",
            file=sys.stderr,
        )
        return 3

    metadata: dict[str, object] = {
        "serial": options.serial,
        "pid": os.getpid(),
        "session": os.environ.get("AO_SESSION_ID", ""),
        "since": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "command": " ".join(Path(item).name for item in command[:2]),
    }

    def publish() -> None:
        try:
            os.ftruncate(fd, 0)
            os.lseek(fd, 0, os.SEEK_SET)
            os.write(fd, (json.dumps(metadata, ensure_ascii=False) + "\n").encode("utf-8"))
            os.fsync(fd)
        except OSError:
            pass

    publish()
    environment = dict(os.environ)
    environment["TANUKI_DEVICE_LOCK_HELD"] = "1"
    try:
        child = subprocess.Popen(command, env=environment, pass_fds=(fd,))
    except OSError as error:
        print(f"无法启动设备任务: {error}", file=sys.stderr)
        return 2
    metadata["childPid"] = child.pid
    publish()
    print(
        f"DEVICE_LOCK_ACQUIRED serial={options.serial} child={child.pid} lock={path}",
        flush=True,
    )

    def forward(signum: int, _frame: object) -> None:
        if child.poll() is None:
            child.send_signal(signum)

    for sig in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(sig, forward)

    try:
        returncode = child.wait()
    except KeyboardInterrupt:
        returncode = 130
    # 负值表示子任务被信号终止；转换为 shell 常见的 128+N。
    return 128 - returncode if returncode < 0 else returncode


if __name__ == "__main__":
    sys.exit(main())

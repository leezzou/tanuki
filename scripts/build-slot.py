#!/usr/bin/env python3
"""跨工作树重构建互斥；开发任务优先于后台发布，不中断已运行任务。"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import fcntl
import os
from pathlib import Path
import subprocess
import signal
import sys
import time
import uuid


def lock_file(path):
    # 锁文件永不删除，避免不同 inode 上出现两个持有者。
    fd = os.open(str(path), os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    return os.fdopen(fd, "a+")


@contextmanager
def acquire(directory, priority="development", cancelled=lambda: False):
    directory = Path(directory).expanduser().resolve()
    directory.mkdir(parents=True, exist_ok=True)
    ticket = directory / (priority + "-" + uuid.uuid4().hex)
    with lock_file(directory / "admission.lock") as admission, lock_file(directory / "build.lock") as build:
        # 票据本身也加锁；进程崩溃留下的票据不会永久饿死发布者。
        with lock_file(ticket) as waiting:
            fcntl.flock(waiting, fcntl.LOCK_EX)
            try:
                while True:
                    if cancelled():
                        raise InterruptedError("等待构建锁时停止")
                    fcntl.flock(admission, fcntl.LOCK_EX)
                    try:
                        foreground = False
                        if priority == "release":
                            for candidate in directory.glob("development-*"):
                                if candidate == ticket:
                                    continue
                                with lock_file(candidate) as other:
                                    try:
                                        fcntl.flock(other, fcntl.LOCK_EX | fcntl.LOCK_NB)
                                    except BlockingIOError:
                                        foreground = True
                        if not foreground:
                            try:
                                fcntl.flock(build, fcntl.LOCK_EX | fcntl.LOCK_NB)
                                break
                            except BlockingIOError:
                                pass
                    finally:
                        fcntl.flock(admission, fcntl.LOCK_UN)
                    time.sleep(0.2)
                yield
            finally:
                # 只删除自己创建且确认仍位于锁目录内的票据。
                if ticket.parent.resolve() != directory or ticket.is_symlink():
                    raise ValueError("构建票据路径改变")
                ticket.unlink()
                fcntl.flock(build, fcntl.LOCK_UN)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", required=True, type=Path)
    parser.add_argument("--priority", choices=("development", "release"), default="development")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("需要 -- 后的命令参数")
    print("[build-slot] 等待共享构建位", flush=True)
    with acquire(args.directory, args.priority):
        print("[build-slot] 已获得构建位", flush=True)
        # 父 wrapper 收到停止信号时仍等待本次子命令退出，再释放锁。
        for signum in (signal.SIGINT, signal.SIGTERM):
            signal.signal(signum, lambda *_: print("[build-slot] 停止请求：等待本次命令退出后归还构建位", flush=True))
        return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""准备稳定目录的合并发布服务；enable/start/stop 是独立的显式操作。"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
LABEL = "com.tanuki.merge-release"
SCRIPTS = ("merge-release.py", "build-slot.py", "merge-release-service.py")
AGENTS_DIRECTORY = Path.home() / "Library/LaunchAgents"


def stable(path):
    value = path.expanduser().resolve()
    source_tree = (ROOT / "AGENTS.md").is_file()
    if (source_tree and (value == ROOT or ROOT in value.parents)) or ".ao" in value.parts or value == Path.home() or value == Path("/"):
        raise ValueError("服务目录必须为专用稳定目录，位于源码/AO 工作树之外")
    return value


def launch(command):
    result = subprocess.run(command, capture_output=True, timeout=60)
    if result.returncode:
        raise ValueError("launchctl 操作失败，状态未假定成功")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "enable", "start", "stop"))
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--repo", default="leezzou/tanuki")
    parser.add_argument("--jdk", type=Path)
    parser.add_argument("--sdk", type=Path)
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--build-slot", type=Path)
    parser.add_argument("--gate-receipts", type=Path)
    parser.add_argument("--installed-code-floor", type=int, default=19)
    parser.add_argument("--confirm-shared-slot", action="store_true", help="启用前确认所有重构建都遵守同一绝对锁路径")
    args = parser.parse_args()
    home = stable(args.home)
    plist = home / (LABEL + ".plist")
    if args.command == "prepare":
        if home.exists():
            raise ValueError("服务目录已存在，拒绝覆盖持久状态/运行代码；升级须先停止并审核迁移")
        if not all((args.jdk, args.sdk, args.keystore, args.build_slot, args.gate_receipts)):
            parser.error("prepare 需要 --jdk/--sdk/--keystore/--build-slot/--gate-receipts")
        keystore = args.keystore.expanduser().resolve()
        if not keystore.is_file() or ROOT in keystore.parents:
            raise ValueError("必须引用仓库外已有证书，只检查路径，不读取密钥")
        if not (args.jdk.expanduser() / "bin/java").is_file() or not args.sdk.expanduser().is_dir():
            raise ValueError("JDK/SDK 不存在")
        if args.installed_code_floor < 19:
            raise ValueError("已知安装 code 上限不能低于 19")
        gh = shutil.which("gh")
        if not gh:
            raise ValueError("缺少现有 gh CLI")
        tools = [args.jdk, args.sdk, keystore, Path(gh), Path(sys.executable)]
        if any(".ao" in path.expanduser().resolve().parts for path in tools):
            raise ValueError("服务工具与证书路径不能依赖会归档的 AO 目录")
        environment = {"PATH": str(Path(gh).parent) + ":/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin",
                       "TANUKI_JDK": str(args.jdk.expanduser().resolve()),
                       "ANDROID_SDK_ROOT": str(args.sdk.expanduser().resolve()),
                       "TANUKI_WORKSPACECHECK_KEYSTORE": str(keystore),
                       "GRADLE_USER_HOME": str(home / "gradle")}
        home.mkdir(parents=True, mode=0o700)
        (home / "bin").mkdir()
        for name in SCRIPTS:
            shutil.copyfile(ROOT / "scripts" / name, home / "bin" / name)
        config = {"repo": args.repo, "installed_code_floor": args.installed_code_floor,
                  "build_slot": str(stable(args.build_slot)), "gate_receipts": str(stable(args.gate_receipts)),
                  "environment": environment, "poll_seconds": 30}
        (home / "config.json").write_text(json.dumps(config, indent=2) + "\n")
        document = {"Label": LABEL, "ProgramArguments": [sys.executable, str(home / "bin/merge-release.py"),
                    "--home", str(home), "watch"], "WorkingDirectory": str(home),
                    "RunAtLoad": True, "KeepAlive": True, "ThrottleInterval": 30,
                    "ExitTimeOut": 7300, "ProcessType": "Background", "LowPriorityIO": True,
                    "StandardOutPath": str(home / "service.stdout.log"), "StandardErrorPath": str(home / "service.stderr.log")}
        plist.write_bytes(plistlib.dumps(document))
        print("准备完成；尚未注册或启动。先 init/plan，再由集成维护方 enable。")
        return 0
    if sys.platform != "darwin":
        raise ValueError("launchd 启停仅支持 macOS")
    if not plist.is_file() or not (home / "state.json").is_file():
        raise ValueError("先 prepare，再明确基线 init")
    domain = "gui/%d" % os.getuid()
    registered = AGENTS_DIRECTORY / (LABEL + ".plist")
    if registered.exists() or registered.is_symlink():
        if registered.is_symlink() or not registered.is_file() or registered.read_bytes() != plist.read_bytes():
            raise ValueError("已有其他发布服务注册，拒绝覆盖或移除")
    if args.command in ("enable", "start"):
        if not args.confirm_shared_slot:
            raise ValueError("启用前必须确认跨工作树共享构建锁已全面采用")
        AGENTS_DIRECTORY.mkdir(parents=True, exist_ok=True)
        if not registered.exists():
            # 用户登录时由 LaunchAgents 恢复；启动命令仍只引用稳定服务目录。
            with registered.open("xb") as output:
                output.write(plist.read_bytes())
        launch(["launchctl", "bootstrap", domain, str(registered)])
    else:
        launch(["launchctl", "bootout", domain, str(plist)])
        if registered.exists():
            # 仅移除精确核对过的本服务注册文件，不清理任何队列/构建/历史附件。
            expected = AGENTS_DIRECTORY.resolve() / (LABEL + ".plist")
            if registered.resolve() != expected or registered.is_symlink() or registered.read_bytes() != plist.read_bytes():
                raise ValueError("服务注册路径或字节发生变化，拒绝删除")
            registered.unlink()
    print("服务操作完成；状态/远端发布事实请用 status/plan 核对。")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print("服务操作停止：" + str(error), file=sys.stderr)
        sys.exit(1)

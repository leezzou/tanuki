# 交接：20261111-1147-mdns-discovery

- Agent/会话标识：android_agent-65（AO worker，DSH）
- 时间：2026-11-11 11:47 CST
- 状态：已完成（PR #71 待 CI/独立审查；未合并、未发布）
- 前一记录：[主机端一键 quickstart](20261111-1017-remote-relay-quickstart.md)（PR #64 已合并）

> 普通 PR 只新增本记录，未修改 `docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json`。

## 用户目标与范围

- 用户本次实际要求：在 #64 的基础上做第二个 PR，让手机“打开远程页面就能看到电脑”：host_agent 广播 mDNS `_tanuki-remote._tcp`，TXT 带显示名/SSH 端口/DSH 端口/hostId/协议版本/指纹摘要且不含 token；优先系统 avahi/dns-sd，缺失时用 Python 标准库最小应答；`--no-mdns` 可关；终端打印二维码；quickstart 提示同 Wi-Fi 可见；补文档与离线自测。
- 本次包含：`discovery.py`（名称清洗、TXT、mDNS 编解码、系统工具/应答器）、`host_agent` 集成、quickstart 的 `--name/--dsh-port/--no-mdns` 与二维码、`test_mdns.py` 20 项、quickstart/relay 测试更新、CI 步骤、`INTERFACES.md`/`REMOTE-CONNECT.md`/`REMOTE-HOST.md` 文档。
- 本次不包含：Android/Kotlin 代码、手机端发现列表 UI、真实手机/路由器/公网验收、合并/发布/tag/Release。
- 用户改变或暂停的要求（需保留）：中文注释；author/committer=`leezzou <115924933+leezzou@users.noreply.github.com>`；PR 交付但不合并、不发布；文档/注释不得出现外部参考实现名称；P2 审查意见按编排方要求记待办，不在本 PR 顺手修。

## 当前状态

- 已实现：主机端 mDNS 广播（avahi/dns-sd 优先，Python 最小应答兜底）、TXT 契约、`--name/--dsh-port/--no-mdns`、quickstart 局域网提示与 `qrencode` 二维码/退化提示、文档与自测。
- 已验证：`test_mdns.py` 20 项、`test_quickstart.py` 20 项、`test_remote_relay.py` 9 项通过；host_agent 默认 `dns-sd` 启动且 SIGTERM 退出 0、无 `dns-sd` 残留；quickstart 默认 mDNS 横幅正确、Ctrl+C 后无 `dns-sd` 残留；假 `qrencode` 成功路径与缺失退化路径均验证。
- 待验证：PR 推送后的 GitHub CI；独立 Codex High 审查；真实手机发现列表、真实 avahi/路由器 AP 隔离/组播屏蔽、真实 `qrencode` 扫码。
- 当前分支：`ao/relay-quickstart/mdns-qr`，基于 `origin/main=8d620ee`（#64 合并后），已 fast-forward 到 `origin/main=95704ff`（#66 docs-only）；PR：<https://github.com/leezzou/tanuki/pull/71>。
- 关键事实与可定位源码：`scripts/remote-relay/discovery.py`、`host_agent.py`、`quickstart.sh`、`quickstart_lib.py`、`test_mdns.py`、`docs/INTERFACES.md` 1.7、`docs/REMOTE-CONNECT.md` 3.4。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/remote-relay/discovery.py` | 新增：显示名清洗、hostId 指纹摘要、TXT 拼装/解析、mDNS 查询/响应编解码（含压缩指针）、PTR/SRV/TXT/A 最小应答、avahi/dns-sd 命令与失败回退。 |
| `scripts/remote-relay/host_agent.py` | 新增 `--name/--dsh-port/--no-mdns`；启动时广播 mDNS，失败只告警；SIGTERM 走 KeyboardInterrupt 路径，finally 停掉系统广播子进程/应答线程。 |
| `scripts/remote-relay/quickstart.sh` | 新增 `--name/--dsh-port/--no-mdns`；横幅打印“列表里应出现 <名称>”；qrencode 二维码，缺失时打印链接大字与安装提示；mDNS 未确认成功时提示用二维码。 |
| `scripts/remote-relay/quickstart_lib.py` | agent 参数增加 `--dsh-port/--name/--no-mdns`；`--print-only` 输出 display_name/dsh_port/mdns 状态；`detect_lan_ip` 统一委托 discovery。 |
| `scripts/remote-relay/test_mdns.py` | 新增 20 项离线自测：名称清洗、TXT、固定查询/响应向量、压缩指针、系统命令、工具失败回退、host_agent `--no-mdns`。 |
| `scripts/remote-relay/test_quickstart.py` | 增加 `--name/--dsh-port/--no-mdns` 参数断言、横幅 mDNS/二维码断言；测试启动默认关闭 mDNS。 |
| `scripts/remote-relay/test_remote_relay.py` | 测试 agent 启动加 `--no-mdns`，避免真实广播。 |
| `.github/workflows/verify.yml` | 新增 `test_mdns.py` CI 步骤。 |
| `docs/INTERFACES.md` | 新增 1.7 mDNS 契约，原 1.7–1.9 顺延为 1.8–1.10。 |
| `docs/REMOTE-CONNECT.md` | quickstart 说明补 mDNS/二维码；新增 3.4 局域网自动发现（前置条件、TXT、失败对照）；实现/验证补 discovery/test_mdns。 |
| `docs/REMOTE-HOST.md` | 补局域网发现前置条件、2.4 主机端说明、实现位置与未验证项。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `cd scripts/remote-relay && python3 test_mdns.py` | 通过：20 tests / 0 failures | 本机 macOS、Python 3.9.6；固定报文/假进程，不绑定 5353 |
| `cd scripts/remote-relay && python3 test_quickstart.py` | 通过：20 tests / 0 failures | 回环端口 + `--no-mdns`，不依赖真实网络 |
| `cd scripts/remote-relay && python3 test_remote_relay.py` | 通过：9 tests / 0 failures | 既有 relay 回归；agent 测试加 `--no-mdns` |
| `bash -n scripts/remote-relay/quickstart.sh` | 通过 | 仅语法检查 |
| host_agent 默认 mDNS（macOS） | 通过：日志 `mDNS 广播：测试主机（dns-sd...）`；SIGTERM 退出 0；`pgrep dns-sd` 前后均为空 | 回环/无中继；不证明真实局域网可见 |
| quickstart 默认 mDNS | 通过：横幅含“列表里应出现「测试主机」”；Ctrl+C 退出 130；`dns-sd` 无残留 | 回环端口；真实手机未验证 |
| 假 `qrencode` 成功路径 | 通过：输出假二维码内容并打印“扫码配对” | 用临时 PATH 假脚本；真实二维码未扫 |
| 无 `qrencode` 退化 | 通过：输出“未安装 qrencode”、安装提示和链接大字 | 本机未安装 qrencode |
| `python3 scripts/check-publication.py --git` / `check-handoff.py --pr` | 待提交前执行 | 新增文件需先 `git add` |

- 哪些是前一轮结果，哪些是本次新验证：本表全部为本次新验证；未引用 #64 的 19 项结果。
- 未测试、超时、失败和暂未复现的情况：真实手机发现列表、真实 avahi（Linux）与 dns-sd（macOS）跨设备、路由器 AP 隔离/访客网络/组播屏蔽、真实 qrencode 扫码、CI 与独立审查未执行。

## 未完成与暂停项

- 未完成：PR 创建与推送后的 CI；独立 Codex High 审查绑定当前 head；真实同网段/跨网设备验收。
- 外部阻塞：无。
- 用户暂停：合并/发布；API Key、真实模型/账号、真机验收保持暂停，本次未触碰。
- P2 审查意见（`--listen` 连接地址、状态目录锁）按编排方要求记为 #64 待办，本 PR 未顺手修改。

## 下一步

1. PR #71 已创建；观察 CI，并请编排方安排独立 Codex High 审查当前 head（不合并、不发布）。
2. 观察 CI；请编排方安排独立 Codex High 审查当前 head。
3. 有真实同网段手机/路由器时，验证发现列表、AP 隔离、UDP 5353 和二维码扫码；记录设备、网络和失败现象。

## 约束与风险

- 数据/设备/权限边界：未改 Android 代码；未读写真实凭据；测试只用临时目录/回环端口/固定报文，不绑定 5353、不启动真实 avahi/dns-sd。
- 关键决策及理由：mDNS 只广播显示名/端口/hostId/版本/指纹摘要，不广播 token；`fp` 明确定义为显示对照而非认证；系统工具优先、Python 最小应答兜底，避免引入依赖；SRV/A 目标对非 ASCII 主机名回退 `tanuki-<hostId 前 8>`，提高 Android 解析兼容性；广播失败只告警，不阻塞中继。
- 失败尝试与避免重复踩坑的结论：`${NO_MDNS:+...}` 在值为 0 时也会展开，已改为显式 `MDNS_ARG`；测试默认 `--no-mdns` 防止 CI 绑定 5353/启动系统广播；`Advertiser.stop()` 会清空 responder 引用，测试需在 stop 前保存对象。
- 安全风险：同网段可伪造 mDNS 记录；发现信息不能替代配对 token/SSH 主机密钥确认；二维码与配对链接含 token，只能在自己的终端显示；Python 兜底绑定 UDP 5353，可能与已有 mDNS 服务冲突，冲突时用 `--no-mdns`。

## 产物与校验

- PR：<https://github.com/leezzou/tanuki/pull/71>（不合并、不发布）。
- 新/旧 APK、源码 ZIP：本次未构建 APK、未生成源码 ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：无安装、无签名、无 tag/Release、未合并。
- 清理过期记录：未删除；本记录为本次唯一记录，未改共享状态/印章。

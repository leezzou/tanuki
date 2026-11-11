# 交接：20261111-1017-remote-relay-quickstart

- Agent/会话标识：android_agent-65（AO worker，DSH）
- 时间：2026-11-11 10:17 CST
- 状态：已完成（已合并 origin/main=7d58e90；PR #64 待最终 CI 与独立审查；未合并到 main、未发布）
- 前一记录：[查看器传输胶囊历史验证](../TEST-REPORT.md)（结论已合并；本任务与其无代码依赖）

> 普通 PR 只新增本记录，未修改 `docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json`。

## 用户目标与范围

- 用户本次实际要求：把远程连接的主机端做成“一条命令、不用手工拼参数”。基于 `origin/main=62e4c30b` 新增 `scripts/remote-relay/quickstart.sh`（可拆小脚本）和文档，一条命令完成证书生成、钉扎计算、relay/agent 启动、配对链接剪贴板与终端提示、Ctrl+C 清理，并补离线自测、更新 `docs/REMOTE-CONNECT.md` 的“主机端 5 分钟上手”和失败对照表。
- 本次包含：quickstart 编排脚本、钉扎/参数/端口辅助模块、`host_agent.py` 的 `--pairing-relay`（连接地址与手机可达地址解耦）、19 项离线自测、CI 步骤、`REMOTE-CONNECT.md` 文档章节。
- 本次不包含：Android/Kotlin 代码、手机端配对 UI、真实公网/真实手机验收、relay 协议变更、合并/发布/tag/Release。
- 用户改变或暂停的要求（需保留）：不改 Android 代码；中文注释；author/committer 必须为 `leezzou <115924933+leezzou@users.noreply.github.com>`；PR 交付但不合并、不发布；文档/注释不得出现外部参考实现名称；保留既有安全与暂停项。

## 当前状态

- 已实现：`quickstart.sh` 一条命令流程、`quickstart_lib.py` 辅助逻辑、`host_agent.py --pairing-relay`、CI 自测步骤、文档 3.1–3.3。
- 已验证：19 项 quickstart 离线自测通过；既有 9 项 relay 回归通过；`--print-only` 无副作用；真实回环启动后 SIGINT 退出码 130，relay/agent 均退出、端口释放、pid 文件清理；发布检查 0 findings。
- 待验证：PR 推送后的 GitHub CI；独立 Codex High 对当前 head 的审查；真实手机 + 真实 sshd/公网中继、真实剪贴板。
- 当前分支：`ao/relay-quickstart/root`，基于 `origin/main=62e4c30`，已合并 `origin/main=7d58e90`（#63 手机端自动配对）；PR：<https://github.com/leezzou/tanuki/pull/64>（上一 head `2cb3341` CI 通过，merge 后 CI 待重跑）；无运行中的 relay/agent 测试进程。
- 关键事实与可定位源码：`scripts/remote-relay/quickstart.sh`、`quickstart_lib.py`、`test_quickstart.py`、`host_agent.py`（`--pairing-relay`）、`docs/REMOTE-CONNECT.md` 第 3 节、`.github/workflows/verify.yml` 的 “Test remote relay quickstart”。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/remote-relay/quickstart.sh` | 新增一键启动：检查 python3/openssl、复用 `gen-cert.sh` 生成/复用证书、自动计算钉扎、端口预检、后台 relay + 健康检查、agent 自动带 `--cert-pin`、剪贴板、大字横幅、`--print-only`、`--no-clipboard`、INT/TERM/HUP trap 清理两个子进程。 |
| `scripts/remote-relay/quickstart_lib.py` | 新增可离线单测的辅助逻辑：证书 DER SHA-256 钉扎、relay/agent 命令拼装、配对文件解析与 6 位码、端口探测/等待、局域网 IPv4 探测、`--print-only` 计划输出。 |
| `scripts/remote-relay/host_agent.py` | 新增向后兼容的 `--pairing-relay`：agent 仍连接 `--relay`，但配对链接可展示手机可达的另一地址；未提供时行为不变。 |
| `scripts/remote-relay/test_quickstart.py` | 新增 19 项自测：钉扎、参数拼装、配对解析、端口、局域网探测、`--print-only`、端口占用、真实回环启动 + SIGINT trap 清理、`--pairing-relay` 回归。 |
| `.github/workflows/verify.yml` | 在既有 relay 回归后新增 quickstart 自测步骤。 |
| `docs/REMOTE-CONNECT.md` | 新增“主机端 5 分钟上手（quickstart）”3.1 三条命令、3.2 预期输出、3.3 常见失败对照；顺延后续章节编号；实现位置与验证记录补 quickstart。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `cd scripts/remote-relay && python3 test_quickstart.py` | 通过：19 tests / 0 failures，约 3 秒 | 本机 macOS、Python 3.9.6、OpenSSL 4.1.0；仅回环与临时目录，不访问外网 |
| `cd scripts/remote-relay && python3 -m unittest test_quickstart -v` | 通过：19 tests / 0 failures | 同一组用例，确认 `python -m unittest` 入口可用 |
| `python3 scripts/remote-relay/test_remote_relay.py` | 通过：9 tests / 0 failures | 既有 relay 端到端回归；确认 `host_agent.py` 改动未破坏原行为 |
| `bash -n scripts/remote-relay/quickstart.sh` | 通过 | 仅语法检查，不替代运行验证 |
| `bash scripts/remote-relay/quickstart.sh --print-only --state-dir <临时目录> --port 18443` | 退出 0；输出证书路径、钉扎状态、relay/agent 命令、配对文件状态；临时目录无新增文件 | 确认 `--print-only` 不建目录、不启动进程 |
| 回环启动后发送 SIGINT（`Popen` + `send_signal`） | 退出码 130；relay/agent PID 均消失；端口释放；`quickstart.pids` 删除；stderr 含“已停止” | 使用 `--listen 127.0.0.1 --public-host 127.0.0.1 --no-clipboard`，不依赖真实网络 |
| 回环启动后读取横幅 | stdout 含完整配对链接、6 位核对码、手机端三步、Ctrl+C 提示；链接中的 `pin` 与证书钉扎一致 | 临时状态目录；未验证真实手机粘贴/扫码 |
| GitHub Actions 首轮 Host job（run 42948018982） | 失败：`test_port_detection_and_wait` 在 Linux 上对 backlog=1 的监听端口做第三次连接探测时超时 | 已改为直接断言 `probe_host` 的通配地址映射并增大 backlog；本地复跑 19 项通过 |
| GitHub Actions（head `2cb3341`） | 通过：push run 42948762327（Android 7m47s、Host 40s）与 PR run 42948753628（Android 5m59s、Host 38s），4 个 check 全 pass | 本记录为 CI 后的 docs-only 提交，最终 head 的 CI 以 PR 为准；未跑设备 |
| `git merge origin/main`（main=`7d58e90`，#63）后复跑 | 通过：quickstart 19 tests、既有 relay 9 tests；仅 `docs/REMOTE-CONNECT.md` 冲突 | 冲突保留 #63 的手机自动读剪贴板/自动算码内容，并同步 quickstart 横幅文案 |
| `python3 scripts/check-publication.py --git` | 通过：0 findings（检查时新增文件尚未 `git add`，提交前需重跑） | 发布检查不打印匹配到的敏感值 |

- 哪些是前一轮结果，哪些是本次新验证：本表全部为本次新验证；未引用前一轮产品测试数字。
- 未测试、超时、失败和暂未复现的情况：真实手机、真实剪贴板工具、真实公网中继/NAT、真实 sshd、弱网、GitHub CI 与独立审查尚未执行；本机为 macOS，Linux 的 `wl-copy/xclip` 分支仅做代码审阅。

## 未完成与暂停项

- 未完成：merge 后最终 head 的 GitHub CI；独立 Codex High 审查绑定最终 head；真实公网/设备验收。
- 外部阻塞：无（本地可完成部分已完成）。
- 用户暂停：API Key、真实模型/账号、真机验收、合并/发布；本次未触碰，也未合并/发布。
- 未删除任何旧交接记录：没有因本任务而过期的记录；历史验证依据保留。

## 下一步

1. PR #64 CI 已通过；请编排方安排独立 Codex High 审查最终 head；不要合并、不要发布。
2. 请编排方安排独立 Codex High 审查当前 PR head；CI 与审查通过后再按用户授权决定合并。
3. 有真实中继主机/手机时，按 `docs/REMOTE-CONNECT.md` 3.1 做一次真实配对，记录中继地址类型、手机网络、证书钉扎与失败码。

## 约束与风险

- 数据/设备/权限边界：未改 Android 代码；未读写真实凭据/用户目录（自测只用临时目录与回环端口）；未执行破坏性设备操作。
- 关键决策及理由：用 Python 辅助模块承载钉扎/参数/解析，shell 只做编排，便于离线单测；新增 `--pairing-relay` 让 agent 连 `127.0.0.1`、配对链接展示手机可达地址，避免跨网络链接写成回环地址；端口检查放在证书生成前，失败不留半套文件；trap 先停 agent 再停 relay，避免重连孤儿。
- 失败尝试与避免重复踩坑的结论：用 bash `&` 手动测试时 SIGINT 会被后台任务忽略，不能用它验证 INT trap；改用 Python `Popen`（默认 SIGINT）验证，退出码固定为 130。`py_compile` 在受限沙箱会尝试写 home 缓存，需设置 `PYTHONPYCACHEPREFIX` 到工作区。
- 合并冲突：`origin/main` 的 #63 改了 `docs/REMOTE-CONNECT.md` 的手机配对流程，与本分支新增章节冲突；已保留双方内容，并把 quickstart 的“粘贴并输入核对码”文案改为“手机自动读取/算码，终端码用于对照”，避免误导。
- 安全风险：配对链接含一次性房间 token，横幅与 `pairing.txt` 均视为秘密；`--print-only` 在配对文件存在时会打印旧链接，仅用于本机排查；默认 `--public-host` 是局域网 IPv4 的最好猜测，跨网络必须显式传公网地址。

## 产物与校验

- PR：<https://github.com/leezzou/tanuki/pull/64>（不合并、不发布）。
- 新/旧 APK、源码 ZIP 及校验文件：本次未构建 APK、未生成源码 ZIP（文档/脚本变化后由发布维护方在发布流程中重打并核对外部 SHA）。
- 是否覆盖安装 / 签名范围 / 未发布事项：无安装、无签名、无 tag/Release、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：无删除；本记录为本次唯一记录，未改共享状态/印章。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。

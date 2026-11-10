# 交接：20261110-1140-native-parity-audit

- Agent/会话标识：android_agent-47（AO worker，项目 android_agent）
- 时间：2026-11-10 11:40 CST
- 状态：已完成（只读审计 + 文档/检查器；未跑产品测试）
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)

## 用户目标与范围

- 用户本次实际要求：Tanuki 原生界面要与官方网页版功能对齐；负责“功能对齐审计”，不改核心实现，输出可执行的 parity 清单与验收用例。先读 AGENTS.md、docs/AGENT-STATE.md、docs/INTERFACES.md、原生 UI 源码、官方 DSH 客户端 UI/协议（只读）；基于 origin/main=0555c3c0 建独立分支；产出完整功能矩阵、每个缺口的验收步骤（JVM/仪器/手工）、可提交 docs/测试清单 PR 或在交接中落盘；定期回报；author/committer=leezzou；PR 交付、不合并、不发布。
- 本次包含：14 类功能域矩阵（会话、对话、输入、附件、模型/推理、审批/权限、工具调用、工作区/文件、搜索、设置、通知、技能/插件、远程、安全模式）；每行状态/优先级/归属/验收用例；验收用例的执行方式与设备前置；矩阵一致性检查脚本；并行分支只读核对；本次交接记录。
- 本次不包含：修改产品代码；实现原生发送/审批/附件/会话管理等缺口；运行 JVM/仪器/设备测试；真实模型/账号验收；合并、发布、创建 tag/Release；claim/attach 他人 PR。
- 用户改变或暂停的要求（需保留）：API Key 保存问题仍由用户暂停；真实模型/账号验收、真机验收仍暂停；设备由独立 QA 独占；不得把宿主结果冒充 Android 执行。

## 当前状态

- 已实现：`docs/NATIVE-PARITY.md`（166 行矩阵，14 个功能域，每行含官方行为、原生现状、缺口/归属/验收）；`scripts/check-native-parity.py`（矩阵结构、ID、状态、优先级、验收引用、路径检查，带 `--self-test`）；本交接记录。
- 已验证：`python3 scripts/check-native-parity.py --self-test` PASS；`python3 scripts/check-native-parity.py` PASS（166 行、14 域）；`python3 -c "compile(...)"` 语法检查 PASS；`python3 scripts/check-handoff.py --record ...` 与无参数 `check-handoff.py` PASS（594 public files）。
- 待验证：所有 JVM/仪器/手工验收用例均未执行；矩阵中的“原生现状”来自源码/既有测试与只读分支核对，不代表当前设备行为。
- 当前目标包名、分支/无 Git 状态：分支 `ao/parity-audit/root`，基线 `0555c3c0`；无运行中产品任务；未安装 APK、未启动设备。
- Rebase（2026-11-10）：PR 从 `0555c3c0` rebase 到 `origin/main=533eff4`；冲突仅 `docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json`。AGENT-STATE 保留 main 的 PR #34 内容并追加本审计链接；CURRENT.json 按新 main 指纹重新生成。
- Review P2 补强（2026-11-10）：检查器新增“跨功能域复用”校验；29 个合理跨域用例均补 `- 跨域：` 理由，未说明理由的跨域复用直接失败。这样既保留共享用例，又能发现“同一用例被不同能力蹭用”的可疑情况。
- Review P2 已处理（2026-11-10）：SESS-14/15、SET-05/07/15 等缺口不再引用不覆盖它们的共享用例；新增 22 个缺口用例（goals、schedule、快捷键、语言、遥测、工作区管理、消息操作/链接/查找/滚动/中断、权限预设、Office/PDF、自动审查/CoT、账户配额、语音、计划模式、全局搜索、应用内徽标、文件管理器打开、应用更新、存储/备份、复制会话 ID/日志导出、@ 提及 UI），每个用例用 `- 覆盖：` 声明真正验证的矩阵行；检查器升级为拒绝“行引用了不覆盖该行的用例”。
- Review P2 第二轮（2026-11-10）：A-INPUT-01 增加 `fileReferences/list`、`@` 提及解析/候选/引用提交断言；新增 A-INPUT-06（@ 提及 UI/引用卡片）；SESS-16 改接 A-SESS-07（复制完整 ID/日志导出）；MODEL-10 改接 A-MODEL-05（account 余额/配额）；A-FILE-06 区分“文件管理器显示”（不复制）与“系统关联打开”（允许 `cacheDir/workspace-open` 单文件只读副本，验证只共享选中文件、只读 URI、原文件不变、临时目录清理）；同步移除不诚实覆盖（MODEL-10 从 A-MODEL-01，SESS-16 从 A-SESS-02/04，FILE-14/16 从 A-FILE-02，SET-10/13/14 从 A-SET-03，CHAT-19 从 A-SESS-05）。
- 编排器范围确认（2026-11-10）：`android_agent-44` 是实现会话管理（DshSessionManagerActivity/Controller/Page、DshSessionAdminApi），不与本审计重复；`docs/NATIVE-PARITY.md` 与 `scripts/check-native-parity.py` 由 android_agent-47 唯一维护，其他 worker 只提交证据、不双写矩阵。P0 已拆到 INPUT-01/02/03、CHAT-15/11、SESS-02/04/12、APPROVAL-01/02/05/06 等行，并标注 42 聊天链路 / 45 审批 / 46 输入 / 44 会话管理。
- 关键事实与可定位源码/公共文档：
  - 原生只读页：`app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt`、`DshNativeController.kt`、`app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt`；PR #26（android_agent-33）已合并 `d9b4aa2`。
  - 官方/原生切换：`app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt`；`docs/NATIVE-UI.md`、`docs/INTERFACES.md` 第 1.7 节。
  - 并行进行中：android_agent-42 未提交 `DshMuxProtocol.kt`/`DshWebSocket.kt`（session/follow、`$events`、审批/提问解析）；android_agent-45 tool-approval、android_agent-46 input-attachments、android_agent-48 remote relay 已建分支无提交；android_agent-44 分支名与本审计同题但无提交。
  - 既有能力：Android 聊天通知 PR #22（`c2f1b44`）、远程 SSH PR #29（`9e7edbb`）、安全模式 PR #25（`5fd1ab4`）、DOCX/XLSX/PDF PR #28/#23/#24、更新检查 PR #19、版本管理 PR #18、视频全屏 PR #17。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `docs/NATIVE-PARITY.md` | 新增功能对齐审计：状态/优先级图例、P0 拆解与 42/45/46/44 归属、14 类矩阵、64 个验收用例（每个声明覆盖行）、自动化与设备前置、并行归属与阻塞、矩阵唯一所有权、不做/不宣称、维护规则。 |
| `scripts/check-native-parity.py` | 新增只读文档门禁：校验 14 域、行 ID 唯一/归属正确、状态/优先级、验收用例定义、每行引用的用例必须覆盖该行、覆盖声明只能引用存在的行、跨域复用必须有 `- 跨域：` 理由、反引号仓库路径存在；`--self-test` 用内置样例验证能发现坏行、覆盖错配与可疑跨域复用。 |
| `.github/workflows/verify.yml` | 在 Host job 增加 `Check native parity matrix` 步骤，运行同一脚本，避免矩阵随代码漂移。 |
| `docs/handoffs/20261110-1140-native-parity-audit.md` | 本记录。 |
| `docs/AGENT-STATE.md` | 仅增加本审计记录链接；不改写当前状态、其他记录或印章。并发汇总仍由编排器指定的维护方完成。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/check-native-parity.py --self-test` | PASS（有效样例通过；坏样例发现 5 个问题） | 本机 Python 3；只验证检查器逻辑，不验证矩阵事实。 |
| `python3 scripts/check-native-parity.py` | PASS（166 行、14 个功能域；ID/状态/优先级/验收引用/路径检查通过） | 只验证文档结构；矩阵中的产品结论来自源码/只读包，未跑产品测试。 |
| `python3 -m py_compile scripts/check-native-parity.py` | 失败（沙箱禁止写宿主 Python 缓存 `~/Library/Caches/com.apple.python/...`） | 不是脚本语法错误；改用下一条等价检查。 |
| `python3 -c "compile(open('scripts/check-native-parity.py',encoding='utf-8').read(),'scripts/check-native-parity.py','exec')"` | PASS（syntax PASS） | 不写字节码缓存；只验证 Python 语法，不执行产品代码。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261110-1140-native-parity-audit.md` | PASS（Handoff refreshed，594 public files） | 刷新印章；新文档/脚本已进入源码指纹。 |
| `python3 scripts/check-handoff.py` | PASS（Handoff current，594 public files） | 无参数检查印章当前。 |
| `python3 scripts/check-publication.py --git` | 0 findings（595 public files） | 提交前运行；未暂存前与提交后各核对一次。 |
| GitHub Actions Host job `Check native parity matrix` | 未在本机执行；PR CI 为准 | 已接入 `.github/workflows/verify.yml`，本轮未等待 CI 完成。 |
| 产品 JVM/仪器/设备 | 未执行 | 本任务为只读审计；不把历史测试当本轮结果。 |

- 哪些是前一轮结果，哪些是本次新验证：本次只新跑矩阵检查器与自测；PR #22/#25/#29/#28/#23/#24 等是既有记录，未在本轮复跑。
- Review P2 修复：新增 22 个缺口用例并补 `- 覆盖：` 声明；检查器从“编号存在”升级为“编号存在且覆盖该行”；再新增跨域复用校验，合理跨域用例补 `- 跨域：` 理由；`--self-test` 覆盖错配与可疑跨域用例均通过。矩阵仍 166 行、14 域、66 个用例。
- 内容复核：修正 10 处措辞，区分 Tanuki 专有运行时/存储能力与官方网页能力，并把 android_agent-42 的 mux/follow 明确标为“未提交、进行中”；矩阵门禁复跑 PASS。
- 范围确认后更新：新增 1.1 P0 拆解表，44 行矩阵补充 42/45/46/44 归属；矩阵仍为 166 行、14 域，门禁复跑 PASS。
- 未测试、超时、失败和暂未复现的情况：所有产品验收用例未执行；真实模型/账号、真机、ROM、远程 sshd、Keystore 均未测试；矩阵中的 `进行中` 分支未提交，不能写成已实现。

## 未完成与暂停项

- 未完成：
  - P0 缺口：原生页无 composer/发送/停止/审批回答；android_agent-42 的 mux 解析尚未接 UI，45/46 尚未提交。
  - P1 缺口：会话生命周期、模型、附件、会话搜索、工作区管理未排期。
  - P2 缺口：工具卡片、终端/浏览器/任务/子代理、插件/技能、交付物、完整设置、远程中继/多主机/Ed25519。
- 外部阻塞：44 已确认实现会话管理，边界已明确；仍需 42/45/46 的接口稳定并提交后再更新矩阵状态。矩阵/检查器由 47 唯一维护，其他 worker 只提交证据。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。
- 设备：本审计未启动/安装/跑仪器；仪器用例需独立 Android QA 会话执行。

## 下一步

1. 运行 `python3 -m py_compile scripts/check-native-parity.py`、`python3 scripts/check-handoff.py --record docs/handoffs/20261110-1140-native-parity-audit.md`、`python3 scripts/check-handoff.py`，回填本记录。
2. 运行 `python3 scripts/check-publication.py --git`，按实际路径暂存 `docs/NATIVE-PARITY.md`、`scripts/check-native-parity.py`、`.github/workflows/verify.yml`、本记录与 `docs/AGENT-STATE.md` 链接；文档提交用 `docs: add native UI parity audit and acceptance matrix`，CI 接线用 `ci: check native parity matrix`。
3. 推送分支并创建 PR（base `main`），正文写摘要、实际验证命令、未验证范围与风险；不合并、不发布、不触发 AO Review（除非用户/编排器明确要求）。
4. 44 实现会话管理、42 接 mux/UI、45 接审批、46 接输入后，由矩阵属主核对 PR/commit/测试证据并更新 SESS/CHAT/INPUT/APPROVAL 行；42 的 mux 接 UI 后优先更新 CHAT-15、SESS-12、APPROVAL-01/05 状态。
5. 独立 Android QA 按第 4 节仪器/手工用例执行；真实模型/账号用例等用户恢复授权。

## 约束与风险

- 数据/设备/权限边界：不读取/输出真实 API Key、Cookie、token、用户会话或内部备份；不使用真实用户数据；不启动设备、不安装 APK、不跑仪器；设备由独立 QA 独占。
- 关键决策及理由：把“官方 WebView 可用”和“原生已对齐”分开记录，避免把回退能力写成原生实现；矩阵按 P0/P1/P2 与 L1/L2/L3 分层，便于排期；验收用例优先用合成 HTTP/WebSocket 服务做 JVM 自动化，设备相关标记为仪器/手工。
- 失败尝试与避免重复踩坑的结论：官方客户端前端 bundle 是动态插件加载，不能只靠 bundle 字符串枚举功能；改用官方客户端只读包 README、协议方法名与前端 devDependencies 组合取证。远程行曾把官方能力写得过宽，已改为只陈述可核实的“可信主机/远程访问配置”，Tanuki SSH 明确为原生能力。
- 协议风险：官方 0.2.1-rc.5 的 `session/follow`、`$events`、审批 waterfall 若升级调整，必须先更新 `docs/INTERFACES.md` 与回归，再改实现。
- 并发风险：`docs/AGENT-STATE.md` 是共享文件；本次只加链接，未改写当前状态。若其他 worker 同时写该文件，合并时以编排器指定维护方为准。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮不构建 APK、不生成源码 ZIP、不更新 `artifacts/`。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布；无 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：已核对 `docs/handoffs/` 现有 18 份记录；它们仍被 `docs/AGENT-STATE.md` 引用或含未完成的设备/Review/QA 事项，本轮未判定为可安全删除，未删除任何旧交接。保留本记录、TEMPLATE、CURRENT.json 与仍使用的验证/未完成记录；若后续集中清理，按 `docs/PUBLICATION.md` 先迁移独有结论再删除。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
- 交付物：
  - `docs/NATIVE-PARITY.md`（主交付）
  - `scripts/check-native-parity.py`（可执行门禁）
  - 本交接记录

结束前执行：

```bash
python3 -m py_compile scripts/check-native-parity.py
python3 scripts/check-native-parity.py --self-test
python3 scripts/check-native-parity.py
python3 scripts/check-handoff.py --record docs/handoffs/20261110-1140-native-parity-audit.md
python3 scripts/check-handoff.py
```

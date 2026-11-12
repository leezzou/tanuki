# Tanuki — Agent 工作规则

此文件是每任 Agent 的接手入口，适用于整个工程。不要依赖此前聊天、`.build` 缓存或前任 Agent 的记忆。

## 1. 每次接手先读

1. [当前状态](docs/AGENT-STATE.md)：当前交付、暂停项、下一步及最新交接记录；交接分工与共享状态规则见 [交接机制](docs/HANDOFFS.md)。
2. 当前状态指向的 `docs/handoffs/` 交接记录。
3. [上手指南](docs/AGENT-QUICKSTART.md)：环境、构建、验证、模块地图。
4. [README](README.md) 和任务相关文档。安全/运行改动先读 [SECURITY](docs/SECURITY.md)；测试结论对照 [TEST-REPORT](docs/TEST-REPORT.md)。
5. 按角色读专题规则：[编排规则](docs/ORCHESTRATOR-RULES.md)、[QA 门禁](docs/QA-GATES.md)、[经验规则](docs/LESSONS.md)、[代码规范](docs/code-standards.md)；接口以 [INTERFACES](docs/INTERFACES.md) 为准。
6. 当前 baseline 以远端实际 SHA 和 provider 状态为准，不把本地 `main`、旧交接或 PR 描述当最新；开始任务时记录实际 SHA。

历史报告、截图和旧交接已按用户上传前精简要求清理，必要实现决策归入专题文档，验证摘要保留于 `docs/TEST-REPORT.md`。历史测试数字/包名不能覆盖当前状态。源码是实现事实，验证记录是“已测”依据；冲突必须查证，不凭旧摘要改代码。

## 2. 每任 Agent 必须交接（包括未完成、受阻或只读任务）

交接分两层，不要混写：

- **普通 PR**：只新增/补完本次唯一记录 `docs/handoffs/YYYYMMDD-HHMM-topic.md`，从 [模板](docs/handoffs/TEMPLATE.md) 复制。**不要修改 `docs/AGENT-STATE.md`，不要修改 `docs/handoffs/CURRENT.json`**；共享状态由集成维护方统一刷新。
- **集成维护方**（同一时间只有一人）：在一批 PR 合并后、发布前或状态明显落后时，更新 `AGENT-STATE.md` 的当前指针与最近 N 条，再运行 `--integrate` 重建印章。
- **发布维护方**：打包前运行 `--release` 严格核对印章、记录哈希与公开源码指纹。

**开始时**创建本次唯一记录，写明本次用户目标、范围和开始状态；不要只写“继续上次”。

**结束、移交或预计中断前**必须：

- 补完本次记录：改动文件与目的、实际验证命令及结果、未测试范围、失败和原因、关键决策、未完成/暂停项、下一步可执行动作、数据与产物边界。
- 更新受影响的 README/专题文档。结论必须能在源码/公共证据中定位，不能仅指向未打包的 `.build`。
- 普通 PR 结束前运行：
  ```bash
  python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md
  ```
  记录自身完整即可通过；`AGENT-STATE.md` 未链接、`CURRENT.json` 印章落后都是允许的，不要为了让检查通过去改共享文件。
- 集成维护方在最新 main 上更新 `AGENT-STATE.md` 后运行：
  ```bash
  python3 scripts/check-handoff.py --integrate --record docs/handoffs/<最新记录>.md
  python3 scripts/check-handoff.py --release
  ```
- 手工发布前必须 `--release` 通过；`scripts/prepare-app-release.py` 默认强制执行，严格模式只读。每次合并的自动队列使用[发布说明](docs/RELEASE.md)中的精确 SHA/tree、本 PR 交接守卫与合并前门禁回执，不改共享状态、不自动补章；此例外不跳过审查/CI/适用 QA。
- 交付源码前再运行 `python3 scripts/package-source.py`，核对 `artifacts/SOURCE-SHA256SUMS`。修改文档也会改变源码包，不能继续发旧 ZIP。
- 具体分工、时间点、迁移步骤与冲突处理见 [交接机制](docs/HANDOFFS.md)。

只读权限的 Agent 不得违反权限去写文件：输出同模板的完整交接文本，明确“未落盘”，交由主 Agent 保存。并发子 Agent 返回结论和证据，由主 Agent 汇总；不要多人同时覆盖当前状态或印章。每轮只指定一名集成维护方处理 `AGENT-STATE.md` / `CURRENT.json`，其他并发 Worker 只回报告，绝不修改共享状态。下一任 Agent 建立新记录，工作过程中保留旧记录，不无理由改写历史。

**每次 Git 提交或推送前，必须先清理已过期的交接文档**，不能只新增记录而持续堆积：

- 过期指任务已完成或被替代，必要结论已完整归入当前状态、专题文档或 `docs/TEST-REPORT.md`，且没有独有的未完成事项；不能仅按日期或“不是最新”就删除。
- 保留当前交接、`TEMPLATE.md`、`CURRENT.json`，以及仍被使用的验证依据和未完成记录；不要依赖 Git 历史或 `.build` 才能接手。
- 先迁移仍有用的事实，再删除过期记录、修复所有受影响引用，并在本次记录写清清理范围、理由及保留位置。已跟踪文件的删除也须纳入本次明确暂存范围。
- 普通 PR 只清理本次确认过期、且属于本次范围的记录；跨 PR 清理和共享状态刷新由集成维护方执行。
- 清理后普通 PR 重跑 `--pr`；集成方重跑 `--integrate` / `--release`，再重打源码包并核对外部 SHA。实际暂存不能残留已判定过期的记录。具体顺序见 [上传检查](docs/PUBLICATION.md)。

交接检查使用公开源文件内容指纹，不依赖 Git、mtime 或本机路径。普通 PR 模式校验本 PR 记录结构，并强制“公开文件有改动就必须附带交接记录”；集成/发布模式才校验共享状态与公开源码指纹。它不能证明自然语言内容真实，仍必须如实记录验证和未完成项。

## 3. 数据、安装和权限硬约束

- 不读取/输出真实 API Key、Cookie、启动 token、用户会话或内部备份。公共证据只用合成数据；原始诊断日志默认放 `.build`，不可直接打包。上传前按 `docs/PUBLICATION.md` 检查 Git 候选和实际暂存内容，保留必要许可署名，不用忽略规则冒充已脱敏。
- 不为排障执行 `pm clear`、卸载用户 App、wipe AVD、恢复旧数据快照或终止其他应用/所有 Node 进程。需要破坏性动作时先取得明确许可。
- 当前目标从 `docs/AGENT-STATE.md` 获取；不要恢复用户已卸载的旧 `.modern`。不同构建共用 APK 输出路径，安装前核验 package 和 instrumentation target，不能凭文件名判断。
- 用户已允许使用模拟器测试代码：每台开发机优先复用同一个既有测试 ARM64 AVD，不得每任 Agent 重新创建。先列出现有 AVD、确认数据可测且无安装/真实任务，启动后明确记录 AVD 名和 serial；本开发机沿用 `Pixel_7_API_34_arm64`。只有没有可用测试 AVD 时才新建。无需反复询问是否可以开模拟器，不 wipe、恢复旧快照或清空既有环境；真机测试仍需另外确认。
- 不对正在安装环境、执行 apt/dpkg 或运行真实任务的应用覆盖安装/启动 instrumentation。设备必须明确选择，数据必须确认可用于测试。
- API Key 保存问题目前由用户暂停；未被新要求恢复前，不自行调查、修改或宣称修复。
- 不自动切换 DSH 的 `danger-full-access`，不关闭审批/TLS/哈希校验，不把 PRoot 宣传成恶意代码沙箱。
- 浏览器查看与操作授权分开：合法工具连接即使没有 AI 操作权限、或用户正在手动浏览，也可列出普通标签、查看当前普通页面及截图；仍须校验 capability 和客户端身份。查看不能隐式切换标签或改变页面；敏感 OAuth 临时页不给 AI 读写，也不出现在 AI 标签列表中。用户自身查看浏览器始终不受 AI 操作授权限制。
- 浏览器操作授权以当前用户选择为准：默认授权工具连接，触碰先暂停 AI 操作再询问；临时接管连续 30 秒无点击、滑动或输入后自动交回操作权，持续交互重置计时。显式撤销 AI 操作权限持久保留，不因计时/切页/后台/重启恢复；切页/后台本身不撤销。点击、输入、滚动、导航、标签变更、查找和缩放都需要操作授权；不要把撤销操作权重新解释为禁止 AI 查看，或套用旧“AI 请求后才授权”方案。

## 4. 开发与验证约定

- 新增和修改的第一方代码注释使用中文；shebang、API标识、第三方版权/许可证保留必要原文，不为翻译改动官方依赖。
- 保留官方 DSH UI/模型/插件/Skills，不另造聊天框，不直接修改已安装的官方 npm 包。原生层负责运行、文件、浏览器和安全边界。
- 涉及安装/下载/导入导出/快照/缓存清理，必须有阶段与耗时反馈；已知总量显示真实进度，未知总量用不定进度，不能造百分比。
- 不把大量磁盘/网络、同步日志重写、全部 WebView 创建/绘制放在主线程。浏览器保留当前页面后台能力；不要调用会影响所有 WebView 的全局 `pauseTimers()`。
- 完整页面截图不能退回原生小窗裁切。`enableSlowWholeDocumentDraw()` 是不可关闭的进程级设置；改动它或进程边界必须重新验证页面尾部像素、AI 缩放和收起小窗，不能只看 DOM。
- 业务代码改动先补合适的回归；构建、JVM、lint 与 Android 仪器测试分别记录。不要把跳过、host timeout 或仅存在测试源码计为通过。
- 同一工作目录不要并行运行不同 flavor/init-script 的 Gradle 构建；不要和实际安装同时跑测试。耗时设备任务给足超时，不能因 ADB 端断开就误判测试成功/失败。
- 本轮未修改产品代码时，如实说明只做了文档/脚本验证，保留前一轮产品测试范围，不冒充重跑全套。
- README 保持简短产品介绍与必要入口；实现、验证和发布细节放专题文档，不把初版首页重新扩成维护报告。
- 新增根目录交付文件时检查 `scripts/package-source.py` 的明确清单；`.build`、本机配置、密钥不得变成必要交接依赖。
- 长期分工：编排/规划/管理、实现、测试、独立 Android QA 和复盘由 DSH 承担；Codex/GPT High 只做独立 Review，不参与开发或编排。按任务独立性并发 1 至 3 个 Worker（默认最多 3 个），同一核心文件或共享接口避免并发写。合并/配置决策前先用 `ao session get` 与 `ao project get` 核对实际值，不自行改 AO 配置。
- 跨端协议、RPC 方法集和共享接口先定契约再实现；依赖其他分支或外部交付时，先集成到最新基线再刷新验证，不把集成前结果当集成后结论。
- 同一问题连续两轮无实质进展时停止盲试，先分析根因、缩小复现范围或更换方案；仍无进展则报告阻塞并请用户决策。
- 独立审查绑定 PR 当前完整 SHA；推送新提交后旧批准不沿用。Worker 自评、Host 结果和模型自述不能替代独立审查或设备 QA。

## 5. 发布约定

- 用户最新要求 main 每次合并由本机持久队列自动发布，patch 按整数递增（0.1.99 后为 0.1.100、0.1.101），不逐次询问版本、不派 Agent 上传。版本/code 按真实公开/保留身份分配；合并前当前 SHA 的独立审查、CI 和适用 QA 不变。服务需经审查验证后由集成维护方显式启用，详见 [发布说明](docs/RELEASE.md)。日常 debug/验证构建不等于发布。
- 用户已明确要求固定顺序：完成修复、候选构建和全部适用测试，收尾版本/测试/专题文档与交接、清理过期记录并完成上传检查，然后统一一次 commit/push，最后生成正式交付附件。不要为回填版本文档或测试收尾拆成多次推送。提交前源码候选包只用于检查；用户取用的发布目录在推送成功后统一刷新。
- 确认包名/签名渠道与递增 `versionCode`（必须高于该包已安装/已发布的所有版本），再使用 `scripts/prepare-app-release.py --version <确认版本> --version-code <递增代码> --channel <workspacecheck|production>`。工作区验证包与正式包不能互相覆盖。
- 打包必须核对 APK 实际包名、版本、Android 要求、证书、大小/SHA-256，输出 `app-update.json`、对应源码与校验清单；保留安装环境，不卸载或清数据。正式密钥须在仓库外，通过环境传递密码，不读取/输出密钥或密码。
- 产物按 [取包说明](docs/ARTIFACTS.md) 分类：完整发布在 `artifacts/releases/<版本>/`，源码在 `sources/`，签名中间包在 `builds/`，旧包在 `archive/`。不在根目录散放 APK/ZIP；发版时同步取包说明与生成的 `artifacts/README.md`，不能按最大版本号选择已撤回候选包。
- 合并、发布、创建 tag/Release 和修改保护策略/权限都需要用户明确授权；没有授权时只交付可合并候选，不代合、不直推默认分支。合并前按 [编排规则](docs/ORCHESTRATOR-RULES.md) 完成当前 SHA 的独立审查、适用 CI 和 QA；AO approved 不等于 GitHub required approval。
- 打包不等于创建 tag、上传 GitHub Release 或自动推送；按用户当前授权分别执行。开发签名包标 Pre-release。完整流程见 [发布说明](docs/RELEASE.md) 与 [应用内更新](docs/APP-UPDATES.md)。

## 6. 推荐入口

```bash
# 不操作设备；先按上手指南准备 JDK17、SDK36/Build Tools 35、NDK27.2.12479018。
python3 scripts/check-handoff.py --pr          # 普通 PR 交接检查
python3 scripts/check-handoff.py --release     # 仅集成/发布前：严格核对共享状态
bash scripts/verify-workspace.sh

# 仅确认无真实数据、已装运行环境、无安装任务的 ARM64 测试设备：
bash scripts/verify-workspace.sh --run-device-tests --confirm-test-data --device <SERIAL>

# 合并前只读门禁（需要当前 PR、独立 review 与适用 QA；不改任何文件）：
python3 scripts/check-development-gates.py --pr <PR> --session <AO worker id> [--qa-evidence <json>]
python3 scripts/test-development-gates.py
```

对外报告始终区分开发 debug 包与正式签名、模拟器与真机、合成登录与真实微信登录、无模型诊断与实际模型任务。完成局部工作不能等同于整个产品已正式验收。

# 全应用文案清单与改写方案

- Agent/会话标识：android_agent-86
- 时间：2026-11-11 CST
- 状态：文案方案已完成；产品文案未修改；通过独立文档分支提交 PR，不合并或发布
- 前一记录：[当前项目状态](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/docs/AGENT-STATE.md)
- 基线：远端 main `4710c5e08ffa8c86d8cdc1bffbec1a1052be0935`，已用远端查询核对；不是仅依据本地 main。
- 预定入库路径：`docs/handoffs/20261111-1730-copy-audit.md`。此文件同时承担本轮唯一交接，不另加重复记录。

## 用户目标与范围

本轮只交付完整清单和中文改写方案。覆盖资源 XML、全部生产 Kotlin 中的页面、对话框、按钮、输入提示、错误分类、安装阶段、通知、Toast、权限及无障碍描述。保留每个出现位置，包括重复文案；底层异常、事件和诊断输出采用扩大范围登记，避免仅搜 `Text` 漏掉上游文案。所有建议独立编写，原文涉及外部参考名的部分已省略，完整源码位置仍可定位。

读者是首次使用或遇到安装、连接问题的普通用户。首页先回答“能做什么、接下来做什么”；出错时先说明失败和可执行动作；设置中只解释选择的影响。熟悉开发的用户仍可在默认折叠的详情中查看版本、地址、校验和运行记录。

不修改产品代码、资源、测试、权限声明、接口或构建设置。不恢复 API Key 保存调查，不使用设备或真实账号，不更改现有暂停项。官方运行程序在连接后生成的界面、模型回复、网页内容、用户文件名和自定义技能文本不是仓库可枚举的固定文案；本轮登记这些动态出口及约束，不捏造完整第三方字符串列表，也不修改已安装程序。

## 当前状态

已完成源码盘点、专项改写、保留/排除分类、术语表、重复说明收口、长度规则和下一轮验收方案。开始时 main 为 `7cfe1446effa76d1e7901f839d6a22996778d013`，收尾发现 #88 已合并，已从 Git 只读归档建立最新 `b324d54d` 源码快照并全量重扫；交付时 #89 动效改造又合并到 `4710c5e0`，已读取全部 14 个变化的生产文件并刷新全量出现位置，最终审计固定在 `4710c5e0`。本报告及全量表均刷新到最终基线，后续并发合并不继续自动漂移。#88 已实现更新页短摘要与详情折叠、安装期文件可浏览但写入受限、旧安装重试失效等行为；方案保留这些事实，不把已经完成的改版再次当作待实现。工作区起始干净，原分支 `ao/copy-audit/root`。Git 写操作起初被默认沙箱拒绝，先交付完整 artifact；编排方随后明确要求逐条授权重试。建分支、fetch 和快进均在各自获准后成功，已核对分支 `ao/copy-audit/plan`、HEAD 与基线一致，工作区起始干净。本轮仅新增本审计文档并按请求提交文档 PR；不合并、不发布。

本文件含三个层次：术语与执行规则、需要优先理解的路径和动态出口、逐文件全量表。全量表中的“保留”也是审计结论，不要求为改而改；“非展示”是排除证据，不计为已显示的用户文案。底层错误项列出建议的**展示文案**，不意味着直接替换异常匹配词或协议键。

## 文案风格三条

1. **说人话**：以用户正在做的事命名。说“正在准备安装文件”“助手启动失败”，不把实现步骤、对象名或异常类名放进主提示。
2. **说清下一步**：写清可执行动作和必要后果。失败后告诉用户检查什么、在哪里重试；归档、删除、停止或扩大权限前说明影响。
3. **不甩术语**：把协议、算法和内部路径留在折叠详情。需要用户亲自输入或核对的地址、端口、私钥类型、安全指纹保留原值，并附用途；不要求用户理解内部实现才能继续。

## 调试感黑名单

下列词和表达不应直接出现在普通页面的主标题、按钮、说明、错误摘要或通知中。全量表保留原文用于定位；内部协议键、错误分类匹配词和命令参数不做盲目替换。确需排障的原始信息经脱敏后只放默认折叠的“问题详情”；用户必须亲自核对的地址、端口、私钥类型和完整安全指纹保留原值。

| 黑名单类别 | 词或表达 | 面向用户的处理 |
|---|---|---|
| 空值与占位 | null、TODO、undefined、未知对象、对象默认字符串 | 缺少可选信息时不显示；缺少必要信息时说明重新选择或重试 |
| 内部连接实现 | interop、proot、transport、relay、loopback、隧道内部端口 | 连接、运行环境、连接服务；详情保留必要实现信息 |
| 内部标识 | hostId、sessionId、callId、requestId、workerId、短 ID、stage | 电脑名称、对话名称、当前步骤；确需反馈的编号放问题详情 |
| 登录实现 | Cookie、cookie、token、Bearer、认证头 | 登录状态或登录验证码；不得展示真实凭据或完整含凭据链接 |
| 校验与归档 | sha256、SHA-256、sha512、SRI、依赖锁、lockfile、安装事务、元数据 | 检查文件、安装信息、版本信息；不要求用户理解校验算法才能继续 |
| 包管理与调度 | npm、apt、dpkg、worker、下载并发窗口、归档映射 | 安装文件、安装组件、下载速度；高级参数放详情 |
| 英文异常与协议 | IOException、IllegalStateException、SocketTimeoutException、UnknownHostException、stack trace、HTTP 原始响应、WebSocket 帧错误 | 按网络、空间、权限、校验、启动等原因给中文摘要与实际下一步 |
| 实现补注 | “（已实现）”“（暂不支持）”“直连切片”“待协议确认”“原生不另建会话库”“回答会走官方协议” | 删除实现自述；当前入口不可用时明确去网页界面或可用替代入口，不暗示已实现 |
| 免责或伪精度 | “不代表整个安装已完成”“进度暂不可估算”“未提供可信总量”“剩余时间未知”、无依据的固定分钟数 | 当前真实动作、已用时间；有可信下载速度样本时才显示下载剩余估计 |
| 无意义重复 | 每卡重复数据保留声明、每页重复权限时效、按钮后再解释按钮含义 | 完整后果保留在相关确认弹窗，常驻区域只显示状态与下一步 |

黑名单针对应用自己的说明，不抹去用户输入、模型名、命令名、软件来源和许可署名，也不弱化权限扩大、数据覆盖、任务中断或信任指纹变化的风险。

## 术语表（概念 → 唯一叫法）

| 概念 | 唯一叫法 | 使用边界 |
|---|---|---|
| 本应用 | Tanuki | 保留产品名，不改名称资源或许可署名 |
| 提供开发能力的程序/引擎 | 助手 | 首页及通知不展示内部缩写；版本、许可与来源详情保留官方组件名 |
| 多轮消息的容器 | 对话 | 新建对话、对话名称、对话列表；持久化和协议键保持不变 |
| 一次请求到回复结束 | 本次回复 | 不是整个对话，不用“会话回合” |
| 当前工作目录集合 | 工作区 | 保留用户已有心智；不与“工作台”混用 |
| 首页 | 工作台 | 是入口页，不是文件夹或运行环境 |
| 可以运行助手的本机组件集合 | 运行环境 | 不在同一层级使用“引擎/运行时/环境”三种称呼 |
| 操作系统基础组件 | 系统组件 | 安装卡主标题；来源详情保留准确软件名 |
| 程序执行所需组件 | 运行组件 | 与系统组件、助手区分；版本详情保留准确软件名 |
| 依赖、归档、安装锁 | 安装文件 / 安装信息 | 文件是下载内容，信息是安装清单，二者有不同含义，不强合为一词 |
| 当前使用的手机端 | 本机 | 本机助手，不同时叫“本地运行时” |
| 另一台运行助手的设备 | 远程电脑 | 远程电脑、电脑地址；有用户名称时使用其名称 |
| 系统网页内核 | 网页组件 | 系统设置入口可补充系统实际名称，方便用户找到 |
| Android 原生页面 | 手机界面 | 与网页界面对应；不宣传未实现功能 |
| 官方 Web 页面 | 网页界面 | 不在按钮中反复加“官方”或缩写 |
| 下载源/镜像站 | 下载源 | 操作按钮“更换下载源”；提供方名称可在列表保留 |
| 中继服务 | 连接服务 | 使用配对链接时显示；独立于目标电脑身份核对 |
| 验证是否是目标电脑的指纹 | 电脑安全指纹 | 原指纹逐字保留，不截断成可误认的短编号 |
| 配对链接中服务证书指纹 | 服务安全指纹 | 与电脑安全指纹区分，不能互相替代 |
| 启动登录 token | 登录验证码 | 只改字段显示名；原始格式、传输和脱敏不变，不展示真实值 |
| 工具审批 | 授权 | 待授权操作、允许一次、拒绝；不与系统权限申请混为一谈 |
| 记忆的审批 | 已记选择 | 只对本次运行、同对话/同工具/同风险有效，不扩大范围 |
| 工作区容量预算、软上限 | 工作区限额 | 是定期检查的限制，不承诺即时阻止每次写入 |
| 使用剩余磁盘 | 使用可用空间 | 不等于无限容量 |
| 程序私有快照 | 应用数据备份 | 包含账号信息、设置、扩展和对话；只在应用内保存 |
| 只备份项目内容 | 导出项目 | 与应用数据备份区分，提示自行检查敏感文件 |
| 扩展使用指南 Skill | 技能 | 界面用“技能”，命令名称和安装路径不改 |
| 斜杠命令 | 快捷命令 | 实际 `/命令` 原样保留并可复制 |
| reasoning effort | 思考深度 | 只改展示名，不改模型实际参数 |
| 技术细节/技术详情 | 问题详情 | 默认折叠，必要原始信息先脱敏；主界面始终是中文摘要 |
| 已安装但不在使用的程序版本 | 备用版本 | “安装为备用”不等于切换数据或当前版本 |
| 完全权限 | 完全权限 | 保留风险显著性；不得改成“增强模式”等弱化风险的称呼 |

## 分类、长度和动态值规则

- **P0**：首次可用路径、安装与真实进度、错误恢复、应用/组件更新、所有权限与信任确认、空态和破坏性后果。**P1**：日常操作、页面导航、聊天和文件内容呈现。**P2**：高级参数、诊断说明和非展示排除项。优先级不是严重事故断言，也不代表已安排实现。
- 按钮固定文字不超过 **6 字**，标题固定文字不超过 **12 字**，一条说明不超过 **40 字**，标点也计入。表中 `<br>` 为分行。说明超过两行时用一句主提示加“查看详情”；展开后的每条解释仍按短句分行。
- 对话名、文件名、电脑名、模型名、版本、容量、地址和完整指纹是内容，不拼进动作按钮或长标题。标题用“删除这条连接？”“卸载备用版本？”；名称、版本和对象清单放正文。完整指纹在确认区可换行，不通过省略号隐藏安全判断信息。
- `{数量}`、`{已完成}`、`{总数}`、`{大小}`、`{版本}`、`{名称}`、`{内容}` 等是**展示模板占位名**，不是待补写的文案。它们一一对应同一行原文中的动态表达式；下一轮保留实际值和含义，不把模板字面量显示给用户。同句多数量按原文分别绑定，不重复使用一个变量代替成功数和失败数。`{内容}` 仅用于无法从词法确定具体类型的动态来源，按该行源码实际字段绑定，不产生新的产品事实。
- 进度只显示当前真实动作及可用的测量值。总量未知写“正在准备”“正在传输”，不编造百分比、总时长或“还需几分钟”。下载 ETA 有可靠速度样本时可显示“下载剩余约 {时间}”，不能用下载完成推断整次安装完成。
- 原始异常不能因“含一个汉字”就直出。缺省值为按操作归类的中文原因与下一步，未知内部字段和枚举不输出 `null`、对象名、短 ID 或原始代码。动态外部内容仍保留用户原文，必要长内容用折叠/复制，不擅自翻译或截断密钥、命令和地址。
- 底层错误文本目前被关键字分类（例如 FailureCatalog、OnboardingPrepare 和 operationStage）。下一轮优先分开展示与分类依据，保留原始匹配值或先改为结构化原因，再修改展示；不能全仓替换中文而破坏安装阶段识别、按钮状态判断或错误归类。

## P0 必改：路径与最终文案

下列路径是落地顺序，逐条原文和行号以全量表为准。

| 路径 | 主要问题与位置 | 目标文案与下一步 |
|---|---|---|
| 首启欢迎 | `ui/OnboardingScreen.kt:224` 承诺“一分钟”；多段说明过长 | 标题“准备开始使用”；说明“在手机上运行助手，或连接自己的电脑。”；按钮“继续” |
| 设备检查 | `onboarding/OnboardingEnvironment.kt:78`、`:128`，硬件与空间信息混在长说明 | 空间不足保留实际门槛；不把安装存储误说成运行内存；不适用本机时明确“可连接远程电脑” |
| 环境准备 | `ui/OnboardingScreen.kt:337`、`:407`、`:408`；安装知识重复解释 | 首次安装前仅说明联网与空间；通知权限按需申请；进度期只显示当前动作、实际耗时及取消入口 |
| 安装进度 | `ui/OperationProgressPresentation.kt:209`、`:218` 无依据分钟估计；`ui/OperationProgress.kt:235` 原始阶段直出 | 未知时间“正在准备”；已知下载“下载剩余约 {时间}”；按钮“取消操作”；100% 只属于下载时进入下一真实阶段 |
| 错误提示 | `runtime/FailureCatalog.kt:277` 起；其他控制器旁路直出异常 | “助手启动失败，请重新启动。”；网络、空间、权限、校验分别给具体下一步，不一律清缓存 |
| 更新 | `ui/ApplicationUpdatePresentation.kt:13`、`:14`、`:38`；主文已有状态映射，详情仍含工程信息 | 保留现有折叠结构，短摘要及失败文案按清单简化；下载后明确“安装更新”，仍需系统确认 |
| 通知 | `notify/ChatNotificationCopy.kt:11` 起；渠道重复声明不含内容 | 标题“回复已完成”“有内容待确认”“对话出现错误”“有操作待授权”；固定正文，不加入私有标题和消息 |
| 系统权限 | `MainActivity.kt:874`、`:1190`、`ui/WorkspaceFilesPage.kt:731` | 先说权限用途与拒绝后的替代入口；系统授权与“允许本次写入”分开，拒绝通知仍可安装 |
| 手机文件写入 | `ui/WorkspaceFilesPage.kt:433` 起；授权时效反复解释 | 标题“允许助手写入手机文件？”；主文明确新增、修改、删除；停止后恢复只读；按钮“保持只读”“允许本次写入” |
| 浏览器接管 | `ui/AgentBrowserPage.kt:321` 起、`browser/BrowserOverlayWindow.kt:352` 起 | 临时接管 30 秒无操作自动交回；明确收回后只由用户手动交回；普通页仍可查看，登录授权页仅用户可见 |
| 完全权限 | `ui/DshSessionManagerPage.kt:594` 起、`ui/DshSessionPresentation.kt:144` 起 | 保留工作区外读写风险、勾选与二次确认；不将此模式作为安装或命令失败的默认修复 |
| 远程信任 | `runtime/RemoteConnectionError.kt:121` 起、`ui/RemoteHostSettings.kt:786` 起 | “电脑安全指纹已变化，已拒绝连接。”；通过可信渠道核对；不能建议跳过证书或密钥检查 |
| 文件空态 | `ui/TanukiPageVisuals.kt:67` 起、`ui/WorkspaceFilesPage.kt:953` 起 | 真空态“还没有文件”＋“从手机导入”；搜索无结果“没有匹配文件”＋“清除搜索”；隐藏文件“文件已隐藏”＋“显示隐藏” |
| 对话空态 | `ui/DshNativePage.kt:171`、`:525`、`ui/DshSessionManagerPage.kt:362` 起 | 尚无对话给“新建对话”；尚无消息给输入提示；筛选无结果给“清除筛选”，不误导为全部数据丢失 |
| 连接空态 | `ui/RemoteHostSettings.kt:464`、`:736` | 没发现电脑可重新扫描、扫码或粘贴；还没保存连接时说明测试后保存；不让用户面对空列表无下一步 |
| 文件预览空态 | `ui/WorkspaceXlsxPreview.kt:98`、`:247`、`ui/WorkspacePdfPreview.kt:197` | 分清没有工作表、空白工作表、PDF 无页面；失败与无内容不混用；提供其他应用打开或返回 |

## 重复说明：唯一保留位置

全量表会逐个保留出现位置；下表决定信息只在哪里完整说明。重复的短动作标签可复用同一资源，不能为去重删除其他入口的按钮。

| 信息 | 保留位置 | 其余位置处理 |
|---|---|---|
| 百分比仅属当前阶段、下载后还有后续动作 | `ui/OperationProgress.kt:251` 改为“下载完成后，将继续解压、检查和安装。” | 删除 `OnboardingScreen:407/:408`、`OperationProgress:279`、`WorkspaceFilesPage:1280` 的技术解释，各自保留真实动作和数值 |
| 手机文件写入仅限本次运行 | `ui/WorkspaceFilesPage.kt:433` 授权弹窗与 `:1129` 权限弹层；两者不同时显示 | 列表只显示“只读/本次可写”，不常驻整段解释；同一弹层不再次重复说明 |
| 临时接管与永久收回的区别 | `ui/AgentBrowserPage.kt:320` 权限设置；首次接管弹窗保留与本次决定直接相关的后果 | 顶部状态短句，通知只显示当前操作权；不重讲实现协议 |
| 应用更新保留环境、禁止卸载与清数据 | `MainActivity.kt:1197` 安装前确认框保留；更新页面详情可查 | 页面常驻提示只给更新状态和动作，签名/校验算法仅放详情 |
| 归档会移出列表，运行中先停止，无法在本页撤销 | `ui/DshSessionManagerPage.kt:505` 或批量 `:541` 的确认弹窗 | 常驻说明改为一行摘要，不重复接口是否有硬删除端点 |
| 手机/网页界面同一份对话及自动回退 | `ui/TanukiUi.kt:949` 设置项 | 删除 `DshNativePage:340` 的常驻实现解释；引导只说如何选择和去哪里切换 |
| 私有备份范围与卸载即丢失 | `ui/TanukiUi.kt:943` 备份入口和 `MainActivity.kt:996` 执行前确认 | 成功反馈只说“应用数据已备份”；不重复文件夹、组件清单 |
| 下载源策略与安装校验 | 下载源选择弹层 `ui/SourceSpeedControls.kt:97` 与组件来源详情 | 安装主界面只说“正在下载安装文件”，不展示并发、包校验算法和锁文件 |
| 设置页分类与未实现状态 | 每个功能入口的用户可用动作 | 删除 `TanukiUi:846/:890/:902/:922` 的工程自述，保留可用子页入口 |

## 动态出口与非字面量边界表

这是完整固定字符串表的补充；这些风险无法靠搜改 `Text("…")` 消除。所有位置仍固定在上面的 baseline。

| 文件:行（Kotlin 前缀同清单） | 当前来源 / 原文形式 | 问题类型 | 建议文案 / 规则 | 优先级 |
|---|---|---|---|---|
| `runtime/RuntimeManager.kt:175`、`:176`、`:904` | 异常 message 或类名 | 工程痕迹 | 分类后的中文主提示，原始异常仅进脱敏的问题详情 | P0 |
| `runtime/RuntimeManager.kt:179` | 原始异常日志 | 工程痕迹 | 只在折叠日志保留脱敏诊断，不用日志文本当主提示 | P2 |
| `ui/OperationProgressPresentation.kt:167`、`:188`、`:223` → `ui/OperationProgress.kt:235` | stage 原文 | 工程痕迹 | 用实际操作状态生成“正在解压/检查/安装”，不能无百分比时一律说准备 | P0 |
| `ui/OperationProgressPresentation.kt:104`、`:111`、`:117` | failedOperation.reason / snapshot.error | 错误出口 | 统一用户原因映射，原始内容仅进问题详情 | P0 |
| `onboarding/OnboardingPrepare.kt:58`、`:97`、`:103`、`:109` → `ui/OnboardingScreen.kt:401` | 安装阶段原文 | 工程痕迹 | 从真实阶段取短中文，不透传锁文件、退出码和包管理输出 | P0 |
| `ui/DshNativeController.kt:784`、`:787` | messageOf 透传 | 错误出口 | 连接异常请重新连接，未识别异常“操作未完成，请查看问题详情。” | P0 |
| `ui/DshSessionManagerController.kt:811`、`:829`、`:831` | 含中文就直出，其余 message | 错误出口 | 有汉字不代表用户可懂；按操作映射，未知错误不显示原始异常或代码 | P0 |
| `ui/DshComposerController.kt:825`、`:831`、`:833` | 上传/命令原始异常 | 错误出口 | “附件上传失败，请重试。” / “命令执行失败，请查看问题详情。” | P0 |
| `web/DshApprovalProtocol.kt:339`、`:344` | englishOrChinese 保留全部中文异常 | 错误出口 | 使用授权失败分类；不能因包含汉字就跳过清理 | P0 |
| `web/DshApprovalSession.kt:177` | 审批回复异常 message | 错误出口 | “授权结果未发送，请重试。”；不改变失败保留卡片行为 | P0 |
| `ui/DshNativePage.kt:820`、`:859` | detail 直接常驻 | 工程痕迹/重复 | 只显示友好原因；问题详情默认折叠，复制仍可用 | P0 |
| `runtime/RemoteConnectionError.kt:137`、`:142`、`:150`、`:158`、`:169` | RemoteHostFailure.message 直接作为 reason | 错误出口 | 从连接阶段与原因选择中文提示，详细原文另存；指纹变化优先拒绝 | P0 |
| `runtime/RemoteHostController.kt:111`、`:133`、`:614` | 保存/删除/存储错误 message | 错误出口 | 保存失败、删除失败分别映射；保留“已断开但未清除配置”的真实部分结果 | P0 |
| `ui/RemoteHostSettings.kt:483`、`:1703`、`:1854` | 搜索/连接错误和 suggestion | 长度/错误出口 | 一句原因加一句下一步，具体网络配置和命令折叠 | P0 |
| `ui/WorkspaceFilesPage.kt:233`、`:870`、`:1368`、`:1486` | 只凭中文判断可显示，或逐文件 raw detail | 错误出口 | 文件失败按已存在/空间/权限/读取失败分类，分组摘要；原文折叠 | P0 |
| `ui/WorkspaceFileViewer.kt:98`、`:389`、`:187`、`:698` | 文件异常含中文就直出 | 错误出口 | “文件无法读取，请重新选择或用其他应用打开。”，细节单独展开 | P0 |
| `ui/WorkspaceDocxPreview.kt:112`、`ui/WorkspaceXlsxPreview.kt:78`、`:87` | 解析异常直接显示 | 错误出口 | 格式、加密、过大分别说明；完整文件可用其他应用打开 | P0 |
| `ui/WorkspacePdfPreview.kt:470`、`:821`、`:824` | 页面/文件异常原文 | 错误出口 | 显示具体页码与可执行动作，不展示解码实现名 | P0 |
| `ui/WorkspaceStorageControls.kt:75`、`:76`、`:108`、`:122` | 配置/统计/保存 error | 错误出口 | 区分“无法统计”和“无法保存”，给刷新用量或重新保存入口 | P0 |
| `ui/DshVersionManagerDialog.kt:62`、`:72` | 已安装版本读取异常 | 错误出口 | “版本列表读取失败，请刷新。” | P0 |
| `ui/AgentBrowserPage.kt:217`、`:218`、`:373` | 连接或下载 error | 错误出口 | 页面加载、下载、证书问题分开；原始网络错误默认折叠 | P0 |
| `ui/DshMarkdown.kt:630`、`:620` | 系统打开链接异常 | 错误出口 | “无法打开链接，可复制地址后用其他应用打开。” | P0 |
| `download/Downloader.kt:121`、`runtime/SourceSpeedTester.kt:250` | 网络异常 message | 工程痕迹 | 网络/下载源/内容校验分开映射，不输出英文异常 | P0 |
| `ui/ApplicationUpdatePresentation.kt:29`、`ui/ApplicationUpdateCard.kt:114` | 主文已使用阶段映射；state.message 现在只在展开详情显示 | 错误出口 | 保留已实现的主文映射，展开详情先脱敏；未知失败仍使用中文原因，不重复底层原文 | P0 |
| `runtime/RuntimeService.kt:34`、`:37` | 更新或阶段状态进入通知 | 工程痕迹/长度 | 通知使用短状态映射，不把长阶段、路径和诊断带到系统通知 | P0 |
| `browser/BrowserService.kt:45` | 下载文件名进入通知扩展正文 | 动态内容/隐私 | 保持现有产品边界；文件名属用户内容，避免与不含对话内容承诺混淆 | P1 |
| `ui/DshSessionManagerPage.kt:1615`、`:1811`、`:1818`、`:1888`、`:1902` | 模型/权限/技能/命令名称和描述由运行程序提供 | 长度/术语 | 名称保留可识别值，描述最多两行后展开；自定义内容不擅改、不当作指令执行 | P1 |
| `ui/DshSessionManagerPage.kt:1987` | 未知权限值直接返回 | 工程痕迹 | 固定显示“自定义权限”，原始值仅放详情，不暗示可选择或扩大权限 | P0 |
| `ui/DshNativePage.kt:942`、`ui/DshSessionManagerPage.kt:1991` | 时间缺省 | 被动 | 无时间时不显示无意义时间标签；需要排序时内部仍保留缺省规则 | P2 |
| `ui/WorkspaceFilesPage.kt:1331`、`ui/DshComposerBar.kt:927` | 用户文件名 | 动态长度 | 独立内容区显示，允许两行、省略及复制，不塞进动作按钮 | P1 |
| `ui/DshMarkdown.kt:938`、`:990` | 图片 alt / 附件名拼进弹窗标题 | 动态长度 | 标题用“查看图片”“查看附件”，原名称放正文 | P1 |
| `ui/WorkspaceFilesPage.kt:860`、`ui/RemoteHostSettings.kt:349`、`:412` | 文件或电脑名称拼进标题 | 动态长度 | “编辑文件”“连接这台电脑？”“删除这条连接？”，名称正文另显 | P0 |
| `MainActivity.kt:1014` | 目录统计的 incompleteReason | 错误出口 | 显示“部分文件未统计”，具体原因在详情；不把英文路径错误拼入统计值 | P1 |
| `web/WebViewSupport.kt:18`、`:23`、`:24` | 提供方、CSS 名和检测异常 | 工程痕迹 | 主文“请更新系统网页组件”，完整能力报告放默认折叠的检测详情 | P0 |
| `app/src/main/AndroidManifest.xml:27` | 应用标签引用 strings.xml/app_name | 保留 | Tanuki | P2 |
| `app/src/main/AndroidManifest.xml:63`、`:70` | 英文前台服务用途元数据 | 非应用文案 | 保留审核用元数据，不当作通知标题；通知另由服务类清点 | P2 |
| Kotlin `contentDescription = null` | 装饰图标或已有文本的图标 | 无障碍边界 | 装饰图标保持不朗读；交互图标须有动作标签，不能全量填固定“图标” | P1 |

## 下一轮实施与验收

1. 下一轮实现者从届时最新 main 建独立分支，作者及 committer 使用 `leezzou <115924933+leezzou@users.noreply.github.com>`。先按基线差异复核行号和新增文案，切勿将本报告视为新 main 的自动覆盖清单；本轮文档已独立入库，无需重复创建同一审计记录。
2. 先实现 P0 显示映射及入口短文案，再逐页迁移 P1，最后整理 P2 详情。只提取面向用户的资源，不改协议键、安装标记、错误匹配、文件名、命令名或签名校验。设置分区目前同时用中文标题作路由比较，改显示名时必须同步映射或引入稳定内部路由，不能只改入口一侧。
3. 以真实状态决定提示和可用动作：取消、部分失败、旧版恢复、运行中停止、工作区限额、系统权限拒绝等均保留现有约束。权限文本变短不能取消确认步骤；不自动改为完全权限，不承诺缓存清理能解决内存不足。
4. 进行定向回归：错误原因与恢复动作、安装阶段识别和真实进度、通知固定最小文本、权限模式名称与确认后果、空态主次动作、设置入口可达。已有测试若断言旧文案，更新为语义和动作断言，不能只通过批量改期望掩盖行为变化。
5. 执行适用构建、JVM、lint。独立 Android QA 检查首启、安装/失败、更新确认、四类权限、浏览器接管、文件与对话空态；检查窄屏、横屏、深色、最大系统字体及 TalkBack。完整安全指纹必须可核对，按钮不能截断到难以区分；无状态依据不能显示固定 ETA。
6. 交付当前 SHA 的 PR、适用 CI 和独立审查，不合并、不发布。本轮未发起原生审查；需要由用户或编排方决定何时启动。本文为方案，不作为本轮设备 QA 或独立审查通过证据。

## 本次改动

仅新增本文件，交付全量表、术语、黑名单、专项改写和实施规则。未修改产品代码、应用资源或依赖；已有产品文件只随分支快进同步到审计基线，不属于本 PR 的改动。下一轮实施前，本文件是方案基线，不是现状说明。

## 验证记录

| 实际命令 / 操作 | 本轮结果 | 证据与限制 |
|---|---|---|
| `git status --short --branch`、`git rev-parse HEAD`、`git ls-remote origin refs/heads/main` | 起始工作区干净、完整 SHA 一致 | 写分支失败后重复核对，工作区和 HEAD 未变 |
| `git switch -c ao/copy-audit/plan`、`git fetch origin main`、`git merge --ff-only 4710c5e08ffa8c86d8cdc1bffbec1a1052be0935` | 默认沙箱分别拦截；编排方要求逐条授权后均成功 | 每次失败后核对状态；快进完成后 HEAD 与审计基线一致，不重写历史 |
| 生产 Kotlin 词法扫描 | 扫描 241 文件，跳过注释，处理模板嵌套及内嵌消息 | 临时脚本与提取结果留系统临时目录，不进入产品或交付目录 |
| 固定字符串和调用方核对 | 覆盖资源、显示属性、状态/失败映射、原生 View 和通知 | 清单附全部位置；纯内部匹配词与调试 label 单列排除 |
| 改写残留、长度、位置和覆盖一致性校验 | 4,787 条位置全部有效，建议逐行长度 ≤40，指定工程术语残留 0 | 另核对按钮/标题使用语境；长名称、版本和指纹放独立内容区，不假装运行排版已验收 |
| 交接结构、公开内容与链接目标检查 | 交接八章节与占位符检查通过；418 个基线链接目标存在；调用仓库 `check-publication.py` 的 `scan_blob` 扫描本文件，0 findings | 初次为 artifact 静态校验；落库后另执行下面的真实工作区检查 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1730-copy-audit.md` | 通过 | 普通 PR 模式，不修改共享状态或印章 |
| `python3 scripts/check-publication.py --git`、`git diff --check` | 通过；889 个公开候选文件、0 findings | 提交前再检查实际暂存范围 |
| 公开源码临时镜像内执行 `python3 scripts/package-source.py`，再执行归档公开检查 | 通过；889 个公开文件，ZIP 检查、外部 SHA-256 和审计文档字节一致，0 findings | 工作材料仅在系统临时目录；使用脚本默认归档名，不代表新的发布版本，无原生二进制交付 |
| 构建、JVM、lint、Android 仪器、设备和真实模型 | 未执行 | 本轮仅文案方案，无产品代码修改；不沿用历史测试数字作本轮通过 |

## 未完成与暂停项

方案已交付并落库，代码替换留下一轮。Git 写限制已通过逐条命令授权解决；提交、推送与 PR 身份以 provider 回执和最终交付报告为准，不为回填编号再新增提交。API Key 保存、真实账号/模型与真机验收继续暂停。独立审查待用户或编排方授权；CI 以当前 PR head 的实际结果为准，下一轮产品代码所需设备 QA 未执行。

## 下一步

本文已作为唯一记录落库到 `docs/handoffs/20261111-1730-copy-audit.md`，通过中文文档 PR 交付，不合并。下一轮按“下一轮实施与验收”落实产品文案。已检查同范围交接：文件复制反馈、横屏复制结果等历史记录属于实现与验证证据，本方案没有替代它们，均保留；没有确认可删除的本任务过期记录，不做跨 PR 清理。无需改动 `AGENT-STATE.md` 或 `CURRENT.json`。

## 约束与风险

本方案来自静态源码和实际调用方核对，未观察运行中的官方页面、系统权限弹窗、真实账号或用户数据。词法候选采用保守扩展范围，可能包含只能经诊断页查看的错误及构造器约束；统计不宣称每条都在普通页面实际展示。排除项有源码位置，便于复核；动态运行文本的值无法静态穷举，已列出口和规则。

普通字符串替换无法修复动态异常泄漏或未接通的功能；“请在网页界面处理”仅在现有网页入口可达时使用。若改写提出新入口、新折叠或主文/详情分层，应在下一轮实现并验证后再采用对应文案，不把方案当成功能已实现。不能降低安全边界、隐藏数据丢失风险、伪造时间、删除许可署名或把原始凭据带入诊断。

## 产物与校验

唯一内容交付为本 Markdown 审计文档；PR 只新增此记录，无产品代码、APK、安装或发布附件。先前完整 artifact 为备用副本，入库版补充黑名单并更新交付状态；以本 PR 文档为准。工作材料、提取结果与生成脚本留在系统临时目录，不作为维护依赖。当前报告保留原始字符串模板和源码位置；涉及外部参考名仅省略该名称，不重复展示。

未触碰设备、账号和用户文件。按用户授权提交、推送文档分支并创建 PR，不合并、不发布、不创建 tag；提交时显式指定 author 和 committer 为 `leezzou <115924933+leezzou@users.noreply.github.com>`。普通 PR 在真实工作区执行交接检查、公开候选检查和适用 CI，不刷新共享状态/印章。源码包仅作为本地公开候选检查，不作为新版本发布附件。

## 最新 main 增量核对（#88）

| 新增或变化的入口 | 最新事实 | 本轮方案 |
|---|---|---|
| `ui/ApplicationUpdatePresentation.kt:13` | 已有短摘要，详情默认折叠 | 摘要改“仅安装适用于当前应用的新版本，下载后会检查文件。”，保留折叠，不再建议新建折叠功能 |
| `ui/ApplicationUpdatePresentation.kt:30`–`:39` | 已按阶段生成中文状态 | 清晰的状态保留；失败给重试与详情入口，准备完成明确点“安装更新” |
| `ui/FileOperationOverlay.kt:20`、`MainActivity.kt:125` | 安装或更新时仍可浏览，写入被阻止 | “正在安装或更新，仍可浏览文件。完成后可修改文件。”；导入、移动和写入均不可误说可立即使用 |
| `ui/FileAccessPresentation.kt:35` | 新增环境忙碌只读分支 | “环境准备中，只能查看文件。”；不把只读误说成永久缺少权限 |
| `ui/OperationProgressPresentation.kt:105` | 更换版本后旧安装重试失效 | “所选版本已变化，请回到组件页重新安装。”，对应按钮“选择组件”，不得仍承诺重试旧命令 |
| `ui/TanukiUi.kt:412` | 新增导航语义名 | “主导航栏”可保留 |

新增/变化条目的逐字原文与最终行号均在全量表。正文专题中删除旧说明的建议，仅在对应说明仍存在时适用；#88 已删除或移动的旧版文案不要求恢复后再删除。

安装期与助手运行期是两个不同只读原因：安装期等待环境操作完成，浏览与导出可继续，导入和其他写入受限；助手运行期需停止助手，停止会中断当前任务。表中涉及助手运行期的“可导入”不能套到安装期。


## 清单统计与覆盖索引

词法扫描 **241 个生产 Kotlin 文件**；发现 **4763 处首轮候选**。内嵌程序容器 **4 处**，拆出中文消息 **22 处**，补入英文枚举显示值后，登记 **4787 处文案/候选记录**。其中 **263 处为非展示排除项**，其余 **4524 处为显示文案或可能到达界面的错误/诊断消息**。优先级按出现位置计：P0=1917、P1=2519、P2=88。本统计不是运行截图覆盖率。

所有下列 Kotlin 路径以 `app/src/main/kotlin/com/tanuki/` 为前缀；资源路径给出完整路径。文件标题链接固定到审计基线；表内位置是原始源码的行号，含同一行多个分支。**每一处重复出现都保留一行**。先按路径定位，再用原文区分同一行分支；不能把整行所有相同字符串盲目替换。

| 文件 | 候选登记数 | 非展示数 |
|---|---:|---:|
| [MainActivity.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/MainActivity.kt) | 151 | 0 |
| [TanukiApp.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/TanukiApp.kt) | 2 | 0 |
| [browser/AgentBrowserController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/AgentBrowserController.kt) | 33 | 0 |
| [browser/BrowserDownloadFileProvider.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserDownloadFileProvider.kt) | 0 | 0 |
| [browser/BrowserIdleReturnPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserIdleReturnPolicy.kt) | 0 | 0 |
| [browser/BrowserIntegration.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserIntegration.kt) | 5 | 0 |
| [browser/BrowserLibrary.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserLibrary.kt) | 48 | 0 |
| [browser/BrowserOverlayGeometry.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserOverlayGeometry.kt) | 0 | 0 |
| [browser/BrowserOverlayWindow.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserOverlayWindow.kt) | 63 | 0 |
| [browser/BrowserPreferences.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserPreferences.kt) | 8 | 0 |
| [browser/BrowserService.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserService.kt) | 17 | 0 |
| [browser/InteractionWebView.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/InteractionWebView.kt) | 0 | 0 |
| [download/DownloadRetryPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/download/DownloadRetryPolicy.kt) | 1 | 1 |
| [download/Downloader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/download/Downloader.kt) | 40 | 0 |
| [lan/LanDiscovery.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/lan/LanDiscovery.kt) | 18 | 0 |
| [lan/LanDiscoveryController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/lan/LanDiscoveryController.kt) | 1 | 0 |
| [notify/ChatNotificationCenter.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationCenter.kt) | 0 | 0 |
| [notify/ChatNotificationCopy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationCopy.kt) | 8 | 0 |
| [notify/ChatNotificationPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationPolicy.kt) | 8 | 0 |
| [notify/ChatNotificationRouting.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationRouting.kt) | 0 | 0 |
| [notify/ChatNotificationStatus.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationStatus.kt) | 6 | 0 |
| [notify/ChatNotifier.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotifier.kt) | 4 | 0 |
| [onboarding/OnboardingController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/onboarding/OnboardingController.kt) | 0 | 0 |
| [onboarding/OnboardingEnvironment.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/onboarding/OnboardingEnvironment.kt) | 26 | 0 |
| [onboarding/OnboardingModel.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/onboarding/OnboardingModel.kt) | 0 | 0 |
| [onboarding/OnboardingPrepare.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/onboarding/OnboardingPrepare.kt) | 34 | 12 |
| [onboarding/OnboardingStore.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/onboarding/OnboardingStore.kt) | 0 | 0 |
| [power/DeviceConditionConfigWriter.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/power/DeviceConditionConfigWriter.kt) | 0 | 0 |
| [power/DeviceConditionInitialization.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/power/DeviceConditionInitialization.kt) | 0 | 0 |
| [power/DeviceConditionMonitor.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/power/DeviceConditionMonitor.kt) | 1 | 1 |
| [power/DeviceConditionPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/power/DeviceConditionPolicy.kt) | 47 | 15 |
| [power/DeviceConditionRefreshQueue.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/power/DeviceConditionRefreshQueue.kt) | 0 | 0 |
| [runtime/CommandDeadline.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/CommandDeadline.kt) | 2 | 1 |
| [runtime/DshCookieIsolation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/DshCookieIsolation.kt) | 3 | 1 |
| [runtime/DshStartup.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/DshStartup.kt) | 5 | 0 |
| [runtime/DshVersions.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/DshVersions.kt) | 27 | 0 |
| [runtime/EarlyRuntimeStart.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/EarlyRuntimeStart.kt) | 0 | 0 |
| [runtime/FailureCatalog.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/FailureCatalog.kt) | 112 | 49 |
| [runtime/FailureRetry.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/FailureRetry.kt) | 0 | 0 |
| [runtime/FileTransfers.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/FileTransfers.kt) | 2 | 1 |
| [runtime/Launcher.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/Launcher.kt) | 11 | 0 |
| [runtime/NpmAdaptiveWorkerPool.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmAdaptiveWorkerPool.kt) | 2 | 2 |
| [runtime/NpmArchiveMapping.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmArchiveMapping.kt) | 10 | 0 |
| [runtime/NpmDependencyLocks.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmDependencyLocks.kt) | 11 | 0 |
| [runtime/NpmDownloadPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmDownloadPolicy.kt) | 3 | 2 |
| [runtime/NpmDownloadProgress.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmDownloadProgress.kt) | 1 | 0 |
| [runtime/NpmLockNormalizer.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmLockNormalizer.kt) | 0 | 0 |
| [runtime/NpmLockTrust.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmLockTrust.kt) | 3 | 0 |
| [runtime/NpmPackageDownloads.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmPackageDownloads.kt) | 95 | 0 |
| [runtime/NpmSources.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmSources.kt) | 11 | 0 |
| [runtime/OperationProgress.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/OperationProgress.kt) | 11 | 0 |
| [runtime/OperationRetry.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/OperationRetry.kt) | 0 | 0 |
| [runtime/RelayTransport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RelayTransport.kt) | 36 | 1 |
| [runtime/RemoteConnectionError.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteConnectionError.kt) | 61 | 5 |
| [runtime/RemoteHost.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHost.kt) | 19 | 0 |
| [runtime/RemoteHostController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt) | 37 | 3 |
| [runtime/RemoteHostProfiles.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostProfiles.kt) | 11 | 0 |
| [runtime/RemoteHostStorage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostStorage.kt) | 10 | 0 |
| [runtime/RemoteHostWizard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt) | 70 | 0 |
| [runtime/RemotePairingCode.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemotePairingCode.kt) | 2 | 0 |
| [runtime/RemoteRelay.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteRelay.kt) | 43 | 0 |
| [runtime/RuntimeCatalog.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeCatalog.kt) | 0 | 0 |
| [runtime/RuntimeComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeComponents.kt) | 74 | 0 |
| [runtime/RuntimeFailurePolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeFailurePolicy.kt) | 27 | 6 |
| [runtime/RuntimeManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt) | 131 | 4 |
| [runtime/RuntimeRequirements.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeRequirements.kt) | 13 | 0 |
| [runtime/RuntimeService.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeService.kt) | 9 | 0 |
| [runtime/RuntimeVersionSelection.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeVersionSelection.kt) | 1 | 0 |
| [runtime/SourceSpeedTester.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/SourceSpeedTester.kt) | 27 | 0 |
| [runtime/SshTransport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/SshTransport.kt) | 3 | 0 |
| [runtime/State.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/State.kt) | 4 | 0 |
| [storage/ContentDocument.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ContentDocument.kt) | 14 | 0 |
| [storage/DataSnapshots.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/DataSnapshots.kt) | 15 | 1 |
| [storage/DirectoryUsage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/DirectoryUsage.kt) | 5 | 0 |
| [storage/DocumentAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/DocumentAccess.kt) | 0 | 0 |
| [storage/DocxTextExtractor.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/DocxTextExtractor.kt) | 12 | 0 |
| [storage/ExportCleanup.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ExportCleanup.kt) | 5 | 0 |
| [storage/ExternalDocumentAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ExternalDocumentAccess.kt) | 2 | 0 |
| [storage/ExternalDocumentPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ExternalDocumentPolicy.kt) | 15 | 2 |
| [storage/FileTypeCatalog.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/FileTypeCatalog.kt) | 0 | 0 |
| [storage/ImportNaming.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ImportNaming.kt) | 9 | 1 |
| [storage/ImportProgress.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ImportProgress.kt) | 0 | 0 |
| [storage/L2sStore.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/L2sStore.kt) | 9 | 0 |
| [storage/PhoneFileAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/PhoneFileAccess.kt) | 0 | 0 |
| [storage/PhoneFileIntegration.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/PhoneFileIntegration.kt) | 7 | 0 |
| [storage/PhoneFileService.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/PhoneFileService.kt) | 2 | 0 |
| [storage/PhoneStorageAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/PhoneStorageAccess.kt) | 4 | 0 |
| [storage/ProjectArchive.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ProjectArchive.kt) | 5 | 0 |
| [storage/SafeTar.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/SafeTar.kt) | 12 | 0 |
| [storage/SharedStoragePaths.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/SharedStoragePaths.kt) | 2 | 2 |
| [storage/TextFileReader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt) | 31 | 19 |
| [storage/TransferEngine.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/TransferEngine.kt) | 25 | 0 |
| [storage/WorkspaceFileManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt) | 57 | 0 |
| [storage/WorkspaceIoGate.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceIoGate.kt) | 0 | 0 |
| [storage/WorkspaceOpenFileProvider.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceOpenFileProvider.kt) | 2 | 0 |
| [storage/WorkspaceStorage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceStorage.kt) | 38 | 0 |
| [storage/WorkspaceTransfer.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceTransfer.kt) | 3 | 0 |
| [storage/XlsxValueFormatter.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/XlsxValueFormatter.kt) | 0 | 0 |
| [storage/XlsxWorkbook.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/XlsxWorkbook.kt) | 38 | 0 |
| [storage/docx/DocxDocumentReader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt) | 64 | 0 |
| [storage/docx/DocxModel.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxModel.kt) | 0 | 0 |
| [storage/docx/DocxNumbering.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxNumbering.kt) | 5 | 1 |
| [storage/docx/DocxStyles.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxStyles.kt) | 6 | 1 |
| [storage/docx/DocxXml.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxXml.kt) | 3 | 0 |
| [ui/AgentBrowserPage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/AgentBrowserPage.kt) | 186 | 4 |
| [ui/ApplicationUpdateCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt) | 20 | 0 |
| [ui/ApplicationUpdatePresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/ApplicationUpdatePresentation.kt) | 22 | 0 |
| [ui/BrowserChrome.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/BrowserChrome.kt) | 17 | 0 |
| [ui/BrowserControlButton.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/BrowserControlButton.kt) | 4 | 0 |
| [ui/BrowserFloatingWindow.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/BrowserFloatingWindow.kt) | 6 | 0 |
| [ui/BrowserIcons.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/BrowserIcons.kt) | 0 | 0 |
| [ui/DocxLayout.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DocxLayout.kt) | 0 | 0 |
| [ui/DshApprovalController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt) | 12 | 0 |
| [ui/DshApprovalPanel.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt) | 45 | 0 |
| [ui/DshApprovalSheet.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt) | 9 | 0 |
| [ui/DshAttachmentReader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshAttachmentReader.kt) | 17 | 0 |
| [ui/DshChatFollowState.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshChatFollowState.kt) | 0 | 0 |
| [ui/DshChatRender.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt) | 55 | 7 |
| [ui/DshChatWindow.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshChatWindow.kt) | 0 | 0 |
| [ui/DshComposerBar.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt) | 49 | 1 |
| [ui/DshComposerController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt) | 34 | 0 |
| [ui/DshComposerDraftStore.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshComposerDraftStore.kt) | 0 | 0 |
| [ui/DshDock.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshDock.kt) | 6 | 0 |
| [ui/DshDockStateMapper.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshDockStateMapper.kt) | 0 | 0 |
| [ui/DshGoalDock.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshGoalDock.kt) | 20 | 0 |
| [ui/DshMarkdown.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshMarkdown.kt) | 33 | 0 |
| [ui/DshNativeController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt) | 17 | 0 |
| [ui/DshNativePage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt) | 56 | 0 |
| [ui/DshPlanCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshPlanCard.kt) | 16 | 0 |
| [ui/DshQuestionCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshQuestionCard.kt) | 11 | 0 |
| [ui/DshSessionManagerActivity.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt) | 0 | 0 |
| [ui/DshSessionManagerController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt) | 45 | 0 |
| [ui/DshSessionManagerPage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt) | 223 | 0 |
| [ui/DshSessionPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionPresentation.kt) | 27 | 0 |
| [ui/DshSessionSync.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionSync.kt) | 0 | 0 |
| [ui/DshTodoDock.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshTodoDock.kt) | 16 | 0 |
| [ui/DshToolCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt) | 23 | 0 |
| [ui/DshVersionManagerDialog.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshVersionManagerDialog.kt) | 34 | 0 |
| [ui/DshWindowMode.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshWindowMode.kt) | 0 | 0 |
| [ui/FailureActions.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FailureActions.kt) | 0 | 0 |
| [ui/FailureUi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FailureUi.kt) | 8 | 0 |
| [ui/FileAccessPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileAccessPresentation.kt) | 21 | 0 |
| [ui/FileListPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileListPresentation.kt) | 0 | 0 |
| [ui/FileOperationOutcome.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileOperationOutcome.kt) | 30 | 8 |
| [ui/FileOperationOverlay.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileOperationOverlay.kt) | 1 | 0 |
| [ui/FileTransferPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileTransferPresentation.kt) | 9 | 0 |
| [ui/FileViewerLayout.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileViewerLayout.kt) | 0 | 0 |
| [ui/GlassNavigationBar.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/GlassNavigationBar.kt) | 3 | 3 |
| [ui/Labels.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/Labels.kt) | 59 | 0 |
| [ui/OnboardingScreen.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt) | 72 | 0 |
| [ui/OperationProgress.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/OperationProgress.kt) | 23 | 1 |
| [ui/OperationProgressPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/OperationProgressPresentation.kt) | 58 | 17 |
| [ui/PdfJumpPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfJumpPolicy.kt) | 6 | 0 |
| [ui/PdfMemoryPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfMemoryPolicy.kt) | 1 | 1 |
| [ui/PdfPageCache.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfPageCache.kt) | 2 | 2 |
| [ui/PdfPageMemory.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfPageMemory.kt) | 1 | 1 |
| [ui/PdfPreloadPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfPreloadPolicy.kt) | 2 | 2 |
| [ui/PdfRenderPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfRenderPolicy.kt) | 5 | 0 |
| [ui/PdfViewportPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfViewportPolicy.kt) | 0 | 0 |
| [ui/ReaderState.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/ReaderState.kt) | 4 | 4 |
| [ui/RemoteHostSettings.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt) | 210 | 0 |
| [ui/RuntimeComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/RuntimeComponents.kt) | 33 | 1 |
| [ui/SetupGuide.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/SetupGuide.kt) | 19 | 3 |
| [ui/SetupScreens.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/SetupScreens.kt) | 77 | 4 |
| [ui/SourceSpeedControls.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/SourceSpeedControls.kt) | 34 | 0 |
| [ui/TanukiAccessibility.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiAccessibility.kt) | 2 | 0 |
| [ui/TanukiAdaptiveComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveComponents.kt) | 4 | 4 |
| [ui/TanukiAdaptiveShell.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveShell.kt) | 0 | 0 |
| [ui/TanukiContrast.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiContrast.kt) | 0 | 0 |
| [ui/TanukiDesign.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiDesign.kt) | 0 | 0 |
| [ui/TanukiLayoutPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiLayoutPolicy.kt) | 3 | 3 |
| [ui/TanukiListKeys.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiListKeys.kt) | 6 | 6 |
| [ui/TanukiMotion.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiMotion.kt) | 0 | 0 |
| [ui/TanukiMotionFeedback.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiMotionFeedback.kt) | 7 | 3 |
| [ui/TanukiNavigation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt) | 10 | 8 |
| [ui/TanukiPageVisuals.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt) | 20 | 0 |
| [ui/TanukiSaveableState.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiSaveableState.kt) | 1 | 1 |
| [ui/TanukiStartupPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiStartupPolicy.kt) | 0 | 0 |
| [ui/TanukiStateViews.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiStateViews.kt) | 15 | 0 |
| [ui/TanukiUi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt) | 303 | 2 |
| [ui/TextPageLayout.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt) | 0 | 0 |
| [ui/VideoFullscreenPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/VideoFullscreenPolicy.kt) | 0 | 0 |
| [ui/VideoPlaybackPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/VideoPlaybackPolicy.kt) | 0 | 0 |
| [ui/WorkspaceDocxPreview.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt) | 25 | 0 |
| [ui/WorkspaceFileViewer.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt) | 108 | 0 |
| [ui/WorkspaceFilesPage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt) | 200 | 2 |
| [ui/WorkspacePdfPreview.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt) | 28 | 0 |
| [ui/WorkspaceStorageControls.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceStorageControls.kt) | 28 | 0 |
| [ui/WorkspaceVideoPlayer.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceVideoPlayer.kt) | 20 | 0 |
| [ui/WorkspaceXlsxPreview.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceXlsxPreview.kt) | 16 | 0 |
| [ui/XlsxPreviewPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/XlsxPreviewPolicy.kt) | 0 | 0 |
| [ui/ZoomableContent.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/ZoomableContent.kt) | 0 | 0 |
| [ui/design/TanukiBottomSheet.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiBottomSheet.kt) | 0 | 0 |
| [ui/design/TanukiColorMath.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiColorMath.kt) | 6 | 6 |
| [ui/design/TanukiComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiComponents.kt) | 16 | 7 |
| [ui/design/TanukiDesignTheme.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiDesignTheme.kt) | 1 | 1 |
| [ui/design/TanukiGlass.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiGlass.kt) | 0 | 0 |
| [ui/design/TanukiMotionSettings.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiMotionSettings.kt) | 0 | 0 |
| [ui/design/TanukiMotionSpecs.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiMotionSpecs.kt) | 0 | 0 |
| [ui/design/TanukiPalette.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiPalette.kt) | 30 | 30 |
| [ui/design/TanukiTokenSpec.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiTokenSpec.kt) | 0 | 0 |
| [update/ApkInstallAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/ApkInstallAccess.kt) | 5 | 0 |
| [update/AppReleaseManifest.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppReleaseManifest.kt) | 13 | 0 |
| [update/AppUpdateFailure.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppUpdateFailure.kt) | 1 | 0 |
| [update/AppUpdateFiles.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppUpdateFiles.kt) | 1 | 0 |
| [update/AppUpdatePolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppUpdatePolicy.kt) | 12 | 0 |
| [update/ApplicationUpdateFileProvider.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/ApplicationUpdateFileProvider.kt) | 0 | 0 |
| [update/ApplicationUpdateManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/ApplicationUpdateManager.kt) | 32 | 0 |
| [update/BackgroundUpdateChecks.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/BackgroundUpdateChecks.kt) | 18 | 0 |
| [update/BackgroundUpdateRepositories.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/BackgroundUpdateRepositories.kt) | 9 | 0 |
| [update/UpdateCheckWorkers.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/UpdateCheckWorkers.kt) | 0 | 0 |
| [update/UpdateManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/UpdateManager.kt) | 46 | 0 |
| [update/UpdateNotifier.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/UpdateNotifier.kt) | 6 | 0 |
| [update/VersionOrder.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/VersionOrder.kt) | 0 | 0 |
| [web/BlobDownloads.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/BlobDownloads.kt) | 11 | 0 |
| [web/DshApprovalProtocol.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt) | 26 | 0 |
| [web/DshApprovalSession.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt) | 27 | 0 |
| [web/DshChatBridge.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshChatBridge.kt) | 0 | 0 |
| [web/DshChatProtocol.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshChatProtocol.kt) | 0 | 0 |
| [web/DshComposerClient.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt) | 13 | 0 |
| [web/DshComposerProtocol.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt) | 36 | 0 |
| [web/DshConnectionPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshConnectionPolicy.kt) | 0 | 0 |
| [web/DshCoordination.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshCoordination.kt) | 0 | 0 |
| [web/DshFileChooser.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshFileChooser.kt) | 0 | 0 |
| [web/DshLive.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshLive.kt) | 0 | 0 |
| [web/DshMuxProtocol.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshMuxProtocol.kt) | 6 | 0 |
| [web/DshNativeApi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt) | 42 | 0 |
| [web/DshOAuth.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshOAuth.kt) | 0 | 0 |
| [web/DshPlan.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshPlan.kt) | 16 | 0 |
| [web/DshQuestion.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshQuestion.kt) | 3 | 0 |
| [web/DshSessionAdminApi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt) | 39 | 0 |
| [web/DshToolTimeline.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt) | 17 | 0 |
| [web/DshTransport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshTransport.kt) | 0 | 0 |
| [web/DshUiMode.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshUiMode.kt) | 0 | 0 |
| [web/DshViewport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshViewport.kt) | 0 | 0 |
| [web/DshWebSocket.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshWebSocket.kt) | 26 | 0 |
| [web/DshWebSocketClient.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt) | 28 | 0 |
| [web/DshWebView.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshWebView.kt) | 14 | 0 |
| [web/LegacyWebCompatibility.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/LegacyWebCompatibility.kt) | 1 | 0 |
| [web/OriginPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/OriginPolicy.kt) | 0 | 0 |
| [web/WebViewSupport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/WebViewSupport.kt) | 10 | 0 |
| app/src/main/res/values/strings.xml | 1 | 0 |

`values*/strings.xml` 实际只有一份，一项应用名；没有多语言变体。其余 res XML 无用户文案。主清单已覆盖 Compose、原生 View/Dialog、Toast、通知、权限、引导、空态及底层失败映射。两个 asset mjs 是工具客户端，没有应用页面模板；其命令行协议错误不作为应用固定文案替换。测试源码不进入生产清单。Manifest 的应用标题复用 app_name；两项前台服务用途说明为系统元数据，补录在边界表，不改权限声明。

## 全量清单表

类型：工程痕迹、被动/实现说明、术语不一致、长度、错误出口、清晰度；“保留”表示已有文案可用。“非展示”明确不改内部匹配、格式校验、动画标签和算法样本；其旁边的展示标签仍独立列出。所有条目是方案，**未改产品实现**。`<br>` 表示分行；长项须按上文的主文/详情规则落地。


### [MainActivity.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/MainActivity.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| MainActivity.kt:118 | 尚未允许悬浮窗；完整浏览器仍可正常使用 | 清晰度 | 未开启悬浮窗，可继续使用浏览器。 | P0 |
| MainActivity.kt:119 | 尚未允许悬浮窗；完整浏览器仍可正常使用 | 清晰度 | 未开启悬浮窗，可继续使用浏览器。 | P0 |
| MainActivity.kt:125 | 环境操作正在进行，文件可继续浏览；请等待操作完成后再写入。 | 清晰度 | 正在安装或更新，仍可浏览文件。完成后可修改文件。 | P0 |
| MainActivity.kt:128 | 请先停止 DSH，再管理文件；避免与 Agent 同时修改文件。 | 术语不一致 | 请先停止助手，再修改文件。 | P1 |
| MainActivity.kt:131 | 手机存储写权限已撤销，源文件未删除 | 清晰度 | 已撤销手机文件写入权限，源文件未删除。 | P0 |
| MainActivity.kt:171 | 手机写入权限尚未可用，DSH 保持只读；请检查系统存储权限。 | 术语不一致 | 助手仍只能读取手机文件。请检查系统文件权限。 | P0 |
| MainActivity.kt:183 | 此设备未提供所有文件访问设置，可使用系统文件选择器导入和导出 | 清晰度 | 此设备无法授权全部文件，请选择要导入或导出的文件。 | P1 |
| MainActivity.kt:205 | 导出文件 | 保留 | 导出文件 | P1 |
| MainActivity.kt:214 | 文件导出完成 | 保留 | 文件导出完成 | P1 |
| MainActivity.kt:243 | 导入项目文件 | 保留 | 导入项目文件 | P1 |
| MainActivity.kt:250 | 导出项目 ZIP | 清晰度 | 导出项目 | P1 |
| MainActivity.kt:260 | 保存网页 Blob 文件 | 清晰度 | 保存网页文件 | P1 |
| MainActivity.kt:265 | 官方 Blob 文件导出完成 | 清晰度 | 网页文件已导出 | P1 |
| MainActivity.kt:279 | 管理页 Cookie 隔离未就绪，已取消下载；请重启应用 | 工程痕迹 | 无法安全切换页面，下载已取消。请重启应用。 | P0 |
| MainActivity.kt:297 | 下载因页面或 Activity 重建而中断，请重新保存文件 | 清晰度 | 下载已中断，请重新保存文件。 | P0 |
| MainActivity.kt:298 | 下载已中断，请重新保存文件 | 保留 | 下载已中断，请重新保存文件 | P0 |
| MainActivity.kt:302 | 通知权限已授予 | 保留 | 通知权限已授予 | P0 |
| MainActivity.kt:302 | 通知权限未授予；可在系统设置开启 | 清晰度 | 未开启通知，可在系统设置中开启。 | P0 |
| MainActivity.kt:348 | 请先完成当前文件保存 | 保留 | 请先完成当前文件保存 | P1 |
| MainActivity.kt:352 | 请先完成当前文件保存 | 保留 | 请先完成当前文件保存 | P1 |
| MainActivity.kt:363 | Agent 连接准备失败，普通浏览仍可用；请检查同名 Skill 或重新打开应用。 | 清晰度 | 助手暂时无法使用浏览器。请重新打开应用。 | P0 |
| MainActivity.kt:365 | 受控浏览器需要支持独立资料的新版 WebView，请从设置更新内核。 | 工程痕迹 | 请在设置中更新系统网页组件，再使用浏览器。 | P0 |
| MainActivity.kt:432 | 安全模式下不能重命名会话 | 术语不一致 | 请先恢复正常模式，再重命名对话。 | P1 |
| MainActivity.kt:442 | 安全模式下不能新建会话 | 术语不一致 | 请先恢复正常模式，再新建对话。 | P1 |
| MainActivity.kt:504 | 点击统计各目录的实际占用 | 保留 | 点击统计各目录的实际占用 | P1 |
| MainActivity.kt:529 | 浏览器不可用 | 保留 | 浏览器不可用 | P1 |
| MainActivity.kt:530 | 登录授权页，AI 不可查看或操作 | 保留 | 登录授权页，AI 不可查看或操作 | P0 |
| MainActivity.kt:531 | AI 操作已暂停，等待接管选择 | 保留 | AI 操作已暂停，等待接管选择 | P1 |
| MainActivity.kt:532 | AI 可操作浏览器 | 保留 | AI 可操作浏览器 | P1 |
| MainActivity.kt:533 | 你在临时操作，空闲30秒后交回 AI | 保留 | 你在临时操作，空闲30秒后交回 AI | P1 |
| MainActivity.kt:534 | 你在操作，AI 仅可查看 | 保留 | 你在操作，AI 仅可查看 | P1 |
| MainActivity.kt:600 | 正在统计实际分配空间… | 清晰度 | 正在统计存储占用… | P1 |
| MainActivity.kt:603 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |
| MainActivity.kt:603 | 统计失败：${Redact.text(it.message ?: "未知错误")} | 被动/实现说明 | 存储统计失败，请重试。 | P0 |
| MainActivity.kt:618 | 已关闭：启动应用后需手动运行 DSH | 术语不一致 | 已关闭自动启动，请手动启动助手。 | P1 |
| MainActivity.kt:618 | 已开启：下次启动应用时自动运行上次正常停止的 DSH | 术语不一致 | 已开启自动启动助手，下次打开应用时生效。 | P1 |
| MainActivity.kt:788 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| MainActivity.kt:788 | 移动到工作区 | 保留 | 移动到工作区 | P1 |
| MainActivity.kt:809 | 浏览器暂不可用 | 保留 | 浏览器暂不可用 | P1 |
| MainActivity.kt:810 | 设置 | 保留 | 设置 | P1 |
| MainActivity.kt:856 | DSH 聊天通知 | 术语不一致 | 对话通知 | P1 |
| MainActivity.kt:861 | 只在应用不在前台或对应会话不可见时提醒；正文只说明事件类型，不包含会话内容。 | 术语不一致 | 回复完成或需要你处理时提醒。通知不显示对话内容。 | P1 |
| MainActivity.kt:864 | 申请通知权限 | 保留 | 申请通知权限 | P0 |
| MainActivity.kt:866 | 渠道设置 | 清晰度 | 通知分类 | P1 |
| MainActivity.kt:870 | 系统通知设置 | 保留 | 系统通知设置 | P1 |
| MainActivity.kt:871 | 关闭 | 保留 | 关闭 | P1 |
| MainActivity.kt:873 | 允许浏览器悬浮窗 | 清晰度 | 开启浏览器小窗 | P0 |
| MainActivity.kt:874 | 需要在系统设置允许「显示在其他应用上层」。授权后，浏览器可停留在其他应用上方；拖动顶部移动，拖动右下角调整大小。只显示本应用的网页，不读取其他应用内容。可以随时关闭窗口或撤销系统授权。 | 长度 | 允许显示在其他应用上方，即可边用其他应用边浏览。<br>拖动顶部移动窗口，拖动底角调整大小。<br>小窗只显示 Tanuki 网页，可随时关闭。 | P0 |
| MainActivity.kt:878 | 无法打开系统悬浮窗设置，请从应用管理的特殊访问权限进入 | 清晰度 | 请在系统应用设置的特殊权限中开启悬浮窗。 | P0 |
| MainActivity.kt:879 | 前往系统设置 | 清晰度 | 去系统设置 | P1 |
| MainActivity.kt:880 | 暂不开启 | 保留 | 暂不开启 | P1 |
| MainActivity.kt:882 | 许可与组件来源 | 保留 | 许可与组件来源 | P1 |
| MainActivity.kt:884 | 关闭 | 保留 | 关闭 | P1 |
| MainActivity.kt:911 | 创建应用私有数据快照 | 术语不一致 | 备份应用数据 | P1 |
| MainActivity.kt:913 | 请先停止 DSH | 术语不一致 | 请先停止助手 | P1 |
| MainActivity.kt:914 | 应用私有数据快照完成 | 术语不一致 | 应用数据已备份 | P1 |
| MainActivity.kt:922 | 清理下载缓存 | 保留 | 清理下载缓存 | P1 |
| MainActivity.kt:925 | 下载缓存已清除 | 保留 | 下载缓存已清除 | P1 |
| MainActivity.kt:934 | 确认 | 清晰度 | 继续 | P1 |
| MainActivity.kt:934 | 确认来源并下载 | 清晰度 | 确认下载 | P1 |
| MainActivity.kt:936 | 取消 | 保留 | 取消 | P0 |
| MainActivity.kt:954 | 准备精简运行环境 | 清晰度 | 整理运行环境 | P1 |
| MainActivity.kt:955 | 停止服务 | 清晰度 | 停止助手 | P1 |
| MainActivity.kt:956 | 重启服务 | 清晰度 | 重启助手 | P1 |
| MainActivity.kt:957 | 尝试恢复正常模式 | 清晰度 | 恢复正常模式 | P1 |
| MainActivity.kt:958 | 退出试用并回到安全模式 | 清晰度 | 返回安全模式 | P1 |
| MainActivity.kt:959 | 安装所选 DSH 版本 | 术语不一致 | 安装助手版本 | P0 |
| MainActivity.kt:960 | 卸载 DSH 程序 | 术语不一致 | 卸载助手 | P1 |
| MainActivity.kt:961 | 切换已安装 DSH 版本 | 术语不一致 | 切换助手版本 | P0 |
| MainActivity.kt:962 | 重新安装当前 DSH | 术语不一致 | 重装助手 | P0 |
| MainActivity.kt:963 | 安装 DSH 备用程序 | 术语不一致 | 安装备用版本 | P0 |
| MainActivity.kt:964 | 安装精简 Ubuntu | 清晰度 | 安装系统组件 | P0 |
| MainActivity.kt:965 | 重新安装 Ubuntu | 清晰度 | 重装系统组件 | P0 |
| MainActivity.kt:966 | 卸载 Ubuntu 镜像 | 清晰度 | 卸载系统组件 | P1 |
| MainActivity.kt:967 | 安装 Node.js | 清晰度 | 安装运行组件 | P0 |
| MainActivity.kt:968 | 重新安装 Node.js | 清晰度 | 重装运行组件 | P0 |
| MainActivity.kt:969 | 卸载 Node.js | 清晰度 | 卸载运行组件 | P1 |
| MainActivity.kt:970 | 恢复数据快照 | 术语不一致 | 恢复数据备份 | P1 |
| MainActivity.kt:971 | 检查当前版本 | 保留 | 检查当前版本 | P1 |
| MainActivity.kt:972 | 整理 Ubuntu 安装缓存 | 清晰度 | 整理安装缓存 | P0 |
| MainActivity.kt:973 | 重新检测 WebView | 工程痕迹 | 检测网页组件 | P1 |
| MainActivity.kt:974 | 重新加载官方网页 | 清晰度 | 重新加载网页 | P1 |
| MainActivity.kt:975 | 导出项目 | 保留 | 导出项目 | P1 |
| MainActivity.kt:976 | 备份私有数据 | 清晰度 | 备份应用数据 | P1 |
| MainActivity.kt:977 | 清除下载缓存 | 保留 | 清除下载缓存 | P1 |
| MainActivity.kt:978 | 确认操作 | 清晰度 | 继续操作？ | P1 |
| MainActivity.kt:982 | 下载所选 Ubuntu Base ${manager.components.selected(RuntimeComponent.UBUNTU).version} ARM64 精简镜像，不含桌面。${if(command=="INIT") "随后安装所选 Node.js ${manager.components.selected(RuntimeComponent.NODE).version} 与官方 DSH ${RuntimeManager.DSH}，仅添加必要工具。" else "本次只验证 App 内 Linux 启动。"}\n\nUbuntu 下载源：${manager.components.sourceFor(RuntimeComponent.UBUNTU).title}；Node.js 下载源：${manager.components.sourceFor(RuntimeComponent.NODE).title}。每个版本使用对应官方 SHA-256 校验。建议预留 4 GB 空间；模型上下文会发送至你选择的服务商。 | 工程痕迹、术语不一致、长度 | 将下载并安装运行环境，建议预留 4 GB 空间。<br>模型处理消息时，相关内容会发送给你选择的服务商。<br>下载来源与版本可在详情中查看。 | P0 |
| MainActivity.kt:982 | 本次只验证 App 内 Linux 启动。 | 清晰度 | 本次仅检查系统组件能否启动。 | P1 |
| MainActivity.kt:982 | 随后安装所选 Node.js ${manager.components.selected(RuntimeComponent.NODE).version} 与官方 DSH ${RuntimeManager.DSH}，仅添加必要工具。 | 术语不一致、长度 | 随后安装助手及运行所需组件。 | P0 |
| MainActivity.kt:983 | Ubuntu 内自行安装的软件与系统修改会被替换。 | 清晰度 | 会替换自行安装的软件和系统修改。 | P0 |
| MainActivity.kt:983 | 下载 Ubuntu Base ${targetVersion ?: manager.components.selected(RuntimeComponent.UBUNTU).version} ARM64，来源：${manager.components.sourceFor(RuntimeComponent.UBUNTU).title}。验证该镜像对应的官方 SHA-256 后安装基础工具。${if(command=="UBUNTU_REINSTALL") "Ubuntu 内自行安装的软件与系统修改会被替换。" else ""}\n\n独立 Node.js、DSH 引擎、配置、会话与项目保留；失败时恢复原 Ubuntu。请先停止 DSH。 | 工程痕迹、术语不一致、长度 | 将安装系统组件。重装会替换其中自行安装的软件和系统修改。<br>助手、对话和项目独立保存。请先停止助手。<br>版本和下载来源在详情中查看。 | P0 |
| MainActivity.kt:984 | 移除 Ubuntu 镜像及其中安装的软件。Node.js 将迁移并保留在独立目录；DSH 程序、配置、会话、快照和项目保留。重新安装 Ubuntu 后才能再次运行 DSH。请先停止服务。 | 术语不一致、长度 | 将删除系统组件及其中安装的软件。<br>对话和项目保留，重新安装后才能运行助手。 | P0 |
| MainActivity.kt:985 | 下载 Node.js ${targetVersion ?: manager.components.selected(RuntimeComponent.NODE).version} ARM64，来源：${manager.components.sourceFor(RuntimeComponent.NODE).title}。验证该版本压缩包、pnpm 和 DSH 的兼容性后再替换；失败时保留当前 Node。Ubuntu、DSH 程序、配置、会话与项目保留。请先停止服务。 | 工程痕迹、术语不一致、长度 | 将安装所选运行组件，检查兼容性后再替换。<br>请先停止助手，版本和来源在详情中查看。 | P0 |
| MainActivity.kt:986 | 只移除 Node.js 与 pnpm 工具层，保留 Ubuntu、DSH 程序、配置、会话与项目。重新安装 Node.js 后才能再次运行 DSH。请先停止服务。 | 工程痕迹、术语不一致、长度 | 将卸载运行组件，重新安装后才能运行助手。<br>对话和项目保留，请先停止助手。 | P0 |
| MainActivity.kt:987 | 卸载 DSH ${targetVersion ?: release?.version ?: ""} 的备用程序目录，保留配置、会话、插件、Skill、项目、数据快照和其他已安装版本。当前使用版本不能卸载；卸载失败会回滚并保留原程序。请先停止服务。 | 术语不一致、长度 | 将卸载所选备用版本，保留对话、项目及其他版本。<br>当前使用版本不能卸载。请先停止助手。 | P0 |
| MainActivity.kt:988 | 切换到已安装 DSH ${targetVersion ?: "所选版本"}；执行兼容性检查并保留切换前快照。请先停止服务。 | 术语不一致 | 将切换到所选版本，并保存切换前的数据备份。<br>请先停止助手。 | P0 |
| MainActivity.kt:988 | 所选版本 | 保留 | 所选版本 | P1 |
| MainActivity.kt:989 | 下载并安装官方 DSH ${release?.version ?: "所选版本"} 程序，使用隔离目录检查启动；不打开正式会话，不更改当前版本或数据。较旧版本可先安装为备用，再恢复匹配的数据快照。请先停止服务。 | 工程痕迹、术语不一致、长度 | 将安装备用版本，并检查能否启动。<br>当前版本和对话保持不变。使用旧版前需恢复对应数据备份。 | P0 |
| MainActivity.kt:989 | 所选版本 | 保留 | 所选版本 | P1 |
| MainActivity.kt:990 | 移除并重新安装当前 DSH ${release?.version ?: ""} 的官方程序与依赖。配置、会话、插件和项目保留；若下载失败，引擎会保持待安装，可重试。请先停止服务。 | 术语不一致、长度 | 将重新下载当前助手版本。对话和项目保留。<br>下载失败时需重试安装，请先停止助手。 | P0 |
| MainActivity.kt:991 | 导出工作区文件，默认排除常见凭据文件。运行中可以导出；发现文件在读取期间变化时会提示重试。整个工作区不是同一时刻的快照，仍需检查源文件中的敏感内容。 | 术语不一致、长度 | 将导出工作区文件，默认跳过常见密钥文件。<br>导出前请检查是否含有私人信息。<br>文件读取时若有变化，请重新导出。 | P1 |
| MainActivity.kt:992 | 只删除下载压缩包和未完成下载，不删除环境或项目。 | 清晰度 | 只清理下载文件，保留运行环境和项目。 | P0 |
| MainActivity.kt:993 | 将停止当前服务并通过你的插件与配置启动正常模式；2 分钟内再次失败会自动回到安全模式。不会删除项目、会话、程序或配置。 | 术语不一致、长度 | 将重启助手并加载你的插件和设置。<br>两分钟内再次启动失败时会回到安全模式。 | P0 |
| MainActivity.kt:994 | 将退出正常模式试用并重新用最小救援配置启动；不加载你的插件与配置，项目、会话、程序和配置均保留。 | 术语不一致、长度 | 将返回安全模式，不加载你的插件和设置。<br>项目和对话保留。 | P1 |
| MainActivity.kt:995 | 安装官方 DSH ${release?.version ?: "所选版本"}，先进行候选启动检查、备份，再切换。请先停止服务。检查不使用正式模型凭据，正式服务保留官方调度设置。 | 术语不一致、长度 | 将检查新版本、备份数据，再切换版本。<br>请先停止助手。 | P0 |
| MainActivity.kt:995 | 所选版本 | 保留 | 所选版本 | P1 |
| MainActivity.kt:996 | 请先停止服务。快照包含私有凭据与会话，仅保存在 App 内；卸载会丢失，不能作为跨设备备份。 | 术语不一致、长度 | 备份包含账号信息和对话，仅保存在应用内。<br>卸载应用会丢失备份，请先停止助手。 | P1 |
| MainActivity.kt:997 | 请先停止服务。恢复会覆盖快照之后的会话与配置；项目代码不回滚。 | 术语不一致 | 恢复会覆盖备份之后的对话和设置，项目文件保持不变。<br>请先停止助手。 | P0 |
| MainActivity.kt:998 | 检查当前已切换版本并保留现有数据与官方调度设置。失败时保留恢复入口，不自动回滚项目。 | 长度 | 将检查当前版本能否启动。失败时可恢复数据备份。 | P0 |
| MainActivity.kt:999 | 启用后续安装的精简策略并清理 apt 下载缓存，保留已安装工具、版权文件、会话和项目。请先停止 DSH。 | 工程痕迹、术语不一致、长度 | 将整理安装缓存，保留已安装软件、对话和项目。<br>请先停止助手。 | P0 |
| MainActivity.kt:1000 | 重新检测会重新加载网页，可能丢失尚未保存的草稿；本机 DSH 任务继续由服务管理。 | 术语不一致 | 重新检测会刷新网页，请先保存草稿。<br>助手任务继续运行。 | P1 |
| MainActivity.kt:1001 | 重新连接会加载网页，可能丢失尚未保存的草稿；本机 DSH 任务继续由服务管理。 | 术语不一致 | 重新连接会刷新网页，请先保存草稿。<br>助手任务继续运行。 | P1 |
| MainActivity.kt:1002 | 可能中断正在运行的任务。无法可靠判断所有 Agent 子任务状态，停止后不会自动重放操作。 | 长度 | 停止会中断当前任务。重新启动后，请查看任务状态再继续。 | P0 |
| MainActivity.kt:1008 | DSH 程序 | 术语不一致 | 助手程序 | P1 |
| MainActivity.kt:1008 | Linux 环境 | 清晰度 | 系统组件 | P1 |
| MainActivity.kt:1009 | 工作区 | 保留 | 工作区 | P1 |
| MainActivity.kt:1009 | 配置、会话和扩展 | 术语不一致 | 设置、对话和扩展 | P1 |
| MainActivity.kt:1010 | 下载缓存 | 清晰度 | 安装缓存 | P1 |
| MainActivity.kt:1010 | 日志 | 保留 | 日志 | P1 |
| MainActivity.kt:1010 | 私有备份 | 清晰度 | 应用数据备份 | P1 |
| MainActivity.kt:1014 | $name：${use.allocatedBytes / (1024 * 1024)} MiB${when { use.incompleteReason != null -&gt; "（${use.incompleteReason}）"; use.unreadableEntries &gt; 0 -&gt; "（有 ${use.unreadableEntries} 项无法读取）"; else -&gt; "" }} | 工程痕迹、长度 | {名称}：{大小} MiB{数量} | P0 |
| MainActivity.kt:1014 | （有 ${use.unreadableEntries} 项无法读取） | 清晰度 | 部分文件无法读取，共 {数量} 项 | P0 |
| MainActivity.kt:1021 | 请先完成操作并停止 DSH，再更新 WebView | 工程痕迹、术语不一致 | 请先停止助手，并等待当前操作结束，再更新网页组件。 | P0 |
| MainActivity.kt:1030 | 未找到可用商店，请使用设备官方渠道更新系统 WebView | 工程痕迹 | 请通过设备官方应用商店更新系统网页组件。 | P0 |
| MainActivity.kt:1038 | 无法打开系统 WebView 设置，请从系统应用管理进入 | 工程痕迹 | 请从系统应用管理中打开网页组件设置。 | P0 |
| MainActivity.kt:1051 | 浏览器暂不可用，请先检查 WebView | 工程痕迹 | 请在设置中检查系统网页组件，再打开浏览器。 | P1 |
| MainActivity.kt:1063 | 浏览器暂不可用，请先检查 WebView | 工程痕迹 | 请在设置中检查系统网页组件，再打开浏览器。 | P1 |
| MainActivity.kt:1094 | 无法打开此文件 | 保留 | 无法打开此文件 | P0 |
| MainActivity.kt:1154 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| MainActivity.kt:1162 | 无法读取所选文件 | 保留 | 无法读取所选文件 | P0 |
| MainActivity.kt:1166 | 已复制到工作区「${destination.name}」；可在文件页打开 | 清晰度 | 已复制到工作区「{名称}」；可在文件页打开 | P1 |
| MainActivity.kt:1185 | 请先停止 DSH，并等待环境与文件任务结束 | 术语不一致 | 请先停止助手，并等待环境和文件操作结束。 | P1 |
| MainActivity.kt:1189 | 允许安装 Tanuki 更新 | 保留 | 允许安装 Tanuki 更新 | P0 |
| MainActivity.kt:1190 | 需要在系统设置中允许此应用安装更新。授权后请返回应用，再次点击安装；不会自动安装或清除数据。 | 长度 | 请在系统设置中允许 Tanuki 安装应用。<br>返回后点“安装更新”，再由系统确认安装。 | P0 |
| MainActivity.kt:1191 | 前往设置 | 保留 | 前往设置 | P1 |
| MainActivity.kt:1191 | 取消 | 保留 | 取消 | P0 |
| MainActivity.kt:1197 | 覆盖更新 Tanuki？ | 清晰度 | 安装 Tanuki 更新？ | P0 |
| MainActivity.kt:1198 | 已核对同包名、同签名和更高版本。Ubuntu、Node.js、DSH 与项目会保留；请勿卸载旧应用。下一步仍需在 Android 安装器中确认。 | 术语不一致、长度 | 更新会保留运行环境和项目，请勿卸载旧应用。<br>接下来请在系统安装界面确认。 | P0 |
| MainActivity.kt:1199 | 取消 | 保留 | 取消 | P0 |
| MainActivity.kt:1199 | 继续安装 | 保留 | 继续安装 | P0 |
| MainActivity.kt:1248 | 无法打开系统通知设置，请从系统应用管理进入 | 保留 | 无法打开系统通知设置，请从系统应用管理进入 | P0 |
| MainActivity.kt:1279 | 无法定位该会话，请在 DSH 侧栏手动打开；任务本身未受影响 | 术语不一致 | 找不到这段对话，请在网页界面中打开。 | P0 |
| MainActivity.kt:1332 | 未能取得附件，页面可能已重建，请重新选择文件 | 清晰度 | 无法读取附件，请重新选择文件。 | P1 |
| MainActivity.kt:1341 | 无法打开系统文件选择器，请稍后重试 | 保留 | 无法打开系统文件选择器，请稍后重试 | P0 |
| MainActivity.kt:1370 | 无法打开所选文件 | 保留 | 无法打开所选文件 | P0 |
| MainActivity.kt:1373 | 已导入 ${uris.size} 个文件到「${destination.name}」 | 清晰度 | 已导入 {数量} 个文件到「{名称}」 | P1 |
| MainActivity.kt:1387 | 项目导出完成，不含 DSH 私有数据 | 术语不一致 | 项目导出完成，不含 助手 私有数据 | P1 |
| MainActivity.kt:1389 | 下载网页文件 | 清晰度 | 项目已导出，不包含对话和账号信息。 | P1 |
| MainActivity.kt:1390 | 下载服务来源已变更 | 保留 | 下载服务来源已变更 | P1 |
| MainActivity.kt:1396 | 下载失败 HTTP ${c.responseCode} | 清晰度 | 网页文件下载失败，请稍后重试。 | P0 |
| MainActivity.kt:1402 | 官方文件下载完成 | 清晰度 | 网页文件已下载 | P1 |

### [TanukiApp.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/TanukiApp.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| TanukiApp.kt:50 | 系统悬浮窗无法启动，请保持应用在前台后重试 | 保留 | 系统悬浮窗无法启动，请保持应用在前台后重试 | P0 |
| TanukiApp.kt:63 | 后台浏览器无法启动，AI 授权已撤销；请保持应用在前台后重试 | 清晰度 | 浏览器无法在后台启动，请保持应用打开后重试。<br>助手操作权限已撤销。 | P0 |

### [browser/AgentBrowserController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/AgentBrowserController.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| browser/AgentBrowserController.kt:40 | 30 秒未操作，浏览器权限已交回 AI | 保留 | 30 秒未操作，浏览器权限已交回 AI | P0 |
| browser/AgentBrowserController.kt:47 | AI 可查看，未获操作权 | 保留 | AI 可查看，未获操作权 | P1 |
| browser/AgentBrowserController.kt:47 | 新页面 | 保留 | 新页面 | P1 |
| browser/AgentBrowserController.kt:141 | 新页面 | 保留 | 新页面 | P1 |
| browser/AgentBrowserController.kt:173 | 独立浏览器需要支持 MULTI_PROFILE 的 WebView | 工程痕迹 | 请更新系统网页组件，再打开浏览器。 | P1 |
| browser/AgentBrowserController.kt:224 | 仅支持 HTTP/HTTPS 文件下载；此类链接请使用站点的普通下载入口 | 清晰度 | 此链接无法直接下载，请使用网页上的下载入口。 | P1 |
| browser/AgentBrowserController.kt:282 | 已阻止此地址 | 保留 | 已阻止此地址 | P1 |
| browser/AgentBrowserController.kt:300 | 页面加载失败，请检查地址后重试 | 清晰度 | 页面打开失败，请检查网址和网络后重试。 | P0 |
| browser/AgentBrowserController.kt:303 | 页面返回错误（${response.statusCode}） | 清晰度 | 网页暂时打不开，请稍后刷新。 | P0 |
| browser/AgentBrowserController.kt:305 | 证书验证失败，已停止加载 | 保留 | 证书验证失败，已停止加载 | P0 |
| browser/AgentBrowserController.kt:309 | 此页面的渲染进程已停止，请关闭该页后新建页面 | 工程痕迹 | 网页已停止显示，请关闭此页后重新打开。 | P1 |
| browser/AgentBrowserController.kt:311 | 授权已撤销 | 保留 | 授权已撤销 | P0 |
| browser/AgentBrowserController.kt:413 | 本次登录已过期或已处理，请回到 DSH 重新登录 | 术语不一致 | 登录已过期，请回到助手重新登录。 | P1 |
| browser/AgentBrowserController.kt:416 | 正在将授权回调交给 DSH 验证，请稍候 | 术语不一致 | 正在确认登录，请稍候。 | P0 |
| browser/AgentBrowserController.kt:427 | 授权已返回 DSH；请回到 DSH 查看登录状态 | 术语不一致 | 登录结果已返回，请回到助手查看。 | P0 |
| browser/AgentBrowserController.kt:429 | DSH 未接受授权或网络中断，请回到 DSH 重新登录 | 术语不一致 | 登录未完成，请回到助手重试。 | P0 |
| browser/AgentBrowserController.kt:445 | 无法打开此地址 | 保留 | 无法打开此地址 | P0 |
| browser/AgentBrowserController.kt:519 | AI 已暂停，等待接管选择 | 保留 | AI 已暂停，等待接管选择 | P1 |
| browser/AgentBrowserController.kt:530 | 你在临时操作，30 秒未操作后交回 AI | 保留 | 你在临时操作，30 秒未操作后交回 AI | P1 |
| browser/AgentBrowserController.kt:547 | AI 可操作浏览器 | 保留 | AI 可操作浏览器 | P1 |
| browser/AgentBrowserController.kt:547 | 当前连接获得浏览器权限 | 清晰度 | 助手已获得浏览器操作权限。 | P0 |
| browser/AgentBrowserController.kt:552 | AI 可查看，操作权已收回且不自动恢复 | 清晰度 | 已收回操作权限。助手仍可查看，需你手动交回操作权。 | P1 |
| browser/AgentBrowserController.kt:552 | 用户收回浏览器权限 | 保留 | 用户收回浏览器权限 | P0 |
| browser/AgentBrowserController.kt:610 | 下载已开始，可在「下载与截图」中查看进度 | 保留 | 下载已开始，可在「下载与截图」中查看进度 | P1 |
| browser/AgentBrowserController.kt:611 | 无法开始下载，请检查大小、下载记录数量和地址（单文件最多 256 MiB） | 清晰度 | 无法开始下载，请检查网址、存储空间和文件大小。<br>单个文件最多 256 MiB。 | P0 |
| browser/AgentBrowserController.kt:616 | 完整网页截图已保存到「下载与截图」；不受小窗范围影响 | 清晰度 | 整页截图已保存，可在“下载与截图”查看。 | P1 |
| browser/AgentBrowserController.kt:628 | 浏览器已关闭 | 保留 | 浏览器已关闭 | P1 |
| browser/AgentBrowserController.kt:646 | 操作未完成，请检查网页或重新打开 | 保留 | 操作未完成，请检查网页或重新打开 | P1 |
| browser/AgentBrowserController.kt:673 | 已阻止此地址 | 保留 | 已阻止此地址 | P1 |
| browser/AgentBrowserController.kt:796 | Agent 请求控制 | 清晰度 | 助手请求操作 | P1 |
| browser/AgentBrowserController.kt:796 | 连接请求授权 | 清晰度 | 请求操作权限 | P0 |
| browser/AgentBrowserController.kt:801 | 代理操作：$method | 清晰度 | 助手正在操作网页 | P1 |
| browser/AgentBrowserController.kt:801 | 代理查看：$method | 清晰度 | 助手正在查看网页 | P1 |

### [browser/BrowserIntegration.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserIntegration.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| browser/BrowserIntegration.kt:27 | 浏览器 Skill 路径被重定向 | 清晰度 | 浏览器连接文件异常，请重新打开应用。 | P1 |
| browser/BrowserIntegration.kt:29 | 已有同名自定义 Skill，未覆盖 | 清晰度 | 已有同名浏览器技能，连接未更新。请检查技能设置。 | P0 |
| browser/BrowserIntegration.kt:45 | 浏览器目录被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| browser/BrowserIntegration.kt:51 | 浏览器工具被重定向 | 保留 | 浏览器工具被重定向 | P1 |
| browser/BrowserIntegration.kt:57 | 浏览器工具暂存路径被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |

### [browser/BrowserLibrary.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserLibrary.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| browser/BrowserLibrary.kt:25 | 收藏夹 | 保留 | 收藏夹 | P1 |
| browser/BrowserLibrary.kt:48 | 浏览器目录被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| browser/BrowserLibrary.kt:61 | 收藏夹 | 保留 | 收藏夹 | P1 |
| browser/BrowserLibrary.kt:65 | 下载中 | 保留 | 下载中 | P1 |
| browser/BrowserLibrary.kt:65 | 已中断 | 保留 | 已中断 | P0 |
| browser/BrowserLibrary.kt:65 | 等待中 | 保留 | 等待中 | P1 |
| browser/BrowserLibrary.kt:66 | 上次运行中断，请重新下载 | 保留 | 上次运行中断，请重新下载 | P0 |
| browser/BrowserLibrary.kt:66 | 下载中 | 保留 | 下载中 | P1 |
| browser/BrowserLibrary.kt:66 | 等待中 | 保留 | 等待中 | P1 |
| browser/BrowserLibrary.kt:105 | 收藏夹 | 保留 | 收藏夹 | P1 |
| browser/BrowserLibrary.kt:116 | 下载记录已满，请先删除不需要的下载 | 保留 | 下载记录已满，请先删除不需要的下载 | P0 |
| browser/BrowserLibrary.kt:120 | 不允许下载此地址 | 保留 | 不允许下载此地址 | P0 |
| browser/BrowserLibrary.kt:121 | 单文件下载上限为 256 MiB | 保留 | 单文件下载上限为 256 MiB | P1 |
| browser/BrowserLibrary.kt:124 | 等待中 | 保留 | 等待中 | P1 |
| browser/BrowserLibrary.kt:130 | 浏览器下载：$name | 清晰度 | 正在下载 {文件名} | P1 |
| browser/BrowserLibrary.kt:135 | 下载地址不允许访问 | 保留 | 下载地址不允许访问 | P0 |
| browser/BrowserLibrary.kt:144 | 下载重定向过多 | 保留 | 下载重定向过多 | P1 |
| browser/BrowserLibrary.kt:145 | 无效的下载跳转 | 清晰度 | 无法使用的下载跳转 | P1 |
| browser/BrowserLibrary.kt:146 | 已阻止不安全的下载跳转 | 保留 | 已阻止不安全的下载跳转 | P1 |
| browser/BrowserLibrary.kt:147 | 已阻止下载跳转 | 保留 | 已阻止下载跳转 | P1 |
| browser/BrowserLibrary.kt:149 | 下载失败 HTTP $status | 错误出口 | 操作未完成，请重试。仍失败时可查看问题详情。 | P0 |
| browser/BrowserLibrary.kt:151 | 单文件下载上限为 256 MiB | 保留 | 单文件下载上限为 256 MiB | P1 |
| browser/BrowserLibrary.kt:152 | 可用空间不足 | 清晰度 | 下载失败，请稍后重试。 | P0 |
| browser/BrowserLibrary.kt:153 | 下载中 | 保留 | 下载中 | P1 |
| browser/BrowserLibrary.kt:162 | 下载超过 256 MiB，已停止 | 保留 | 下载超过 256 MiB，已停止 | P1 |
| browser/BrowserLibrary.kt:174 | 下载内容不完整 | 保留 | 下载内容不完整 | P1 |
| browser/BrowserLibrary.kt:175 | 无法保存下载 | 保留 | 无法保存下载 | P0 |
| browser/BrowserLibrary.kt:177 | 已完成 | 保留 | 已完成 | P1 |
| browser/BrowserLibrary.kt:184 | 已取消 | 保留 | 已取消 | P0 |
| browser/BrowserLibrary.kt:187 | 下载未完成，请检查网络、空间或重新下载 | 保留 | 下载未完成，请检查网络、空间或重新下载 | P1 |
| browser/BrowserLibrary.kt:187 | 失败 | 保留 | 失败 | P0 |
| browser/BrowserLibrary.kt:187 | 已取消 | 保留 | 已取消 | P0 |
| browser/BrowserLibrary.kt:194 | 等待中 | 保留 | 等待中 | P1 |
| browser/BrowserLibrary.kt:194 | 网页截图-${System.currentTimeMillis()}.png | 清晰度 | 网页截图-{内容}.png | P1 |
| browser/BrowserLibrary.kt:197 | 保存网页截图 | 保留 | 保存网页截图 | P1 |
| browser/BrowserLibrary.kt:200 | 已完成 | 保留 | 已完成 | P1 |
| browser/BrowserLibrary.kt:202 | 失败 | 保留 | 失败 | P0 |
| browser/BrowserLibrary.kt:202 | 无法保存截图，请检查可用空间 | 保留 | 无法保存截图，请检查可用空间 | P0 |
| browser/BrowserLibrary.kt:208 |  | 清晰度 | 空值保持不显示，不输出缺省技术值。 | P1 |
| browser/BrowserLibrary.kt:211 | 导出浏览器文件 | 保留 | 导出浏览器文件 | P1 |
| browser/BrowserLibrary.kt:216 | 文件已导出 | 保留 | 文件已导出 | P1 |
| browser/BrowserLibrary.kt:235 | 下载中 | 保留 | 下载中 | P1 |
| browser/BrowserLibrary.kt:235 | 已取消 | 保留 | 已取消 | P0 |
| browser/BrowserLibrary.kt:235 | 等待中 | 保留 | 等待中 | P1 |
| browser/BrowserLibrary.kt:241 | 已完成 | 保留 | 已完成 | P1 |
| browser/BrowserLibrary.kt:241 | 文件尚未下载完成 | 保留 | 文件尚未下载完成 | P1 |
| browser/BrowserLibrary.kt:242 | 下载文件不存在 | 保留 | 下载文件不存在 | P1 |
| browser/BrowserLibrary.kt:245 | 请先取消下载并等待取消完成 | 保留 | 请先取消下载并等待取消完成 | P0 |

### [browser/BrowserOverlayWindow.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserOverlayWindow.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| browser/BrowserOverlayWindow.kt:97 | 浏览器系统悬浮窗 | 清晰度 | 浏览器悬浮窗 | P1 |
| browser/BrowserOverlayWindow.kt:103 | 浏览器 | 保留 | 浏览器 | P1 |
| browser/BrowserOverlayWindow.kt:105 | 拖动浏览器悬浮窗 | 保留 | 拖动浏览器悬浮窗 | P1 |
| browser/BrowserOverlayWindow.kt:109 | 收起 | 保留 | 收起 | P1 |
| browser/BrowserOverlayWindow.kt:109 | 收起浏览器悬浮窗 | 清晰度 | 收起悬浮窗 | P1 |
| browser/BrowserOverlayWindow.kt:113 | 悬浮窗网址输入框 | 保留 | 悬浮窗网址输入框 | P1 |
| browser/BrowserOverlayWindow.kt:113 | 搜索或输入网址 | 清晰度 | 输入网址 | P1 |
| browser/BrowserOverlayWindow.kt:129 | 撤销 | 保留 | 撤销 | P1 |
| browser/BrowserOverlayWindow.kt:129 | 撤销 AI 浏览器操作权限 | 清晰度 | 收回操作权 | P0 |
| browser/BrowserOverlayWindow.kt:135 | 搜索或输入网址开始浏览\n\n拖动顶部移动窗口\n拖动左下角或右下角调整大小 | 清晰度 | 输入网址即可浏览。<br>拖动顶部移动小窗，拖动底角调整大小。 | P1 |
| browser/BrowserOverlayWindow.kt:140 | 后退 | 保留 | 后退 | P1 |
| browser/BrowserOverlayWindow.kt:140 | 悬浮窗后退 | 保留 | 悬浮窗后退 | P1 |
| browser/BrowserOverlayWindow.kt:141 | 悬浮窗页面总览 | 保留 | 悬浮窗页面总览 | P1 |
| browser/BrowserOverlayWindow.kt:141 | 页面 | 清晰度 | 页面总览 | P1 |
| browser/BrowserOverlayWindow.kt:144 | 拖动左下角调整悬浮窗大小 | 清晰度 | 拖动左下角调整大小 | P1 |
| browser/BrowserOverlayWindow.kt:145 | 拖动右下角调整悬浮窗大小 | 清晰度 | 拖动右下角调整大小 | P1 |
| browser/BrowserOverlayWindow.kt:149 | 拖动左下角或右下角可调整窗口宽度和高度 | 保留 | 拖动左下角或右下角可调整窗口宽度和高度 | P1 |
| browser/BrowserOverlayWindow.kt:165 | ${context.getString(R.string.app_name)} browser overlay | 清晰度 | Tanuki 浏览器小窗 | P1 |
| browser/BrowserOverlayWindow.kt:192 | 全屏 | 保留 | 全屏 | P1 |
| browser/BrowserOverlayWindow.kt:192 | 打开完整浏览器 | 清晰度 | 打开浏览器 | P1 |
| browser/BrowserOverlayWindow.kt:193 | 关闭 | 保留 | 关闭 | P1 |
| browser/BrowserOverlayWindow.kt:193 | 关闭浏览器悬浮窗 | 清晰度 | 关闭悬浮窗 | P1 |
| browser/BrowserOverlayWindow.kt:196 | 前往 | 清晰度 | 打开网址 | P1 |
| browser/BrowserOverlayWindow.kt:196 | 悬浮窗前往网址 | 清晰度 | 打开悬浮窗网址 | P1 |
| browser/BrowserOverlayWindow.kt:209 | 悬浮窗新建页面 | 清晰度 | 新建页面 | P1 |
| browser/BrowserOverlayWindow.kt:209 | 新建 | 保留 | 新建 | P1 |
| browser/BrowserOverlayWindow.kt:290 | 浏览器 | 保留 | 浏览器 | P1 |
| browser/BrowserOverlayWindow.kt:291 | 等待接管 | 保留 | 等待接管 | P1 |
| browser/BrowserOverlayWindow.kt:292 | 浏览器 · AI | 保留 | 浏览器 · AI | P1 |
| browser/BrowserOverlayWindow.kt:293 | 浏览器 · 你 | 保留 | 浏览器 · 你 | P1 |
| browser/BrowserOverlayWindow.kt:296 | 登录授权页 · AI 不可查看或操作 | 清晰度 | 登录授权页，助手无法查看或操作。 | P0 |
| browser/BrowserOverlayWindow.kt:297 | AI 可查看 · 操作已暂停 | 保留 | AI 可查看 · 操作已暂停 | P1 |
| browser/BrowserOverlayWindow.kt:298 | AI 可操作 · 点网页临时接管 | 清晰度 | 助手可操作，触碰网页可临时接管。 | P1 |
| browser/BrowserOverlayWindow.kt:299 | 你在操作 · ${state.idleReturnSeconds}秒未操作后交回 AI | 清晰度 | 你在操作，{秒数} 秒未操作后交回助手。 | P1 |
| browser/BrowserOverlayWindow.kt:300 | 你在操作 · AI 可查看 | 保留 | 你在操作 · AI 可查看 | P1 |
| browser/BrowserOverlayWindow.kt:302 | 交回 | 清晰度 | 交回操作权 | P1 |
| browser/BrowserOverlayWindow.kt:302 | 撤销 | 保留 | 撤销 | P1 |
| browser/BrowserOverlayWindow.kt:303 | 交回 AI 浏览器操作权限 | 清晰度 | 交回操作权 | P0 |
| browser/BrowserOverlayWindow.kt:303 | 撤销 AI 浏览器操作权限 | 清晰度 | 收回操作权 | P0 |
| browser/BrowserOverlayWindow.kt:306 | 页面 ${state.tabs.size} | 清晰度 | 页面 {数量} | P1 |
| browser/BrowserOverlayWindow.kt:323 | 搜索或输入网址开始浏览\n\n拖动顶部移动窗口\n拖动右下角调整大小 | 清晰度 | 搜索或输入网址开始浏览<br><br>拖动顶部移动窗口<br>拖动右下角调整大小 | P1 |
| browser/BrowserOverlayWindow.kt:323 | 网页渲染已中断\n请在完整浏览器中新建页面 | 工程痕迹 | 网页显示已中断<br>请在完整浏览器中新建页面 | P0 |
| browser/BrowserOverlayWindow.kt:352 | 收回 AI 浏览器操作权限？ | 清晰度 | 临时接管浏览器？ | P0 |
| browser/BrowserOverlayWindow.kt:353 | AI 操作已暂停，这次点击尚未执行。临时接管期间 AI 可查看网页，不能点击、输入或导航；30 秒未操作会自动交回操作权。 | 长度 | 助手操作已暂停，本次点击还未执行。<br>临时接管时助手仍可查看，30 秒无操作后自动交回操作权。 | P1 |
| browser/BrowserOverlayWindow.kt:354 | 临时接管 | 保留 | 临时接管 | P1 |
| browser/BrowserOverlayWindow.kt:355 | 撤销 AI 操作权限 | 清晰度 | 收回操作权 | P0 |
| browser/BrowserOverlayWindow.kt:356 | 继续交给 AI | 清晰度 | 交给助手 | P1 |
| browser/BrowserOverlayWindow.kt:360 | 下载文件？ | 保留 | 下载文件？ | P1 |
| browser/BrowserOverlayWindow.kt:361 | 文件将保存在浏览器中，可在完整浏览器的「下载与截图」查看进度和导出。 | 清晰度 | 文件保存在“下载与截图”，可查看进度或导出。 | P1 |
| browser/BrowserOverlayWindow.kt:362 | 确认下载 | 保留 | 确认下载 | P1 |
| browser/BrowserOverlayWindow.kt:363 | 取消下载 | 保留 | 取消下载 | P0 |
| browser/BrowserOverlayWindow.kt:366 | 网页未能打开 | 保留 | 网页未能打开 | P1 |
| browser/BrowserOverlayWindow.kt:367 | 重新加载 | 保留 | 重新加载 | P1 |
| browser/BrowserOverlayWindow.kt:368 | 打开完整浏览器 | 清晰度 | 打开浏览器 | P1 |
| browser/BrowserOverlayWindow.kt:371 | ${state.tabs.size} 个页面 | 清晰度 | {数量} 个页面 | P1 |
| browser/BrowserOverlayWindow.kt:373 | 当前 ·  | 清晰度 | 当前 · | P1 |
| browser/BrowserOverlayWindow.kt:373 | 新页面 | 保留 | 新页面 | P1 |
| browser/BrowserOverlayWindow.kt:377 | 返回网页 | 保留 | 返回网页 | P1 |
| browser/BrowserOverlayWindow.kt:388 | 展开 | 保留 | 展开 | P1 |
| browser/BrowserOverlayWindow.kt:388 | 收起 | 保留 | 收起 | P1 |
| browser/BrowserOverlayWindow.kt:389 | 展开浏览器悬浮窗 | 清晰度 | 展开悬浮窗 | P1 |
| browser/BrowserOverlayWindow.kt:389 | 收起浏览器悬浮窗 | 清晰度 | 收起悬浮窗 | P1 |
| browser/BrowserOverlayWindow.kt:420 | 拖动左下角或右下角调整大小 | 保留 | 拖动左下角或右下角调整大小 | P1 |

### [browser/BrowserPreferences.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserPreferences.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| browser/BrowserPreferences.kt:6 | Windows PC | 术语不一致 | Windows 电脑 | P1 |
| browser/BrowserPreferences.kt:6 | 移动端 | 保留 | 移动端 | P1 |
| browser/BrowserPreferences.kt:7 | Linux PC | 术语不一致 | Linux 电脑 | P1 |
| browser/BrowserPreferences.kt:7 | Mac | 术语不一致 | Mac 电脑 | P1 |
| browser/BrowserPreferences.kt:16 | 必应 | 保留 | 必应 | P1 |
| browser/BrowserPreferences.kt:16 | 百度 | 保留 | 百度 | P1 |
| browser/BrowserPreferences.kt:17 | DuckDuckGo | 保留 | DuckDuckGo | P1 |
| browser/BrowserPreferences.kt:17 | Google | 保留 | Google | P1 |

### [browser/BrowserService.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/browser/BrowserService.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| browser/BrowserService.kt:25 | 浏览器后台运行 | 清晰度 | 浏览器正在后台运行 | P1 |
| browser/BrowserService.kt:26 | 浏览器后台运行 · AI 可查看，未获操作权 | 清晰度 | 浏览器正在后台运行，助手只能查看。 | P1 |
| browser/BrowserService.kt:31 | 下载中 | 保留 | 下载中 | P1 |
| browser/BrowserService.kt:31 | 等待中 | 保留 | 等待中 | P1 |
| browser/BrowserService.kt:32 | AI 可查看，未获操作权 | 保留 | AI 可查看，未获操作权 | P1 |
| browser/BrowserService.kt:32 | AI 已暂停，等待接管选择 | 保留 | AI 已暂停，等待接管选择 | P1 |
| browser/BrowserService.kt:32 | AI 已获操作权，可后台操作 | 清晰度 | 助手可在后台操作浏览器。 | P1 |
| browser/BrowserService.kt:33 | ${if (state.overlayFrame != null) "悬浮窗 · " else ""}${state.tabs.size} 个页面 · $control | 工程痕迹 | {状态}{数量} 个页面 · {内容} | P1 |
| browser/BrowserService.kt:33 | 悬浮窗 ·  | 清晰度 | 悬浮窗 · | P1 |
| browser/BrowserService.kt:45 | ${getString(R.string.app_name)} 浏览器 | 清晰度 | {名称} 浏览器 | P1 |
| browser/BrowserService.kt:47 | 撤销 AI 操作授权 | 清晰度 | 收回操作权 | P0 |
| browser/BrowserService.kt:48 | 关闭悬浮窗 | 保留 | 关闭悬浮窗 | P1 |
| browser/BrowserService.kt:49 | 停止后台浏览器 | 清晰度 | 停止浏览器 | P1 |
| browser/BrowserService.kt:73 | 需要先允许「显示在其他应用上层」，才能打开系统悬浮窗 | 保留 | 需要先允许「显示在其他应用上层」，才能打开系统悬浮窗 | P0 |
| browser/BrowserService.kt:80 | 请从通知或桌面打开完整浏览器 | 保留 | 请从通知或桌面打开完整浏览器 | P1 |
| browser/BrowserService.kt:83 | 系统未能显示悬浮窗，请检查悬浮窗权限并重试 | 保留 | 系统未能显示悬浮窗，请检查悬浮窗权限并重试 | P0 |
| browser/BrowserService.kt:84 | 悬浮窗未打开，请检查系统权限 | 保留 | 悬浮窗未打开，请检查系统权限 | P0 |

### [download/DownloadRetryPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/download/DownloadRetryPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| download/DownloadRetryPolicy.kt:9 | 重试序号超出范围 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [download/Downloader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/download/Downloader.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| download/Downloader.kt:37 | 下载失败 HTTP $status: $host（文件未同步或已删除） | 清晰度 | 下载文件暂不可用，请更换下载源。 | P0 |
| download/Downloader.kt:38 | 下载失败 HTTP 429: $host（请求过于频繁，已指数退避） | 清晰度 | 下载请求过多，正在等待重试。 | P0 |
| download/Downloader.kt:39 | 下载失败 HTTP $status: $host（服务端暂时不可用，已指数退避） | 清晰度 | 下载服务暂不可用，正在等待重试。 | P0 |
| download/Downloader.kt:40 | 下载失败 HTTP 403: $host（源拒绝访问） | 清晰度 | 下载源拒绝访问，请更换下载源。 | P0 |
| download/Downloader.kt:41 | 下载失败 HTTP $status: $host | 清晰度 | 下载失败，请检查网络或更换下载源。 | P0 |
| download/Downloader.kt:44 | 下载内容超过安全大小限制 | 保留 | 下载内容超过安全大小限制 | P1 |
| download/Downloader.kt:68 | SHA-256 校验值格式无效 | 工程痕迹 | 下载校验信息无法使用，请刷新版本列表。 | P1 |
| download/Downloader.kt:80 | 下载目标路径无效 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| download/Downloader.kt:81 | 无法创建下载目录 | 保留 | 无法创建下载目录 | P0 |
| download/Downloader.kt:82 | 下载地址无效 | 清晰度 | 下载地址无法使用 | P1 |
| download/Downloader.kt:89 | 下载目标不是普通文件 | 保留 | 下载目标不是普通文件 | P1 |
| download/Downloader.kt:90 | 下载缓存不是普通文件 | 保留 | 下载缓存不是普通文件 | P1 |
| download/Downloader.kt:91 | 下载校验缓存不是普通文件 | 保留 | 下载校验缓存不是普通文件 | P1 |
| download/Downloader.kt:92 | 下载来源缓存不是普通文件 | 保留 | 下载来源缓存不是普通文件 | P1 |
| download/Downloader.kt:120 | 下载中断（已重试 ${DownloadRetryPolicy.MAX_ATTEMPTS - 1} 次）： | 清晰度 | 下载多次中断，请检查网络或更换下载源。 | P0 |
| download/Downloader.kt:121 | ${error.message ?: "网络错误"}，请检查网络或切换下载源 | 清晰度 | 下载中断，请检查网络或更换下载源。 | P0 |
| download/Downloader.kt:121 | 网络错误 | 保留 | 网络错误 | P0 |
| download/Downloader.kt:127 | 续传失败，请重试 | 保留 | 续传失败，请重试 | P0 |
| download/Downloader.kt:132 | 下载内容长度不一致 | 保留 | 下载内容长度不一致 | P1 |
| download/Downloader.kt:133 | 下载内容长度与声明不一致 | 保留 | 下载内容长度与声明不一致 | P1 |
| download/Downloader.kt:134 | ${check.label} 校验失败 | 清晰度 | 下载文件不完整，请重新下载。 | P0 |
| download/Downloader.kt:173 | 下载大小与声明不一致 | 保留 | 下载大小与声明不一致 | P1 |
| download/Downloader.kt:181 | 续传文件长度不一致 | 保留 | 续传文件长度不一致 | P1 |
| download/Downloader.kt:193 | 下载内容超出声明长度 | 保留 | 下载内容超出声明长度 | P1 |
| download/Downloader.kt:194 | 空间不足，下载已停止；项目不会被删除 | 清晰度 | 存储空间不足，请清理空间后重新下载。 | P0 |
| download/Downloader.kt:201 | 下载内容不完整 | 保留 | 下载内容不完整 | P1 |
| download/Downloader.kt:208 | 下载地址无效 | 清晰度 | 下载地址无法使用 | P1 |
| download/Downloader.kt:225 | 下载重定向缺少目标 | 保留 | 下载重定向缺少目标 | P1 |
| download/Downloader.kt:226 | 下载重定向次数过多 | 保留 | 下载重定向次数过多 | P1 |
| download/Downloader.kt:228 | 下载重定向目标无效 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| download/Downloader.kt:229 | 下载重定向目标无效 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| download/Downloader.kt:235 | 下载源必须使用 HTTPS | 保留 | 下载源必须使用 HTTPS | P1 |
| download/Downloader.kt:236 | 下载源不得包含凭据 | 清晰度 | 下载地址包含账号信息，已停止下载。请更换下载源。 | P1 |
| download/Downloader.kt:238 | 下载源不在允许列表：${host ?: "未知"} | 被动/实现说明 | 下载来源无法确认，请选择其他下载源。 | P0 |
| download/Downloader.kt:238 | 未知 | 被动/实现说明 | 未知 | P1 |
| download/Downloader.kt:239 | 下载源端口不受支持：$host | 清晰度 | 下载地址无法使用，请更换下载源。 | P1 |
| download/Downloader.kt:289 | 仅接受 sha512-&lt;base64&gt; 完整性校验值 | 工程痕迹 | 下载校验信息无法使用，请刷新版本列表。 | P1 |
| download/Downloader.kt:291 | sha512 完整性校验值无效 | 工程痕迹 | 下载校验信息无法使用，请刷新版本列表。 | P1 |
| download/Downloader.kt:293 | sha512 完整性校验值长度无效 | 工程痕迹 | 下载校验信息无法使用，请刷新版本列表。 | P1 |
| download/Downloader.kt:324 | 下载路径被重定向，已停止：${file.name} | 清晰度 | 下载位置异常，已停止下载。请重新打开应用。 | P1 |

### [lan/LanDiscovery.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/lan/LanDiscovery.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| lan/LanDiscovery.kt:230 | 局域网发现的电脑 | 保留 | 局域网发现的电脑 | P1 |
| lan/LanDiscovery.kt:231 | 正在查找同一 Wi-Fi 下的电脑… | 保留 | 正在查找同一 Wi-Fi 下的电脑… | P1 |
| lan/LanDiscovery.kt:232 | 没有发现同一 Wi-Fi 下的电脑 | 保留 | 没有发现同一 Wi-Fi 下的电脑 | P0 |
| lan/LanDiscovery.kt:233 | 在同一 Wi-Fi 下运行电脑端命令（仓库根目录），并保持终端开着： | 清晰度 | 请在同一 Wi-Fi 下打开电脑上的连接程序，并保持窗口打开。 | P1 |
| lan/LanDiscovery.kt:235 | 复制命令 | 保留 | 复制命令 | P1 |
| lan/LanDiscovery.kt:236 | 已复制 | 保留 | 已复制 | P1 |
| lan/LanDiscovery.kt:237 | 重新扫描 | 保留 | 重新扫描 | P1 |
| lan/LanDiscovery.kt:238 | 改用扫码 | 保留 | 改用扫码 | P1 |
| lan/LanDiscovery.kt:239 | 手动填写地址 | 清晰度 | 填写地址 | P1 |
| lan/LanDiscovery.kt:240 | 未开启局域网发现权限，已改为扫码或粘贴连接。 | 保留 | 未开启局域网发现权限，已改为扫码或粘贴连接。 | P0 |
| lan/LanDiscovery.kt:241 | 其他方式 | 保留 | 其他方式 | P1 |
| lan/LanDiscovery.kt:242 | 不在同一 Wi-Fi，或列表里没有电脑时，扫码或粘贴配对链接。 | 保留 | 不在同一 Wi-Fi，或列表里没有电脑时，扫码或粘贴配对链接。 | P0 |
| lan/LanDiscovery.kt:243 | 连接时会核对电脑指纹，密码和私钥只加密存在本机 | 清晰度 | 首次连接请核对电脑安全指纹。密码和私钥加密保存在手机。 | P0 |
| lan/LanDiscovery.kt:244 | 在线 | 保留 | 在线 | P1 |
| lan/LanDiscovery.kt:245 | 离线 | 保留 | 离线 | P1 |
| lan/LanDiscovery.kt:246 | 平台未提供 | 保留 | 平台未提供 | P1 |
| lan/LanDiscovery.kt:247 | 地址无法直连，可改用扫码 | 保留 | 地址无法直连，可改用扫码 | P0 |
| lan/LanDiscovery.kt:253 | $PLATFORM · 协议 v${host.protocolVersion} | 清晰度 | 此地址无法连接，请改用配对链接。 | P1 |

### [lan/LanDiscoveryController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/lan/LanDiscoveryController.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| lan/LanDiscoveryController.kt:191 | 暂时找不到局域网电脑，可以重新扫描或扫码、粘贴连接。 | 清晰度 | 电脑连接信息见详情。 | P1 |

### [notify/ChatNotificationCopy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationCopy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| notify/ChatNotificationCopy.kt:11 | DSH 会话回合完成 | 术语不一致 | 回复已完成 | P0 |
| notify/ChatNotificationCopy.kt:12 | DSH 等待你的确认 | 术语不一致 | 有内容待确认 | P0 |
| notify/ChatNotificationCopy.kt:13 | DSH 会话出现错误 | 术语不一致 | 对话出现错误 | P0 |
| notify/ChatNotificationCopy.kt:14 | DSH 请求权限审批 | 术语不一致 | 有操作待授权 | P0 |
| notify/ChatNotificationCopy.kt:18 | 有会话已结束当前回合。点击返回 Tanuki 查看。 | 术语不一致 | 助手已完成本次回复，点此查看。 | P0 |
| notify/ChatNotificationCopy.kt:19 | 有工具调用或问题在等待处理。点击返回对应会话。 | 术语不一致 | 有操作或问题等你处理，点此打开对话。 | P0 |
| notify/ChatNotificationCopy.kt:20 | 会话报告了错误。点击返回 Tanuki 查看状态。 | 术语不一致 | 对话遇到问题，点此查看处理方法。 | P0 |
| notify/ChatNotificationCopy.kt:21 | 有工具调用在等待你的授权决定。点击返回对应会话。 | 术语不一致 | 有操作需要你授权，点此查看。 | P0 |

### [notify/ChatNotificationPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| notify/ChatNotificationPolicy.kt:141 | 通知已开启 | 保留 | 通知已开启 | P0 |
| notify/ChatNotificationPolicy.kt:142 | 事件格式无效，已忽略 | 清晰度 | 无法识别这条提醒，已忽略。 | P0 |
| notify/ChatNotificationPolicy.kt:143 | 应用内聊天通知已关闭 | 保留 | 应用内聊天通知已关闭 | P0 |
| notify/ChatNotificationPolicy.kt:144 | 未授予通知权限 | 保留 | 未授予通知权限 | P0 |
| notify/ChatNotificationPolicy.kt:145 | 系统已关闭本应用通知 | 保留 | 系统已关闭本应用通知 | P0 |
| notify/ChatNotificationPolicy.kt:146 | 系统已关闭 DSH 会话通知渠道 | 术语不一致 | 系统已关闭对话通知。 | P0 |
| notify/ChatNotificationPolicy.kt:147 | 应用在前台且对应会话可见，不重复提醒 | 术语不一致 | 你正在查看这段对话，无需另行通知。 | P0 |
| notify/ChatNotificationPolicy.kt:148 | 同一事件已提醒过 | 清晰度 | 这条提醒已发送。 | P0 |

### [notify/ChatNotificationStatus.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotificationStatus.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| notify/ChatNotificationStatus.kt:13 | 已关闭 | 保留 | 已关闭 | P0 |
| notify/ChatNotificationStatus.kt:14 | 未授予通知权限 | 保留 | 未授予通知权限 | P0 |
| notify/ChatNotificationStatus.kt:15 | 系统已关闭本应用通知 | 保留 | 系统已关闭本应用通知 | P0 |
| notify/ChatNotificationStatus.kt:16 | 系统已关闭 DSH 会话通知渠道 | 术语不一致 | 系统已关闭对话通知。 | P0 |
| notify/ChatNotificationStatus.kt:17 | 已开启 | 保留 | 已开启 | P0 |
| notify/ChatNotificationStatus.kt:20 | $current；上次事件未提醒：${suppressed.description()} | 清晰度 | {当前状态}。上次未提醒：{原因} | P0 |

### [notify/ChatNotifier.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/notify/ChatNotifier.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| notify/ChatNotifier.kt:42 | DSH 会话通知 | 术语不一致 | 对话通知 | P0 |
| notify/ChatNotifier.kt:43 | 回合完成、等待确认、错误或权限请求；不包含会话内容 | 术语不一致 | 回复完成或需要处理时提醒，通知不显示对话内容。 | P0 |
| notify/ChatNotifier.kt:101 | DSH 会话动态 | 术语不一致 | 对话提醒 | P0 |
| notify/ChatNotifier.kt:102 | 点击返回 Tanuki 查看会话。通知不包含会话内容。 | 术语不一致 | 点此返回 Tanuki 查看对话。 | P0 |

### [onboarding/OnboardingEnvironment.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/onboarding/OnboardingEnvironment.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| onboarding/OnboardingEnvironment.kt:17 | 未知 | 被动/实现说明 | 未知 | P0 |
| onboarding/OnboardingEnvironment.kt:74 | 可用存储 | 保留 | 可用存储 | P0 |
| onboarding/OnboardingEnvironment.kt:77 | 至少 ${formatBytes(STORAGE_MIN_BYTES)} | 清晰度 | 至少 {大小} | P0 |
| onboarding/OnboardingEnvironment.kt:78 | 可用空间不足。请先清理手机存储或转移大文件，至少留出 1.5 GB；建议预留 4 GB 后再回来重试。 | 长度 | 空间不足，请至少腾出 1.5 GB。建议预留 4 GB。 | P0 |
| onboarding/OnboardingEnvironment.kt:82 | 可用存储 | 保留 | 可用存储 | P0 |
| onboarding/OnboardingEnvironment.kt:85 | 建议 ${formatBytes(STORAGE_RECOMMENDED_BYTES)} | 清晰度 | 建议 {大小} | P0 |
| onboarding/OnboardingEnvironment.kt:86 | 空间偏少，仍可继续；安装或更新时可能因空间不足中断，建议清理到 4 GB 以上。 | 清晰度 | 空间偏少，建议腾出 4 GB 后再准备。 | P0 |
| onboarding/OnboardingEnvironment.kt:90 | 可用存储 | 保留 | 可用存储 | P0 |
| onboarding/OnboardingEnvironment.kt:93 | 建议 ${formatBytes(STORAGE_RECOMMENDED_BYTES)} | 清晰度 | 建议 {大小} | P0 |
| onboarding/OnboardingEnvironment.kt:99 | 运行内存 | 保留 | 运行内存 | P0 |
| onboarding/OnboardingEnvironment.kt:102 | 至少 ${formatBytes(MEMORY_MIN_BYTES)} | 清晰度 | 至少 {大小} | P0 |
| onboarding/OnboardingEnvironment.kt:103 | 运行内存不足。请关闭后台应用后重试；这台设备更适合连接远程电脑使用。 | 清晰度 | 运行内存不足，可改用远程电脑。 | P0 |
| onboarding/OnboardingEnvironment.kt:107 | 运行内存 | 保留 | 运行内存 | P0 |
| onboarding/OnboardingEnvironment.kt:110 | 建议 ${formatBytes(MEMORY_RECOMMENDED_BYTES)} | 清晰度 | 建议 {大小} | P0 |
| onboarding/OnboardingEnvironment.kt:111 | 内存偏少，仍可继续；同时运行较多应用时可能变慢，建议一次只保留一个会话。 | 术语不一致 | 运行内存偏少，同时打开多个应用时可能变慢。 | P0 |
| onboarding/OnboardingEnvironment.kt:115 | 运行内存 | 保留 | 运行内存 | P0 |
| onboarding/OnboardingEnvironment.kt:118 | 建议 ${formatBytes(MEMORY_RECOMMENDED_BYTES)} | 清晰度 | 建议 {大小} | P0 |
| onboarding/OnboardingEnvironment.kt:124 | 设备架构 | 保留 | 设备架构 | P0 |
| onboarding/OnboardingEnvironment.kt:128 | 本版本只支持 ARM64 设备。当前设备无法在本机准备运行环境，可以改用“连接远程电脑”。 | 长度 | 这台设备无法在本机运行助手，可连接远程电脑。 | P0 |
| onboarding/OnboardingEnvironment.kt:132 | 设备架构 | 保留 | 设备架构 | P0 |
| onboarding/OnboardingEnvironment.kt:141 | 系统版本 | 保留 | 系统版本 | P0 |
| onboarding/OnboardingEnvironment.kt:144 | Android 8.0（API $MIN_SDK）或更高 | 清晰度 | Android 8.0 或更高版本 | P0 |
| onboarding/OnboardingEnvironment.kt:145 | 系统版本过低，无法安装本应用所需的运行组件。请升级系统或改用其他设备。 | 清晰度 | 系统版本过低，请升级系统或换一台设备。 | P0 |
| onboarding/OnboardingEnvironment.kt:149 | 系统版本 | 保留 | 系统版本 | P0 |
| onboarding/OnboardingEnvironment.kt:152 | Android 8.0（API $MIN_SDK）或更高 | 清晰度 | Android 8.0 或更高版本 | P0 |
| onboarding/OnboardingEnvironment.kt:160 | 未知 | 被动/实现说明 | 未知 | P0 |

### [onboarding/OnboardingPrepare.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/onboarding/OnboardingPrepare.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| onboarding/OnboardingPrepare.kt:64 | 准备未完成 | 保留 | 准备未完成 | P0 |
| onboarding/OnboardingPrepare.kt:72 | 已取消 | 保留 | 已取消 | P0 |
| onboarding/OnboardingPrepare.kt:73 | 已停止准备 | 保留 | 已停止准备 | P0 |
| onboarding/OnboardingPrepare.kt:73 | ；可以随时重试，完整下载会被复用。 | 清晰度 | 可随时重新准备，已完成的下载可继续使用。 | P0 |
| onboarding/OnboardingPrepare.kt:79 | 环境已就绪 | 保留 | 环境已就绪 | P0 |
| onboarding/OnboardingPrepare.kt:80 | 安装与自检已完成，可以开始使用。 | 清晰度 | 运行环境已准备好，继续设置模型即可使用。 | P0 |
| onboarding/OnboardingPrepare.kt:87 | 下载运行环境 | 清晰度 | 正在下载环境 | P0 |
| onboarding/OnboardingPrepare.kt:88 | 正在下载，完成后会自动解压并校验。 | 保留 | 正在下载，完成后会自动解压并校验。 | P0 |
| onboarding/OnboardingPrepare.kt:93 | 解压 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:96 | 解压运行环境 | 清晰度 | 正在解压文件 | P0 |
| onboarding/OnboardingPrepare.kt:99 | 校验 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:102 | 校验运行环境 | 清晰度 | 正在检查文件 | P0 |
| onboarding/OnboardingPrepare.kt:108 | 补齐系统组件 | 清晰度 | 正在安装组件 | P0 |
| onboarding/OnboardingPrepare.kt:117 | 校验运行环境 | 清晰度 | 正在检查文件 | P0 |
| onboarding/OnboardingPrepare.kt:118 | 正在做启动与完整性检查。 | 清晰度 | 正在检查运行环境能否启动。 | P0 |
| onboarding/OnboardingPrepare.kt:123 | 等待开始 | 保留 | 等待开始 | P0 |
| onboarding/OnboardingPrepare.kt:124 | 准备过程会下载、解压并校验必要组件。 | 清晰度 | 准备时会下载并安装运行所需组件。 | P0 |
| onboarding/OnboardingPrepare.kt:141 | 准备没有完成，但系统没有返回具体原因。请重试；若仍失败，可在设置 → 关于与诊断中查看运行诊断。 | 长度 | 准备失败，请重试。仍失败时可在设置中查看诊断信息。 | P0 |
| onboarding/OnboardingPrepare.kt:144 | 空间不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:145 | 存储空间不足。请清理手机存储，至少留出 1.5 GB 后重试；已下载的完整文件会复用。 | 长度 | 空间不足，请至少腾出 1.5 GB 后重试。 | P0 |
| onboarding/OnboardingPrepare.kt:146 | 校验 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:147 | 下载文件校验未通过，内容可能不完整。已保留可用缓存，请重试；反复失败可更换网络或下载源。 | 长度 | 下载文件不完整，请重新下载。仍失败时可换一个下载源。 | P0 |
| onboarding/OnboardingPrepare.kt:148 | 拒绝访问 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:148 | 权限 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:149 | 文件访问被拒绝。请检查系统存储权限后重试；必要时在系统设置中允许本应用访问存储。 | 清晰度 | 无法访问所需文件，请重新选择文件后重试。 | P0 |
| onboarding/OnboardingPrepare.kt:150 | 取消 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:151 | 操作已取消。可以随时重试，已完成的下载会继续复用。 | 清晰度 | 准备已取消，可随时重试。 | P0 |
| onboarding/OnboardingPrepare.kt:152 | 下载失败 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:152 | 下载源 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:152 | 网络 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:152 | 超时 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:152 | 连接失败 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| onboarding/OnboardingPrepare.kt:153 | 网络连接失败或下载源不可用。请切换网络后重试；准备过程会自动尝试其他可用下载源。 | 清晰度 | 网络或下载源暂不可用，请切换网络后重试。 | P0 |
| onboarding/OnboardingPrepare.kt:154 | 环境准备没有完成，操作已经停止。请重试；若仍失败，可展开技术细节查看原因或复制反馈。 | 长度 | 环境准备失败，请重试。仍失败时可展开详情反馈问题。 | P0 |

### [power/DeviceConditionMonitor.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/power/DeviceConditionMonitor.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| power/DeviceConditionMonitor.kt:118 | 只有后台更新检查可以使用此入口 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [power/DeviceConditionPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/power/DeviceConditionPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| power/DeviceConditionPolicy.kt:104 | 轻热阈值不能为 NONE | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:105 | 轻热阈值必须低于中热阈值 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:106 | 中热阈值必须低于严重热阈值 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:107 | 低电进入阈值必须在 0..100 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:109 | 低电退出阈值必须大于进入阈值且不超过 100 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:111 | 温度迟滞时间不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:112 | 内存迟滞时间不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:114 | 下载并发额度必须逐级递减且至少为 1 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:117 | 日志行数上限必须逐级递减且至少为 1 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:120 | 预览尺寸上限必须逐级递减且不小于 256 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:123 | 预览像素上限必须逐级递减且至少为 1 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:125 | 低电预览尺寸上限无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:126 | 低电预览像素上限无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:164 | 设备状态正常 | 保留 | 设备状态正常 | P1 |
| power/DeviceConditionPolicy.kt:168 | 设备严重过热 | 保留 | 设备严重过热 | P1 |
| power/DeviceConditionPolicy.kt:169 | 设备过热 | 保留 | 设备过热 | P1 |
| power/DeviceConditionPolicy.kt:170 | 设备温度偏高 | 保留 | 设备温度偏高 | P1 |
| power/DeviceConditionPolicy.kt:172 | 电量偏低且未充电 | 保留 | 电量偏低且未充电 | P1 |
| power/DeviceConditionPolicy.kt:173 | 系统内存紧张 | 保留 | 系统内存紧张 | P1 |
| power/DeviceConditionPolicy.kt:179 | 正常 | 保留 | 正常 | P1 |
| power/DeviceConditionPolicy.kt:180 | 轻度降级，已降低非关键并发 | 清晰度 | 手机温度偏高，已降低后台活动。 | P1 |
| power/DeviceConditionPolicy.kt:181 | 过热，后台更新与下载已降速 | 清晰度 | 手机过热，已降低后台更新和下载速度。 | P0 |
| power/DeviceConditionPolicy.kt:182 | 严重过热，仅保留必要前台任务与最低下载窗口 | 清晰度 | 手机严重过热，已减少后台活动。 | P1 |
| power/DeviceConditionPolicy.kt:183 | 低电，后台更新延后、下载降速 | 清晰度 | 电量偏低，已延后后台更新并降低下载速度。 | P0 |
| power/DeviceConditionPolicy.kt:217 | 只有后台更新检查可以登记延后 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:254 | 只有后台更新检查可以申请准入 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| power/DeviceConditionPolicy.kt:362 | 前台任务与 DSH 任务不暂停、不取消 | 术语不一致 | 当前任务继续运行。 | P0 |
| power/DeviceConditionPolicy.kt:364 | 温控与省电策略已关闭 | 保留 | 温控与省电策略已关闭 | P1 |
| power/DeviceConditionPolicy.kt:366 | ${state.reasonText()}，${workLabel(work)}已跳过一次，恢复后补跑 | 清晰度 | {设备状态}，本次后台更新已延后，恢复后会自动重试。 | P1 |
| power/DeviceConditionPolicy.kt:370 | 当前状态允许后台更新检查 | 清晰度 | 可以在后台检查更新。 | P0 |
| power/DeviceConditionPolicy.kt:371 | ${state.reasonText()}，${workLabel(work)}延后到状态恢复 | 清晰度 | {设备状态}，后台更新会在恢复后继续。 | P1 |
| power/DeviceConditionPolicy.kt:373 | 依赖下载按正常额度运行 | 清晰度 | 正在正常下载。 | P1 |
| power/DeviceConditionPolicy.kt:374 | ${state.reasonText()}，依赖下载并发降至 ${state.workerCap} | 工程痕迹 | {设备状态}，已降低下载速度。 | P1 |
| power/DeviceConditionPolicy.kt:376 | 用户主动下载保持单路运行，不取消已开始的文件 | 清晰度 | 当前下载继续进行。 | P0 |
| power/DeviceConditionPolicy.kt:378 | 日志按正常行数展示 | 清晰度 | 正在显示运行日志。 | P1 |
| power/DeviceConditionPolicy.kt:379 | ${state.reasonText()}，日志展示降为 ${state.logLineLimit} 行 | 清晰度 | {设备状态}，已减少日志显示数量。 | P1 |
| power/DeviceConditionPolicy.kt:381 | 预览按正常分辨率渲染 | 工程痕迹 | 正在显示文件预览。 | P1 |
| power/DeviceConditionPolicy.kt:382 | ${state.reasonText()}，预览上限降至 ${state.previewMaxDimension} 像素 | 清晰度 | {设备状态}，已降低预览清晰度。 | P1 |
| power/DeviceConditionPolicy.kt:384 | 前台任务与 DSH 任务不暂停、不取消 | 术语不一致 | 当前任务继续运行。 | P0 |
| power/DeviceConditionPolicy.kt:489 | APK 更新检查 | 清晰度 | 应用更新 | P0 |
| power/DeviceConditionPolicy.kt:490 | DSH 更新检查 | 术语不一致 | 助手更新 | P0 |
| power/DeviceConditionPolicy.kt:491 | 依赖下载 | 清晰度 | 安装文件下载 | P1 |
| power/DeviceConditionPolicy.kt:492 | 用户下载 | 保留 | 用户下载 | P1 |
| power/DeviceConditionPolicy.kt:493 | 日志展示 | 清晰度 | 运行日志 | P1 |
| power/DeviceConditionPolicy.kt:494 | 预览渲染 | 工程痕迹 | 文件预览 | P1 |
| power/DeviceConditionPolicy.kt:495 | 前台任务 | 保留 | 前台任务 | P1 |
| power/DeviceConditionPolicy.kt:496 | DSH 任务 | 术语不一致 | 助手任务 | P1 |

### [runtime/CommandDeadline.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/CommandDeadline.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/CommandDeadline.kt:7 | 命令执行超时（${timeoutMs / 1000} 秒） | 清晰度 | 操作等待时间过长，请检查状态后重试。 | P0 |
| runtime/CommandDeadline.kt:10 | 命令超时必须大于零 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [runtime/DshCookieIsolation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/DshCookieIsolation.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/DshCookieIsolation.kt:68 | Cookie 隔离不能在主线程执行 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/DshCookieIsolation.kt:73 | 无法让旧管理页停止请求，已取消切换 | 清晰度 | 页面仍在连接，切换已取消。请稍后重试。 | P0 |
| runtime/DshCookieIsolation.kt:77 | 无法清理管理 WebView Cookie，已取消切换 | 工程痕迹 | 无法安全切换页面，切换已取消。请重启应用。 | P0 |

### [runtime/DshStartup.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/DshStartup.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/DshStartup.kt:57 | DSH 启动支持目录被重定向 | 术语不一致、错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| runtime/DshStartup.kt:59 | DSH 启动支持路径不是目录 | 术语不一致、错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| runtime/DshStartup.kt:62 | DSH 启动缓存脚本被重定向 | 术语不一致 | 助手 启动缓存脚本被重定向 | P1 |
| runtime/DshStartup.kt:65 | DSH 启动缓存脚本为空 | 术语不一致 | 助手 启动缓存脚本为空 | P0 |
| runtime/DshStartup.kt:73 | DSH 启动缓存暂存路径被重定向 | 术语不一致、错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |

### [runtime/DshVersions.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/DshVersions.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/DshVersions.kt:44 | 旧版切换需先恢复匹配的数据快照 | 术语不一致 | 使用旧版本前，请先恢复对应的数据备份。 | P1 |
| runtime/DshVersions.kt:189 | 更新检查或安装正在进行，请稍后再操作 DSH 版本 | 术语不一致 | 更新检查或安装正在进行，请稍后再操作 助手 版本 | P0 |
| runtime/DshVersions.kt:210 | 正在使用的 DSH 版本不能卸载；请先切换到其他版本 | 术语不一致 | 正在使用的 助手 版本不能卸载；请先切换到其他版本 | P1 |
| runtime/DshVersions.kt:211 | DSH 正在运行，请先停止服务再卸载版本 | 术语不一致 | 助手 正在运行，请先停止服务再卸载版本 | P1 |
| runtime/DshVersions.kt:212 | 更新检查或安装正在进行，请稍后再卸载版本 | 保留 | 更新检查或安装正在进行，请稍后再卸载版本 | P0 |
| runtime/DshVersions.kt:213 | 请等待当前操作完成并停止 DSH 后再卸载版本 | 术语不一致 | 请等待当前操作完成并停止 助手 后再卸载版本 | P1 |
| runtime/DshVersions.kt:214 | 当前安装元数据缺少有效 DSH 版本，无法确认正在使用的版本；已拒绝卸载 | 工程痕迹、术语不一致 | 无法确认当前使用的版本，已停止卸载。请恢复对应数据备份。 | P0 |
| runtime/DshVersions.kt:228 | DSH 版本格式无效 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |
| runtime/DshVersions.kt:231 | DSH $version 未安装或程序目录不完整 | 术语不一致 | 所选助手版本未安装或文件不完整，请重新安装。 | P0 |
| runtime/DshVersions.kt:251 | DSH 版本格式无效 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |
| runtime/DshVersions.kt:252 | DSH $version 未安装或程序目录不完整 | 术语不一致 | 所选助手版本未安装或文件不完整，请重新安装。 | P0 |
| runtime/DshVersions.kt:253 | 上次卸载的残留目录尚未清理，请重新打开应用完成恢复 | 保留 | 上次卸载的残留目录尚未清理，请重新打开应用完成恢复 | P1 |
| runtime/DshVersions.kt:255 | 无法创建卸载隔离目录；原版本已保留 | 工程痕迹 | 卸载未开始，请检查空间后重试。原版本保留。 | P0 |
| runtime/DshVersions.kt:260 | 无法开始卸载 DSH $version；原版本未改动：${reason(error)} | 术语不一致 | 无法开始卸载 助手 {版本}；原版本未改动：{原因} | P0 |
| runtime/DshVersions.kt:264 | 无法移出 DSH $version 程序目录；原版本已保留 | 术语不一致 | 无法移出 助手 {版本} 程序目录；原版本已保留 | P0 |
| runtime/DshVersions.kt:273 | 卸载 DSH $version 未能写入事务记录，已回滚并保留原版本：${reason(error)} | 工程痕迹、术语不一致 | 卸载失败，已恢复原版本。请重新打开应用后重试。 | P1 |
| runtime/DshVersions.kt:277 | 卸载 DSH $version 未能写入事务记录，且回滚未完成；已保留恢复记录，程序在 ${quarantine.name}，请重新打开应用完成恢复：${reason(error)} | 工程痕迹、术语不一致、长度 | 卸载未完成，需要恢复。请重新打开应用，保留现有文件。 | P1 |
| runtime/DshVersions.kt:287 | 卸载 DSH $version 失败，程序目录已隔离且残留清理未完成；请重新打开应用完成清理，用户数据未删除：${reason(error)} | 术语不一致、长度 | 卸载清理未完成，请重新打开应用继续处理。 | P0 |
| runtime/DshVersions.kt:337 | 上次卸载回滚未完成，请保留数据后重试 | 保留 | 上次卸载回滚未完成，请保留数据后重试 | P1 |
| runtime/DshVersions.kt:368 | 请等待当前操作完成并停止 DSH 后再切换版本 | 术语不一致 | 请等待当前操作完成并停止 助手 后再切换版本 | P1 |
| runtime/DshVersions.kt:370 | 请先停止 DSH 服务再切换版本 | 术语不一致 | 请先停止 助手 服务再切换版本 | P1 |
| runtime/DshVersions.kt:371 | DSH 版本格式无效 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |
| runtime/DshVersions.kt:372 | 当前安装元数据缺少有效 DSH 版本；请先恢复匹配的数据快照 | 工程痕迹、术语不一致 | 无法确认当前版本，请恢复对应的数据备份。 | P0 |
| runtime/DshVersions.kt:373 | 当前 DSH $current 程序缺失或不完整；请先重装当前版本或恢复匹配的数据快照，再切换 | 术语不一致、长度 | 当前助手文件不完整，请重装当前版本或恢复对应备份。 | P1 |
| runtime/DshVersions.kt:374 | DSH $target 已是当前版本 | 术语不一致 | 助手 {内容} 已是当前版本 | P1 |
| runtime/DshVersions.kt:375 | DSH $target 未安装或程序目录不完整 | 术语不一致 | 所选版本未安装或文件不完整，请先安装。 | P0 |
| runtime/DshVersions.kt:376 | DSH $target 的依赖锁缺失或与 .installed 不一致；请先卸载并重装该版本，当前版本保留 | 工程痕迹、术语不一致、长度 | 所选版本文件不完整，请卸载该备用版本后重新安装。 | P1 |

### [runtime/FailureCatalog.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/FailureCatalog.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/FailureCatalog.kt:79 | 应用版本：$versionName | 清晰度 | 应用版本：{版本} | P0 |
| runtime/FailureCatalog.kt:85 | 发生时间：$time | 清晰度 | 发生时间：{内容} | P0 |
| runtime/FailureCatalog.kt:87 | 已尝试：$attempt 次 | 清晰度 | 已尝试：{数量} 次 | P0 |
| runtime/FailureCatalog.kt:89 | 技术详情： | 清晰度 | 问题详情： | P0 |
| runtime/FailureCatalog.kt:126 | 断网 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:126 | 网络 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:126 | 超时 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:126 | 连接中断 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:126 | 连接失败 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:127 | 无法连接 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:127 | 连接被重置 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:132 | 磁盘已满 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:132 | 空间不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:133 | 剩余空间不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:133 | 存储空间不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:133 | 容量不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:133 | 磁盘空间不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:136 | 权限 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:137 | 授权失效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:137 | 未授权 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:137 | 被拒绝 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:140 | 完整性 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:140 | 校验不一致 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:140 | 校验失败 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:141 | 内容长度 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:141 | 签名 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:141 | 长度不符 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:141 | 长度与声明 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:144 | 压缩包 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:144 | 损坏的压缩 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:144 | 解包 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:144 | 解压 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:147 | node.js 要求 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:147 | 不兼容 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:147 | 仅支持 arm64 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:147 | 版本格式无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:147 | 版本要求 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:150 | 占用 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:150 | 忙碌 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:150 | 正在进行 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:150 | 请先停止 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:150 | 请稍后 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:150 | 请等待 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:152 | 复制 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:152 | 导入 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:152 | 导出 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:152 | 文件 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:152 | 目录 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:163 | 重新获取版本 | 清晰度 | 刷新版本 | P0 |
| runtime/FailureCatalog.kt:164 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:254 | 文件 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:255 | 无法写入 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:255 | 无法读取 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:256 | 写入失败 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:256 | 读取失败 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FailureCatalog.kt:278 | 安装没有完成 | 清晰度 | 安装失败 | P0 |
| runtime/FailureCatalog.kt:279 | 更新没有完成 | 清晰度 | 更新失败 | P0 |
| runtime/FailureCatalog.kt:280 | DSH 没有正常运行 | 术语不一致 | 助手已停止 | P0 |
| runtime/FailureCatalog.kt:281 | 文件操作没有完成 | 清晰度 | 文件操作失败 | P0 |
| runtime/FailureCatalog.kt:282 | 操作没有完成 | 清晰度 | 操作失败 | P0 |
| runtime/FailureCatalog.kt:287 | 网络连接中断，更新已经停下；现有应用和环境没有改动，恢复网络后可以重试。 | 清晰度 | 更新因网络中断，请联网后重试。 | P0 |
| runtime/FailureCatalog.kt:288 | 网络连接中断，操作已经停下；已下载的部分会保留，恢复网络后可以重试。 | 清晰度 | 网络连接中断，请联网后重试。 | P0 |
| runtime/FailureCatalog.kt:289 | 设备空间不足，操作已经停止；项目、会话和已安装的组件都还在。清理下载缓存或设备空间后可以重试。 | 术语不一致、长度 | 存储空间不足，请清理空间后重试。 | P0 |
| runtime/FailureCatalog.kt:290 | 系统没有允许访问需要的文件，操作因此停止；原有文件没有被覆盖。可以到系统设置授权后重试。 | 长度 | 无法访问所需文件，请检查文件权限后重试。 | P0 |
| runtime/FailureCatalog.kt:291 | 下载内容没有通过完整性检查，损坏的文件已经丢弃；可以重新下载或换一个下载源。 | 清晰度 | 下载文件校验未通过，请重新下载或更换下载源。 | P0 |
| runtime/FailureCatalog.kt:292 | 下载内容无法解压，可能没有下载完整；可以重试或换一个下载源。 | 清晰度 | 下载文件无法解压，请重新下载或更换下载源。 | P0 |
| runtime/FailureCatalog.kt:293 | 所选版本与当前环境不匹配，现有组件没有被替换；请选择其他版本再试。 | 清晰度 | 所选版本与运行环境不兼容，请选择其他版本。 | P0 |
| runtime/FailureCatalog.kt:294 | 另一个安装或更新还在进行，请等它结束后再试。 | 清晰度 | 正在安装或更新，请等待完成后重试。 | P0 |
| runtime/FailureCatalog.kt:295 | DSH 在启动完成前停下了，所以页面暂时打不开；项目和会话都还在，可以重新启动。 | 术语不一致 | 助手启动失败，请重新启动。 | P0 |
| runtime/FailureCatalog.kt:296 | DSH 运行中意外停止，当前任务被中断；项目和会话都还在，可以重新启动，已中断的任务不会自动重放。 | 术语不一致、长度 | 助手意外停止，当前任务已中断。重启后请检查任务状态。 | P0 |
| runtime/FailureCatalog.kt:297 | 系统回收了 DSH，可能是内存紧张；项目和会话都还在，可以清理下载缓存后重新启动。 | 术语不一致 | 助手已停止，可能是运行内存不足。请关闭闲置应用后重启助手。 | P0 |
| runtime/FailureCatalog.kt:298 | 文件没有读取或写入成功，原有文件没有被覆盖；请检查文件是否还在，然后重试。 | 清晰度 | 文件读写失败，请确认文件仍在并重试。 | P0 |
| runtime/FailureCatalog.kt:299 | 操作没有完成，现有环境、项目和会话都没有被删除；可以重试或清理下载缓存后再试。 | 术语不一致 | 操作失败，请重试。仍失败时可复制问题详情反馈。 | P0 |
| runtime/FailureCatalog.kt:309 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:310 | 查看存储占用 | 保留 | 查看存储占用 | P0 |
| runtime/FailureCatalog.kt:311 | 试用正常模式 | 清晰度 | 恢复正常模式 | P0 |
| runtime/FailureCatalog.kt:312 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:313 | 回到安全模式 | 保留 | 回到安全模式 | P0 |
| runtime/FailureCatalog.kt:314 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:315 | 重新启动 | 保留 | 重新启动 | P0 |
| runtime/FailureCatalog.kt:316 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:319 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:320 | 查看存储占用 | 保留 | 查看存储占用 | P0 |
| runtime/FailureCatalog.kt:321 | 去系统设置 | 保留 | 去系统设置 | P0 |
| runtime/FailureCatalog.kt:322 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:323 | 选择其他版本 | 清晰度 | 换个版本 | P0 |
| runtime/FailureCatalog.kt:324 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:325 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:326 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:327 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:328 | 换一个下载源 | 清晰度 | 更换下载源 | P0 |
| runtime/FailureCatalog.kt:329 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:332 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:333 | 查看存储占用 | 保留 | 查看存储占用 | P0 |
| runtime/FailureCatalog.kt:334 | 去系统设置 | 保留 | 去系统设置 | P0 |
| runtime/FailureCatalog.kt:335 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:336 | 选择其他版本 | 清晰度 | 换个版本 | P0 |
| runtime/FailureCatalog.kt:337 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:339 | 选择版本重试 | 清晰度 | 选择版本 | P0 |
| runtime/FailureCatalog.kt:340 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:341 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:342 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:345 | 查看存储占用 | 保留 | 查看存储占用 | P0 |
| runtime/FailureCatalog.kt:346 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:347 | 去系统设置 | 保留 | 去系统设置 | P0 |
| runtime/FailureCatalog.kt:348 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:349 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:350 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:352 | 重试 | 保留 | 重试 | P0 |
| runtime/FailureCatalog.kt:353 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| runtime/FailureCatalog.kt:380 | 查看存储占用 | 保留 | 查看存储占用 | P0 |
| runtime/FailureCatalog.kt:381 | 查看系统设置 | 保留 | 查看系统设置 | P0 |

### [runtime/FileTransfers.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/FileTransfers.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/FileTransfers.kt:112 | 文件传输必须在协程中启动 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/FileTransfers.kt:135 | 文件长度与声明不一致，请重新下载 | 保留 | 文件长度与声明不一致，请重新下载 | P1 |

### [runtime/Launcher.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/Launcher.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/Launcher.kt:56 | 暂无日志 | 清晰度 | 还没有运行日志，启动助手后可查看。 | P0 |
| runtime/Launcher.kt:61 | 日志暂时无法读取 | 清晰度 | 日志读取失败，请刷新重试。 | P0 |
| runtime/Launcher.kt:129 | 运行进程未能停止，请重试停止后再操作组件 | 保留 | 运行进程未能停止，请重试停止后再操作组件 | P1 |
| runtime/Launcher.kt:159 | 下载缓存目录被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| runtime/Launcher.kt:163 | 运行临时目录被重定向，已停止启动 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| runtime/Launcher.kt:165 | 运行临时路径不是目录 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| runtime/Launcher.kt:179 | 手机文件工具入口被重定向，已停止启动 | 保留 | 手机文件工具入口被重定向，已停止启动 | P1 |
| runtime/Launcher.kt:187 | DSH 启动支持目录缺失 | 术语不一致、错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| runtime/Launcher.kt:189 | DSH 启动支持挂载点被重定向 | 术语不一致、错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| runtime/Launcher.kt:193 | DSH 启动路径缓存绑定失败，按原生方式启动：${it.message} | 工程痕迹、术语不一致 | 启动优化暂不可用，正在继续启动。 | P0 |
| runtime/Launcher.kt:229 | [单行输出已截断]  | 清晰度 | 本条记录过长，仅显示部分内容。 | P1 |

### [runtime/NpmAdaptiveWorkerPool.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmAdaptiveWorkerPool.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmAdaptiveWorkerPool.kt:25 | 任务数量不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/NpmAdaptiveWorkerPool.kt:26 | 调度轮询间隔必须为正 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [runtime/NpmArchiveMapping.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmArchiveMapping.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmArchiveMapping.kt:30 | npm 归档地址无效 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:34 | npm 归档地址必须来自官方锁文件 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:40 | 未知 npm 镜像 | 工程痕迹、被动/实现说明、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:46 | npm 锁归档地址无效 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:49 | npm 锁包含非 HTTPS 或带参数的归档地址 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:52 | npm 锁归档地址不在镜像白名单：${uri.host ?: "未知"} | 工程痕迹、被动/实现说明、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:52 | 未知 | 被动/实现说明 | 未知 | P1 |
| runtime/NpmArchiveMapping.kt:71 | npm 归档路径无效 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:73 | npm 归档路径包含不安全字符 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmArchiveMapping.kt:78 | npm 归档路径包含非法段 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |

### [runtime/NpmDependencyLocks.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmDependencyLocks.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmDependencyLocks.kt:25 | 依赖锁版本身份无效 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:28 | 依赖锁必须绑定规范的 SHA-512 校验 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:51 | 依赖锁缓存路径越界 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:53 | 依赖锁缓存路径被重定向，已保留原文件 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:55 | 依赖锁缓存路径越界 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:79 | 依赖锁缓存大小无效 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:128 | 依赖锁大小无效 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:136 | 依赖锁缓存写入失败 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmDependencyLocks.kt:145 | 依赖锁大小无效 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmDependencyLocks.kt:150 | 依赖锁缓存写入失败 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmDependencyLocks.kt:166 | APK 预置 DSH 依赖锁校验失败 | 术语不一致、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |

### [runtime/NpmDownloadPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmDownloadPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmDownloadPolicy.kt:21 | 并发策略参数无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/NpmDownloadPolicy.kt:45 | 单 host 并发上限无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/NpmDownloadPolicy.kt:60 | npm 官方 | 工程痕迹 | 官方源 | P1 |

### [runtime/NpmDownloadProgress.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmDownloadProgress.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmDownloadProgress.kt:30 | 依赖归档为空 | 清晰度 | 安装文件为空 | P0 |

### [runtime/NpmLockTrust.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmLockTrust.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmLockTrust.kt:16 | 镜像锁与官方锁的依赖集合不一致 | 清晰度 | 安装文件信息与官方记录不一致，请刷新版本后重试。 | P1 |
| runtime/NpmLockTrust.kt:18 | 镜像锁中 $name 的版本、官方归档路径或 SRI 与官方不一致 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| runtime/NpmLockTrust.kt:29 | 依赖 $name 的 SHA-512 格式无效 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |

### [runtime/NpmPackageDownloads.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmPackageDownloads.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmPackageDownloads.kt:30 | DSH 版本格式无效 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P0 |
| runtime/NpmPackageDownloads.kt:35 | 无法创建安装目录 | 保留 | 无法创建安装目录 | P0 |
| runtime/NpmPackageDownloads.kt:37 |  | 清晰度 | 空值保持不显示，不输出缺省技术值。 | P0 |
| runtime/NpmPackageDownloads.kt:59 | 解析 DSH 依赖（元数据响应 $responses 次，缓存优先） | 工程痕迹、术语不一致 | 正在准备安装文件 | P0 |
| runtime/NpmPackageDownloads.kt:62 | 写入已验证的 npm 缓存 | 工程痕迹 | 正在准备已下载文件 | P0 |
| runtime/NpmPackageDownloads.kt:67 | 请检查当前阶段与设备存储后重试 | 保留 | 请检查当前阶段与设备存储后重试 | P0 |
| runtime/NpmPackageDownloads.kt:67 | 请检查网络后重试 | 保留 | 请检查网络后重试 | P0 |
| runtime/NpmPackageDownloads.kt:69 | $label：命令超过 $minutes 分钟；不会清除已有公开缓存，$suggestion | 清晰度 | 安装等待时间过长，请检查网络和存储后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:72 | $label：退出码 ${result.exitCode}； | 工程痕迹 | 安装失败，请检查问题详情后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:74 | ；公开元数据缓存已保留，请根据错误检查网络或 Node 兼容性后重试 | 工程痕迹 | 请检查网络和运行组件版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:84 | 检查 DSH 解析环境 | 术语不一致 | 正在检查安装条件 | P0 |
| runtime/NpmPackageDownloads.kt:88 | 无法检查 DSH 安装使用的 Node/npm：${Redact.text(environment.stderr).takeLast(600)} | 工程痕迹、术语不一致 | 无法检查运行组件，请重新安装运行组件后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:93 | 使用 ${source.title} 解析 DSH 依赖元数据（单请求 ${NpmMetadataPolicy.FETCH_TIMEOUT_MS / 1000} 秒超时） | 工程痕迹、术语不一致 | 正在获取安装文件信息 | P0 |
| runtime/NpmPackageDownloads.kt:94 | 依赖解析失败（${source.title}） | 清晰度 | 安装文件信息获取失败，请检查网络后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:99 | 依赖锁缺失或过大 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:113 | npmmirror 元数据解析失败，改用官方元数据： | 工程痕迹 | 当前下载源不可用，正在改用官方源。 | P0 |
| runtime/NpmPackageDownloads.kt:114 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |
| runtime/NpmPackageDownloads.kt:123 | 无法用官方元数据核验镜像锁的传递依赖 SRI，已拒绝镜像锁； | 工程痕迹 | 下载信息无法确认，已停止安装。请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:124 | 请检查 npm 官方 registry 网络后重试 | 工程痕迹 | 请检查网络后重试，或更换下载源。 | P0 |
| runtime/NpmPackageDownloads.kt:131 | 镜像锁的传递依赖版本、官方归档路径与 SRI 已与官方元数据逐项一致 | 工程痕迹 | 安装文件信息已确认。 | P0 |
| runtime/NpmPackageDownloads.kt:134 | 镜像锁的传递依赖与官方元数据不一致，已拒绝镜像锁并使用官方锁： | 工程痕迹 | 当前下载信息不一致，正在改用官方信息。 | P0 |
| runtime/NpmPackageDownloads.kt:135 | 未知差异 | 被动/实现说明 | 未知差异 | P0 |
| runtime/NpmPackageDownloads.kt:153 | 检查依赖平台与完整性 | 清晰度 | 正在检查安装文件 | P0 |
| runtime/NpmPackageDownloads.kt:154 | 依赖检查失败 | 清晰度 | 所需组件检查失败 | P0 |
| runtime/NpmPackageDownloads.kt:155 | 依赖检查期间锁发生变化 | 清晰度 | 所需组件检查期间锁发生变化 | P0 |
| runtime/NpmPackageDownloads.kt:159 | 复用已验证的 DSH 依赖锁 | 术语不一致 | 正在使用已保存的安装信息 | P0 |
| runtime/NpmPackageDownloads.kt:160 | 复用已安装 DSH 的依赖锁 | 术语不一致 | 正在使用已有安装信息 | P0 |
| runtime/NpmPackageDownloads.kt:161 | 使用 APK 预置 DSH 依赖锁 | 术语不一致 | 正在使用预置安装信息 | P0 |
| runtime/NpmPackageDownloads.kt:162 | 解析 DSH 依赖（官方元数据核验镜像锁） | 工程痕迹、术语不一致 | 正在获取安装信息 | P0 |
| runtime/NpmPackageDownloads.kt:165 | DSH 依赖锁缓存未能保存，本次继续安装；下次可能需要重新解析 | 术语不一致 | 安装信息未保存，下次安装时会重新获取。 | P0 |
| runtime/NpmPackageDownloads.kt:168 | 已保存依赖锁 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:169 | 已安装依赖锁 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:170 | APK 预置依赖锁 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:171 | 官方解析依赖锁 | 清晰度 | 正在使用官方安装信息 | P0 |
| runtime/NpmPackageDownloads.kt:173 | $title 未通过当前环境校验，尝试下一份锁： | 清晰度 | 这份安装信息不可用，正在尝试其他信息。 | P0 |
| runtime/NpmPackageDownloads.kt:173 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |
| runtime/NpmPackageDownloads.kt:177 | 依赖下载清单不完整 | 清晰度 | 所需组件下载清单不完整 | P0 |
| runtime/NpmPackageDownloads.kt:187 | 依赖下载清单包含重复归档 | 清晰度 | 所需组件下载清单包含重复归档 | P0 |
| runtime/NpmPackageDownloads.kt:194 | 空间不足，下载至少需要保留 64 MiB；当前程序和已校验缓存保留 | 清晰度 | 存储空间不足，请至少腾出 64 MiB 后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:197 | 下载 DSH 依赖 | 术语不一致 | 正在下载安装文件 | P0 |
| runtime/NpmPackageDownloads.kt:198 | 依赖共 ${entries.size} 个；默认 8 路并发，单源最多 8 路，逐包 SHA-512 校验 | 工程痕迹、长度 | 共 {数量} 个安装文件，正在下载并检查。 | P0 |
| runtime/NpmPackageDownloads.kt:200 | 校验 DSH 依赖归档 | 术语不一致 | 正在检查下载文件 | P0 |
| runtime/NpmPackageDownloads.kt:209 | 已验证依赖归档大小无效或总量超过限制 | 清晰度 | 已验证安装文件大小无法使用或总量超过限制 | P0 |
| runtime/NpmPackageDownloads.kt:215 | 检查 DSH 安装空间 | 术语不一致 | 正在检查可用空间 | P0 |
| runtime/NpmPackageDownloads.kt:219 | DSH 解压与安装空间不足，预计还需预留 ${(total * 6 + 64L * 1024 * 1024) / (1024 * 1024)} MiB；已校验依赖和当前程序保留 | 术语不一致、长度 | 安装空间不足，请再腾出 {所需空间} 后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:222 | 写入已验证的 npm 缓存 | 工程痕迹 | 正在准备已下载文件 | P0 |
| runtime/NpmPackageDownloads.kt:224 | 依赖缓存写入失败 | 清晰度 | 所需组件缓存写入失败 | P0 |
| runtime/NpmPackageDownloads.kt:225 | 依赖锁在下载期间发生变化 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:226 | 离线安装 DSH 依赖 | 术语不一致 | 正在安装已下载文件 | P0 |
| runtime/NpmPackageDownloads.kt:227 | 依赖离线安装失败 | 清晰度 | 所需组件离线安装失败 | P0 |
| runtime/NpmPackageDownloads.kt:228 | 离线安装修改了依赖锁 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:231 | 安装结果校验失败 | 保留 | 安装结果校验失败 | P0 |
| runtime/NpmPackageDownloads.kt:234 | DSH 依赖安装完成 | 术语不一致 | 安装文件已准备好 | P0 |
| runtime/NpmPackageDownloads.kt:239 | DSH 依赖安装失败： | 术语不一致 | 助手 所需组件安装失败： | P0 |
| runtime/NpmPackageDownloads.kt:239 | 无法完成安装 | 清晰度 | 安装失败，请重试。 | P0 |
| runtime/NpmPackageDownloads.kt:281 | DSH 依赖 ·  | 术语不一致 | 安装文件 | P0 |
| runtime/NpmPackageDownloads.kt:282 |  等 ${labels.size} 个来源 | 清晰度 | 等 {数量} 个下载源 | P0 |
| runtime/NpmPackageDownloads.kt:284 | 下载 DSH 依赖（已校验 ${observed.verified}/${observed.archives}） | 术语不一致 | 正在下载并检查文件：{已完成}/{总数} | P0 |
| runtime/NpmPackageDownloads.kt:310 | 已校验缓存 | 保留 | 已校验缓存 | P0 |
| runtime/NpmPackageDownloads.kt:322 | ${source.title} 有依赖未同步（HTTP $status），立即切换其他源 | 清晰度 | 当前下载源缺少文件，正在更换下载源。 | P0 |
| runtime/NpmPackageDownloads.kt:326 | ${source.title} 拒绝访问（HTTP $status），切换其他源 | 清晰度 | 当前下载源拒绝访问，正在更换下载源。 | P0 |
| runtime/NpmPackageDownloads.kt:333 | 触发限流（HTTP 429），已指数退避 | 清晰度 | 下载请求过多，正在等待重试。 | P0 |
| runtime/NpmPackageDownloads.kt:334 | 服务异常（HTTP $status），已指数退避 | 清晰度 | 下载服务暂不可用，正在等待重试。 | P0 |
| runtime/NpmPackageDownloads.kt:335 | 连续失败 | 保留 | 连续失败 | P0 |
| runtime/NpmPackageDownloads.kt:337 | DSH 下载源 ${source.title} $why，本次改用其他源： | 术语不一致 | 当前下载源不可用，正在更换下载源。 | P0 |
| runtime/NpmPackageDownloads.kt:338 | 下载失败 | 保留 | 下载失败 | P0 |
| runtime/NpmPackageDownloads.kt:346 | 依赖下载失败：${downloadFailure(lastError)}；已校验缓存保留，请检查网络或切换下载源后重试 | 清晰度 | 安装文件下载失败，请检查网络或更换下载源后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:357 | 没有可用的 DSH 依赖下载源 | 术语不一致 | 没有可用下载源，请检查网络或更换下载源。 | P0 |
| runtime/NpmPackageDownloads.kt:360 | 依赖在下载源未同步（HTTP $status）：$detail | 清晰度 | 下载源缺少文件，请更换下载源。 | P0 |
| runtime/NpmPackageDownloads.kt:361 | 下载源限流（HTTP 429，已指数退避）：$detail | 清晰度 | 下载请求过多，请稍后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:362 | 下载源服务异常（HTTP $status，已指数退避）：$detail | 清晰度 | 下载源暂不可用，请稍后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:390 | DSH 版本或完整性与选定发布不一致 | 术语不一致 | 助手 版本或完整性与选定发布不一致 | P0 |
| runtime/NpmPackageDownloads.kt:393 | 根依赖与选定 DSH 版本不一致 | 术语不一致 | 根所需组件与选定 助手 版本不一致 | P0 |
| runtime/NpmPackageDownloads.kt:397 | 依赖清单缺失或大小无效 | 清晰度 | 所需组件清单缺失或大小无法使用 | P0 |
| runtime/NpmPackageDownloads.kt:406 | 依赖缺少有效的 SHA-512 完整性校验 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:408 | SHA-512 完整性校验格式无效 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:445 | 依赖归档不是规范的 npm 官方地址 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:451 | 依赖缺少有效的 SHA-512 校验 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:455 | 安装环境必须是 linux arm64 glibc | 保留 | 安装环境必须是 linux arm64 glibc | P0 |
| runtime/NpmPackageDownloads.kt:458 | DSH 发布完整性不一致 | 术语不一致 | 助手 发布完整性不一致 | P0 |
| runtime/NpmPackageDownloads.kt:463 | DSH 根依赖无效 | 术语不一致 | 助手 根所需组件无法使用 | P0 |
| runtime/NpmPackageDownloads.kt:470 | 依赖图过大 | 清晰度 | 所需组件图过大 | P0 |
| runtime/NpmPackageDownloads.kt:473 | 必需依赖不兼容或缺失 | 清晰度 | 必需所需组件不兼容或缺失 | P0 |
| runtime/NpmPackageDownloads.kt:475 | 无法安全剔除可选依赖 | 清晰度 | 无法安全剔除可选所需组件 | P0 |
| runtime/NpmPackageDownloads.kt:481 | 依赖锁包含不支持的本地链接 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:486 | 依赖环境检查失败 | 清晰度 | 所需组件环境检查失败 | P0 |
| runtime/NpmPackageDownloads.kt:500 | 必需依赖无法从锁文件解析 | 清晰度 | 必需所需组件无法从锁文件解析 | P0 |
| runtime/NpmPackageDownloads.kt:511 | 依赖路径无效 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/NpmPackageDownloads.kt:514 | 依赖锁内容不一致 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:518 | 同一归档存在不同完整性校验 | 保留 | 同一归档存在不同完整性校验 | P0 |
| runtime/NpmPackageDownloads.kt:523 | 已安装依赖版本与锁不一致 | 清晰度 | 已安装所需组件版本与锁不一致 | P0 |
| runtime/NpmPackageDownloads.kt:526 | 依赖归档数量无效 | 清晰度 | 安装文件数量无法使用 | P0 |
| runtime/NpmPackageDownloads.kt:530 | 依赖图、平台、版本或完整性校验失败 | 清晰度 | 所需组件图、平台、版本或完整性校验失败 | P0 |
| runtime/NpmPackageDownloads.kt:541 | 归档清单校验失败 | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/NpmPackageDownloads.kt:560 | 已下载归档的完整性校验或 npm 缓存写入失败 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |

### [runtime/NpmSources.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/NpmSources.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/NpmSources.kt:24 | DSH 版本格式无效 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |
| runtime/NpmSources.kt:43 | 请等待当前操作完成并停止 DSH | 术语不一致 | 请等待当前操作完成并停止 助手 | P1 |
| runtime/NpmSources.kt:44 | 未知 DSH 下载源 | 被动/实现说明、术语不一致 | 未知 助手 下载源 | P1 |
| runtime/NpmSources.kt:59 | 测速 DSH 依赖下载源 | 术语不一致 | 正在检测下载速度 | P1 |
| runtime/NpmSources.kt:86 | DSH 版本格式无效 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |
| runtime/NpmSources.kt:110 | DSH 下载源测速结果未保存：${Redact.text(error.message ?: "未知错误")} | 被动/实现说明、术语不一致 | 测速结果未保存，可重新测速。 | P0 |
| runtime/NpmSources.kt:110 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |
| runtime/NpmSources.kt:138 | npm 官方 | 工程痕迹 | 官方源 | P1 |
| runtime/NpmSources.kt:140 | 华为云 npm | 工程痕迹 | 华为云 | P1 |
| runtime/NpmSources.kt:141 | 华为云 npm 镜像 | 工程痕迹 | 华为云 | P1 |
| runtime/NpmSources.kt:142 | 腾讯云 npm | 工程痕迹 | 腾讯云 | P1 |

### [runtime/OperationProgress.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/OperationProgress.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/OperationProgress.kt:42 | 准备安装 Ubuntu | 清晰度 | 正在准备系统组件 | P0 |
| runtime/OperationProgress.kt:43 | 准备安装 Node.js | 清晰度 | 正在准备运行组件 | P0 |
| runtime/OperationProgress.kt:44 | 准备检查基础工具 | 清晰度 | 正在检查基础工具 | P0 |
| runtime/OperationProgress.kt:45 | 准备卸载 Ubuntu | 清晰度 | 正在卸载系统组件 | P0 |
| runtime/OperationProgress.kt:46 | 准备卸载 Node.js | 清晰度 | 正在卸载运行组件 | P0 |
| runtime/OperationProgress.kt:47 | 准备卸载 DSH | 术语不一致 | 正在卸载助手 | P0 |
| runtime/OperationProgress.kt:48 | 准备运行环境 | 保留 | 准备运行环境 | P0 |
| runtime/OperationProgress.kt:49 | 检查运行环境 | 保留 | 检查运行环境 | P0 |
| runtime/OperationProgress.kt:50 | 执行后台运行检查 | 清晰度 | 正在检查后台运行 | P0 |
| runtime/OperationProgress.kt:51 | 整理 Ubuntu 缓存 | 清晰度 | 正在整理安装缓存 | P0 |
| runtime/OperationProgress.kt:52 | 准备执行操作 | 保留 | 准备执行操作 | P0 |

### [runtime/RelayTransport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RelayTransport.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RelayTransport.kt:40 | 中继地址格式无效 | 清晰度 | 配对链接中的连接地址无法使用，请在电脑上重新生成链接。 | P1 |
| runtime/RelayTransport.kt:48 | 中继证书指纹格式无效 | 清晰度 | 配对链接中的安全信息无法使用，请在电脑上重新生成链接。 | P0 |
| runtime/RelayTransport.kt:58 | 当前系统不支持 TLS 1.2/1.3 | 清晰度 | 系统无法建立安全连接，请升级系统后重试。 | P1 |
| runtime/RelayTransport.kt:67 | 中继 TLS 版本过低 | 清晰度 | 连接服务的安全版本过低，已停止连接。请更新电脑连接程序。 | P1 |
| runtime/RelayTransport.kt:82 | 中继证书指纹不匹配 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RelayTransport.kt:83 | 中继证书指纹不匹配，已拒绝连接 | 清晰度 | 连接安全信息不一致，已拒绝连接。请重新复制电脑上的链接。 | P0 |
| runtime/RelayTransport.kt:85 | 中继 TLS 握手失败；请检查网络和证书 | 清晰度 | 无法确认连接服务身份，请检查网络和手机时间后重试。 | P0 |
| runtime/RelayTransport.kt:88 | 连接中继超时；请检查网络 | 清晰度 | 连接超时，请检查网络后重试。 | P0 |
| runtime/RelayTransport.kt:91 | 无法解析中继地址 | 清晰度 | 找不到连接服务，请检查网络或重新复制配对链接。 | P0 |
| runtime/RelayTransport.kt:94 | 无法连接中继；请检查网络 | 清晰度 | 无法连接，请检查网络后重试。 | P0 |
| runtime/RelayTransport.kt:96 | 中继连接失败 | 清晰度 | 连接失败，请重试。 | P0 |
| runtime/RelayTransport.kt:105 | 中继客户端证书不受支持 | 清晰度 | 无法确认连接服务身份，请更新电脑连接程序后重试。 | P0 |
| runtime/RelayTransport.kt:109 | 中继未提供证书 | 清晰度 | 无法确认连接服务身份，请更新电脑连接程序后重试。 | P0 |
| runtime/RelayTransport.kt:113 | 中继证书已过期或尚未生效 | 清晰度 | 连接服务证书未生效或已过期，请检查时间和证书。 | P0 |
| runtime/RelayTransport.kt:117 | 中继证书指纹不匹配 | 清晰度 | 连接安全信息不一致，已拒绝连接。请重新复制电脑上的链接。 | P0 |
| runtime/RelayTransport.kt:153 | 中继会话已关闭 | 术语不一致 | 远程连接已关闭。 | P1 |
| runtime/RelayTransport.kt:154 | 中继桥已建立 | 清晰度 | 远程连接已建立。 | P1 |
| runtime/RelayTransport.kt:212 | 中继桥拒绝了非回环连接 | 错误出口 | 连接信息异常，请重新连接。 | P0 |
| runtime/RelayTransport.kt:263 | 中继返回了未知流的数据 | 被动/实现说明、错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RelayTransport.kt:282 | 中继在数据阶段发送了控制帧 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RelayTransport.kt:286 | 中继在数据阶段发送了重复的流打开帧 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RelayTransport.kt:352 | 远程配置未包含中继配对信息 | 保留 | 远程配置未包含中继配对信息 | P1 |
| runtime/RelayTransport.kt:367 | 远程配置未包含中继配对信息 | 保留 | 远程配置未包含中继配对信息 | P1 |
| runtime/RelayTransport.kt:425 | 中继未返回配对挑战 | 保留 | 中继未返回配对挑战 | P1 |
| runtime/RelayTransport.kt:431 | 配对密钥格式无效；请重新生成配对链接 | 清晰度 | 配对密钥格式无法使用；请重新生成配对链接 | P1 |
| runtime/RelayTransport.kt:437 | 中继在配对完成前关闭了连接 | 保留 | 中继在配对完成前关闭了连接 | P1 |
| runtime/RelayTransport.kt:441 | 中继控制帧格式无效 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RelayTransport.kt:451 | 中继返回了未预期的配对状态 | 保留 | 中继返回了未预期的配对状态 | P1 |
| runtime/RelayTransport.kt:457 | 中继返回了无效流号 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RelayTransport.kt:462 | 中继返回了未预期的帧 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RelayTransport.kt:475 | 中继在配对完成前关闭了连接 | 保留 | 中继在配对完成前关闭了连接 | P1 |
| runtime/RelayTransport.kt:478 | 中继控制帧格式无效 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RelayTransport.kt:484 | 中继配对超时；请确认主机端 agent 在线 | 清晰度 | 配对超时，请确认电脑上的连接程序仍在运行。 | P0 |
| runtime/RelayTransport.kt:487 | 无法解析中继地址 | 清晰度 | 找不到连接服务，请检查网络或重新复制配对链接。 | P0 |
| runtime/RelayTransport.kt:490 | 中继连接中断；请检查网络 | 清晰度 | 远程连接中断，请检查网络后重试。 | P0 |
| runtime/RelayTransport.kt:492 | 中继配对失败 | 清晰度 | 配对失败，请重新复制电脑上的配对链接。 | P0 |

### [runtime/RemoteConnectionError.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteConnectionError.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemoteConnectionError.kt:10 | TCP 连接 | 清晰度 | 连接电脑 | P1 |
| runtime/RemoteConnectionError.kt:11 | SSH 握手 | 清晰度 | 建立安全连接 | P1 |
| runtime/RemoteConnectionError.kt:12 | 用户认证 | 清晰度 | 登录电脑 | P1 |
| runtime/RemoteConnectionError.kt:13 | 端口转发 | 清晰度 | 连接助手 | P1 |
| runtime/RemoteConnectionError.kt:14 | DSH 探测 | 术语不一致 | 检查助手 | P1 |
| runtime/RemoteConnectionError.kt:15 | 完成 | 保留 | 完成 | P1 |
| runtime/RemoteConnectionError.kt:51 | 连接 | 保留 | 连接 | P1 |
| runtime/RemoteConnectionError.kt:68 | 配对链接已过期：中继已不保留这次配对 | 清晰度 | 配对链接已过期。 | P1 |
| runtime/RemoteConnectionError.kt:69 | 回到电脑上重新生成配对链接，再把新链接复制到手机；旧链接不能继续使用 | 清晰度 | 请在电脑上重新生成配对链接，再复制到手机。 | P1 |
| runtime/RemoteConnectionError.kt:72 | 中继上找不到这个配对房间 | 清晰度 | 找不到这次配对。 | P1 |
| runtime/RemoteConnectionError.kt:73 | 确认链接来自电脑上正在运行的那次配对；中继重启或链接失效时，请在电脑上重新生成链接 | 长度 | 请确认电脑连接程序仍在运行，并重新复制配对链接。 | P1 |
| runtime/RemoteConnectionError.kt:76 | 电脑端当前不在线，中继无法把它和手机接起来 | 清晰度 | 电脑当前不在线。 | P0 |
| runtime/RemoteConnectionError.kt:77 | 确认电脑上的 DSH 和远程连接服务仍在运行并已连上中继，然后重试；电脑刚重启时请重新生成链接 | 术语不一致、长度 | 请打开电脑上的助手和连接程序，再重试。 | P1 |
| runtime/RemoteConnectionError.kt:80 | 中继证书指纹与配对链接不一致，已拒绝连接 | 清晰度 | 连接安全指纹不一致，已拒绝连接。 | P0 |
| runtime/RemoteConnectionError.kt:81 | 先不要继续连接：重新从电脑复制最新配对链接；若仍不一致，可能有人在替换中继，请核对电脑上显示的中继证书指纹 | 长度 | 请重新复制电脑上的配对链接。仍不一致时，请核对连接服务的安全指纹。 | P0 |
| runtime/RemoteConnectionError.kt:84 | 中继端口不可达：手机没能和链接里的中继地址建立连接 | 清晰度 | 无法连接配对链接中的地址。 | P1 |
| runtime/RemoteConnectionError.kt:85 | 确认手机当前网络能访问该地址和端口（可切换 Wi-Fi/移动网络重试），并确认中继服务在运行；地址或端口变化后需重新生成链接 | 长度 | 请检查手机网络及电脑连接程序。地址变化后请重新生成链接。 | P1 |
| runtime/RemoteConnectionError.kt:88 | 中继 TLS 握手失败，无法确认中继身份 | 清晰度 | 无法确认连接服务身份。 | P0 |
| runtime/RemoteConnectionError.kt:89 | 确认中继证书仍有效、手机系统时间正确；使用自签名中继时必须使用带证书指纹的新配对链接 | 长度 | 请检查手机时间及连接服务证书，再重新生成配对链接。 | P0 |
| runtime/RemoteConnectionError.kt:92 | 中继拒绝了配对密钥 | 清晰度 | 配对信息已失效。 | P0 |
| runtime/RemoteConnectionError.kt:93 | 链接里的配对密钥已失效或不属于该房间；请在电脑上重新生成配对链接后重试 | 清晰度 | 请在电脑上重新生成配对链接后重试。 | P1 |
| runtime/RemoteConnectionError.kt:96 | 该配对房间已有另一个客户端在使用 | 清晰度 | 另一台手机正在使用这次配对。 | P1 |
| runtime/RemoteConnectionError.kt:97 | 先在另一台手机上断开，或等当前连接结束后重试；必要时在电脑上重新生成链接 | 清晰度 | 请先断开另一台手机，或在电脑上重新生成配对链接。 | P1 |
| runtime/RemoteConnectionError.kt:100 | 中继协议不兼容，连接已停止 | 清晰度 | 手机和电脑的连接版本不匹配。 | P1 |
| runtime/RemoteConnectionError.kt:101 | 确认手机和电脑上的连接组件版本匹配；更新后再试，仍失败请保留错误阶段信息反馈 | 清晰度 | 请更新两端的连接程序后重试。 | P0 |
| runtime/RemoteConnectionError.kt:112 | 请按上方提示修改后再试 | 保留 | 请按上方提示修改后再试 | P1 |
| runtime/RemoteConnectionError.kt:121 | 远程主机密钥已变化：与已保存记录不一致，已拒绝连接 | 术语不一致 | 电脑安全指纹已变化，已拒绝连接。 | P0 |
| runtime/RemoteConnectionError.kt:122 | 这可能是主机重装，也可能是中间人攻击。请先在远程主机核对新的 SHA256 指纹；确认安全后删除旧连接再重新添加 | 工程痕迹、术语不一致、长度 | 请通过可信渠道核对新指纹。确认是自己的电脑后，再重新添加连接。 | P0 |
| runtime/RemoteConnectionError.kt:143 | 在远程主机确认 DSH 已启动并监听 127.0.0.1:&lt;远程 DSH 端口&gt;；如端口不同，返回上一步修改端口后重试 | 术语不一致、长度 | 请先启动电脑上的助手，确认端口与这里填写的一致。 | P1 |
| runtime/RemoteConnectionError.kt:146 | 主机密钥交换 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteConnectionError.kt:151 | 确认远程 sshd 正常运行，并使用 RSA/ECDSA 主机密钥；当前暂不支持 Ed25519 主机密钥 | 被动/实现说明、长度 | 请在电脑上使用兼容的登录密钥，具体格式见连接详情。 | P1 |
| runtime/RemoteConnectionError.kt:154 | 中继 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteConnectionError.kt:159 | 确认中继地址可达、配对链接未过期；必要时重新生成配对链接 | 清晰度 | 请检查网络，并重新复制电脑上的配对链接。 | P1 |
| runtime/RemoteConnectionError.kt:162 | 信任记录 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteConnectionError.kt:162 | 凭据 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteConnectionError.kt:163 | 隧道端口 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteConnectionError.kt:170 | 请重试；若持续失败，重启应用后重新配置，仍失败请保留错误阶段信息反馈 | 清晰度 | 请重试。仍失败时可复制问题详情反馈。 | P0 |
| runtime/RemoteConnectionError.kt:181 | 无法解析主机地址 | 保留 | 无法解析主机地址 | P0 |
| runtime/RemoteConnectionError.kt:182 | 检查主机名拼写，或改用 IP 地址；局域网连接请确认手机和电脑在同一 Wi-Fi/热点 | 长度 | 请检查电脑地址。局域网连接需让手机和电脑连上同一 Wi-Fi。 | P1 |
| runtime/RemoteConnectionError.kt:187 | 连接超时：${(stage ?: RemoteConnectionStage.TCP).label}未在限定时间内完成 | 工程痕迹 | 连接电脑超时。 | P0 |
| runtime/RemoteConnectionError.kt:188 | 检查网络、主机地址和 SSH 端口是否可达；确认防火墙或安全组已放行后重试 | 清晰度 | 请检查网络、电脑地址和连接端口后重试。 | P1 |
| runtime/RemoteConnectionError.kt:193 | 目标端口拒绝连接 | 保留 | 目标端口拒绝连接 | P0 |
| runtime/RemoteConnectionError.kt:194 | 确认远程 sshd 已启动、SSH 端口填写正确；局域网连接确认电脑未休眠且防火墙放行 | 长度 | 请打开电脑的远程登录，确认端口正确且电脑未休眠。 | P1 |
| runtime/RemoteConnectionError.kt:200 | SSH 用户认证失败 | 清晰度 | 电脑登录失败。 | P0 |
| runtime/RemoteConnectionError.kt:201 | 核对用户名和密码/私钥；私钥必须与远程 authorized_keys 匹配，带口令的私钥要填写正确口令 | 长度 | 请核对电脑登录账号、密码或私钥口令后重试。 | P1 |
| runtime/RemoteConnectionError.kt:207 | 私钥无法读取或口令不正确 | 清晰度 | 无法读取私钥，或私钥口令不正确。 | P0 |
| runtime/RemoteConnectionError.kt:208 | 重新选择未损坏的 RSA/ECDSA 私钥文件；确认私钥口令正确，且私钥未设置额外权限限制 | 长度 | 请重新选择兼容的私钥文件，并核对私钥口令。 | P0 |
| runtime/RemoteConnectionError.kt:213 | 远程主机密钥校验失败，已拒绝连接 | 术语不一致 | 无法确认电脑身份，已拒绝连接。 | P0 |
| runtime/RemoteConnectionError.kt:214 | 确认远程 sshd 主机密钥正常；不要通过关闭校验来绕过，请先核对指纹 | 清晰度 | 请通过可信渠道核对电脑安全指纹，再重新连接。 | P0 |
| runtime/RemoteConnectionError.kt:219 | SSH 已认证，但远程端口转发失败 | 清晰度 | 电脑登录成功，但无法连接助手。 | P0 |
| runtime/RemoteConnectionError.kt:220 | 确认远程 DSH 正在监听 127.0.0.1:&lt;远程 DSH 端口&gt;，且 sshd 未禁用 AllowTcpForwarding | 术语不一致、长度 | 请启动电脑上的助手，并在连接设置中允许转发。具体设置见详情。 | P1 |
| runtime/RemoteConnectionError.kt:226 | SSH 算法协商失败 | 清晰度 | 电脑登录密钥格式不兼容。 | P0 |
| runtime/RemoteConnectionError.kt:227 | 当前支持 RSA/ECDSA 主机密钥与私钥；请在远程主机启用 RSA/ECDSA，Ed25519 暂不支持 | 被动/实现说明、术语不一致、长度 | 请使用 RSA 或 ECDSA 密钥。密钥格式说明见连接详情。 | P1 |
| runtime/RemoteConnectionError.kt:229 | 网络连接中断 | 保留 | 网络连接中断 | P0 |
| runtime/RemoteConnectionError.kt:230 | 连接未完成 | 保留 | 连接未完成 | P1 |
| runtime/RemoteConnectionError.kt:246 | 确认主机地址、SSH 端口和网络可达；局域网连接确认手机与电脑在同一网络，防火墙已放行 | 长度 | 请确认地址、端口和网络可用，并检查电脑防火墙。 | P1 |
| runtime/RemoteConnectionError.kt:248 | 确认对端端口运行的是 sshd，且主机密钥算法受支持；可先在电脑上执行 ssh -p &lt;端口&gt; &lt;用户&gt;@&lt;主机&gt; 验证 | 长度 | 请确认电脑已开启远程登录。连接详情中可查看检查命令。 | P1 |
| runtime/RemoteConnectionError.kt:250 | 核对用户名与密码/私钥；确认远程账号允许 SSH 登录，私钥已加入 authorized_keys | 长度 | 请核对账号、密码或私钥，并确认电脑允许该账号远程登录。 | P0 |
| runtime/RemoteConnectionError.kt:252 | 确认远程 DSH 监听 127.0.0.1:&lt;远程 DSH 端口&gt;，且 sshd 允许 TCP 转发 | 术语不一致、长度 | 请启动电脑上的助手，并允许远程连接程序连接助手。 | P0 |
| runtime/RemoteConnectionError.kt:254 | 确认远程 DSH 已启动并监听配置的端口；必要时在远程主机查看 DSH 日志 | 术语不一致 | 请确认电脑上的助手已启动，且端口填写正确。 | P1 |
| runtime/RemoteConnectionError.kt:256 | 连接已建立但收尾失败，请重试；若持续失败请返回上一步检查配置 | 清晰度 | 连接未完成，请重试。仍失败时请返回检查配置。 | P0 |

### [runtime/RemoteHost.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHost.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemoteHost.kt:56 | 远程主机展示名过长（上限 128 字符） | 术语不一致 | 远程电脑展示名过长（上限 128 字符） | P1 |
| runtime/RemoteHost.kt:57 | 远程主机展示名不能包含控制字符 | 术语不一致 | 电脑名称包含不可见字符，请重新输入。 | P1 |
| runtime/RemoteHost.kt:59 | 远程主机格式无效；请填写 IPv4、IPv6 或域名，不要包含协议、路径或端口 | 术语不一致、长度 | 请输入电脑地址，不要包含协议、路径或端口。 | P1 |
| runtime/RemoteHost.kt:61 | SSH 端口必须在 1-65535 之间 | 保留 | SSH 端口必须在 1-65535 之间 | P1 |
| runtime/RemoteHost.kt:62 | SSH 用户名格式无效（1-64 位字母、数字、点、下划线或短横线） | 清晰度 | 用户名需为 1–64 个字母、数字、点、下划线或短横线。 | P1 |
| runtime/RemoteHost.kt:63 | 远程 DSH 端口必须在 1-65535 之间 | 术语不一致 | 助手端口需在 1–65535 之间。 | P1 |
| runtime/RemoteHost.kt:66 | 请填写 SSH 密码 | 保留 | 请填写 SSH 密码 | P1 |
| runtime/RemoteHost.kt:67 | SSH 密码过长（上限 $MAX_PASSWORD_CHARS 字符） | 清晰度 | SSH 密码过长（上限 {内容} 字符） | P1 |
| runtime/RemoteHost.kt:71 | 请粘贴 SSH 私钥 | 保留 | 请粘贴 SSH 私钥 | P1 |
| runtime/RemoteHost.kt:73 | 私钥文件过大（上限 ${MAX_PRIVATE_KEY_BYTES / 1024} KiB） | 清晰度 | 私钥文件过大（上限 {大小} KiB） | P1 |
| runtime/RemoteHost.kt:76 | 私钥格式无效；请粘贴 OpenSSH 或 PEM 私钥全文 | 清晰度 | 私钥格式无法使用；请粘贴 OpenSSH 或 PEM 私钥全文 | P1 |
| runtime/RemoteHost.kt:78 | 私钥口令过长（上限 $MAX_PASSPHRASE_CHARS 字符） | 清晰度 | 私钥口令过长（上限 {内容} 字符） | P1 |
| runtime/RemoteHost.kt:81 | 远程 DSH token 过长（上限 $MAX_TOKEN_CHARS 字符） | 工程痕迹、术语不一致 | 登录验证码过长，请重新复制。 | P1 |
| runtime/RemoteHost.kt:82 | 远程 DSH token 不能包含空白或控制字符 | 工程痕迹、术语不一致 | 登录验证码含有空格或不可见字符，请重新复制。 | P1 |
| runtime/RemoteHost.kt:189 | 本地运行时 | 术语不一致 | 本机助手 | P1 |
| runtime/RemoteHost.kt:206 | 本地运行时未启动 | 术语不一致 | 本机助手未启动 | P1 |
| runtime/RemoteHost.kt:222 | 正在建立 SSH 隧道并检测远程 DSH… | 术语不一致 | 正在连接电脑上的助手… | P1 |
| runtime/RemoteHost.kt:242 | 已通过 SSH 隧道连接远程主机 $host | 术语不一致 | 已连接电脑：{电脑名} | P1 |
| runtime/RemoteHost.kt:255 | 已回退本地运行时：$message | 术语不一致 | 已回到本机助手。{原因} | P1 |

### [runtime/RemoteHostController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemoteHostController.kt:71 | 已保存的远程配置无法读取：${friendlyStorage(error)} | 清晰度 | 已保存的连接无法读取，请重新添加连接。 | P0 |
| runtime/RemoteHostController.kt:106 | 命名连接库不可用，未保存；本次仍可直接连接 | 清晰度 | 连接未保存，本次可继续连接。 | P1 |
| runtime/RemoteHostController.kt:111 | 保存连接失败，请重试 | 保留 | 保存连接失败，请重试 | P0 |
| runtime/RemoteHostController.kt:118 | 已保存连接“${profile.name}” | 清晰度 | 已保存连接“{名称}” | P1 |
| runtime/RemoteHostController.kt:133 | 删除连接失败，请重试 | 保留 | 删除连接失败，请重试 | P0 |
| runtime/RemoteHostController.kt:170 | 已删除连接${removed?.name?.let { name -&gt; "“$name”" } ?: ""}；当前远程会话不受影响 | 术语不一致 | 已删除连接，当前连接继续使用。 | P0 |
| runtime/RemoteHostController.kt:172 | 已删除连接，但活动配置副本清理失败；请使用“清除当前活动配置” | 清晰度 | 连接已删除，但当前配置未清除。请点“清除当前配置”。 | P0 |
| runtime/RemoteHostController.kt:187 | 没有待确认的主机密钥 | 清晰度 | 没有需要确认的电脑指纹，请重新连接。 | P0 |
| runtime/RemoteHostController.kt:208 | 已取消连接测试，继续使用当前远程连接 | 保留 | 已取消连接测试，继续使用当前远程连接 | P0 |
| runtime/RemoteHostController.kt:211 | 已取消首次主机密钥确认，继续使用本地运行时 | 术语不一致 | 已取消电脑身份确认，继续使用本机助手。 | P0 |
| runtime/RemoteHostController.kt:222 | 已断开远程主机，WebView 已切回本地运行时 | 工程痕迹、术语不一致 | 已断开电脑，回到本机助手。 | P1 |
| runtime/RemoteHostController.kt:240 | 已清除远程主机配置，WebView 已切回本地运行时 | 工程痕迹、术语不一致 | 已清除连接配置，回到本机助手。 | P1 |
| runtime/RemoteHostController.kt:241 | 已断开远程主机，但清除本机配置失败；请重试 | 术语不一致 | 已断开远程电脑，但清除本机配置失败；请重试 | P0 |
| runtime/RemoteHostController.kt:247 | 远程主机配置清除失败，请重试 | 术语不一致 | 远程电脑配置清除失败，请重试 | P0 |
| runtime/RemoteHostController.kt:265 | 正在校验远程主机配置… | 术语不一致 | 正在校验远程电脑配置… | P1 |
| runtime/RemoteHostController.kt:294 | 远程主机身份无效 | 术语不一致 | 远程电脑身份无法使用 | P1 |
| runtime/RemoteHostController.kt:302 | 正在通过中继 ${config.relay.endpointLabel()} 配对 $host… | 工程痕迹 | 正在配对电脑… | P1 |
| runtime/RemoteHostController.kt:304 | 正在连接 ${config.user}@$host:${config.sshPort}… | 清晰度 | 正在连接电脑… | P1 |
| runtime/RemoteHostController.kt:335 | 首次连接 $host，请核对主机密钥指纹后确认 | 清晰度 | 首次连接，请核对这台电脑的安全指纹。 | P0 |
| runtime/RemoteHostController.kt:362 | 正在通过中继建立 SSH 通道并检测远程 DSH… | 工程痕迹、术语不一致 | 正在连接电脑上的助手… | P1 |
| runtime/RemoteHostController.kt:364 | 正在建立 SSH 隧道并检测远程 DSH… | 术语不一致 | 正在连接电脑上的助手… | P1 |
| runtime/RemoteHostController.kt:382 | 远程 DSH 端口未响应；请确认远程 DSH 已启动并监听 127.0.0.1:${config.remoteDshPort} | 术语不一致、长度 | 电脑上的助手未响应，请启动后检查端口。 | P1 |
| runtime/RemoteHostController.kt:386 | 主机密钥信任记录无法保存，已取消远程连接 | 清晰度 | 无法保存电脑信任记录，连接已取消。请重试。 | P0 |
| runtime/RemoteHostController.kt:393 | 连接测试成功：SSH 认证通过，远程 DSH 端口可达 | 术语不一致 | 连接测试成功：SSH 登录验证通过，远程 助手 端口可达 | P1 |
| runtime/RemoteHostController.kt:409 | 凭据无法加密保存，已取消远程连接 | 清晰度 | 无法安全保存登录信息，连接已取消。请重试。 | P0 |
| runtime/RemoteHostController.kt:412 | 本机 SSH 隧道端口无效 | 清晰度 | 无法建立远程连接，请重试。 | P1 |
| runtime/RemoteHostController.kt:414 | 无法隔离管理网页 Cookie，已取消远程连接 | 工程痕迹 | 无法安全切换页面，连接已取消。请重启应用。 | P0 |
| runtime/RemoteHostController.kt:457 | SSH 隧道已断开 | 清晰度 | 与电脑的连接已断开。 | P1 |
| runtime/RemoteHostController.kt:457 | SSH 隧道已断开；$cookieError | 工程痕迹 | 与电脑的连接已断开，请重试。 | P1 |
| runtime/RemoteHostController.kt:466 | SSH 隧道已断开 | 清晰度 | 与电脑的连接已断开。 | P1 |
| runtime/RemoteHostController.kt:467 | 已切回本地运行时；检查网络后重新连接远程主机 | 术语不一致 | 已回到本机，请检查网络后重新连接电脑。 | P1 |
| runtime/RemoteHostController.kt:502 |  | 清晰度 | 空值保持不显示，不输出缺省技术值。 | P1 |
| runtime/RemoteHostController.kt:526 | 管理页 Cookie 隔离失败，请重启应用后重试 | 工程痕迹 | 无法安全切换页面，请重启应用后重试。 | P0 |
| runtime/RemoteHostController.kt:614 | 中继 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteHostController.kt:614 | 已保存 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteHostController.kt:614 | 连接 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RemoteHostController.kt:615 | 请重试，或删除本机远程配置后重新添加 | 保留 | 请重试，或删除本机远程配置后重新添加 | P0 |

### [runtime/RemoteHostProfiles.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostProfiles.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemoteHostProfiles.kt:40 | 连接“$name”的配对信息不完整；请删除后重新添加 | 清晰度 | 连接信息不完整，请删除后重新添加。 | P0 |
| runtime/RemoteHostProfiles.kt:42 | 连接“$name”的配对信息不完整；请删除后重新添加 | 清晰度 | 连接信息不完整，请删除后重新添加。 | P0 |
| runtime/RemoteHostProfiles.kt:44 | 连接“$name”的配对信息不完整；请删除后重新添加 | 清晰度 | 连接信息不完整，请删除后重新添加。 | P0 |
| runtime/RemoteHostProfiles.kt:46 | 连接“$name”的配对信息不完整；请删除后重新添加 | 清晰度 | 连接信息不完整，请删除后重新添加。 | P0 |
| runtime/RemoteHostProfiles.kt:48 | 连接“$name”的配对密钥无法解密；请删除后重新添加 | 清晰度 | 无法读取保存的配对信息，请删除后重新添加。 | P0 |
| runtime/RemoteHostProfiles.kt:58 | 连接“$name”的配对信息无效：$it | 清晰度 | 连接信息无法使用，请删除后重新添加。 | P1 |
| runtime/RemoteHostProfiles.kt:90 | 连接配置不完整：$it | 清晰度 | 连接信息不完整，请返回检查配置。 | P1 |
| runtime/RemoteHostProfiles.kt:94 | 最多保存 $MAX_PROFILES 个连接；请先删除不用的连接 | 清晰度 | 最多保存 {内容} 个连接；请先删除不用的连接 | P0 |
| runtime/RemoteHostProfiles.kt:98 | 要编辑的连接不存在；请刷新后重试 | 保留 | 要编辑的连接不存在；请刷新后重试 | P1 |
| runtime/RemoteHostProfiles.kt:174 | 要使用的连接不存在；请刷新后重试 | 保留 | 要使用的连接不存在；请刷新后重试 | P1 |
| runtime/RemoteHostProfiles.kt:200 | 连接标识无效 | 清晰度 | 连接信息无法使用，请重新添加。 | P1 |

### [runtime/RemoteHostStorage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostStorage.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemoteHostStorage.kt:29 | 远程主机设置保存失败 | 术语不一致 | 远程电脑设置保存失败 | P0 |
| runtime/RemoteHostStorage.kt:33 | 远程主机设置删除失败 | 术语不一致 | 远程电脑设置删除失败 | P0 |
| runtime/RemoteHostStorage.kt:37 | 远程主机设置清理失败 | 术语不一致 | 远程电脑设置清理失败 | P0 |
| runtime/RemoteHostStorage.kt:59 | AES-GCM IV 长度异常 | 清晰度 | 无法安全保存登录信息，请重新添加连接。 | P0 |
| runtime/RemoteHostStorage.kt:147 | 中继配对信息不完整；请清除远程主机配置后重新配对 | 术语不一致 | 配对信息不完整，请重新添加连接。 | P1 |
| runtime/RemoteHostStorage.kt:149 | 中继配对信息不完整；请清除远程主机配置后重新配对 | 术语不一致 | 配对信息不完整，请重新添加连接。 | P1 |
| runtime/RemoteHostStorage.kt:151 | 中继配对信息不完整；请清除远程主机配置后重新配对 | 术语不一致 | 配对信息不完整，请重新添加连接。 | P1 |
| runtime/RemoteHostStorage.kt:153 | 中继配对信息不完整；请清除远程主机配置后重新配对 | 术语不一致 | 配对信息不完整，请重新添加连接。 | P1 |
| runtime/RemoteHostStorage.kt:155 | 中继配对密钥无法解密；请重新配对 | 清晰度 | 无法读取配对信息，请重新配对。 | P0 |
| runtime/RemoteHostStorage.kt:164 | 中继配对信息无效：$it | 清晰度 | 配对信息无法使用，请重新配对。 | P1 |

### [runtime/RemoteHostWizard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemoteHostWizard.kt:36 | 直连 SSH | 清晰度 | 直接连接 | P1 |
| runtime/RemoteHostWizard.kt:37 | 远程主机有公网 IP、域名或 IPv6，SSH 端口可直接访问。 | 术语不一致 | 电脑地址和远程登录端口可访问时，直接填写地址连接。 | P1 |
| runtime/RemoteHostWizard.kt:38 | 主机示例：dev.example.com 或 [2001:db8::10] | 保留 | 主机示例：dev.example.com 或 [2001:db8::10] | P1 |
| runtime/RemoteHostWizard.kt:41 | 配对链接 | 清晰度 | 配对连接 | P1 |
| runtime/RemoteHostWizard.kt:42 | 双方都没有公网 IP 时，用主机端生成的一次性链接经 TLS 中继连接。 | 清晰度 | 无法直接访问电脑地址时，使用电脑生成的配对链接。 | P0 |
| runtime/RemoteHostWizard.kt:43 | 链接形如 tanuki-relay://v1?relay=…（由主机端生成） | 工程痕迹 | 粘贴电脑生成的完整配对链接。 | P1 |
| runtime/RemoteHostWizard.kt:46 | 局域网 | 保留 | 局域网 | P1 |
| runtime/RemoteHostWizard.kt:47 | 手机和电脑在同一 Wi-Fi/热点，直接用内网地址连接。 | 保留 | 手机和电脑在同一 Wi-Fi/热点，直接用内网地址连接。 | P1 |
| runtime/RemoteHostWizard.kt:48 | 主机示例：192.168.1.20 或 10.0.0.8 | 保留 | 主机示例：192.168.1.20 或 10.0.0.8 | P1 |
| runtime/RemoteHostWizard.kt:59 | 选择连接方式 | 保留 | 选择连接方式 | P1 |
| runtime/RemoteHostWizard.kt:60 | 填写主机与账号 | 清晰度 | 填写电脑与账号 | P1 |
| runtime/RemoteHostWizard.kt:61 | 确认这台电脑 | 清晰度 | 确认电脑 | P1 |
| runtime/RemoteHostWizard.kt:62 | 选择认证方式 | 清晰度 | 选择登录方式 | P1 |
| runtime/RemoteHostWizard.kt:63 | 核对主机指纹 | 清晰度 | 核对安全指纹 | P0 |
| runtime/RemoteHostWizard.kt:64 | 测试连接 | 保留 | 测试连接 | P1 |
| runtime/RemoteHostWizard.kt:65 | 完成 | 保留 | 完成 | P1 |
| runtime/RemoteHostWizard.kt:70 | 读取配对链接 | 保留 | 读取配对链接 | P1 |
| runtime/RemoteHostWizard.kt:139 | 已保存的私钥（不显示内容） | 清晰度 | 使用已保存的私钥 | P1 |
| runtime/RemoteHostWizard.kt:192 | $sshUser@127.0.0.1:$sshPort（经中继转发） | 清晰度 | 通过连接服务转发 | P1 |
| runtime/RemoteHostWizard.kt:197 | 链接未包含电脑名，可返回上一步填写展示名 | 清晰度 | 电脑未提供名称，可返回上一步填写。 | P1 |
| runtime/RemoteHostWizard.kt:206 | 未命名电脑（${pairing.hostId.take(8)}） | 工程痕迹 | 未命名电脑 | P1 |
| runtime/RemoteHostWizard.kt:207 | 上一步填写的展示名 | 清晰度 | 你填写的电脑名称 | P1 |
| runtime/RemoteHostWizard.kt:212 | 系统 CA（链接未固定证书） | 清晰度 | 使用系统信任的证书 | P0 |
| runtime/RemoteHostWizard.kt:216 | &lt;用户名&gt; | 保留 | &lt;用户名&gt; | P1 |
| runtime/RemoteHostWizard.kt:288 | 请填写主机地址；示例：192.168.1.20 或 dev.example.com | 长度 | 请输入电脑地址，例如 192.168.1.20。 | P1 |
| runtime/RemoteHostWizard.kt:289 | 只填主机名或 IP，不要带 http://、ssh:// 等协议前缀 | 清晰度 | 只填写电脑地址，不要带网址或协议前缀。 | P1 |
| runtime/RemoteHostWizard.kt:290 | 主机地址不能包含路径；SSH 端口请填在下方端口框 | 清晰度 | 地址中不要包含路径，端口请填在下方。 | P1 |
| runtime/RemoteHostWizard.kt:291 | 主机地址不要包含用户名；用户名请填在账号框 | 清晰度 | 地址中不要包含用户名，请填在账号框。 | P1 |
| runtime/RemoteHostWizard.kt:292 | 主机地址不能包含空格或控制字符 | 清晰度 | 地址包含空格或不可见字符，请重新输入。 | P1 |
| runtime/RemoteHostWizard.kt:294 | 不要在这里填写端口；IPv6 地址请用方括号，例如 [2001:db8::1] | 清晰度 | 端口请填在下方。IPv6 地址请加方括号。 | P1 |
| runtime/RemoteHostWizard.kt:297 | 主机地址格式无效；示例：192.168.1.20、dev.example.com 或 [2001:db8::1] | 长度 | 地址格式不正确，请参照输入框中的示例。 | P1 |
| runtime/RemoteHostWizard.kt:304 | 请填写$label；示例：$example | 清晰度 | 请填写{名称}；示例：{内容} | P1 |
| runtime/RemoteHostWizard.kt:305 | $label 必须是数字；示例：$example | 清晰度 | {名称} 必须是数字；示例：{内容} | P1 |
| runtime/RemoteHostWizard.kt:306 | $label 需在 1-65535 之间；示例：$example | 清晰度 | {名称} 需在 1-65535 之间；示例：{内容} | P1 |
| runtime/RemoteHostWizard.kt:311 | 请填写 SSH 用户名；示例：tanuki 或 ubuntu | 保留 | 请填写 SSH 用户名；示例：tanuki 或 ubuntu | P1 |
| runtime/RemoteHostWizard.kt:312 | 用户名只能包含字母、数字、点、下划线或短横线（1-64 位） | 清晰度 | 请输入电脑登录用户名，例如 tanuki。 | P1 |
| runtime/RemoteHostWizard.kt:317 | 请填写 SSH 密码；如果使用私钥，请切换到私钥认证 | 清晰度 | 用户名需为 1–64 个字母、数字、点、下划线或短横线。 | P1 |
| runtime/RemoteHostWizard.kt:319 | SSH 密码过长（上限 ${RemoteHostConfig.MAX_PASSWORD_CHARS} 字符） | 清晰度 | 请输入电脑登录密码，或改用私钥登录。 | P1 |
| runtime/RemoteHostWizard.kt:327 | 私钥文件内容为空，请重新选择 | 清晰度 | 请选择私钥文件，或粘贴完整私钥。 | P0 |
| runtime/RemoteHostWizard.kt:327 | 请选择私钥文件，或粘贴 OpenSSH/PEM 私钥全文 | 保留 | 请选择私钥文件，或粘贴 OpenSSH/PEM 私钥全文 | P1 |
| runtime/RemoteHostWizard.kt:330 | 私钥文件过大（上限 ${RemoteHostConfig.MAX_PRIVATE_KEY_BYTES / 1024} KiB）；请选择正确的私钥文件 | 清晰度 | 私钥文件过大（上限 {大小} KiB）；请选择正确的私钥文件 | P1 |
| runtime/RemoteHostWizard.kt:333 | 私钥格式无法识别；请选择 OpenSSH 或 PEM 私钥文件（当前支持 RSA/ECDSA） | 长度 | 私钥格式不兼容，请选择 RSA 或 ECDSA 私钥。 | P0 |
| runtime/RemoteHostWizard.kt:340 | 私钥口令过长（上限 ${RemoteHostConfig.MAX_PASSPHRASE_CHARS} 字符） | 清晰度 | 私钥口令过长（上限 {来源} 字符） | P1 |
| runtime/RemoteHostWizard.kt:347 | 远程 DSH token 过长（上限 ${RemoteHostConfig.MAX_TOKEN_CHARS} 字符） | 工程痕迹、术语不一致 | 登录验证码过长，请重新复制。 | P1 |
| runtime/RemoteHostWizard.kt:349 | 远程 DSH token 不能包含空白或控制字符 | 工程痕迹、术语不一致 | 登录验证码含有空格或不可见字符，请重新复制。 | P1 |
| runtime/RemoteHostWizard.kt:355 | 请粘贴主机端生成的 tanuki-relay:// 配对链接 | 工程痕迹 | 请粘贴电脑生成的完整配对链接。 | P1 |
| runtime/RemoteHostWizard.kt:357 | 格式不符 | 保留 | 格式不符 | P1 |
| runtime/RemoteHostWizard.kt:357 | 配对链接无效：${error.message ?: "格式不符"}；请重新复制完整链接 | 清晰度 | 配对链接无法使用，请重新复制完整链接。 | P1 |
| runtime/RemoteHostWizard.kt:362 | 请为连接起一个名称；示例：家里电脑、公司开发机 | 保留 | 请为连接起一个名称；示例：家里电脑、公司开发机 | P1 |
| runtime/RemoteHostWizard.kt:363 | 连接名称过长（上限 $MAX_PROFILE_NAME 字符） | 清晰度 | 连接名称过长（上限 {名称} 字符） | P1 |
| runtime/RemoteHostWizard.kt:364 | 连接名称不能包含控制字符 | 清晰度 | 连接名称不能包含不可见字符 | P1 |
| runtime/RemoteHostWizard.kt:373 | 请选择一种连接方式后再继续 | 清晰度 | 请选择连接方式后继续。 | P1 |
| runtime/RemoteHostWizard.kt:386 | 家里电脑（可留空） | 保留 | 家里电脑（可留空） | P1 |
| runtime/RemoteHostWizard.kt:396 | 请先选择连接方式 | 保留 | 请先选择连接方式 | P1 |
| runtime/RemoteHostWizard.kt:413 | 配对码与配对链接不一致；本链接自动算出的是 $expected。 | 清晰度 | 配对码不一致，请核对电脑上的这次配对。 | P1 |
| runtime/RemoteHostWizard.kt:414 | 请确认你对照的是电脑上同一次配对，或点“填入自动码”修正 | 清晰度 | 请重新复制电脑上的配对链接，再核对配对码。 | P1 |
| runtime/RemoteHostWizard.kt:420 | 远程主机展示名过长（上限 128 字符） | 术语不一致 | 远程电脑展示名过长（上限 128 字符） | P1 |
| runtime/RemoteHostWizard.kt:422 | 远程主机展示名不能包含控制字符 | 术语不一致 | 电脑名称包含不可见字符，请重新输入。 | P1 |
| runtime/RemoteHostWizard.kt:427 | SSH 端口 | 保留 | SSH 端口 | P1 |
| runtime/RemoteHostWizard.kt:429 | 远程 DSH 端口 | 术语不一致 | 助手端口 | P1 |
| runtime/RemoteHostWizard.kt:565 | 剪贴板内容不是配对链接，已保留下面的粘贴框；也可以手动粘贴。 | 清晰度 | 剪贴板中没有配对链接，请手动粘贴。 | P1 |
| runtime/RemoteHostWizard.kt:575 | 格式不符 | 保留 | 格式不符 | P1 |
| runtime/RemoteHostWizard.kt:582 | 剪贴板里的旧链接不可用，已保留当前填写的配对链接。 | 清晰度 | 剪贴板中的链接已失效，当前填写内容未改变。 | P1 |
| runtime/RemoteHostWizard.kt:593 | 剪贴板里的配对链接无法解析：$reason；请重新复制电脑上的完整链接，或手动粘贴 | 清晰度 | 配对链接无法读取，请重新复制电脑上的完整链接。 | P0 |
| runtime/RemoteHostWizard.kt:597 | 已读取剪贴板，但链接不可用；请重新复制后再试。 | 清晰度 | 配对链接不可用，请重新复制。 | P1 |
| runtime/RemoteHostWizard.kt:616 | 剪贴板里的配对链接与当前内容一致，已确认 6 位码。 | 清晰度 | 配对链接一致，请核对电脑显示的 6 位码。 | P1 |
| runtime/RemoteHostWizard.kt:628 | 检测到新的配对链接，但当前粘贴框已有你编辑的内容，未自动替换。 | 清晰度 | 发现新配对链接。要替换当前填写的内容吗？ | P1 |
| runtime/RemoteHostWizard.kt:645 | 已从剪贴板读取配对链接，并自动算出 6 位码。 | 清晰度 | 已填入配对链接，请核对电脑显示的 6 位码。 | P1 |
| runtime/RemoteHostWizard.kt:667 | 已改用剪贴板里的配对链接，并自动算出 6 位码。 | 清晰度 | 已更换配对链接，请核对电脑显示的 6 位码。 | P1 |
| runtime/RemoteHostWizard.kt:948 | 已保存到“我的连接” | 保留 | 已保存到“我的连接” | P1 |

### [runtime/RemotePairingCode.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemotePairingCode.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemotePairingCode.kt:35 | 请输入电脑上显示的 6 位配对码 | 保留 | 请输入电脑上显示的 6 位配对码 | P1 |
| runtime/RemotePairingCode.kt:36 | 配对码必须是 6 位数字 | 清晰度 | 请输入 6 位数字配对码。 | P1 |

### [runtime/RemoteRelay.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RemoteRelay.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RemoteRelay.kt:36 | 中继地址格式无效；请使用 IPv4、IPv6 或域名生成配对链接 | 清晰度 | 连接地址无法使用，请在电脑上重新生成配对链接。 | P1 |
| runtime/RemoteRelay.kt:38 | 中继端口必须在 1-65535 之间 | 清晰度 | 连接端口需在 1–65535 之间。 | P1 |
| runtime/RemoteRelay.kt:39 | 配对房间号无效；请重新生成配对链接 | 清晰度 | 配对信息无法使用，请在电脑上重新生成链接。 | P1 |
| runtime/RemoteRelay.kt:40 | 配对密钥无效；请重新生成配对链接 | 清晰度 | 配对信息无法使用，请在电脑上重新生成链接。 | P1 |
| runtime/RemoteRelay.kt:41 | 主机标识无效；请重新生成配对链接 | 清晰度 | 电脑信息无法使用，请在电脑上重新生成链接。 | P1 |
| runtime/RemoteRelay.kt:43 | 中继证书指纹无效；必须是 sha256- 开头的 base64url SHA-256 | 工程痕迹、长度 | 安全指纹无法使用，请在电脑上重新生成配对链接。 | P0 |
| runtime/RemoteRelay.kt:87 | 配对链接格式无效；应以 $URI_PREFIX 开头 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:90 | 配对链接包含不允许的字符 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P0 |
| runtime/RemoteRelay.kt:94 | 配对链接包含空字段 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:96 | 配对链接字段格式无效 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:99 | 配对链接包含未知字段 | 被动/实现说明、错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:100 | 配对链接字段重复 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:102 | 配对链接缺少中继地址 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:103 | 配对链接缺少房间号 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:104 | 配对链接缺少配对密钥 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:105 | 配对链接缺少主机标识 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:126 | 中继地址格式无效 | 清晰度 | 配对链接中的连接地址无法使用，请在电脑上重新生成链接。 | P1 |
| runtime/RemoteRelay.kt:132 | 中继地址必须包含端口 | 保留 | 中继地址必须包含端口 | P1 |
| runtime/RemoteRelay.kt:134 | IPv6 中继地址必须使用方括号 | 保留 | IPv6 中继地址必须使用方括号 | P1 |
| runtime/RemoteRelay.kt:136 | 中继地址格式无效 | 清晰度 | 配对链接中的连接地址无法使用，请在电脑上重新生成链接。 | P1 |
| runtime/RemoteRelay.kt:138 | 中继端口无效 | 清晰度 | 中继端口无法使用 | P1 |
| runtime/RemoteRelay.kt:139 | 中继端口必须在 1-65535 之间 | 清晰度 | 连接端口需在 1–65535 之间。 | P1 |
| runtime/RemoteRelay.kt:141 | 中继地址格式无效 | 清晰度 | 配对链接中的连接地址无法使用，请在电脑上重新生成链接。 | P1 |
| runtime/RemoteRelay.kt:148 | 配对链接字段编码无效 | 错误出口 | 配对链接不完整或无法使用，请重新复制电脑上的完整链接。 | P1 |
| runtime/RemoteRelay.kt:222 | 控制帧必须是非空单行文本 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:227 | 流号无效 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:228 | 中继帧过大 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:230 | 中继控制帧过大 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:249 | 中继帧长度无效 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:255 | 中继帧类型未知 | 被动/实现说明、错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:257 | 中继流号无效 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:261 | 中继控制帧过大 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:275 | 中继连接在帧中途关闭 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:295 | 配对密钥无效 | 清晰度 | 配对信息无法使用，请重新生成链接。 | P1 |
| runtime/RemoteRelay.kt:401 | 中继上找不到这个配对房间；请确认链接来自电脑上正在运行的那次配对 | 清晰度 | 找不到这次配对，请重新复制电脑上的配对链接。 | P1 |
| runtime/RemoteRelay.kt:405 | 配对密钥不正确；请重新复制电脑上的配对链接 | 清晰度 | 配对信息不正确，请重新复制电脑上的配对链接。 | P1 |
| runtime/RemoteRelay.kt:409 | 该配对房间已有客户端连接；请稍后重试或重新配对 | 清晰度 | 另一台手机正在使用这次配对，请稍后重试或重新配对。 | P1 |
| runtime/RemoteRelay.kt:413 | 电脑端当前未连接到中继；请确认电脑上的远程连接服务仍在运行 | 清晰度 | 电脑未连接，请打开电脑连接程序后重试。 | P1 |
| runtime/RemoteRelay.kt:417 | 电脑连接本机 SSH 超时；请确认远程电脑的 sshd 已启动 | 清晰度 | 电脑连接超时，请先打开电脑的远程登录。 | P0 |
| runtime/RemoteRelay.kt:421 | 配对链接已过期；请在电脑上重新生成后再复制 | 清晰度 | 链接已过期，请在电脑上重新生成。 | P1 |
| runtime/RemoteRelay.kt:423 | 中继控制帧过大 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| runtime/RemoteRelay.kt:424 | 中继协议错误 | 清晰度 | 连接信息异常，请更新连接程序后重试。 | P0 |
| runtime/RemoteRelay.kt:427 | 中继返回错误： | 清晰度 | 连接服务返回错误，请稍后重试。 | P0 |

### [runtime/RuntimeComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeComponents.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RuntimeComponents.kt:30 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeComponents.kt:30 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeComponents.kt:45 | 请等待当前操作完成并停止 DSH | 术语不一致 | 请等待当前操作完成并停止 助手 | P0 |
| runtime/RuntimeComponents.kt:74 | 下载源测速结果未保存：${Redact.text(error.message ?: "未知错误")} | 被动/实现说明、错误出口 | 操作未完成，请重试。仍失败时可查看问题详情。 | P0 |
| runtime/RuntimeComponents.kt:74 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |
| runtime/RuntimeComponents.kt:88 | 自动测速完成，已选择下载源：${best.title} | 清晰度 | 自动测速完成，已选择下载源：{名称} | P0 |
| runtime/RuntimeComponents.kt:109 | Ubuntu 官方 | 保留 | Ubuntu 官方 | P0 |
| runtime/RuntimeComponents.kt:110 | 华为云 | 保留 | 华为云 | P0 |
| runtime/RuntimeComponents.kt:111 | 华为云镜像 | 保留 | 华为云镜像 | P0 |
| runtime/RuntimeComponents.kt:112 | 清华 TUNA | 保留 | 清华 TUNA | P0 |
| runtime/RuntimeComponents.kt:113 | 中科大 USTC | 保留 | 中科大 USTC | P0 |
| runtime/RuntimeComponents.kt:114 | 阿里云 | 保留 | 阿里云 | P0 |
| runtime/RuntimeComponents.kt:115 | 南京大学 NJU | 保留 | 南京大学 NJU | P0 |
| runtime/RuntimeComponents.kt:116 | 北外 BFSU | 保留 | 北外 BFSU | P0 |
| runtime/RuntimeComponents.kt:118 | Node.js 官方 | 保留 | Node.js 官方 | P0 |
| runtime/RuntimeComponents.kt:120 | 清华 TUNA | 保留 | 清华 TUNA | P0 |
| runtime/RuntimeComponents.kt:121 | 华为云 | 保留 | 华为云 | P0 |
| runtime/RuntimeComponents.kt:122 | 华为云镜像 | 保留 | 华为云镜像 | P0 |
| runtime/RuntimeComponents.kt:123 | 腾讯云 | 保留 | 腾讯云 | P0 |
| runtime/RuntimeComponents.kt:124 | 阿里云 | 保留 | 阿里云 | P0 |
| runtime/RuntimeComponents.kt:125 | 南京大学 NJU | 保留 | 南京大学 NJU | P0 |
| runtime/RuntimeComponents.kt:151 | 请等待当前操作完成并停止 DSH | 术语不一致 | 请等待当前操作完成并停止 助手 | P0 |
| runtime/RuntimeComponents.kt:158 | 已启用自动选择下载源：安装前先测速，再选择最快的可用源 | 清晰度 | 已开启自动选源，安装前会选择较快的下载源。 | P0 |
| runtime/RuntimeComponents.kt:162 | 未知下载源 | 被动/实现说明 | 未知下载源 | P0 |
| runtime/RuntimeComponents.kt:167 | 已固定下载源：${options.first { it.id==id }.title}；自动测速不会再更改 | 清晰度 | 已优先使用：{下载源} | P0 |
| runtime/RuntimeComponents.kt:186 | 下载缓存路径被重定向，已保留原文件 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeComponents.kt:190 | 已校验缓存 | 保留 | 已校验缓存 | P0 |
| runtime/RuntimeComponents.kt:191 | 复用已校验的 ${artifact.cacheName} | 清晰度 | 正在使用已下载文件 | P0 |
| runtime/RuntimeComponents.kt:202 | 下载 ${artifact.cacheName}：${source.title} | 清晰度 | 正在下载安装文件 | P0 |
| runtime/RuntimeComponents.kt:206 | 已从 ${source.title} 下载 ${artifact.cacheName} 并通过 SHA-256 校验 | 工程痕迹 | 下载文件已通过检查。 | P0 |
| runtime/RuntimeComponents.kt:214 | 下载源 ${source.title} 失败，改用下一个源：${Redact.text(error.message ?: "未知错误")} | 被动/实现说明 | 当前下载源不可用，正在尝试其他下载源。 | P0 |
| runtime/RuntimeComponents.kt:214 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |
| runtime/RuntimeComponents.kt:217 | 没有可用的下载源 | 保留 | 没有可用的下载源 | P0 |
| runtime/RuntimeComponents.kt:253 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeComponents.kt:253 | 未知镜像 | 被动/实现说明 | 未知镜像 | P0 |
| runtime/RuntimeComponents.kt:263 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeComponents.kt:264 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeComponents.kt:265 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeComponents.kt:271 | 组件路径越界 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeComponents.kt:273 | 组件目录被重定向，已保留文件：${cursor.name} | 清晰度 | 组件位置异常，已停止操作。请重新打开应用。 | P0 |
| runtime/RuntimeComponents.kt:274 | 组件路径越界 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeComponents.kt:279 | 请先停止 DSH 再迁移组件 | 术语不一致 | 请先停止 助手 再迁移组件 | P0 |
| runtime/RuntimeComponents.kt:285 | 无法迁移 ${destination.name}，原目录已保留 | 清晰度 | 无法迁移 {名称}，原目录已保留 | P0 |
| runtime/RuntimeComponents.kt:286 | ${destination.name} 已迁移至独立组件目录 | 清晰度 | 组件已整理完成。 | P0 |
| runtime/RuntimeComponents.kt:322 | 组件恢复未完成，请保留数据后重试 | 保留 | 组件恢复未完成，请保留数据后重试 | P0 |
| runtime/RuntimeComponents.kt:336 | DSH 替换事务缺少有效版本 | 工程痕迹、术语不一致 | 助手版本信息缺失，请恢复对应的数据备份。 | P0 |
| runtime/RuntimeComponents.kt:339 | 未知组件替换事务 | 工程痕迹、被动/实现说明 | 组件更新记录异常，请重新打开应用。 | P0 |
| runtime/RuntimeComponents.kt:348 | 存在待恢复的组件，请重新打开应用完成恢复 | 保留 | 存在待恢复的组件，请重新打开应用完成恢复 | P0 |
| runtime/RuntimeComponents.kt:353 | 无法保留旧组件，未进行替换 | 保留 | 无法保留旧组件，未进行替换 | P0 |
| runtime/RuntimeComponents.kt:366 | 未声明 | 保留 | 未声明 | P0 |
| runtime/RuntimeComponents.kt:369 | $label 要求 Node.js $range；所选版本不兼容，当前组件保留。${result.stderr.takeLast(800)} | 清晰度 | 所选版本与当前运行环境不兼容，请选择其他版本。 | P0 |
| runtime/RuntimeComponents.kt:389 | DSH 与候选组件启动检查失败，当前组件保留：${result.stderr.takeLast(1500)} | 术语不一致 | 新组件启动检查失败，当前组件保留。请重新选择版本。 | P0 |
| runtime/RuntimeComponents.kt:397 | Node.js 下载和解压空间不足，请先清理存储；当前组件保留 | 清晰度 | 安装空间不足，请清理空间后重试。 | P0 |
| runtime/RuntimeComponents.kt:401 | 下载 Node.js ${artifact.version} | 清晰度 | 下载 Node.js {版本} | P0 |
| runtime/RuntimeComponents.kt:403 | 校验与解压 Node.js | 清晰度 | 正在解压运行组件 | P0 |
| runtime/RuntimeComponents.kt:410 | 验证 Node.js ${artifact.version} | 清晰度 | 正在检查运行组件 | P0 |
| runtime/RuntimeComponents.kt:412 | Node.js 候选启动检查失败：${result.stderr} | 清晰度 | 运行组件无法启动，请选择其他版本。 | P0 |
| runtime/RuntimeComponents.kt:418 | 无法提交 Node.js 组件 | 保留 | 无法提交 Node.js 组件 | P0 |
| runtime/RuntimeComponents.kt:433 | 请先安装已知版本的 Ubuntu，再安装 Node.js | 清晰度 | 请先安装系统组件，再安装运行组件。 | P0 |
| runtime/RuntimeComponents.kt:439 | 下载 Ubuntu ${artifact.version} | 清晰度 | 下载 Ubuntu {版本} | P0 |
| runtime/RuntimeComponents.kt:448 | ${if(component==RuntimeComponent.UBUNTU)"Ubuntu" else "Node.js"} 安装完成 | 清晰度 | {内容} 安装完成 | P0 |
| runtime/RuntimeComponents.kt:456 | 正在卸载 ${if(component==RuntimeComponent.UBUNTU)"Ubuntu" else "Node.js"} | 清晰度 | 正在卸载 {内容} | P0 |
| runtime/RuntimeComponents.kt:461 | 组件已卸载，项目、会话与其他组件保留 | 术语不一致 | 组件已卸载，项目、对话与其他组件保留 | P0 |
| runtime/RuntimeComponents.kt:484 | 卸载 DSH $selected 程序 | 术语不一致 | 卸载 助手 {内容} 程序 | P0 |
| runtime/RuntimeComponents.kt:495 | 请稍后重试 | 保留 | 请稍后重试 | P0 |
| runtime/RuntimeComponents.kt:495 | ；共享缓存回收未完成（用户数据未删除）：${it.message ?: "请稍后重试"} | 清晰度 | 缓存清理未完成，请稍后重试。 | P0 |
| runtime/RuntimeComponents.kt:497 | DSH $selected 已卸载；配置、会话、扩展、项目与其他版本保留$cleanupWarning | 术语不一致 | 已卸载备用版本，对话、项目和其他版本保留。 | P0 |
| runtime/RuntimeComponents.kt:498 | DSH $selected 程序已卸载；用户数据、快照与其他已安装版本保留 | 术语不一致 | 备用版本已卸载。 | P0 |
| runtime/RuntimeComponents.kt:507 | 请先处理上次更新恢复，再切换已安装版本 | 保留 | 请先处理上次更新恢复，再切换已安装版本 | P0 |
| runtime/RuntimeComponents.kt:519 | 请先安装 Ubuntu 和 Node.js，再切换 DSH 版本 | 术语不一致 | 请先安装 Ubuntu 和 Node.js，再切换 助手 版本 | P0 |
| runtime/RuntimeComponents.kt:523 | 检查 DSH $target 与当前 Node.js 的兼容性 | 术语不一致 | 正在检查所选版本能否运行 | P0 |
| runtime/RuntimeComponents.kt:527 | 保存当前版本的数据快照 | 术语不一致 | 保存当前版本的数据备份 | P0 |
| runtime/RuntimeComponents.kt:532 | 已切换到 DSH $target；旧版本程序与切换前快照保留 | 术语不一致 | 已切换助手版本，切换前的数据备份保留。 | P0 |
| runtime/RuntimeComponents.kt:533 | DSH 已切换到 $target；未重新下载或删除版本，切换前快照 $snapshot 保留 | 术语不一致 | 版本已切换。 | P0 |

### [runtime/RuntimeFailurePolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeFailurePolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RuntimeFailurePolicy.kt:77 | 早崩溃窗口必须为正 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeFailurePolicy.kt:78 | 试用窗口必须为正 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeFailurePolicy.kt:79 | 安全模式阈值必须为正 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeFailurePolicy.kt:80 | 自动重启上限不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeFailurePolicy.kt:81 | 退避参数无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeFailurePolicy.kt:82 | 磁盘保留空间不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeFailurePolicy.kt:217 | DSH 版本格式无效 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P0 |
| runtime/RuntimeFailurePolicy.kt:271 | 安全模式启动失败 | 保留 | 安全模式启动失败 | P0 |
| runtime/RuntimeFailurePolicy.kt:272 | 试用正常模式未通过 | 清晰度 | 恢复正常模式失败。 | P0 |
| runtime/RuntimeFailurePolicy.kt:275 | 连续 $streak 次启动失败 | 清晰度 | 连续 {内容} 次启动失败 | P0 |
| runtime/RuntimeFailurePolicy.kt:284 | 启动未就绪或就绪后早期崩溃 | 清晰度 | 助手启动失败。 | P0 |
| runtime/RuntimeFailurePolicy.kt:289 | 安全模式启动命令失败 | 清晰度 | 安全模式启动失败。 | P0 |
| runtime/RuntimeFailurePolicy.kt:290 | 试用正常模式启动失败 | 清晰度 | 恢复正常模式启动失败。 | P0 |
| runtime/RuntimeFailurePolicy.kt:295 | 启动命令失败 | 清晰度 | 助手启动失败。 | P0 |
| runtime/RuntimeFailurePolicy.kt:303 | 试用正常模式在延迟就绪后晚崩溃 | 清晰度 | 恢复正常模式失败。 | P0 |
| runtime/RuntimeFailurePolicy.kt:312 | DSH 稳定运行后崩溃 | 术语不一致 | 助手运行中意外停止。 | P0 |
| runtime/RuntimeFailurePolicy.kt:316 | DSH 进程被终止或低内存 | 术语不一致 | 助手已停止，可能是运行内存不足。 | P0 |
| runtime/RuntimeFailurePolicy.kt:316 | 输出或磁盘空间不足 | 清晰度 | 存储空间不足。 | P0 |
| runtime/RuntimeFailurePolicy.kt:329 | 试用正常模式失败（$reason）；已回到安全模式，此类失败不会自动重启。请先检查存储或内存后手动启动 | 长度 | 恢复正常模式失败，已回到安全模式。<br>请检查存储空间和运行内存后手动启动。 | P0 |
| runtime/RuntimeFailurePolicy.kt:341 | $reason；已停止自动重启。请先检查存储或内存，确认后再手动启动 | 清晰度 | 自动重启已停止。请检查存储空间和运行内存后手动启动。 | P0 |
| runtime/RuntimeFailurePolicy.kt:357 | $reason；已进入安全模式，将用最小救援配置重启（不加载你的插件和配置），项目、会话和程序均不会删除 | 术语不一致、长度 | 已进入安全模式，不加载你的插件和设置。<br>请检查问题后，再尝试恢复正常模式。 | P0 |
| runtime/RuntimeFailurePolicy.kt:365 | $reason；安全模式已停止自动重启。请检查运行环境后手动重试，或从组件维护入口重新检查 | 长度 | 安全模式启动失败，请检查运行环境后重试。 | P0 |
| runtime/RuntimeFailurePolicy.kt:375 | $reason；自动重启已达上限 $maxRestartAttempts 次，已停止自动重启。请检查环境后手动启动 | 清晰度 | 已多次重启失败，请检查运行环境后手动启动。 | P0 |
| runtime/RuntimeFailurePolicy.kt:383 | $reason；将在 ${delay / 1000} 秒后自动重启（$attempt/$maxRestartAttempts 次），已中断任务不会自动重放 | 长度 | 将在 {秒数} 秒后重启助手，当前为第 {次数} 次。<br>重启后请检查中断的任务。 | P0 |
| runtime/RuntimeFailurePolicy.kt:418 | 磁盘已满 | 保留 | 磁盘已满 | P0 |
| runtime/RuntimeFailurePolicy.kt:418 | 磁盘空间不足 | 保留 | 磁盘空间不足 | P0 |
| runtime/RuntimeFailurePolicy.kt:418 | 空间不足 | 保留 | 空间不足 | P0 |

### [runtime/RuntimeManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RuntimeManager.kt:62 | Cookie 隔离失败 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeManager.kt:123 | 上次正常模式试用期间进程中断，已回到安全模式 | 清晰度 | 正常模式启动中断，已回到安全模式。 | P0 |
| runtime/RuntimeManager.kt:125 | 环境已安装，健康状态待检查 | 清晰度 | 环境已安装，请检查能否运行。 | P0 |
| runtime/RuntimeManager.kt:125 | 首次联网准备 Ubuntu、Node.js 和官方 DSH | 术语不一致 | 首次使用，请联网准备运行环境。 | P0 |
| runtime/RuntimeManager.kt:126 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeManager.kt:126 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeManager.kt:126 | 未安装 | 保留 | 未安装 | P0 |
| runtime/RuntimeManager.kt:133 | 上次候选安装或检查已中断，当前版本未切换，可以重试 | 清晰度 | 上次安装中断，请重新安装所选版本。 | P0 |
| runtime/RuntimeManager.kt:134 | 更新中断；正式数据可能已迁移，请检查当前版本或恢复匹配快照 | 术语不一致 | 更新中断，请先检查当前版本，或恢复对应的数据备份。 | P0 |
| runtime/RuntimeManager.kt:137 | 数据已恢复，等待启动检查 | 保留 | 数据已恢复，等待启动检查 | P0 |
| runtime/RuntimeManager.kt:138 | 数据恢复中断 | 保留 | 数据恢复中断 | P0 |
| runtime/RuntimeManager.kt:139 | 上次后台自检未完整结束，未自动重放 | 清晰度 | 上次后台检查未完成，请重新检查。 | P0 |
| runtime/RuntimeManager.kt:140 | 组件恢复未完成 | 保留 | 组件恢复未完成 | P0 |
| runtime/RuntimeManager.kt:154 | 已清理上次未提交的项目导入，现有项目保留 | 清晰度 | 未完成的导入已清理，现有项目保留。 | P0 |
| runtime/RuntimeManager.kt:158 | 请检查存储 | 保留 | 请检查存储 | P0 |
| runtime/RuntimeManager.kt:158 | 项目导入恢复需要处理：${error.message ?: "请检查存储"} | 清晰度 | 导入恢复失败，请检查存储后重试。 | P0 |
| runtime/RuntimeManager.kt:175 | 文件访问被拒绝；安装或恢复时无法读取目录：${t.file ?: t.message} | 清晰度 | 无法访问安装所需文件，请重试。仍失败时可查看详情。 | P0 |
| runtime/RuntimeManager.kt:233 | 已有环境操作正在进行 | 保留 | 已有环境操作正在进行 | P0 |
| runtime/RuntimeManager.kt:236 | 另一个操作占用环境，请稍后重试 | 保留 | 另一个操作占用环境，请稍后重试 | P0 |
| runtime/RuntimeManager.kt:238 | 另一个安装、切换、卸载或更新操作正在进行，请稍后重试 | 保留 | 另一个安装、切换、卸载或更新操作正在进行，请稍后重试 | P0 |
| runtime/RuntimeManager.kt:238 | 操作未开始：环境正被其他操作占用 | 清晰度 | 其他操作正在进行，请等待完成后重试。 | P0 |
| runtime/RuntimeManager.kt:249 | 请先停止服务；可能中断正在执行的任务 | 保留 | 请先停止服务；可能中断正在执行的任务 | P0 |
| runtime/RuntimeManager.kt:258 | 组件操作已停止；当前安装状态已刷新 | 保留 | 组件操作已停止；当前安装状态已刷新 | P0 |
| runtime/RuntimeManager.kt:259 | Ubuntu 缓存整理已取消；已生效的精简策略保留 | 清晰度 | 缓存整理已取消，已完成的整理保留。 | P0 |
| runtime/RuntimeManager.kt:259 | 已取消，可重试并复用完整下载 | 保留 | 已取消，可重试并复用完整下载 | P0 |
| runtime/RuntimeManager.kt:260 | 操作取消 | 保留 | 操作取消 | P0 |
| runtime/RuntimeManager.kt:266 | 组件操作失败，请查看错误信息 | 保留 | 组件操作失败，请查看错误信息 | P0 |
| runtime/RuntimeManager.kt:267 | Ubuntu 缓存整理失败，详见错误记录 | 清晰度 | 缓存整理失败，请查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:280 | 安装元数据损坏，已保留所有数据；请恢复匹配快照，不能自动选择旧程序读取现有会话 | 工程痕迹、术语不一致、长度 | 无法确认当前版本，请恢复对应的数据备份。 | P0 |
| runtime/RuntimeManager.kt:281 | 当前环境已独立选择其他 DSH 版本；请使用匹配版本的恢复流程，不能用 APK 默认版本覆盖现有会话 | 术语不一致、长度 | 请使用与现有对话匹配的助手版本，或恢复对应的数据备份。 | P0 |
| runtime/RuntimeManager.kt:286 | App 内 Linux 启动失败 exit=${probe.exitCode}: ${probe.stderr} | 工程痕迹 | 系统组件启动失败，请重新检查运行环境。 | P0 |
| runtime/RuntimeManager.kt:287 | Ubuntu 基础启动实验通过；尚未安装 Node.js 和 DSH | 术语不一致 | 系统组件可以启动，接下来请安装运行组件和助手。 | P0 |
| runtime/RuntimeManager.kt:291 | 安装官方 DSH $DSH（锁定入口版本，保存依赖锁） | 术语不一致 | 正在安装助手 | P0 |
| runtime/RuntimeManager.kt:293 | 运行检查 | 保留 | 运行检查 | P0 |
| runtime/RuntimeManager.kt:295 | 健康检查失败: ${result.stderr} | 清晰度 | 运行检查失败，请查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:296 | Ubuntu 镜像版本信息缺失 | 保留 | Ubuntu 镜像版本信息缺失 | P0 |
| runtime/RuntimeManager.kt:297 | Node.js 版本信息缺失 | 保留 | Node.js 版本信息缺失 | P0 |
| runtime/RuntimeManager.kt:299 | 初始化完成，启动后可在官方设置中配置 API Key | 清晰度 | 环境已准备好，启动助手后可设置模型服务。 | P0 |
| runtime/RuntimeManager.kt:310 | 请先完成 Ubuntu 和 Node.js 安装 | 保留 | 请先完成 Ubuntu 和 Node.js 安装 | P0 |
| runtime/RuntimeManager.kt:314 | 基础工具检查尚未通过 | 保留 | 基础工具检查尚未通过 | P0 |
| runtime/RuntimeManager.kt:315 | 基础工具已检查，缺失项已补齐 | 保留 | 基础工具已检查，缺失项已补齐 | P0 |
| runtime/RuntimeManager.kt:331 | 补齐工作区基础组件（Ubuntu 签名软件源） | 清晰度 | 正在安装基础工具 | P0 |
| runtime/RuntimeManager.kt:339 | 用户目录配置路径越界，已保留原配置 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:343 | 无法创建用户目录配置 | 保留 | 无法创建用户目录配置 | P0 |
| runtime/RuntimeManager.kt:353 | Ubuntu 配置路径越界 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:382 | Ubuntu 默认缓存路径被重定向，已保留缓存和项目 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:386 | Ubuntu 默认缓存路径不是目录 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:390 | Ubuntu 安装包缓存路径不是目录 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:391 | 无法读取 Ubuntu 安装包缓存 | 保留 | 无法读取 Ubuntu 安装包缓存 | P0 |
| runtime/RuntimeManager.kt:395 | 无法恢复 Ubuntu partial 缓存目录 | 保留 | 无法恢复 Ubuntu partial 缓存目录 | P0 |
| runtime/RuntimeManager.kt:404 | 请先完成 Ubuntu 初始化 | 保留 | 请先完成 Ubuntu 初始化 | P0 |
| runtime/RuntimeManager.kt:405 | 整理 Ubuntu 可重建安装缓存 | 保留 | 整理 Ubuntu 可重建安装缓存 | P0 |
| runtime/RuntimeManager.kt:408 | Ubuntu 精简策略已启用，默认 apt 安装包缓存已清理；项目和已安装工具保留 | 工程痕迹、长度 | 安装缓存已整理，已安装软件和项目保留。 | P0 |
| runtime/RuntimeManager.kt:409 | Ubuntu Base 按需安装工具；保留版权，后续包不默认安装可选文档和翻译 | 清晰度 | 系统组件只安装所需工具。 | P0 |
| runtime/RuntimeManager.kt:412 | 设备检查 | 保留 | 设备检查 | P0 |
| runtime/RuntimeManager.kt:413 | 第一版仅支持 ARM64：${Build.SUPPORTED_ABIS.toList()} | 清晰度 | 此设备无法在本机运行助手，可连接远程电脑。 | P0 |
| runtime/RuntimeManager.kt:414 | 至少需要 1.5GB 可用空间，建议预留 4GB | 保留 | 至少需要 1.5GB 可用空间，建议预留 4GB | P0 |
| runtime/RuntimeManager.kt:417 | Ubuntu 镜像校验记录未知，已保留环境；请使用组件重新安装入口 | 被动/实现说明 | 无法确认系统组件版本，请重新安装系统组件。 | P0 |
| runtime/RuntimeManager.kt:422 | Ubuntu ${artifact.version} 下载 | 清晰度 | 正在下载系统组件 | P0 |
| runtime/RuntimeManager.kt:423 | 正在选择下载源 | 保留 | 正在选择下载源 | P0 |
| runtime/RuntimeManager.kt:425 | Ubuntu 校验与解压 | 清晰度 | 正在解压系统组件 | P0 |
| runtime/RuntimeManager.kt:432 | 正在解压 Ubuntu（已写入 ${written / (1024 * 1024)} MB） | 清晰度 | 正在解压文件，已写入 {大小}。 | P0 |
| runtime/RuntimeManager.kt:442 | 验证暂存 Ubuntu 启动能力 | 清晰度 | 正在检查系统组件能否启动 | P0 |
| runtime/RuntimeManager.kt:444 | Ubuntu 启动失败 exit=${test.exitCode} ${test.stderr}; target=${context.applicationInfo.targetSdkVersion} | 工程痕迹、长度 | 系统组件启动失败，请查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:445 | ${artifact.version} ${artifact.sha256} | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeManager.kt:446 | 已有不完整环境，请通过专用修复入口处理 | 清晰度 | 环境安装不完整，请从组件页面继续准备。 | P0 |
| runtime/RuntimeManager.kt:447 | 无法提交 Ubuntu 环境 | 保留 | 无法提交 Ubuntu 环境 | P0 |
| runtime/RuntimeManager.kt:451 | Ubuntu 暂存镜像身份不匹配，已保留文件 | 清晰度 | 系统组件版本信息不一致，请重新安装。 | P0 |
| runtime/RuntimeManager.kt:463 | ${artifact.version} ${artifact.sha256} | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeManager.kt:486 | 复用已校验下载缓存：$name | 清晰度 | 正在使用已下载文件 | P0 |
| runtime/RuntimeManager.kt:496 | pnpm 组件路径被重定向 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/RuntimeManager.kt:500 | pnpm 恢复未完成 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/RuntimeManager.kt:504 | 候选 Node.js 无法运行 pnpm $PNPM，当前 Node 保留：${result.stderr.takeLast(1500)} | 工程痕迹、长度 | 新运行组件与插件工具不兼容，请选择其他版本。 | P0 |
| runtime/RuntimeManager.kt:507 | 准备官方插件管理工具 pnpm $PNPM | 工程痕迹 | 正在准备插件工具 | P0 |
| runtime/RuntimeManager.kt:508 | 准备 pnpm $PNPM（官方 npm 发行包） | 工程痕迹 | 正在下载插件工具 | P0 |
| runtime/RuntimeManager.kt:509 | 官方 npm（pnpm） | 工程痕迹 | 官方源 | P0 |
| runtime/RuntimeManager.kt:510 | 校验与解压 pnpm $PNPM | 工程痕迹 | 正在解压插件工具 | P0 |
| runtime/RuntimeManager.kt:514 | pnpm 入口缺失 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/RuntimeManager.kt:519 | 无法保留旧 pnpm | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/RuntimeManager.kt:520 | 无法提交 pnpm 组件 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| runtime/RuntimeManager.kt:531 | 已安装 DSH 的依赖锁不一致，请保留用户数据后修复此版本 | 术语不一致 | 当前版本的安装文件不一致，请修复该版本。 | P0 |
| runtime/RuntimeManager.kt:535 | 缺少选定 DSH 发布的完整性信息，请刷新版本列表 | 术语不一致 | 缺少所选版本的校验信息，请刷新版本列表。 | P0 |
| runtime/RuntimeManager.kt:542 | DSH 完整性与选定版本元数据不一致 | 工程痕迹、术语不一致 | 下载内容与所选版本不一致，请重新下载。 | P0 |
| runtime/RuntimeManager.kt:545 | 候选 DSH 程序不完整 | 术语不一致 | 候选 助手 程序不完整 | P0 |
| runtime/RuntimeManager.kt:547 | 候选目录冲突，当前版本保留 | 保留 | 候选目录冲突，当前版本保留 | P0 |
| runtime/RuntimeManager.kt:548 | 无法提交候选 DSH | 术语不一致 | 无法提交候选 助手 | P0 |
| runtime/RuntimeManager.kt:549 | 候选目录冲突 | 清晰度 | 安装位置冲突，请重新打开应用后重试。 | P0 |
| runtime/RuntimeManager.kt:553 | ${args.first()} 失败 exit=${it.exitCode}: ${it.stderr.takeLast(5000)} | 工程痕迹 | 操作失败，请查看问题详情后重试。 | P0 |
| runtime/RuntimeManager.kt:556 | 请先完成环境初始化 | 保留 | 请先完成环境初始化 | P0 |
| runtime/RuntimeManager.kt:557 | 后台自检运行中：0/60 | 保留 | 后台自检运行中：0/60 | P0 |
| runtime/RuntimeManager.kt:563 | 后台自检运行中：${line.substringAfter(' ')}/60 | 清晰度 | 后台自检运行中：{内容}/60 | P0 |
| runtime/RuntimeManager.kt:565 | 后台自检中断 exit=${result.exitCode}，不会自动重放任务 | 工程痕迹 | 后台检查已中断，请重新检查。 | P0 |
| runtime/RuntimeManager.kt:570 | 后台自检完成：60/60，详见诊断结果 | 保留 | 后台自检完成：60/60，详见诊断结果 | P0 |
| runtime/RuntimeManager.kt:571 | 后台自检实际完成 60 次计时与文件写入，exit=0 | 工程痕迹 | 后台检查已完成，共检查 60 次。 | P0 |
| runtime/RuntimeManager.kt:608 | 本机没有可用的命令沙箱；workspace-write 文件工具可用，shell 需在官方 DSH 中明确选择兼容权限模式 | 术语不一致、长度 | 本机无法在当前权限下执行命令。<br>文件操作仍可用；权限选项及风险请在助手设置中查看。 | P0 |
| runtime/RuntimeManager.kt:609 | 本机命令沙箱提供部分能力，请以 DSH 权限设置的能力提示为准 | 术语不一致 | 本机命令权限能力有限，请在助手权限设置中查看可用选项。 | P0 |
| runtime/RuntimeManager.kt:610 | 本机命令沙箱检测通过 | 清晰度 | 本机可以按当前权限设置执行命令。 | P0 |
| runtime/RuntimeManager.kt:611 | 命令沙箱能力尚未确认，可在运行检查中重试 | 清晰度 | 命令权限尚未确认，请重新检查运行环境。 | P0 |
| runtime/RuntimeManager.kt:633 | 健康检查失败 exit=${result.exitCode}: ${result.stderr} | 工程痕迹 | 运行检查失败，请查看问题详情。 | P0 |
| runtime/RuntimeManager.kt:646 | 请先停止 DSH，再调整项目容量预算 | 术语不一致 | 请先停止 助手，再调整项目空间限额 | P0 |
| runtime/RuntimeManager.kt:648 | 项目容量预算已保存；现有文件保留 | 术语不一致 | 项目空间限额已保存；现有文件保留 | P0 |
| runtime/RuntimeManager.kt:661 | 启动优化：已请求在首帧前提前拉起已就绪的 DSH | 术语不一致 | 正在启动助手 | P0 |
| runtime/RuntimeManager.kt:663 | 提前启动未能发起，仍可手动启动：${Redact.text(error.message ?: error.javaClass.simpleName)} | 清晰度 | 自动启动未成功，请手动启动助手。 | P0 |
| runtime/RuntimeManager.kt:741 | ，使用安全模式 | 保留 | ，使用安全模式 | P0 |
| runtime/RuntimeManager.kt:742 | 按失败策略自动重启（$attempt/${failurePolicy.maxRestartAttempts}）$suffix | 清晰度 | 正在自动重启助手，第 {次数} 次。 | P0 |
| runtime/RuntimeManager.kt:757 | 正常模式试用通过，已恢复 normal | 清晰度 | 已恢复正常模式。 | P0 |
| runtime/RuntimeManager.kt:758 | 安全模式运行稳定；不会自动切回正常模式，可在界面点击尝试恢复 | 清晰度 | 安全模式运行正常，可手动恢复正常模式。 | P0 |
| runtime/RuntimeManager.kt:759 | 服务稳定运行，启动失败计数已清零 | 清晰度 | 助手运行正常。 | P0 |
| runtime/RuntimeManager.kt:772 | 开始试用正常模式；2 分钟内失败会自动回到安全模式 | 清晰度 | 正在恢复正常模式，两分钟内再次失败会回到安全模式。 | P0 |
| runtime/RuntimeManager.kt:783 | 已退出正常模式试用，回到安全模式 | 清晰度 | 已返回安全模式。 | P0 |
| runtime/RuntimeManager.kt:791 | 服务正在停止，请稍后启动 | 保留 | 服务正在停止，请稍后启动 | P0 |
| runtime/RuntimeManager.kt:807 | 请先完成初始化 | 保留 | 请先完成初始化 | P0 |
| runtime/RuntimeManager.kt:808 | 请先恢复匹配快照或完成中断版本检查 | 术语不一致 | 请先恢复匹配数据备份或完成中断版本检查 | P0 |
| runtime/RuntimeManager.kt:816 | 设备剩余空间不足 64 MiB，请先清理存储 | 保留 | 设备剩余空间不足 64 MiB，请先清理存储 | P0 |
| runtime/RuntimeManager.kt:851 | 管理页 Cookie 隔离失败，已停止加载；请重启应用 | 工程痕迹 | 无法安全切换页面，请重启应用。 | P0 |
| runtime/RuntimeManager.kt:852 | DSH 在就绪前退出 | 术语不一致 | 助手启动失败。 | P0 |
| runtime/RuntimeManager.kt:855 | DSH 在就绪后退出 | 术语不一致 | 助手意外停止。 | P0 |
| runtime/RuntimeManager.kt:861 | ${Build.MODEL}; API ${Build.VERSION.SDK_INT}; target ${context.applicationInfo.targetSdkVersion}; debug ${BuildConfig.DEBUG} | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| runtime/RuntimeManager.kt:863 | DSH 已用安全模式启动：独立最小配置，未加载用户插件与配置 | 术语不一致 | 助手已在安全模式启动，不加载你的插件和设置。 | P0 |
| runtime/RuntimeManager.kt:863 | DSH 本机服务已就绪 | 术语不一致 | 本机助手已就绪。 | P0 |
| runtime/RuntimeManager.kt:870 | 恢复后的版本已通过本机启动检查；会话内容仍可在官方页面核对 | 术语不一致 | 恢复完成，请启动助手并查看对话。 | P0 |
| runtime/RuntimeManager.kt:872 | 安全模式使用独立配置，未加载恢复的用户数据；恢复检查仍待正常模式完成 | 清晰度 | 请在正常模式启动助手，完成数据恢复检查。 | P0 |
| runtime/RuntimeManager.kt:886 | 日志管道异常 | 保留 | 日志管道异常 | P0 |
| runtime/RuntimeManager.kt:956 | 管理页 Cookie 隔离失败，已拒绝加载本地端点；请重启应用 | 工程痕迹 | 无法安全切换页面，请重启应用。 | P0 |
| runtime/RuntimeManager.kt:957 | 管理页 Cookie 隔离失败，请重启应用 | 工程痕迹 | 无法安全切换页面，请重启应用。 | P0 |
| runtime/RuntimeManager.kt:981 | 空间保护未能停止全部进程，将在下次检查时重试；也可使用停止入口 | 清晰度 | 部分任务尚未停止，请手动停止助手后重试。 | P0 |
| runtime/RuntimeManager.kt:1034 | $it；DSH 已停止，任务可能中断，不会自动重放。请调整预算或清理空间后手动启动 | 术语不一致、长度 | 空间不足，助手已停止，任务可能中断。<br>请清理空间或调整工作区限额后，再启动助手。 | P0 |
| runtime/RuntimeManager.kt:1062 | 用户停止服务；不自动恢复任务 | 清晰度 | 助手已停止，请查看任务状态后再继续。 | P0 |
| runtime/RuntimeManager.kt:1065 | ${reason ?: "停止服务"}：未能确认进程已全部停止 | 清晰度 | 无法确认所有任务已停止，请重试停止助手。 | P0 |
| runtime/RuntimeManager.kt:1065 | 停止服务 | 清晰度 | 停止助手 | P0 |
| runtime/RuntimeManager.kt:1111 | 无法清理非应用所有的目录：${path.fileName} | 清晰度 | 无法清理非应用所有的目录：{名称} | P0 |

### [runtime/RuntimeRequirements.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeRequirements.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RuntimeRequirements.kt:42 | 必要组件自检失败： | 保留 | 必要组件自检失败： | P0 |
| runtime/RuntimeRequirements.kt:49 | 检测必要组件 | 保留 | 检测必要组件 | P0 |
| runtime/RuntimeRequirements.kt:51 | 必要组件检测通过 | 保留 | 必要组件检测通过 | P0 |
| runtime/RuntimeRequirements.kt:52 | 缺少必要组件： | 保留 | 缺少必要组件： | P0 |
| runtime/RuntimeRequirements.kt:54 | 补齐必要组件： | 清晰度 | 正在安装缺少的基础工具 | P0 |
| runtime/RuntimeRequirements.kt:63 | 依赖配置修复失败： | 清晰度 | 基础工具修复失败，请重试。 | P0 |
| runtime/RuntimeRequirements.kt:65 | dpkg 配置修复失败： | 工程痕迹 | 基础工具修复失败，请重试。 | P0 |
| runtime/RuntimeRequirements.kt:67 | apt update 失败，已保留原环境 | 工程痕迹 | 软件列表更新失败，请检查网络后重试。 | P0 |
| runtime/RuntimeRequirements.kt:74 | 必要组件安装失败 exit=${install.exitCode}： | 工程痕迹 | 基础工具安装失败，请重试。 | P0 |
| runtime/RuntimeRequirements.kt:76 | 补齐后仍缺少： | 清晰度 | 部分基础工具仍未安装，请重试。 | P0 |
| runtime/RuntimeRequirements.kt:78 | 补齐完成：$pkgs | 清晰度 | 基础工具已安装完成。 | P0 |
| runtime/RuntimeRequirements.kt:88 | 下载系统组件 | 保留 | 下载系统组件 | P0 |
| runtime/RuntimeRequirements.kt:88 | 安装系统组件 | 保留 | 安装系统组件 | P0 |

### [runtime/RuntimeService.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeService.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RuntimeService.kt:25 | 本机开发环境 | 保留 | 本机开发环境 | P1 |
| runtime/RuntimeService.kt:26 | 准备运行环境 | 保留 | 准备运行环境 | P1 |
| runtime/RuntimeService.kt:37 | 环境 ${s.environment.label} · 服务 ${s.service.label} | 清晰度 | 运行环境：{环境状态}，助手：{运行状态} | P1 |
| runtime/RuntimeService.kt:39 |  · 安全模式 | 清晰度 | · 安全模式 | P1 |
| runtime/RuntimeService.kt:40 |  · 正常模式试用 | 清晰度 | 正在恢复正常模式 | P1 |
| runtime/RuntimeService.kt:43 |  · 等待自动重启 | 清晰度 | 正在等待重启 | P1 |
| runtime/RuntimeService.kt:73 |  · 正在处理 | 清晰度 | 正在处理 | P1 |
| runtime/RuntimeService.kt:73 |  本机服务 | 清晰度 | 本机助手 | P1 |
| runtime/RuntimeService.kt:84 | 停止（可能中断任务） | 清晰度 | 停止助手 | P0 |

### [runtime/RuntimeVersionSelection.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/RuntimeVersionSelection.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/RuntimeVersionSelection.kt:10 | 未知组件版本 | 被动/实现说明 | 未知组件版本 | P1 |

### [runtime/SourceSpeedTester.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/SourceSpeedTester.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/SourceSpeedTester.kt:77 | 已取消 | 保留 | 已取消 | P0 |
| runtime/SourceSpeedTester.kt:131 | 测速超时 | 保留 | 测速超时 | P0 |
| runtime/SourceSpeedTester.kt:132 | 测速已取消 | 保留 | 测速已取消 | P0 |
| runtime/SourceSpeedTester.kt:133 | 测速未完成 | 保留 | 测速未完成 | P1 |
| runtime/SourceSpeedTester.kt:155 | 测速已取消 | 保留 | 测速已取消 | P0 |
| runtime/SourceSpeedTester.kt:170 | 跳转次数过多 | 保留 | 跳转次数过多 | P1 |
| runtime/SourceSpeedTester.kt:171 | 下载源缺少跳转地址 | 保留 | 下载源缺少跳转地址 | P1 |
| runtime/SourceSpeedTester.kt:175 | 下载源返回 HTTP $code | 清晰度 | 下载源暂不可用，请换一个下载源。 | P1 |
| runtime/SourceSpeedTester.kt:183 | 跳转次数过多 | 保留 | 跳转次数过多 | P1 |
| runtime/SourceSpeedTester.kt:190 | 下载源返回了不匹配的范围 | 保留 | 下载源返回了不匹配的范围 | P0 |
| runtime/SourceSpeedTester.kt:191 | 下载源返回了不匹配的范围 | 保留 | 下载源返回了不匹配的范围 | P0 |
| runtime/SourceSpeedTester.kt:194 | 下载源文件大小与预期不符 | 保留 | 下载源文件大小与预期不符 | P1 |
| runtime/SourceSpeedTester.kt:198 | 下载源文件大小与预期不符 | 保留 | 下载源文件大小与预期不符 | P1 |
| runtime/SourceSpeedTester.kt:227 | 下载源返回的数据过少 | 保留 | 下载源返回的数据过少 | P1 |
| runtime/SourceSpeedTester.kt:228 | 下载源返回的不是有效压缩包，可能被拦截 | 保留 | 下载源返回的不是有效压缩包，可能被拦截 | P1 |
| runtime/SourceSpeedTester.kt:237 | 测速地址必须使用 HTTPS | 保留 | 测速地址必须使用 HTTPS | P1 |
| runtime/SourceSpeedTester.kt:238 | 测速地址不能包含凭据 | 保留 | 测速地址不能包含凭据 | P1 |
| runtime/SourceSpeedTester.kt:239 | 测速地址端口不受支持 | 保留 | 测速地址端口不受支持 | P1 |
| runtime/SourceSpeedTester.kt:241 | 不是已知的公共下载源 | 保留 | 不是已知的公共下载源 | P1 |
| runtime/SourceSpeedTester.kt:246 | 连接或读取超时 | 保留 | 连接或读取超时 | P0 |
| runtime/SourceSpeedTester.kt:247 | 无法解析下载源 | 保留 | 无法解析下载源 | P0 |
| runtime/SourceSpeedTester.kt:248 | 无法连接下载源 | 保留 | 无法连接下载源 | P0 |
| runtime/SourceSpeedTester.kt:249 | TLS 连接失败 | 清晰度 | 无法建立安全连接，请更换网络或下载源。 | P0 |
| runtime/SourceSpeedTester.kt:250 | 网络读取失败 | 保留 | 网络读取失败 | P0 |
| runtime/SourceSpeedTester.kt:251 | 测速失败 | 保留 | 测速失败 | P0 |
| runtime/SourceSpeedTester.kt:253 | [地址] | 保留 | [地址] | P1 |
| runtime/SourceSpeedTester.kt:253 | 测速失败 | 保留 | 测速失败 | P0 |

### [runtime/SshTransport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/SshTransport.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/SshTransport.kt:17 | 远程主机密钥已变化 | 术语不一致 | 远程电脑密钥已变化 | P1 |
| runtime/SshTransport.kt:101 | 无法完成 SSH 主机密钥交换；请确认主机可达，当前切片支持 RSA/ECDSA 主机密钥 | 长度 | 无法确认电脑身份，请核对连接地址和安全指纹。 | P0 |
| runtime/SshTransport.kt:152 | 远程主机格式无效 | 术语不一致 | 远程电脑格式无法使用 | P1 |

### [runtime/State.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/runtime/State.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| runtime/State.kt:32 | 尚未初始化 | 清晰度 | 尚未准备环境 | P1 |
| runtime/State.kt:35 | 未安装 | 保留 | 未安装 | P0 |
| runtime/State.kt:35 | 未安装 | 保留 | 未安装 | P0 |
| runtime/State.kt:36 | 未安装 | 保留 | 未安装 | P0 |

### [storage/ContentDocument.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ContentDocument.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/ContentDocument.kt:71 | 此文件不是内置预览支持的图片 | 清晰度 | 此图片无法预览，请用其他应用打开。 | P1 |
| storage/ContentDocument.kt:77 | 此图片格式无法内置预览，请使用其他应用打开。 | 保留 | 此图片格式无法内置预览，请使用其他应用打开。 | P0 |
| storage/ContentDocument.kt:83 | 图片无法解码，请使用其他应用打开。 | 清晰度 | 图片无法读取，请用其他应用打开。 | P0 |
| storage/ContentDocument.kt:141 | 无法打开此文件 | 保留 | 无法打开此文件 | P0 |
| storage/ContentDocument.kt:156 | 无法打开此 PDF 的只读描述符 | 清晰度 | 无法读取 PDF，请重新选择文件。 | P0 |
| storage/ContentDocument.kt:211 | 无法准备 $label 临时目录 | 清晰度 | 无法准备预览文件，请清理空间后重试。 | P0 |
| storage/ContentDocument.kt:212 | 无法准备 $label 临时目录 | 清晰度 | 无法准备预览文件，请清理空间后重试。 | P0 |
| storage/ContentDocument.kt:216 | 设备空间不足，无法准备此 $label 文件 | 清晰度 | 设备空间不足，无法准备此 {名称} 文件 | P0 |
| storage/ContentDocument.kt:220 | 无法打开此文件 | 保留 | 无法打开此文件 | P0 |
| storage/ContentDocument.kt:227 | 此 $label 文件超过 ${ExternalDocumentPolicy.humanBytes(maxBytes)} 的内置预览上限 | 清晰度 | 此 {名称} 文件超过 {大小} 的内置预览上限 | P1 |
| storage/ContentDocument.kt:233 | 设备空间不足，复制已取消 | 清晰度 | 空间不足，复制已取消。请清理空间后重试。 | P0 |
| storage/ContentDocument.kt:278 | 无法打开此文件 | 保留 | 无法打开此文件 | P0 |
| storage/ContentDocument.kt:292 | 文件在读取期间发生变化，请重试 | 保留 | 文件在读取期间发生变化，请重试 | P1 |
| storage/ContentDocument.kt:307 | 无法打开此文件 | 保留 | 无法打开此文件 | P0 |

### [storage/DataSnapshots.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/DataSnapshots.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/DataSnapshots.kt:19 | private snapshot; does not contain workspaces or Ubuntu | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/DataSnapshots.kt:26 | 没有可恢复快照 | 术语不一致 | 还没有可恢复的数据备份，请先创建备份。 | P0 |
| storage/DataSnapshots.kt:29 | 快照未完成 | 术语不一致 | 数据备份不完整，请选择其他备份。 | P1 |
| storage/DataSnapshots.kt:33 | 快照匹配的 DSH 程序缺失 | 术语不一致 | 备份对应的助手版本未安装，请先安装该版本。 | P1 |
| storage/DataSnapshots.kt:34 | Node.js 不匹配，停止自动回退 | 清晰度 | 运行组件版本不匹配，请安装与备份匹配的版本。 | P0 |
| storage/DataSnapshots.kt:35 | Ubuntu 版本不匹配，停止自动回退 | 清晰度 | 系统组件版本不匹配，请安装与备份匹配的版本。 | P0 |
| storage/DataSnapshots.kt:39 | 先前恢复记录未处理，保留数据并停止 | 清晰度 | 上次恢复尚未完成，请先完成恢复。 | P1 |
| storage/DataSnapshots.kt:44 | 数据切换中断，保留 before-restore 目录 | 清晰度 | 数据恢复中断，请保留现有文件并重新打开应用。 | P0 |
| storage/DataSnapshots.kt:67 | 恢复暂存数据不完整，已保留所有副本；需要人工检查恢复记录 | 清晰度 | 恢复文件不完整，请保留现有文件并反馈问题。 | P1 |
| storage/DataSnapshots.kt:70 | 恢复目录冲突，已停止覆盖 | 清晰度 | 恢复位置有冲突，已停止覆盖。请保留现有文件并反馈问题。 | P0 |
| storage/DataSnapshots.kt:79 | 已完成上次准备好的数据恢复；服务保持停止，等待启动检查 | 清晰度 | 数据已恢复，请启动助手完成检查。 | P1 |
| storage/DataSnapshots.kt:93 | 备份空间不足 | 保留 | 备份空间不足 | P0 |
| storage/DataSnapshots.kt:100 | 备份拒绝指向应用目录或已知客体映射之外的链接 | 清晰度 | 部分文件不在备份范围内，备份已停止。请查看详情。 | P0 |
| storage/DataSnapshots.kt:103 | 备份空间不足 | 保留 | 备份空间不足 | P0 |
| storage/DataSnapshots.kt:105 | 备份不支持设备或 socket 文件: ${path.fileName} | 清晰度 | 备份包含无法处理的文件，请查看详情。 | P1 |

### [storage/DirectoryUsage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/DirectoryUsage.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/DirectoryUsage.kt:33 | 目录条目超过单次统计上限 | 保留 | 目录条目超过单次统计上限 | P1 |
| storage/DirectoryUsage.kt:34 | 目录统计超时，请稍后重试 | 保留 | 目录统计超时，请稍后重试 | P0 |
| storage/DirectoryUsage.kt:49 | 项目空间已超出预算，统计提前结束 | 术语不一致 | 工作区已超出限额，请调整限额后重新统计。 | P1 |
| storage/DirectoryUsage.kt:53 | 目录大小超出可统计范围 | 保留 | 目录大小超出可统计范围 | P1 |
| storage/DirectoryUsage.kt:66 | 目录深度超过单次统计上限 | 保留 | 目录深度超过单次统计上限 | P1 |

### [storage/DocxTextExtractor.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/DocxTextExtractor.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/DocxTextExtractor.kt:28 | 文件不存在或不是普通文件 | 保留 | 文件不存在或不是普通文件 | P1 |
| storage/DocxTextExtractor.kt:29 | 此 DOCX 文件过大，已停止预览 | 保留 | 此 DOCX 文件过大，已停止预览 | P1 |
| storage/DocxTextExtractor.kt:33 | 无法读取此 DOCX：文件已加密、损坏或不是有效的 Word 文档 | 清晰度 | 无法读取文档，请用其他应用打开或重新导出。 | P0 |
| storage/DocxTextExtractor.kt:41 | 此 DOCX 压缩包条目过多，已停止预览 | 保留 | 此 DOCX 压缩包条目过多，已停止预览 | P1 |
| storage/DocxTextExtractor.kt:43 | 此 DOCX 解压后过大，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/DocxTextExtractor.kt:46 | 此 DOCX 缺少正文（word/document.xml），无法预览 | 清晰度 | 文档缺少正文，请重新导出文件。 | P0 |
| storage/DocxTextExtractor.kt:47 | 此 DOCX 正文解压后过大，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/DocxTextExtractor.kt:54 | 此 DOCX 正文格式不正确，无法预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/DocxTextExtractor.kt:59 | 此 DOCX 正文格式不正确，无法预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/DocxTextExtractor.kt:85 | 此 DOCX 包含不受支持的文档类型声明，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/DocxTextExtractor.kt:97 | 此 DOCX 正文包含无法解析的实体引用，格式不正确 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/DocxTextExtractor.kt:120 | 此 DOCX 正文解压后超过上限，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |

### [storage/ExportCleanup.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ExportCleanup.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/ExportCleanup.kt:8 | 未完成的目标文件无法自动删除，请在目标位置检查 | 清晰度 | 未完成的导出文件无法清理，请到目标位置检查。 | P0 |
| storage/ExportCleanup.kt:13 | 已取消导出，未完成的目标文件已删除 | 清晰度 | 导出已取消，未完成的导出文件已清理。 | P0 |
| storage/ExportCleanup.kt:14 | 已取消导出，但未完成的目标文件无法自动删除，请在目标位置检查 | 清晰度 | 导出已取消，请到目标位置清理未完成的文件。 | P0 |
| storage/ExportCleanup.kt:17 | 导出失败，未完成的目标文件已删除；应用内原文件仍保留 | 清晰度 | 导出失败，原文件保留。请重试。 | P0 |
| storage/ExportCleanup.kt:18 | 导出失败，未完成的目标文件无法自动删除；应用内原文件仍保留 | 清晰度 | 导出失败，请到目标位置清理未完成的文件。<br>应用内原文件保留。 | P0 |

### [storage/ExternalDocumentAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ExternalDocumentAccess.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/ExternalDocumentAccess.kt:38 | 外部文件 | 保留 | 外部文件 | P1 |
| storage/ExternalDocumentAccess.kt:94 | 无法读取此文件，请重试或复制到工作区后再打开。 | 保留 | 无法读取此文件，请重试或复制到工作区后再打开。 | P0 |

### [storage/ExternalDocumentPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ExternalDocumentPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/ExternalDocumentPolicy.kt:100 | 此来源没有授予长期读取权限；应用进程重建后可能需要重新选择文件。 | 清晰度 | 此文件的读取权限可能在应用重开后失效，届时请重新选择。 | P0 |
| storage/ExternalDocumentPolicy.kt:102 | 无法访问此文件：读取权限已失效，或文件已被移动、删除。请重新选择文件，或复制到工作区后再打开。 | 长度 | 无法读取文件，请重新选择，或复制到工作区后打开。 | P0 |
| storage/ExternalDocumentPolicy.kt:104 | 此文件提供方不支持直接读取，请复制到工作区或使用其他应用打开。 | 清晰度 | 此来源无法直接打开，请先复制到工作区，或用其他应用打开。 | P1 |
| storage/ExternalDocumentPolicy.kt:106 | 不支持此文件来源：只允许打开系统文件选择器提供的 content:// 文件。 | 清晰度 | 请通过系统文件选择器重新选择文件。 | P0 |
| storage/ExternalDocumentPolicy.kt:108 | ；请使用其他应用打开，或复制到工作区后处理。 | 保留 | ；请使用其他应用打开，或复制到工作区后处理。 | P1 |
| storage/ExternalDocumentPolicy.kt:166 | 外部文件 | 保留 | 外部文件 | P1 |
| storage/ExternalDocumentPolicy.kt:196 | 图片 | 保留 | 图片 | P1 |
| storage/ExternalDocumentPolicy.kt:197 | 文件 | 保留 | 文件 | P1 |
| storage/ExternalDocumentPolicy.kt:199 | 此$label 为 ${humanBytes(bytes)}，超过内置预览上限 ${humanBytes(limit)}$TOO_LARGE_SUFFIX | 清晰度 | 此{名称} 为 {大小}，超过内置预览上限 {大小}{内容} | P1 |
| storage/ExternalDocumentPolicy.kt:230 | 不支持直接读取 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/ExternalDocumentPolicy.kt:230 | 无法打开描述符 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/ExternalDocumentPolicy.kt:242 | 大小未知 | 被动/实现说明 | 正在读取文件大小 | P1 |
| storage/ExternalDocumentPolicy.kt:263 | 文件超过允许的大小上限 | 保留 | 文件超过允许的大小上限 | P0 |
| storage/ExternalDocumentPolicy.kt:266 | 复制上限无效 | 清晰度 | 复制上限无法使用 | P1 |
| storage/ExternalDocumentPolicy.kt:275 | 文件来源暂时无法读取 | 保留 | 文件来源暂时无法读取 | P0 |

### [storage/ImportNaming.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ImportNaming.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/ImportNaming.kt:30 | 导入 | 保留 | 导入 | P1 |
| storage/ImportNaming.kt:33 | 导入 | 保留 | 导入 | P1 |
| storage/ImportNaming.kt:39 | 导入 | 保留 | 导入 | P1 |
| storage/ImportNaming.kt:63 | 导入-${format.format(Date(nowMillis))} | 清晰度 | 导入-{内容} | P1 |
| storage/ImportNaming.kt:68 | 序号必须从 1 开始 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/ImportNaming.kt:80 | 无法为同名条目分配新名称，请先整理目标目录 | 保留 | 无法为同名条目分配新名称，请先整理目标目录 | P0 |
| storage/ImportNaming.kt:85 | 导入暂存目录无效 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| storage/ImportNaming.kt:96 | 无法为导入项目分配唯一名称，原有项目已保留 | 保留 | 无法为导入项目分配唯一名称，原有项目已保留 | P0 |
| storage/ImportNaming.kt:108 | 无法为同名文件分配新名称，请先整理目标目录 | 保留 | 无法为同名文件分配新名称，请先整理目标目录 | P0 |

### [storage/L2sStore.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/L2sStore.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/L2sStore.kt:20 | 硬链接存储目录被重定向，已停止启动 | 清晰度 | 运行文件位置异常，已停止启动。请重新打开应用。 | P1 |
| storage/L2sStore.kt:22 | 硬链接存储路径不是目录 | 清晰度 | 运行文件位置异常，请重新打开应用。 | P1 |
| storage/L2sStore.kt:24 | 硬链接存储与运行环境必须位于同一文件系统 | 清晰度 | 运行文件存储异常，请重新打开应用。 | P1 |
| storage/L2sStore.kt:42 | 硬链接目标超出应用文件区：${path.fileName} | 清晰度 | 运行文件位置异常，请查看问题详情。 | P1 |
| storage/L2sStore.kt:47 | 硬链接索引缺失：${path.fileName} | 清晰度 | 运行文件索引缺失，请修复运行环境。 | P1 |
| storage/L2sStore.kt:51 | 硬链接索引无效：${path.fileName} | 清晰度 | 运行文件索引无法使用，请修复运行环境。 | P1 |
| storage/L2sStore.kt:53 | 硬链接内容缺失：${path.fileName} | 清晰度 | 运行文件缺失，请修复运行环境。 | P1 |
| storage/L2sStore.kt:56 | 硬链接内容缺失：${path.fileName} | 清晰度 | 运行文件缺失，请修复运行环境。 | P1 |
| storage/L2sStore.kt:75 | 硬链接引用统计未完成 | 清晰度 | 运行文件统计未完成，请重试。 | P1 |

### [storage/PhoneFileIntegration.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/PhoneFileIntegration.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/PhoneFileIntegration.kt:17 | 手机文件连接配置无效 | 清晰度 | 手机文件连接配置无法使用 | P1 |
| storage/PhoneFileIntegration.kt:27 | 手机文件 Skill 路径被重定向 | 清晰度 | 手机文件连接异常，请重新打开应用。 | P1 |
| storage/PhoneFileIntegration.kt:29 | 已有同名自定义手机文件 Skill，未覆盖 | 清晰度 | 已有同名手机文件技能，连接未更新。请检查技能设置。 | P0 |
| storage/PhoneFileIntegration.kt:46 | 手机文件工具目录被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| storage/PhoneFileIntegration.kt:48 | 手机文件工具目录不可用 | 保留 | 手机文件工具目录不可用 | P1 |
| storage/PhoneFileIntegration.kt:53 | 手机文件工具路径被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| storage/PhoneFileIntegration.kt:61 | 手机文件工具暂存路径被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |

### [storage/PhoneFileService.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/PhoneFileService.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/PhoneFileService.kt:107 | DSH 手机文件写入 | 术语不一致 | 助手写入手机文件 | P1 |
| storage/PhoneFileService.kt:107 | DSH 手机文件读取 | 术语不一致 | 助手读取手机文件 | P1 |

### [storage/PhoneStorageAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/PhoneStorageAccess.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/PhoneStorageAccess.kt:52 | 内部存储 | 保留 | 内部存储 | P1 |
| storage/PhoneStorageAccess.kt:59 | ${label.ifBlank { "外部存储" }} (${volume.uuid ?: result.size}) | 清晰度 | {存储名称} | P1 |
| storage/PhoneStorageAccess.kt:59 | 外部存储 | 保留 | 外部存储 | P1 |
| storage/PhoneStorageAccess.kt:67 | 外部存储 (${result.size}) | 清晰度 | 存储卡 {序号} | P1 |

### [storage/ProjectArchive.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/ProjectArchive.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/ProjectArchive.kt:21 | 工作区被重定向 | 保留 | 工作区被重定向 | P1 |
| storage/ProjectArchive.kt:24 | 工作区不存在或超出允许范围 | 清晰度 | 工作区不可访问，请重新打开应用后重试。 | P0 |
| storage/ProjectArchive.kt:28 | 导出期间项目文件发生变化，请刷新后重试；未生成完整导出 | 保留 | 导出期间项目文件发生变化，请刷新后重试；未生成完整导出 | P1 |
| storage/ProjectArchive.kt:30 | 导出文件超出允许范围 | 保留 | 导出文件超出允许范围 | P0 |
| storage/ProjectArchive.kt:64 | 项目包含无法导出的符号链接或特殊文件：${relative(path)}；未生成完整导出 | 清晰度 | 项目含有无法导出的文件，导出已停止。请查看详情。 | P0 |

### [storage/SafeTar.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/SafeTar.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/SafeTar.kt:15 | 归档绝对路径 | 保留 | 归档绝对路径 | P1 |
| storage/SafeTar.kt:17 | 归档路径越界: $name | 错误出口 | 安装文件信息异常，请刷新版本后重试。 | P1 |
| storage/SafeTar.kt:26 | 暂存目录必须为空 | 清晰度 | 文件正在准备，请等待完成后重试。 | P0 |
| storage/SafeTar.kt:37 | 归档文件数超限 | 清晰度 | 压缩包文件过多，请分批处理。 | P1 |
| storage/SafeTar.kt:45 | 解包大小超限 | 清晰度 | 解压内容过大，请使用其他文件。 | P1 |
| storage/SafeTar.kt:46 | 解包空间不足 | 清晰度 | 解压空间不足，请清理空间后重试。 | P0 |
| storage/SafeTar.kt:48 | 重复归档条目: ${e.name} | 清晰度 | 压缩包含有重复文件，请重新下载或重新打包。 | P1 |
| storage/SafeTar.kt:65 | 不支持的归档类型: ${e.name} | 清晰度 | 压缩包含有不支持的文件，请重新下载或重新打包。 | P1 |
| storage/SafeTar.kt:90 | 硬链接目标不存在 | 保留 | 硬链接目标不存在 | P1 |
| storage/SafeTar.kt:102 | 符号链接越界 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| storage/SafeTar.kt:103 | 符号链接覆盖已有文件 | 清晰度 | 解压会覆盖已有文件，已停止操作。请换一个位置。 | P0 |
| storage/SafeTar.kt:111 | 链接父目录不安全 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |

### [storage/SharedStoragePaths.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/SharedStoragePaths.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/SharedStoragePaths.kt:11 | 共享存储根必须是绝对路径 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/SharedStoragePaths.kt:22 | 共享存储相对路径无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [storage/TextFileReader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/TextFileReader.kt:50 | 文本位置无效 | 清晰度 | 文本位置无法使用 | P1 |
| storage/TextFileReader.kt:51 | 此文件来源不支持向后跳转，请重新打开文件 | 清晰度 | 此来源无法跳到前文，请重新打开文件。 | P1 |
| storage/TextFileReader.kt:59 | 文件已缩短，请重新打开 | 保留 | 文件已缩短，请重新打开 | P1 |
| storage/TextFileReader.kt:94 | 二进制 | 清晰度 | 非文本内容 | P1 |
| storage/TextFileReader.kt:97 | 自动 | 保留 | 自动 | P1 |
| storage/TextFileReader.kt:116 | 自动 | 保留 | 自动 | P1 |
| storage/TextFileReader.kt:123 | 自动 | 保留 | 自动 | P1 |
| storage/TextFileReader.kt:124 | 文本位置无效 | 清晰度 | 文本位置无法使用 | P1 |
| storage/TextFileReader.kt:125 | 不支持此文本编码 | 清晰度 | 无法识别文字格式，请更换编码后重试。 | P1 |
| storage/TextFileReader.kt:127 | 文件已缩小，请重新打开 | 保留 | 文件已缩小，请重新打开 | P1 |
| storage/TextFileReader.kt:141 | 自动 | 保留 | 自动 | P1 |
| storage/TextFileReader.kt:212 | 文件已缩短或暂时无法读取，请重新打开 | 保留 | 文件已缩短或暂时无法读取，请重新打开 | P0 |
| storage/TextFileReader.kt:512 | 的一是不了在人有我他这个们中来上大为和国地到以说时要就出会可也你对生能而子那得于着下自之年过发后作里用道行所然家种事成方多经么去法学如都同现当没动面起看定天分还进好小部其些主样理心她本前开但因只从想实日军者意无力它与长把 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:513 | 机十民第公此已工使情明性知全三又关点正业外将两高间由问很最重并物手应战向头文体政美相见被利什二等产或新己制身果加西斯月话合回特代内信表化老给世位次度门任常先海通教儿原东声提立及比员解水名 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:514 | 真论处走义各入几口认条平系气题活尔更别打女变四神总何电数安少报才结反受目太量再感建务做接必场件计管期市直德资命山金指克许统区保至队形社便空决治展马科司五基眼书非则听白却界达光放强即像 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:515 | 难且权思王象完设式色路记南品住告类求据程北边死张该交规万取拉格望觉术领共确传师观清今切院让识候带导争运笑飞风步改收根干造言联持组每济车亲极林服快办议往元英士证近失转夫令准布始怎呢存未 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:516 | 远叫台单影具罗字爱击流备兵连调深商算质团集百需价花党华城石级整府离况亚请技际约示复病息究线似官火断精满支视消越器容照须九增研写称企八功吗包片史委乎查轻易早曾除农找装广显吧阿李标谈吃图 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:517 | 念六引历首医局突专费号尽另周较注语仅考落青随选列武红响虽推势参希古众构房半节土投某案黑维革划敌致陈律足态护七兴派孩验责营星够章音跟志底站严巴例防族供效续施留讲型料终答紧黄绝奇察母京段依 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:518 | 批群项故按河米围江织害斗双境客纪采举杀攻父苏密低朝友诉止细愿千值仍男钱破网热助倒育属坐帝限船脸职速刻乐否刚威毛状率甚独球般普怕弹校苦创假久错承印晚兰试股拿脑预谁益阳若哪微尼继送急血惊 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:519 | 伤素药适波夜省初喜卫源食险待述陆习置居劳财环排福纳欢雷警获模充负云停木游龙树疑层冷洲冲射略范竟句室异激汉村哈策演简卡罪判担州静退既衣您宗积余痛检差富灵协角占配征修皮挥胜降阶审沉坚善妈 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:520 | 刘读啊超免压银买皇养伊怀执副乱抗犯追帮宣佛岁航优怪香著田铁控税左右份穿艺背阵草脚概恶块顿敢守酒岛托央户烈洋哥索胡款靠评版宝座释景顾弟登货互付伯慢欧换闻危忙核暗姐介坏讨丽良序升监临亮露 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:521 | 永呼味野架域沙掉括舰鱼杂误湾吉减编楚肯测败屋跑梦散温困剑渐封救贵枪缺楼县尚毫移娘朋画班智亦耳恩短掌恐遗固席松秘谢鲁遇康虑幸均销钟诗藏赶剧票损忽巨炮旧端探湖录叶春乡附吸予礼港雨呀板庭妇 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:522 | 归睛饭额含顺输摇招婚脱补谓督毒油疗旅泽材灭逐莫笔亡鲜词圣择寻厂睡博勒烟授诺伦岸奥唐卖俄炸载洛健堂旁宫喝借君禁阴园谋宋避抓荣姑孙逃牙束跳顶玉镇雪午练迫爷篇肉嘴馆遍凡础洞卷坦牛宁纸诸训私 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:523 | 庄祖丝翻暴森塔默握戏隐熟骨访弱蒙歌店鬼软典欲萨伙遭盘爸扩盖弄雄稳忘亿刺拥徒姆杨齐赛趣曲刀床迎冰虚玩析窗醒妻透购替塞努休虎扬途侵刑绿兄迅套贸毕唯谷轮库迹尤竞街促延震弃甲伟麻川申缓潜闪售 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:524 | 灯针哲络抵朱埃抱鼓植纯夏忍页杰筑折郑贝尊吴秀混臣雅振染盛怒舞圆搞狂措姓残秋培迷诚宽宇猛摆梅毁伸摩盟末乃悲拍丁赵硬麦蒋操耶阻订彩抽赞魔纷沿喊违妹浪汇币丰蓝殊献桌啦瓦莱援译夺汽烧距裁偏符 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:525 | 勇触课敬哭懂墙袭召罚侠厅拜巧侧韩冒债曼融惯享戴童犹乘挂奖绍厚纵障讯涉彻刊丈爆乌役描洗玛患妙镜唱烦签仙彼弗症仿倾牌陷鸟轰咱菜闭奋庆撤泪茶疾缘播朗杜奶季丹狗尾仪偷奔珠虫驻孔宜艾桥淡翼恨 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:526 | 繁寒伴叹旦愈潮粮缩罢聚径恰挑袋灰捕徐珍幕映裂泰隔启尖忠累炎暂估泛荒偿横拒瑞忆孤鼻闹羊呆厉衡胞零穷舍码赫婆魂灾洪腿胆津俗辩胸晓劲贫仁偶辑邦恢赖圈摸仰润堆碰艇稍迟辆废净凶署壁御奉旋冬矿 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:527 | 抬蛋晨伏吹鸡倍糊秦盾杯租骑乏隆诊奴摄丧污渡旗甘耐凭扎抢绪粗肩梁幻菲皆碎宙叔岩荡综爬荷悉蒂返井壮薄悄扫敏碍殖详迪矛霍允幅撒剩凯颗骂赏液番箱贴漫酸郎腰舒眉忧浮辛恋餐吓挺励辞艘键伍峰尺昨 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:528 | 黎辈贯侦滑券崇扰宪绕趋慈乔阅汗枝拖墨胁插箭腊粉泥氏彭拔骗凤慧媒佩愤扑龄驱惜豪掩兼跃尸肃帕驶堡届欣惠册储飘桑闲惨洁踪勃宾频仇磨递邪撞拟滚奏巡颜剂绩贡疯坡瞧截燃焦殿伪柳锁逼颇昏劝呈搜勤 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:529 | 戒驾漂饮曹朵仔柔俩孟腐幼践籍牧凉牲佳娜浓芳稿竹腹跌逻垂遵脉貌柏狱猜怜惑陶兽帐饰贷昌叙躺钢沟寄扶铺邓寿惧询汤盗肥尝匆辉奈扣廷澳嘛董迁凝慰厌脏腾幽尊缴谎遭姚闹愤 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/TextFileReader.kt:531 | 臺灣繁體中文測試國學語言說時間會為與這個們來對開關發現實際應該還進過讓從麼樣點電腦網路檔案編輯碼顯示確認錯誤讀寫檢查轉換數據資料庫連線圖片聲音視頻預覽設定支援環境變更條件結果記錄項目類型問題幫助訊息軟體硬體系統執行應用程式開啟關閉選擇複製貼上刪除儲存載入匯出匯入傳送接收等候暫停繼續完成失敗成功警告提示取消無長短舊壞熱晚昨週億錢幣價買賣付銀帳戶卡號稱姓地址話機郵箱密碼登註冊權限員廠商師課書筆記紙張門窗牆房間廚浴廳臥園樹雲陽亮國際鄉車輛飛船鐵捷運計腳踏貨救護消防設備零件材能源電力風核石油氣煤炭污染垃圾資源回收再生永續發展 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [storage/TransferEngine.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/TransferEngine.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/TransferEngine.kt:29 | 已复制并校验到「${target.fileName}」，但删除源文件未完成；目标已保留，请检查源文件。 | 清晰度 | 文件已复制，但源文件未删除。请检查源文件后自行处理。 | P0 |
| storage/TransferEngine.kt:72 | 源文件在读取期间变大，已取消复制 | 保留 | 源文件在读取期间变大，已取消复制 | P0 |
| storage/TransferEngine.kt:82 | 源文件在读取期间变小，已取消复制 | 保留 | 源文件在读取期间变小，已取消复制 | P0 |
| storage/TransferEngine.kt:85 | 复制校验失败，目标文件与源文件不一致 | 清晰度 | 复制后检查不一致，请重试。 | P0 |
| storage/TransferEngine.kt:131 | 源目录在复制期间发生变化，已取消；源文件已保留 | 保留 | 源目录在复制期间发生变化，已取消；源文件已保留 | P0 |
| storage/TransferEngine.kt:149 | 目标目录无效 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| storage/TransferEngine.kt:150 | 目标目录不存在或是链接 | 保留 | 目标目录不存在或是链接 | P1 |
| storage/TransferEngine.kt:152 | 不能复制到自身或子目录 | 保留 | 不能复制到自身或子目录 | P1 |
| storage/TransferEngine.kt:156 | 同名条目已存在，未覆盖 | 保留 | 同名条目已存在，未覆盖 | P0 |
| storage/TransferEngine.kt:158 | 只能替换同名普通文件；文件夹请选择保留两者或跳过 | 清晰度 | 文件夹不能直接替换，请选择“保留两者”或“跳过”。 | P1 |
| storage/TransferEngine.kt:166 | 正在复制并校验 | 保留 | 正在复制并校验 | P1 |
| storage/TransferEngine.kt:168 | 正在核对源文件与容量 | 保留 | 正在核对源文件与容量 | P1 |
| storage/TransferEngine.kt:170 | 源目录或文件已变化，源文件未删除 | 保留 | 源目录或文件已变化，源文件未删除 | P0 |
| storage/TransferEngine.kt:173 | 源文件内容已变化，源文件未删除 | 保留 | 源文件内容已变化，源文件未删除 | P0 |
| storage/TransferEngine.kt:175 | 源文件已变化，源文件未删除 | 保留 | 源文件已变化，源文件未删除 | P0 |
| storage/TransferEngine.kt:178 | 目标出现新的同名条目，未覆盖；请重试 | 保留 | 目标出现新的同名条目，未覆盖；请重试 | P0 |
| storage/TransferEngine.kt:181 | 目标文件已被修改，未覆盖 | 保留 | 目标文件已被修改，未覆盖 | P0 |
| storage/TransferEngine.kt:183 | 正在提交 | 清晰度 | 正在完成复制 | P1 |
| storage/TransferEngine.kt:183 | 正在提交并清理源文件 | 清晰度 | 正在完成移动 | P1 |
| storage/TransferEngine.kt:203 | 传输完成，但临时目录清理失败，请检查目标目录 | 清晰度 | 文件已传输，但临时文件未清理。请检查目标文件夹。 | P0 |
| storage/TransferEngine.kt:218 | 源目录已被替换，停止清理；目标已保留 | 保留 | 源目录已被替换，停止清理；目标已保留 | P1 |
| storage/TransferEngine.kt:228 | 目录层级超过 128 层，无法安全复制 | 清晰度 | 文件夹层级过多，无法复制。请分批复制。 | P0 |
| storage/TransferEngine.kt:241 | 不支持复制链接或特殊文件 | 清晰度 | 此类链接或特殊文件无法复制，请选择普通文件。 | P1 |
| storage/TransferEngine.kt:246 | 文件在复制期间被修改或替换，已停止操作 | 保留 | 文件在复制期间被修改或替换，已停止操作 | P1 |
| storage/TransferEngine.kt:302 | 冲突项不在当前批次 | 清晰度 | 这项文件冲突已失效，请重新选择文件。 | P1 |

### [storage/WorkspaceFileManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/WorkspaceFileManager.kt:35 | 工作区 | 保留 | 工作区 | P1 |
| storage/WorkspaceFileManager.kt:35 | 用户目录 | 保留 | 用户目录 | P1 |
| storage/WorkspaceFileManager.kt:36 | Ubuntu 文件 | 保留 | Ubuntu 文件 | P1 |
| storage/WorkspaceFileManager.kt:36 | 手机存储 | 保留 | 手机存储 | P1 |
| storage/WorkspaceFileManager.kt:46 | 请先授予手机存储写权限，再移动文件 | 清晰度 | 请先授权手机文件写入，再移动文件。 | P0 |
| storage/WorkspaceFileManager.kt:76 | 名称无效或属于内部存储条目 | 清晰度 | 名称无法使用或属于内部存储条目 | P1 |
| storage/WorkspaceFileManager.kt:89 | 不能修改位置根目录 | 保留 | 不能修改位置根目录 | P1 |
| storage/WorkspaceFileManager.kt:93 | 存储卷不可用，请检查存储权限或重新选择 | 保留 | 存储卷不可用，请检查存储权限或重新选择 | P0 |
| storage/WorkspaceFileManager.kt:95 | 存储卷不可用 | 清晰度 | 此存储位置不可用，请检查权限后重新选择。 | P1 |
| storage/WorkspaceFileManager.kt:99 | 不能通过链接访问目录 | 保留 | 不能通过链接访问目录 | P1 |
| storage/WorkspaceFileManager.kt:107 | 目录超出允许范围 | 清晰度 | 此文件夹不在可访问范围内，请选择其他位置。 | P0 |
| storage/WorkspaceFileManager.kt:111 | 目录尚未安装、已移除或被重定向 | 清晰度 | 此文件夹尚不可用，请检查组件安装状态。 | P0 |
| storage/WorkspaceFileManager.kt:118 | 不能通过链接访问目录 | 保留 | 不能通过链接访问目录 | P1 |
| storage/WorkspaceFileManager.kt:124 | 目录不存在或是符号链接 | 保留 | 目录不存在或是符号链接 | P1 |
| storage/WorkspaceFileManager.kt:128 | 同名条目已存在，未覆盖 | 保留 | 同名条目已存在，未覆盖 | P0 |
| storage/WorkspaceFileManager.kt:133 | 不通过手机存储中的符号链接读取文件 | 保留 | 不通过手机存储中的符号链接读取文件 | P1 |
| storage/WorkspaceFileManager.kt:134 | 不支持预览或复制普通符号链接 | 清晰度 | 此链接无法直接预览或复制，请选择原文件。 | P1 |
| storage/WorkspaceFileManager.kt:142 | 链接内容超出当前位置和允许的硬链接存储 | 清晰度 | 文件不在可访问范围内，请重新选择文件。 | P0 |
| storage/WorkspaceFileManager.kt:146 | 不支持此文件类型，或文件已不存在 | 保留 | 不支持此文件类型，或文件已不存在 | P1 |
| storage/WorkspaceFileManager.kt:149 | 此位置只读；请在工作区编辑项目文件 | 清晰度 | 此处只可查看，请复制到工作区后修改。 | P1 |
| storage/WorkspaceFileManager.kt:160 | 内部共享存储 | 保留 | 内部共享存储 | P1 |
| storage/WorkspaceFileManager.kt:198 | 文本不能超过 256 KiB | 保留 | 文本不能超过 256 KiB | P1 |
| storage/WorkspaceFileManager.kt:216 | 同名条目已存在，未覆盖 | 保留 | 同名条目已存在，未覆盖 | P0 |
| storage/WorkspaceFileManager.kt:229 | 图片无法解码 | 保留 | 图片无法解码 | P0 |
| storage/WorkspaceFileManager.kt:232 | 文本超过 256 KiB，无法预览 | 保留 | 文本超过 256 KiB，无法预览 | P0 |
| storage/WorkspaceFileManager.kt:242 | 文本超过 256 KiB，无法预览 | 保留 | 文本超过 256 KiB，无法预览 | P0 |
| storage/WorkspaceFileManager.kt:245 | 此文件不是可预览的文本或图片 | 清晰度 | 此文件无法预览，请用其他应用打开。 | P1 |
| storage/WorkspaceFileManager.kt:249 | 内置编辑只支持 UTF-8 文本；请使用查看器阅读，或使用其他应用打开。 | 清晰度 | 此文件可查看但无法直接编辑，请用其他应用修改。 | P1 |
| storage/WorkspaceFileManager.kt:277 | 此条目不是普通文件，无法预览 | 清晰度 | 此文件类型无法预览，请用其他应用打开。 | P0 |
| storage/WorkspaceFileManager.kt:298 | 自动 | 保留 | 自动 | P1 |
| storage/WorkspaceFileManager.kt:313 | 文件已变化，请重新打开 | 保留 | 文件已变化，请重新打开 | P1 |
| storage/WorkspaceFileManager.kt:316 | 文件已变化，请重新打开 | 保留 | 文件已变化，请重新打开 | P1 |
| storage/WorkspaceFileManager.kt:320 | 文件已变化，请重新打开 | 保留 | 文件已变化，请重新打开 | P1 |
| storage/WorkspaceFileManager.kt:329 | 文件打开缓存不可用 | 保留 | 文件打开缓存不可用 | P1 |
| storage/WorkspaceFileManager.kt:342 | 设备空间不足，无法准备此文件 | 清晰度 | 空间不足，无法打开文件。请清理空间后重试。 | P0 |
| storage/WorkspaceFileManager.kt:349 | 用户选择的文件 | 保留 | 用户选择的文件 | P1 |
| storage/WorkspaceFileManager.kt:365 | 不支持导出此文件类型 | 保留 | 不支持导出此文件类型 | P1 |
| storage/WorkspaceFileManager.kt:373 | 文件在读取期间发生变化，请稍后重试 | 保留 | 文件在读取期间发生变化，请稍后重试 | P1 |
| storage/WorkspaceFileManager.kt:378 | 文件在读取期间发生变化，请稍后重试 | 保留 | 文件在读取期间发生变化，请稍后重试 | P1 |
| storage/WorkspaceFileManager.kt:383 | 文件在读取期间发生变化，请稍后重试 | 保留 | 文件在读取期间发生变化，请稍后重试 | P1 |
| storage/WorkspaceFileManager.kt:400 | 链接或特殊文件仅可预览，请先复制为独立文件 | 保留 | 链接或特殊文件仅可预览，请先复制为独立文件 | P1 |
| storage/WorkspaceFileManager.kt:401 | 文件已变大，请重新打开 | 清晰度 | 文件已变大，请重新打开。 | P1 |
| storage/WorkspaceFileManager.kt:403 | 文件已被修改，未覆盖；请复制当前草稿并重新打开文件 | 清晰度 | 文件已被修改，本次未覆盖。请复制草稿后重新打开。 | P0 |
| storage/WorkspaceFileManager.kt:405 | 文本不能超过 256 KiB | 保留 | 文本不能超过 256 KiB | P1 |
| storage/WorkspaceFileManager.kt:420 | 不能移动到自身或子目录 | 保留 | 不能移动到自身或子目录 | P1 |
| storage/WorkspaceFileManager.kt:422 | 链接仅支持原目录重命名；跨目录请复制后删除原条目 | 清晰度 | 此链接只能在原文件夹重命名。跨文件夹请复制后再删除原条目。 | P0 |
| storage/WorkspaceFileManager.kt:430 | 不能复制到自身或子目录 | 保留 | 不能复制到自身或子目录 | P1 |
| storage/WorkspaceFileManager.kt:443 | 请选择手机卷内的文件或文件夹，不能操作整个存储卷 | 清晰度 | 请选择具体文件或文件夹，不能选择整个存储位置。 | P1 |
| storage/WorkspaceFileManager.kt:474 | 所选条目不可删除，请改用复制到工作区 | 清晰度 | 此条目无法删除，可复制到工作区。 | P0 |
| storage/WorkspaceFileManager.kt:482 | 目标目录已被替换，操作已取消 | 保留 | 目标目录已被替换，操作已取消 | P0 |
| storage/WorkspaceFileManager.kt:490 | 手机存储卷已变化，源文件未删除 | 保留 | 手机存储卷已变化，源文件未删除 | P0 |
| storage/WorkspaceFileManager.kt:495 | 设备剩余空间不足，操作已取消；未完成的源文件保留 | 保留 | 设备剩余空间不足，操作已取消；未完成的源文件保留 | P0 |
| storage/WorkspaceFileManager.kt:513 | 删除未完成，部分条目可能已删除；请刷新检查 | 保留 | 删除未完成，部分条目可能已删除；请刷新检查 | P0 |
| storage/WorkspaceFileManager.kt:520 | 目录包含内部硬链接存储或临时条目，不能操作 | 清晰度 | 文件夹含有受保护文件，无法操作。请查看详情。 | P1 |
| storage/WorkspaceFileManager.kt:521 | 目录包含链接，无法安全移动或重命名 | 清晰度 | 文件夹含有链接，无法直接移动或重命名。 | P0 |
| storage/WorkspaceFileManager.kt:530 | 目录层级过深 | 保留 | 目录层级过深 | P1 |
| storage/WorkspaceFileManager.kt:542 | 设备剩余空间不足，复制已取消 | 保留 | 设备剩余空间不足，复制已取消 | P0 |

### [storage/WorkspaceOpenFileProvider.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceOpenFileProvider.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/WorkspaceOpenFileProvider.kt:9 | 文件打开仅提供只读权限 | 保留 | 文件打开仅提供只读权限 | P0 |
| storage/WorkspaceOpenFileProvider.kt:10 | 无法打开所选文件 | 保留 | 无法打开所选文件 | P0 |

### [storage/WorkspaceStorage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceStorage.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/WorkspaceStorage.kt:33 | 请输入有效容量，最多保留六位小数 | 保留 | 请输入有效容量，最多保留六位小数 | P1 |
| storage/WorkspaceStorage.kt:36 | 容量数值过大 | 保留 | 容量数值过大 | P1 |
| storage/WorkspaceStorage.kt:37 | 容量预算至少为 64 MiB（0.0625 GiB） | 术语不一致 | 工作区限额至少为 64 MiB。 | P1 |
| storage/WorkspaceStorage.kt:50 | 容量预算至少为 64 MiB | 术语不一致 | 工作区限额至少为 64 MiB。 | P1 |
| storage/WorkspaceStorage.kt:51 | 容量预算不能小于当前已用空间；已有文件不会被删除 | 术语不一致 | 限额不能小于当前已用空间，请调高限额。 | P0 |
| storage/WorkspaceStorage.kt:52 | 容量预算超过设备当前可供项目使用的空间 | 术语不一致 | 空间限额超过设备当前可供项目使用的空间 | P1 |
| storage/WorkspaceStorage.kt:102 | 容量设置无法读取，请重新保存预算；现有项目已保留 | 术语不一致 | 无法读取容量设置，请重新设置工作区限额。 | P0 |
| storage/WorkspaceStorage.kt:108 | 项目目录不存在或已被重定向 | 错误出口 | 文件位置异常，已停止操作。请重新选择文件或查看问题详情。 | P1 |
| storage/WorkspaceStorage.kt:109 | 项目目录超出应用存储范围 | 保留 | 项目目录超出应用存储范围 | P1 |
| storage/WorkspaceStorage.kt:113 | 项目导入正在进行 | 保留 | 项目导入正在进行 | P1 |
| storage/WorkspaceStorage.kt:121 | 未完成导入记录无法读取；现有项目已保留 | 清晰度 | 无法读取上次导入记录，请重新打开应用后重试。 | P0 |
| storage/WorkspaceStorage.kt:124 | 未完成导入记录无效；现有项目已保留 | 清晰度 | 上次导入记录无法使用，请重新打开应用后重试。 | P1 |
| storage/WorkspaceStorage.kt:140 | 部分项目文件无法统计，当前用量不完整 | 保留 | 部分项目文件无法统计，当前用量不完整 | P0 |
| storage/WorkspaceStorage.kt:141 | 项目空间统计未完成 | 保留 | 项目空间统计未完成 | P1 |
| storage/WorkspaceStorage.kt:148 | 无法统计项目空间，请检查目录后重试 | 保留 | 无法统计项目空间，请检查目录后重试 | P0 |
| storage/WorkspaceStorage.kt:150 | 无法统计项目空间 | 保留 | 无法统计项目空间 | P0 |
| storage/WorkspaceStorage.kt:157 | 请等待当前项目导入完成 | 保留 | 请等待当前项目导入完成 | P1 |
| storage/WorkspaceStorage.kt:161 | ${sample.error ?: "请先完成项目空间统计"}；也可先选“跟随可用空间”解除自定预算 | 术语不一致 | 请先刷新工作区用量，或选择“使用可用空间”。 | P1 |
| storage/WorkspaceStorage.kt:161 | 请先完成项目空间统计 | 保留 | 请先完成项目空间统计 | P1 |
| storage/WorkspaceStorage.kt:185 | 设备剩余空间不足 64 MiB，请先清理下载缓存或设备存储 | 保留 | 设备剩余空间不足 64 MiB，请先清理下载缓存或设备存储 | P0 |
| storage/WorkspaceStorage.kt:187 | 项目空间已超出容量预算，请在设置中调高预算或改为跟随可用空间 | 术语不一致 | 工作区已超出限额，请在设置中调高限额或选择“使用可用空间”。 | P1 |
| storage/WorkspaceStorage.kt:188 | ${current.error ?: "项目空间统计不完整"}；可先改为“跟随可用空间”再启动 | 清晰度 | 用量尚未确认，请刷新，或选择“使用可用空间”后再启动。 | P1 |
| storage/WorkspaceStorage.kt:188 | 项目空间统计不完整 | 保留 | 项目空间统计不完整 | P1 |
| storage/WorkspaceStorage.kt:195 | 已有项目导入正在进行 | 保留 | 已有项目导入正在进行 | P1 |
| storage/WorkspaceStorage.kt:198 | ${sample.error ?: "项目空间统计不完整"}；请刷新，或先改为“跟随可用空间”再重试 | 清晰度 | 用量尚未确认，请刷新，或选择“使用可用空间”后重试。 | P1 |
| storage/WorkspaceStorage.kt:198 | 项目空间统计不完整 | 保留 | 项目空间统计不完整 | P1 |
| storage/WorkspaceStorage.kt:213 | 单次导入超过 512 MiB，请分批导入 | 保留 | 单次导入超过 512 MiB，请分批导入 | P1 |
| storage/WorkspaceStorage.kt:215 | 导入将超过项目容量预算，已保留原有项目 | 术语不一致 | 导入将超过项目空间限额，已保留原有项目 | P1 |
| storage/WorkspaceStorage.kt:216 | 设备剩余空间不足，已保留原有项目 | 保留 | 设备剩余空间不足，已保留原有项目 | P0 |
| storage/WorkspaceStorage.kt:225 | 无法确认导入后的空间占用 | 清晰度 | 无法确认导入后的用量，请重新统计后重试。 | P0 |
| storage/WorkspaceStorage.kt:226 | 导入后实际占用超过容量预算，已保留原有项目 | 术语不一致 | 导入后实际占用超过空间限额，已保留原有项目 | P1 |
| storage/WorkspaceStorage.kt:227 | 导入后设备剩余空间不足，已保留原有项目 | 保留 | 导入后设备剩余空间不足，已保留原有项目 | P0 |
| storage/WorkspaceStorage.kt:242 | 导入名称与大小数量不一致 | 清晰度 | 导入文件信息不完整，请重新选择文件。 | P1 |
| storage/WorkspaceStorage.kt:254 | 导入临时目录已存在 | 清晰度 | 上次导入尚未完成，请重新打开应用后重试。 | P1 |
| storage/WorkspaceStorage.kt:295 | 未完成的导入目录清理失败，下次操作前将重试 | 保留 | 未完成的导入目录清理失败，下次操作前将重试 | P0 |
| storage/WorkspaceStorage.kt:309 | 设备剩余空间不足 64 MiB | 保留 | 设备剩余空间不足 64 MiB | P0 |
| storage/WorkspaceStorage.kt:310 | 项目空间已超出容量预算 | 术语不一致 | 项目空间已超出空间限额 | P1 |
| storage/WorkspaceStorage.kt:313 | 项目空间连续统计失败，无法确认容量预算；可改为跟随可用空间后重试 | 术语不一致 | 工作区用量无法确认，请重新统计或选择“使用可用空间”。 | P0 |

### [storage/WorkspaceTransfer.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/WorkspaceTransfer.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/WorkspaceTransfer.kt:46 | 请先选择文件 | 保留 | 请先选择文件 | P1 |
| storage/WorkspaceTransfer.kt:47 | 正在统计所选文件 | 保留 | 正在统计所选文件 | P1 |
| storage/WorkspaceTransfer.kt:95 | 操作未完成，请检查文件 | 保留 | 操作未完成，请检查文件 | P1 |

### [storage/XlsxWorkbook.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/XlsxWorkbook.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/XlsxWorkbook.kt:117 | 文件不存在或不是普通文件 | 保留 | 文件不存在或不是普通文件 | P1 |
| storage/XlsxWorkbook.kt:118 | 此 XLSX 文件过大，已停止预览 | 保留 | 此 XLSX 文件过大，已停止预览 | P1 |
| storage/XlsxWorkbook.kt:124 | 此 XLSX 没有可显示的工作表 | 清晰度 | 这个文件没有工作表，请检查原文件。 | P0 |
| storage/XlsxWorkbook.kt:145 | 无法读取此 XLSX：文件已加密、损坏或不是有效的 Excel 工作簿 | 保留 | 无法读取此 XLSX：文件已加密、损坏或不是有效的 Excel 工作簿 | P0 |
| storage/XlsxWorkbook.kt:153 | 此 XLSX 压缩包条目过多，已停止预览 | 清晰度 | 表格内容过多，请用其他应用打开。 | P1 |
| storage/XlsxWorkbook.kt:155 | 此 XLSX 解压后过大，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/XlsxWorkbook.kt:168 | 此 XLSX 包含不安全的路径引用，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/XlsxWorkbook.kt:175 | 此 XLSX 包含空路径的工作表引用，无法预览 | 清晰度 | 表格文件不完整，请重新导出或用其他应用打开。 | P0 |
| storage/XlsxWorkbook.kt:179 | 此 XLSX 引用外部工作表，内置预览不支持 | 清晰度 | 此表格引用了外部内容，请用其他应用打开。 | P1 |
| storage/XlsxWorkbook.kt:186 | 此 XLSX 包含不安全的路径引用，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/XlsxWorkbook.kt:188 | 此 XLSX 包含不安全的路径引用，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/XlsxWorkbook.kt:193 | 此 XLSX 包含空路径的工作表引用，无法预览 | 清晰度 | 表格文件不完整，请重新导出或用其他应用打开。 | P0 |
| storage/XlsxWorkbook.kt:206 | 此 XLSX 缺少${label}，无法预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/XlsxWorkbook.kt:207 | 此 XLSX 的${label}解压后过大，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/XlsxWorkbook.kt:213 | 此 XLSX 的${label}格式不正确，无法预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/XlsxWorkbook.kt:218 | 此 XLSX 的${label}格式不正确，无法预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/XlsxWorkbook.kt:239 | 此 XLSX 的 XML 层级过深，已停止预览 | 清晰度 | 表格结构过于复杂，请用其他应用打开。 | P1 |
| storage/XlsxWorkbook.kt:242 | 此 XLSX 包含不受支持的文档类型声明，已停止预览 | 清晰度 | 此表格格式无法预览，请用其他应用打开。 | P1 |
| storage/XlsxWorkbook.kt:244 | 此 XLSX 包含无法解析的实体引用，格式不正确 | 清晰度 | 表格文件格式异常，请重新导出或用其他应用打开。 | P0 |
| storage/XlsxWorkbook.kt:267 | 共享字符串表 | 清晰度 | 表格文字 | P1 |
| storage/XlsxWorkbook.kt:277 | 此 XLSX 共享字符串数量超过内置预览上限，已停止预览 | 保留 | 此 XLSX 共享字符串数量超过内置预览上限，已停止预览 | P1 |
| storage/XlsxWorkbook.kt:295 | 此 XLSX 共享字符串总量超过内置预览上限，已停止预览 | 保留 | 此 XLSX 共享字符串总量超过内置预览上限，已停止预览 | P1 |
| storage/XlsxWorkbook.kt:365 | 样式表 | 保留 | 样式表 | P1 |
| storage/XlsxWorkbook.kt:536 | 此 XLSX 缺少工作簿定义（xl/workbook.xml），无法预览 | 清晰度 | 表格文件不完整，请重新导出或用其他应用打开。 | P0 |
| storage/XlsxWorkbook.kt:540 | 工作簿定义 | 清晰度 | 工作簿信息 | P1 |
| storage/XlsxWorkbook.kt:547 | 此 XLSX 工作表数量超过内置预览上限，已停止预览 | 保留 | 此 XLSX 工作表数量超过内置预览上限，已停止预览 | P1 |
| storage/XlsxWorkbook.kt:549 | 工作表${rawSheets.size + 1} | 清晰度 | 工作表{数量} | P1 |
| storage/XlsxWorkbook.kt:550 | 工作表${rawSheets.size + 1} | 清晰度 | 工作表{数量} | P1 |
| storage/XlsxWorkbook.kt:552 | 此 XLSX 缺少工作表“$name”的引用，无法预览 | 清晰度 | 工作表信息不完整，请重新导出或用其他应用打开。 | P0 |
| storage/XlsxWorkbook.kt:560 | 此 XLSX 没有可显示的工作表 | 清晰度 | 这个文件没有工作表，请检查原文件。 | P0 |
| storage/XlsxWorkbook.kt:564 | 此 XLSX 缺少工作表“$name”的定义，无法预览 | 清晰度 | 工作表内容缺失，请重新导出或用其他应用打开。 | P0 |
| storage/XlsxWorkbook.kt:566 | 此 XLSX 引用外部或不支持的工作表，内置预览不支持 | 清晰度 | 此表格引用了外部内容，请用其他应用打开。 | P1 |
| storage/XlsxWorkbook.kt:575 | 此 XLSX 缺少工作表关系定义（xl/_rels/workbook.xml.rels），无法预览 | 长度 | 工作表信息不完整，请重新导出或用其他应用打开。 | P0 |
| storage/XlsxWorkbook.kt:578 | 工作簿关系定义 | 清晰度 | 工作簿信息 | P1 |
| storage/XlsxWorkbook.kt:684 | 工作表“${reference.name}” | 清晰度 | 工作表“{名称}” | P1 |
| storage/XlsxWorkbook.kt:772 | 此 XLSX 的共享字符串索引无效，文件可能已损坏 | 清晰度 | 表格文字信息异常，请重新导出或用其他应用打开。 | P1 |
| storage/XlsxWorkbook.kt:774 | 此 XLSX 的共享字符串索引超出范围，文件可能已损坏 | 清晰度 | 表格文字信息异常，请重新导出或用其他应用打开。 | P1 |
| storage/XlsxWorkbook.kt:946 | 此 XLSX 的${label}解压后超过上限，已停止预览 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |

### [storage/docx/DocxDocumentReader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/docx/DocxDocumentReader.kt:27 | 文件不存在或不是普通文件。 | 保留 | 文件不存在或不是普通文件。 | P2 |
| storage/docx/DocxDocumentReader.kt:29 | 此 DOCX 文件过大（超过 ${limits.maxArchiveBytes / (1024 * 1024)} MiB），已停止预览。 | 清晰度 | 此 DOCX 文件过大（超过 {大小} MiB），已停止预览。 | P1 |
| storage/docx/DocxDocumentReader.kt:34 | 无法读取此 DOCX：文件已损坏、已加密或不是有效的 Word 文档。请用其他应用打开或重新导出。 | 长度 | 文档无法读取，请用其他应用打开或重新导出。 | P0 |
| storage/docx/DocxDocumentReader.kt:54 | 此 DOCX 缺少正文（word/document.xml），文件可能已损坏。 | 清晰度 | 文档缺少正文，请重新导出或用其他应用打开。 | P1 |
| storage/docx/DocxDocumentReader.kt:56 | 正文 | 保留 | 正文 | P2 |
| storage/docx/DocxDocumentReader.kt:63 | 文档内容超过内置预览上限，仅显示前面部分。 | 保留 | 文档内容超过内置预览上限，仅显示前面部分。 | P1 |
| storage/docx/DocxDocumentReader.kt:82 | 此 DOCX 压缩包条目过多（超过 ${limits.maxEntries} 个），已停止预览。 | 清晰度 | 文档内容过多，请用其他应用打开。 | P1 |
| storage/docx/DocxDocumentReader.kt:87 | 此 DOCX 包含不安全的路径条目（$checked），已拒绝预览。 | 清晰度 | 文档包含不安全的文件信息，已停止预览。请更换文件。 | P0 |
| storage/docx/DocxDocumentReader.kt:90 | 此 DOCX 包含重复的压缩包条目，无法安全预览。 | 清晰度 | 文档含有重复文件，已停止预览。请重新导出文件。 | P0 |
| storage/docx/DocxDocumentReader.kt:95 | 此 DOCX 的单个条目解压后超过上限，已停止预览。 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/docx/DocxDocumentReader.kt:100 | 此 DOCX 解压后总大小超过上限，已停止预览。 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/docx/DocxDocumentReader.kt:110 | 文档包含宏（vbaProject.bin），内置预览只读且不会执行宏。 | 清晰度 | 文档中的宏不会运行。请用其他应用查看完整内容。 | P2 |
| storage/docx/DocxDocumentReader.kt:113 | 页眉和页脚不会显示。 | 保留 | 页眉和页脚不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:116 | 脚注和尾注不会显示。 | 保留 | 脚注和尾注不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:119 | 批注不会显示。 | 保留 | 批注不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:122 | 嵌入对象不会显示。 | 保留 | 嵌入对象不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:129 | 关系表 | 保留 | 关系表 | P2 |
| storage/docx/DocxDocumentReader.kt:145 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:159 | 文档中有关系目标路径不安全或无效，已忽略。 | 清晰度 | 部分内容无法读取，已跳过。请用其他应用查看完整内容。 | P1 |
| storage/docx/DocxDocumentReader.kt:163 | 文档中有图片位于不支持的位置，已跳过。 | 清晰度 | 部分图片无法显示，请用其他应用查看完整内容。 | P2 |
| storage/docx/DocxDocumentReader.kt:172 | 样式表 | 保留 | 样式表 | P2 |
| storage/docx/DocxDocumentReader.kt:176 | 样式表超过内置预览上限，文字按默认样式显示。 | 清晰度 | 文档样式过多，已使用默认样式显示。 | P1 |
| storage/docx/DocxDocumentReader.kt:184 | 编号表 | 保留 | 编号表 | P2 |
| storage/docx/DocxDocumentReader.kt:188 | 编号表超过内置预览上限，列表按普通段落显示。 | 清晰度 | 列表样式过多，已按普通段落显示。 | P1 |
| storage/docx/DocxDocumentReader.kt:199 | 文档图片数量超过内置预览上限，部分图片不会显示。 | 保留 | 文档图片数量超过内置预览上限，部分图片不会显示。 | P1 |
| storage/docx/DocxDocumentReader.kt:204 | 文档引用的图片在压缩包中缺失，已跳过。 | 清晰度 | 部分图片缺失，已跳过。请用其他应用检查原文件。 | P2 |
| storage/docx/DocxDocumentReader.kt:208 | 文档中有图片超过单张大小上限，已跳过。 | 保留 | 文档中有图片超过单张大小上限，已跳过。 | P1 |
| storage/docx/DocxDocumentReader.kt:213 | 单张图片解压后超过上限 | 清晰度 | 图片内容过大，无法显示。 | P1 |
| storage/docx/DocxDocumentReader.kt:216 | 文档中有图片读取失败或超过上限，已跳过。 | 保留 | 文档中有图片读取失败或超过上限，已跳过。 | P0 |
| storage/docx/DocxDocumentReader.kt:221 | 文档图片总量超过内置预览上限，部分图片不会显示。 | 保留 | 文档图片总量超过内置预览上限，部分图片不会显示。 | P1 |
| storage/docx/DocxDocumentReader.kt:245 | 此 DOCX 的${label}解压后超过上限，已停止预览。 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/docx/DocxDocumentReader.kt:249 | 此 DOCX 的${label}无法解析，文件可能已损坏。 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/docx/DocxDocumentReader.kt:257 | 此 DOCX 的${label}无法解析，文件可能已损坏。 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P0 |
| storage/docx/DocxDocumentReader.kt:266 | 此 DOCX 的${label}格式不正确，文件可能已损坏。 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/docx/DocxDocumentReader.kt:268 | 此 DOCX 的${label}压缩数据校验失败，文件可能已损坏。 | 清晰度 | 文档无法读取，请重新导出或用其他应用打开。 | P0 |
| storage/docx/DocxDocumentReader.kt:271 | 无法读取此 DOCX 的${label}，文件可能已损坏。 | 清晰度 | 无法读取此 DOCX 的{名称}，文件可能已损坏。 | P0 |
| storage/docx/DocxDocumentReader.kt:303 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:308 | 此 DOCX 缺少正文内容，文件可能已损坏。 | 错误出口 | 文件无法完整预览，请用其他应用打开或重新导出。 | P1 |
| storage/docx/DocxDocumentReader.kt:320 | 文档中的嵌入内容块不会显示。 | 保留 | 文档中的嵌入内容块不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:330 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:348 | 段落内的表格按独立表格显示。 | 清晰度 | 段落内的表格已单独显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:357 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:398 | 复杂域或修订内容按结果文字近似显示。 | 清晰度 | 部分格式已简化，请用其他应用查看原始排版。 | P2 |
| storage/docx/DocxDocumentReader.kt:430 | 嵌入对象不会显示。 | 保留 | 嵌入对象不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:444 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:471 | 此 DOCX 正文包含无法解析的实体引用，格式不正确。 | 清晰度 | 文档格式异常，请重新导出或用其他应用打开。 | P0 |
| storage/docx/DocxDocumentReader.kt:475 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:518 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:522 | 文本框内容不会显示。 | 保留 | 文本框内容不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:524 | 文档中有无法识别的图形对象，已跳过。 | 清晰度 | 部分图形无法显示，请用其他应用查看完整内容。 | P0 |
| storage/docx/DocxDocumentReader.kt:529 | 外部图片或缺失的图片关系不会加载。 | 清晰度 | 外部图片或缺失图片无法显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:534 | 文档引用的图片在压缩包中缺失，已跳过。 | 清晰度 | 部分图片缺失，已跳过。请用其他应用检查原文件。 | P2 |
| storage/docx/DocxDocumentReader.kt:542 | 嵌套层级较深的表格仅部分显示。 | 清晰度 | 嵌套表格只显示部分内容，请用其他应用查看完整表格。 | P2 |
| storage/docx/DocxDocumentReader.kt:578 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:599 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:620 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:657 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:694 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:725 | 多栏排版按单栏显示。 | 保留 | 多栏排版按单栏显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:729 | 页眉和页脚不会显示。 | 保留 | 页眉和页脚不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:733 | 脚注和尾注不会显示。 | 保留 | 脚注和尾注不会显示。 | P2 |
| storage/docx/DocxDocumentReader.kt:744 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxDocumentReader.kt:758 | 页面尺寸异常，已按可用范围调整。 | 保留 | 页面尺寸异常，已按可用范围调整。 | P2 |
| storage/docx/DocxDocumentReader.kt:769 | 列表编号定义缺失，编号按普通段落显示。 | 清晰度 | 列表编号信息缺失，已按普通段落显示。 | P2 |

### [storage/docx/DocxNumbering.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxNumbering.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/docx/DocxNumbering.kt:152 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxNumbering.kt:182 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxNumbering.kt:205 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxNumbering.kt:215 | %${level + 1}. | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/docx/DocxNumbering.kt:248 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |

### [storage/docx/DocxStyles.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxStyles.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/docx/DocxStyles.kt:197 | (?:heading&#124;标题)\\s*([1-9]) | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| storage/docx/DocxStyles.kt:235 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxStyles.kt:265 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxStyles.kt:300 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxStyles.kt:353 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |
| storage/docx/DocxStyles.kt:418 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |

### [storage/docx/DocxXml.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/storage/docx/DocxXml.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| storage/docx/DocxXml.kt:25 | 此 DOCX 结构过于复杂（元素过多），已停止预览。 | 清晰度 | 文档结构过于复杂，请用其他应用打开。 | P2 |
| storage/docx/DocxXml.kt:29 | 此 DOCX 的 XML 嵌套过深，已停止预览。 | 清晰度 | 文档结构过于复杂，请用其他应用打开。 | P2 |
| storage/docx/DocxXml.kt:172 | 此 DOCX 包含不受支持的文档类型声明，已停止预览。 | 清晰度 | 此文档格式无法预览，请用其他应用打开。 | P2 |

### [ui/AgentBrowserPage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/AgentBrowserPage.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/AgentBrowserPage.kt:99 | 收藏夹 | 保留 | 收藏夹 | P1 |
| ui/AgentBrowserPage.kt:112 | 关于浏览器 | 保留 | 关于浏览器 | P1 |
| ui/AgentBrowserPage.kt:112 | 数据管理 | 保留 | 数据管理 | P1 |
| ui/AgentBrowserPage.kt:112 | 权限与后台 | 保留 | 权限与后台 | P0 |
| ui/AgentBrowserPage.kt:112 | 浏览器设置 | 保留 | 浏览器设置 | P1 |
| ui/AgentBrowserPage.kt:112 | 通用 | 保留 | 通用 | P1 |
| ui/AgentBrowserPage.kt:117 | 打开下载文件 | 保留 | 打开下载文件 | P1 |
| ui/AgentBrowserPage.kt:118 | 没有可打开此文件的应用，或文件已移除；可选择导出 | 保留 | 没有可打开此文件的应用，或文件已移除；可选择导出 | P0 |
| ui/AgentBrowserPage.kt:147 | 浏览器 | 保留 | 浏览器 | P1 |
| ui/AgentBrowserPage.kt:149 | 网址输入框 | 保留 | 网址输入框 | P1 |
| ui/AgentBrowserPage.kt:165 | 搜索或输入网址 | 清晰度 | 输入网址 | P1 |
| ui/AgentBrowserPage.kt:170 | 前往 | 清晰度 | 打开网址 | P1 |
| ui/AgentBrowserPage.kt:176 | 设置 | 保留 | 设置 | P1 |
| ui/AgentBrowserPage.kt:180 | 后退 | 保留 | 后退 | P1 |
| ui/AgentBrowserPage.kt:181 | 前进 | 保留 | 前进 | P1 |
| ui/AgentBrowserPage.kt:182 | 刷新网页 | 保留 | 刷新网页 | P1 |
| ui/AgentBrowserPage.kt:184 | 页面总览 | 保留 | 页面总览 | P1 |
| ui/AgentBrowserPage.kt:185 | ${state.tabs.size} 个页面 | 清晰度 | {数量} 个页面 | P1 |
| ui/AgentBrowserPage.kt:185 | 页面总览 | 保留 | 页面总览 | P1 |
| ui/AgentBrowserPage.kt:194 | 浏览器进入全屏 | 清晰度 | 全屏浏览 | P1 |
| ui/AgentBrowserPage.kt:195 | 浏览器菜单 | 保留 | 浏览器菜单 | P1 |
| ui/AgentBrowserPage.kt:197 | 登录授权页 · AI 不可查看或操作 | 清晰度 | 登录授权页，助手无法查看或操作。 | P0 |
| ui/AgentBrowserPage.kt:202 | AI 可操作 · 点网页可临时接管 | 保留 | AI 可操作 · 点网页可临时接管 | P1 |
| ui/AgentBrowserPage.kt:202 | AI 可查看，操作已暂停 | 保留 | AI 可查看，操作已暂停 | P1 |
| ui/AgentBrowserPage.kt:202 | 登录授权页 · AI 不可查看或操作 | 清晰度 | 登录授权页，助手无法查看或操作。 | P0 |
| ui/AgentBrowserPage.kt:203 | AI 可查看 · 操作权已收回 | 保留 | AI 可查看 · 操作权已收回 | P1 |
| ui/AgentBrowserPage.kt:203 | 你在操作 · ${it}秒未操作后交回 AI | 清晰度 | 你在操作 · {内容}秒未操作后交回 AI | P1 |
| ui/AgentBrowserPage.kt:206 | 交回 AI | 保留 | 交回 AI | P1 |
| ui/AgentBrowserPage.kt:206 | 撤销 AI 操作权限 | 清晰度 | 收回操作权 | P0 |
| ui/AgentBrowserPage.kt:211 | 页内查找输入框 | 保留 | 页内查找输入框 | P1 |
| ui/AgentBrowserPage.kt:212 | ${state.findIndex} / ${state.findCount} | 清晰度 | {状态} / {数量} | P1 |
| ui/AgentBrowserPage.kt:212 | 页内查找 | 保留 | 页内查找 | P1 |
| ui/AgentBrowserPage.kt:213 | 上一个匹配 | 保留 | 上一个匹配 | P1 |
| ui/AgentBrowserPage.kt:214 | 下一个匹配 | 保留 | 下一个匹配 | P1 |
| ui/AgentBrowserPage.kt:215 | 关闭查找 | 保留 | 关闭查找 | P1 |
| ui/AgentBrowserPage.kt:221 | 关闭提示 | 保留 | 关闭提示 | P1 |
| ui/AgentBrowserPage.kt:228 | 网页正在悬浮窗中显示 | 保留 | 网页正在悬浮窗中显示 | P1 |
| ui/AgentBrowserPage.kt:229 | 可显示在其他应用上方，拖动左下角或右下角调整大小。 | 保留 | 可显示在其他应用上方，拖动左下角或右下角调整大小。 | P1 |
| ui/AgentBrowserPage.kt:230 | 收回到完整浏览器 | 清晰度 | 返回浏览器 | P1 |
| ui/AgentBrowserPage.kt:256 | 开始浏览 | 保留 | 开始浏览 | P1 |
| ui/AgentBrowserPage.kt:257 | 退出全屏后输入网址 | 清晰度 | 输入网址 | P1 |
| ui/AgentBrowserPage.kt:258 | 搜索或输入网址 | 清晰度 | 输入网址 | P1 |
| ui/AgentBrowserPage.kt:259 | ${BrowserSearchEngine.from(library.searchEngine).title} · 收藏、历史和下载在顶部菜单 | 清晰度 | 收藏、历史和下载都在顶部菜单。 | P1 |
| ui/AgentBrowserPage.kt:267 | 浏览器退出全屏 | 清晰度 | 退出全屏 | P1 |
| ui/AgentBrowserPage.kt:272 | 设置 | 保留 | 设置 | P1 |
| ui/AgentBrowserPage.kt:273 | 浏览器退出全屏 | 清晰度 | 退出全屏 | P1 |
| ui/AgentBrowserPage.kt:274 | 新建页面 | 保留 | 新建页面 | P1 |
| ui/AgentBrowserPage.kt:274 | 页面总览 | 保留 | 页面总览 | P1 |
| ui/AgentBrowserPage.kt:275 | 历史记录 | 保留 | 历史记录 | P1 |
| ui/AgentBrowserPage.kt:275 | 清空历史记录 | 保留 | 清空历史记录 | P1 |
| ui/AgentBrowserPage.kt:275 | 清空记录 | 保留 | 清空记录 | P1 |
| ui/AgentBrowserPage.kt:278 | 页面总览 | 保留 | 页面总览 | P1 |
| ui/AgentBrowserPage.kt:279 | ${state.tabs.size} 个页面 | 清晰度 | {数量} 个页面 | P1 |
| ui/AgentBrowserPage.kt:288 | 页面预览 | 保留 | 页面预览 | P1 |
| ui/AgentBrowserPage.kt:289 | 新页面 | 保留 | 新页面 | P1 |
| ui/AgentBrowserPage.kt:290 | 尚未打开网址 | 保留 | 尚未打开网址 | P1 |
| ui/AgentBrowserPage.kt:292 | 关闭此页 | 保留 | 关闭此页 | P1 |
| ui/AgentBrowserPage.kt:297 | 浏览器设置 | 保留 | 浏览器设置 | P1 |
| ui/AgentBrowserPage.kt:298 | 搜索引擎与页面显示 | 术语不一致 | 搜索与网页显示 | P1 |
| ui/AgentBrowserPage.kt:298 | 通用 | 保留 | 通用 | P1 |
| ui/AgentBrowserPage.kt:298 | 通用 | 保留 | 通用 | P1 |
| ui/AgentBrowserPage.kt:299 | AI 接管、收回权限和后台运行 | 清晰度 | 助手操作权限与后台运行 | P0 |
| ui/AgentBrowserPage.kt:299 | 权限与后台 | 保留 | 权限与后台 | P0 |
| ui/AgentBrowserPage.kt:299 | 权限与后台 | 保留 | 权限与后台 | P0 |
| ui/AgentBrowserPage.kt:300 | 历史、收藏与下载文件 | 保留 | 历史、收藏与下载文件 | P1 |
| ui/AgentBrowserPage.kt:300 | 数据管理 | 保留 | 数据管理 | P1 |
| ui/AgentBrowserPage.kt:300 | 数据管理 | 保留 | 数据管理 | P1 |
| ui/AgentBrowserPage.kt:301 | 关于浏览器 | 保留 | 关于浏览器 | P1 |
| ui/AgentBrowserPage.kt:301 | 关于浏览器 | 保留 | 关于浏览器 | P1 |
| ui/AgentBrowserPage.kt:301 | 内核与数据隔离说明 | 清晰度 | 浏览器信息 | P1 |
| ui/AgentBrowserPage.kt:303 | 通用 | 保留 | 通用 | P1 |
| ui/AgentBrowserPage.kt:304 | 搜索引擎 | 术语不一致 | 搜索引擎 | P1 |
| ui/AgentBrowserPage.kt:304 | 搜索引擎 | 术语不一致 | 搜索引擎 | P1 |
| ui/AgentBrowserPage.kt:305 | 当前页面显示模式 | 保留 | 当前页面显示模式 | P1 |
| ui/AgentBrowserPage.kt:305 | 页面显示模式 | 保留 | 页面显示模式 | P1 |
| ui/AgentBrowserPage.kt:308 | 避让网页底部控件 | 清晰度 | 避让底部内容 | P1 |
| ui/AgentBrowserPage.kt:309 | 开启后预留底部空间，避免网页自己的固定输入框或按钮被导航遮挡 | 清晰度 | 开启后留出底部空间，避免遮住网页按钮或输入框。 | P1 |
| ui/AgentBrowserPage.kt:313 | 数据管理 | 保留 | 数据管理 | P1 |
| ui/AgentBrowserPage.kt:314 | ${library.history.size} 条记录 | 清晰度 | {数量} 条记录 | P1 |
| ui/AgentBrowserPage.kt:314 | 历史记录 | 保留 | 历史记录 | P1 |
| ui/AgentBrowserPage.kt:314 | 历史记录 | 保留 | 历史记录 | P1 |
| ui/AgentBrowserPage.kt:315 | ${library.bookmarks.size} 个收藏 · 按文件夹分类 | 清晰度 | {数量} 个收藏 · 按文件夹分类 | P1 |
| ui/AgentBrowserPage.kt:315 | 收藏夹 | 保留 | 收藏夹 | P1 |
| ui/AgentBrowserPage.kt:315 | 收藏夹 | 保留 | 收藏夹 | P1 |
| ui/AgentBrowserPage.kt:316 | ${library.downloads.size} 个文件 · 查看、导出或删除 | 清晰度 | {数量} 个文件 · 查看、导出或删除 | P0 |
| ui/AgentBrowserPage.kt:316 | 下载与截图 | 保留 | 下载与截图 | P1 |
| ui/AgentBrowserPage.kt:316 | 下载与截图 | 保留 | 下载与截图 | P1 |
| ui/AgentBrowserPage.kt:317 | 不会删除收藏或下载 | 保留 | 不会删除收藏或下载 | P0 |
| ui/AgentBrowserPage.kt:317 | 清空历史记录 | 保留 | 清空历史记录 | P1 |
| ui/AgentBrowserPage.kt:317 | 清空历史记录 | 保留 | 清空历史记录 | P1 |
| ui/AgentBrowserPage.kt:319 | 权限与后台 | 保留 | 权限与后台 | P0 |
| ui/AgentBrowserPage.kt:320 | AI 可以操作浏览器 | 保留 | AI 可以操作浏览器 | P1 |
| ui/AgentBrowserPage.kt:320 | 浏览器由你控制 | 保留 | 浏览器由你控制 | P1 |
| ui/AgentBrowserPage.kt:321 | 当前工具连接可查看普通网页，默认允许操作；登录授权临时页始终不向 AI 开放。触碰网页先暂停 AI；临时接管后，30 秒没有点击、滑动或输入会交回 AI。切页与后台不会另外撤销权限。 | 长度 | 助手可查看普通网页。触碰网页时会先暂停操作。<br>临时接管后，30 秒无操作会交回助手。<br>登录授权页始终仅你可见。 | P0 |
| ui/AgentBrowserPage.kt:322 | 交回 AI | 保留 | 交回 AI | P1 |
| ui/AgentBrowserPage.kt:322 | 撤销 AI 操作权限 | 清晰度 | 收回操作权 | P0 |
| ui/AgentBrowserPage.kt:323 | 显式撤销会记住你的选择，不因空闲、切页或重启自动交回。提交、付款、发布等业务操作仍需另外确认。 | 长度 | 收回操作权后，只有你手动交回才会恢复。<br>提交、付款或发布前仍需另行确认。 | P1 |
| ui/AgentBrowserPage.kt:325 | 后台运行已停止 | 保留 | 后台运行已停止 | P1 |
| ui/AgentBrowserPage.kt:325 | 后台运行已开启 | 保留 | 后台运行已开启 | P1 |
| ui/AgentBrowserPage.kt:326 | 后台通知可收回 AI 操作权或停止浏览器及下载，不会停止 DSH。Android 仍可能限制后台网络或回收进程。 | 术语不一致、长度 | 可在通知中收回操作权或停止浏览器和下载。<br>助手继续运行；后台活动可能受系统限制。 | P1 |
| ui/AgentBrowserPage.kt:329 | 系统悬浮窗 | 保留 | 系统悬浮窗 | P1 |
| ui/AgentBrowserPage.kt:330 | 显示在其他应用上方；拖动顶部移动，拖动左下或右下角改变大小。首次使用需要系统悬浮窗授权。AI 始终读取完整文档，不受小窗裁切；敏感系统页面可能隐藏悬浮窗。 | 长度 | 浏览器可显示在其他应用上方。<br>拖动顶部移动小窗，拖动底角调整大小。<br>首次使用请授权悬浮窗；部分系统页面会隐藏小窗。 | P0 |
| ui/AgentBrowserPage.kt:331 | 关闭系统悬浮窗 | 清晰度 | 关闭悬浮窗 | P1 |
| ui/AgentBrowserPage.kt:331 | 打开系统悬浮窗 | 清晰度 | 打开悬浮窗 | P1 |
| ui/AgentBrowserPage.kt:333 | 暂无操作记录 | 保留 | 暂无操作记录 | P0 |
| ui/AgentBrowserPage.kt:335 | 关于浏览器 | 保留 | 关于浏览器 | P1 |
| ui/AgentBrowserPage.kt:336 | 独立 Android WebView | 工程痕迹 | 网页组件 | P1 |
| ui/AgentBrowserPage.kt:337 | 浏览器与 DSH 管理页隔离 Cookie 和网站存储。参考 外部参考名（省略） 的交互组织，不是 外部参考名（省略） 内核，也不会读取 外部参考名（省略） 的数据。 | 工程痕迹、术语不一致、长度 | 浏览器与助手页面的登录信息和网站数据分别保存。 | P1 |
| ui/AgentBrowserPage.kt:338 | 页面显示模式修改 User-Agent；切换为 PC 或 Mac 不会更换浏览器内核。文件上传、相机、麦克风和定位默认不开放。 | 工程痕迹、长度 | 切换页面模式会重新加载网页。<br>网页中的文件上传、相机、麦克风和定位默认关闭。 | P1 |
| ui/AgentBrowserPage.kt:339 | 连接权限不等于经过认证的单个模型或会话身份。只有持有本机连接能力的工具可以请求操作。 | 术语不一致、长度 | 浏览器权限授予当前工具连接，无法区分连接内的不同助手。 | P0 |
| ui/AgentBrowserPage.kt:342 | 筛选$screen | 清晰度 | 筛选{内容} | P1 |
| ui/AgentBrowserPage.kt:345 | 历史记录 | 保留 | 历史记录 | P1 |
| ui/AgentBrowserPage.kt:347 | 还没有匹配的历史记录。 | 清晰度 | 没有匹配记录，试试其他关键词或清除搜索。 | P0 |
| ui/AgentBrowserPage.kt:352 | 收藏夹 | 保留 | 收藏夹 | P1 |
| ui/AgentBrowserPage.kt:354 | 还没有匹配的收藏。打开网页后，选择菜单里的「收藏当前页」。 | 清晰度 | 还没有收藏。打开网页后，选择“收藏当前页”。 | P0 |
| ui/AgentBrowserPage.kt:359 | 新页面打开 | 保留 | 新页面打开 | P1 |
| ui/AgentBrowserPage.kt:359 | 移除收藏 | 保留 | 移除收藏 | P1 |
| ui/AgentBrowserPage.kt:365 | 下载和网页截图保存在这里。卸载应用前请导出需要保留的内容。 | 清晰度 | 下载文件和网页截图会显示在这里。<br>卸载应用前，请先导出要保留的文件。 | P1 |
| ui/AgentBrowserPage.kt:368 | ${record.status} · ${browserFileSize(record.received)} | 清晰度 | {状态} · {大小} | P1 |
| ui/AgentBrowserPage.kt:369 | 下载中 | 保留 | 下载中 | P1 |
| ui/AgentBrowserPage.kt:369 | 等待中 | 保留 | 等待中 | P1 |
| ui/AgentBrowserPage.kt:375 | 导出 | 保留 | 导出 | P1 |
| ui/AgentBrowserPage.kt:375 | 已完成 | 保留 | 已完成 | P1 |
| ui/AgentBrowserPage.kt:375 | 打开 | 保留 | 打开 | P1 |
| ui/AgentBrowserPage.kt:376 | 下载中 | 保留 | 下载中 | P1 |
| ui/AgentBrowserPage.kt:376 | 取消下载 | 保留 | 取消下载 | P0 |
| ui/AgentBrowserPage.kt:376 | 等待中 | 保留 | 等待中 | P1 |
| ui/AgentBrowserPage.kt:378 | 已完成 | 保留 | 已完成 | P1 |
| ui/AgentBrowserPage.kt:378 | 重新下载 | 保留 | 重新下载 | P1 |
| ui/AgentBrowserPage.kt:379 | 删除 | 保留 | 删除 | P0 |
| ui/AgentBrowserPage.kt:379 | 删除下载:${record.id} | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/AgentBrowserPage.kt:393 | 工具箱 | 保留 | 工具箱 | P1 |
| ui/AgentBrowserPage.kt:394 | 页内查找 | 保留 | 页内查找 | P1 |
| ui/AgentBrowserPage.kt:395 | 网页缩放 | 保留 | 网页缩放 | P1 |
| ui/AgentBrowserPage.kt:396 | 页面显示模式 | 保留 | 页面显示模式 | P1 |
| ui/AgentBrowserPage.kt:397 | 下载与截图 | 保留 | 下载与截图 | P1 |
| ui/AgentBrowserPage.kt:397 | 截取网页画面 | 清晰度 | 整页截图 | P1 |
| ui/AgentBrowserPage.kt:398 | 收藏夹 | 保留 | 收藏夹 | P1 |
| ui/AgentBrowserPage.kt:398 | 收藏当前页 | 保留 | 收藏当前页 | P1 |
| ui/AgentBrowserPage.kt:402 | 收回 AI 浏览器操作权限？ | 清晰度 | 临时接管浏览器？ | P0 |
| ui/AgentBrowserPage.kt:404 | AI 操作已暂停，这次点击尚未执行。临时接管期间 AI 可查看网页，不能点击、输入或导航；30 秒未操作会自动交回操作权。 | 长度 | 助手操作已暂停，本次点击还未执行。<br>临时接管时助手仍可查看，30 秒无操作后自动交回操作权。 | P1 |
| ui/AgentBrowserPage.kt:405 | 撤销 AI 操作权限 | 清晰度 | 收回操作权 | P0 |
| ui/AgentBrowserPage.kt:407 | 临时接管 | 保留 | 临时接管 | P1 |
| ui/AgentBrowserPage.kt:408 | 继续交给 AI | 清晰度 | 交给助手 | P1 |
| ui/AgentBrowserPage.kt:410 | ${request.name}\n${request.url.take(160)}\n\n保存到应用私有下载目录，可查看、导出或删除。单文件最多 256 MiB。 | 长度 | 文件将保存在“下载与截图”，可导出或删除。<br>单个文件最多 256 MiB。文件名和地址另行显示。 | P0 |
| ui/AgentBrowserPage.kt:410 | 下载此文件？ | 保留 | 下载此文件？ | P1 |
| ui/AgentBrowserPage.kt:411 | 下载与截图 | 保留 | 下载与截图 | P1 |
| ui/AgentBrowserPage.kt:411 | 确认下载 | 保留 | 确认下载 | P1 |
| ui/AgentBrowserPage.kt:412 | 取消 | 保留 | 取消 | P0 |
| ui/AgentBrowserPage.kt:413 | 删除下载: | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/AgentBrowserPage.kt:413 | 删除下载文件？ | 保留 | 删除下载文件？ | P0 |
| ui/AgentBrowserPage.kt:415 | 收藏当前页 | 保留 | 收藏当前页 | P1 |
| ui/AgentBrowserPage.kt:416 | 收藏名称 | 保留 | 收藏名称 | P1 |
| ui/AgentBrowserPage.kt:417 | 收藏文件夹 | 保留 | 收藏文件夹 | P1 |
| ui/AgentBrowserPage.kt:417 | 输入新名称即可建立收藏分类 | 保留 | 输入新名称即可建立收藏分类 | P1 |
| ui/AgentBrowserPage.kt:419 | 搜索引擎 | 术语不一致 | 搜索引擎 | P1 |
| ui/AgentBrowserPage.kt:421 | 搜索词只在你点「前往」后发送给所选引擎。 | 术语不一致 | 点“前往”后，搜索词才会发送给所选搜索服务。 | P1 |
| ui/AgentBrowserPage.kt:422 | 页面显示模式 | 保留 | 页面显示模式 | P1 |
| ui/AgentBrowserPage.kt:426 | 修改当前页面的 User-Agent 并重新加载。未提交的输入可能丢失；网站也可能继续显示响应式布局。 | 工程痕迹、长度 | 切换后会重新加载网页，请先保存尚未提交的内容。 | P1 |
| ui/AgentBrowserPage.kt:428 | 网页缩放 | 保留 | 网页缩放 | P1 |
| ui/AgentBrowserPage.kt:429 | ${state.zoomPercent}% · 也可双指缩放网页 | 清晰度 | {进度}% · 也可双指缩放网页 | P1 |
| ui/AgentBrowserPage.kt:430 | 恢复 100% | 清晰度 | 重置缩放 | P1 |
| ui/AgentBrowserPage.kt:430 | 放大网页 | 保留 | 放大网页 | P1 |
| ui/AgentBrowserPage.kt:430 | 缩小网页 | 保留 | 缩小网页 | P1 |
| ui/AgentBrowserPage.kt:431 | 适合宽度 | 保留 | 适合宽度 | P1 |
| ui/AgentBrowserPage.kt:432 | 缩放范围可能受网站限制，以网页实际显示为准。 | 清晰度 | 部分网页会限制缩放。 | P1 |
| ui/AgentBrowserPage.kt:434 | 删除历史记录与页面前进/后退历史，不删除收藏、下载或网站登录状态。 | 清晰度 | 清空浏览历史，不删除收藏、下载和网站登录信息。 | P0 |
| ui/AgentBrowserPage.kt:434 | 清空历史记录 | 保留 | 清空历史记录 | P1 |
| ui/AgentBrowserPage.kt:435 | 同时删除应用内文件与下载记录。已导出到其他位置的副本不受影响。 | 清晰度 | 将删除应用内文件和下载记录，已导出的副本保留。 | P0 |
| ui/AgentBrowserPage.kt:439 | 收藏当前页 | 保留 | 收藏当前页 | P1 |
| ui/AgentBrowserPage.kt:440 | 页面显示模式 | 保留 | 页面显示模式 | P1 |
| ui/AgentBrowserPage.kt:441 | 清空历史记录 | 保留 | 清空历史记录 | P1 |
| ui/AgentBrowserPage.kt:442 | 删除下载: | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/AgentBrowserPage.kt:442 | 请先取消下载，再删除文件 | 保留 | 请先取消下载，再删除文件 | P0 |
| ui/AgentBrowserPage.kt:445 | 保存收藏 | 保留 | 保存收藏 | P1 |
| ui/AgentBrowserPage.kt:445 | 删除下载: | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/AgentBrowserPage.kt:445 | 完成 | 保留 | 完成 | P1 |
| ui/AgentBrowserPage.kt:445 | 应用并重新加载 | 清晰度 | 应用并刷新 | P1 |
| ui/AgentBrowserPage.kt:445 | 收藏当前页 | 保留 | 收藏当前页 | P1 |
| ui/AgentBrowserPage.kt:445 | 清空历史记录 | 保留 | 清空历史记录 | P1 |
| ui/AgentBrowserPage.kt:445 | 确认删除 | 清晰度 | 删除文件 | P0 |
| ui/AgentBrowserPage.kt:445 | 确认清空 | 清晰度 | 清空历史 | P1 |
| ui/AgentBrowserPage.kt:445 | 页面显示模式 | 保留 | 页面显示模式 | P1 |
| ui/AgentBrowserPage.kt:446 | 取消 | 保留 | 取消 | P0 |
| ui/AgentBrowserPage.kt:446 | 搜索引擎 | 术语不一致 | 搜索引擎 | P1 |
| ui/AgentBrowserPage.kt:446 | 网页缩放 | 保留 | 网页缩放 | P1 |

### [ui/ApplicationUpdateCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/ApplicationUpdateCard.kt:39 | 尚未检查 | 保留 | 尚未检查 | P0 |
| ui/ApplicationUpdateCard.kt:41 | 未知 | 被动/实现说明 | 未知 | P0 |
| ui/ApplicationUpdateCard.kt:45 | Tanuki 应用更新 | 保留 | Tanuki 应用更新 | P0 |
| ui/ApplicationUpdateCard.kt:47 | 当前版本 $currentVersion | 清晰度 | 当前版本 {版本} | P0 |
| ui/ApplicationUpdateCard.kt:72 | 检查更新 | 保留 | 检查更新 | P0 |
| ui/ApplicationUpdateCard.kt:74 | 后台每 30 分钟检查一次（需联网） · 上次 $lastChecked | 清晰度 | 联网时约每 30 分钟检查。上次检查：{时间} | P0 |
| ui/ApplicationUpdateCard.kt:77 | 上次自动检查未完成，稍后会重试；也可以手动检查。 | 清晰度 | 上次自动检查失败，可点“检查更新”重试。 | P0 |
| ui/ApplicationUpdateCard.kt:82 | 可用版本 ${candidate.versionName} | 清晰度 | 可用版本 {版本} | P0 |
| ui/ApplicationUpdateCard.kt:83 | 下载大小 ${megabytes(candidate.bytes)} MiB | 清晰度 | 下载大小 {大小} MiB | P0 |
| ui/ApplicationUpdateCard.kt:86 | 下载更新 | 保留 | 下载更新 | P0 |
| ui/ApplicationUpdateCard.kt:89 | 安装更新 | 保留 | 安装更新 | P0 |
| ui/ApplicationUpdateCard.kt:90 | 请先停止 DSH，并等待环境和文件任务结束；下载文件会保留。 | 术语不一致 | 请先停止助手，并等待环境和文件操作完成。 | P0 |
| ui/ApplicationUpdateCard.kt:96 | 应用更新下载进度 | 保留 | 应用更新下载进度 | P0 |
| ui/ApplicationUpdateCard.kt:97 | ${megabytes(state.received)} / ${megabytes(state.total)} MiB | 清晰度 | {大小} / {大小} MiB | P0 |
| ui/ApplicationUpdateCard.kt:98 | 应用更新正在处理 | 保留 | 应用更新正在处理 | P0 |
| ui/ApplicationUpdateCard.kt:99 | 已用 ${((now - state.startedAt).coerceAtLeast(0) / 1_000)} 秒 | 清晰度 | 已用 {状态} 秒 | P0 |
| ui/ApplicationUpdateCard.kt:100 | 取消更新任务 | 清晰度 | 取消更新 | P0 |
| ui/ApplicationUpdateCard.kt:103 | 了解详情 | 保留 | 了解详情 | P0 |
| ui/ApplicationUpdateCard.kt:103 | 收起详情 | 保留 | 收起详情 | P0 |
| ui/ApplicationUpdateCard.kt:111 | 应用包名：${it.packageName} | 清晰度 | 应用包名：{名称} | P0 |

### [ui/ApplicationUpdatePresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/ApplicationUpdatePresentation.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/ApplicationUpdatePresentation.kt:13 | 只会安装同一来源的更高版本，下载后会校验完整性 | 清晰度 | 仅安装适用于当前应用的新版本，下载后会检查文件。 | P0 |
| ui/ApplicationUpdatePresentation.kt:14 | 仅接收本项目同包名、同签名且版本更高的 APK；下载后校验大小和 SHA-256，安装前再次核对。Android 安装器会继续校验签名并等待你的确认。 | 工程痕迹、长度 | 下载后会检查文件和签名。<br>点“安装更新”后，还需在系统安装界面确认。 | P0 |
| ui/ApplicationUpdatePresentation.kt:15 | 覆盖更新保留已安装的 Ubuntu、Node.js、DSH 和项目。不要卸载旧应用，也不要清除数据；正式签名与开发签名不能相互覆盖。 | 术语不一致、长度 | 更新会保留运行环境和项目，请勿卸载应用或清除数据。<br>签名不同的安装包无法直接更新。 | P0 |
| ui/ApplicationUpdatePresentation.kt:18 | 正在检查 | 保留 | 正在检查 | P0 |
| ui/ApplicationUpdatePresentation.kt:19 | 已是最新 | 保留 | 已是最新 | P0 |
| ui/ApplicationUpdatePresentation.kt:20 | 有新版本 | 保留 | 有新版本 | P0 |
| ui/ApplicationUpdatePresentation.kt:21 | 暂无可用更新 | 保留 | 暂无可用更新 | P0 |
| ui/ApplicationUpdatePresentation.kt:22 | 检查未完成 | 保留 | 检查未完成 | P0 |
| ui/ApplicationUpdatePresentation.kt:23 | 已是最新 | 保留 | 已是最新 | P0 |
| ui/ApplicationUpdatePresentation.kt:24 | 正在检查 | 保留 | 正在检查 | P0 |
| ui/ApplicationUpdatePresentation.kt:25 | 检查未完成 | 保留 | 检查未完成 | P0 |
| ui/ApplicationUpdatePresentation.kt:26 | 尚未检查 | 保留 | 尚未检查 | P0 |
| ui/ApplicationUpdatePresentation.kt:30 | 检查是否有新版本。 | 保留 | 检查是否有新版本。 | P0 |
| ui/ApplicationUpdatePresentation.kt:31 | 正在检查新版本… | 保留 | 正在检查新版本… | P0 |
| ui/ApplicationUpdatePresentation.kt:32 | 你正在使用最新版本。 | 保留 | 你正在使用最新版本。 | P0 |
| ui/ApplicationUpdatePresentation.kt:33 | 暂时没有可用更新，稍后再来看看。 | 保留 | 暂时没有可用更新，稍后再来看看。 | P0 |
| ui/ApplicationUpdatePresentation.kt:34 | 发现新版本，可以下载更新。 | 保留 | 发现新版本，可以下载更新。 | P0 |
| ui/ApplicationUpdatePresentation.kt:35 | 正在下载更新… | 保留 | 正在下载更新… | P0 |
| ui/ApplicationUpdatePresentation.kt:36 | 正在校验下载文件… | 清晰度 | 正在检查更新文件… | P0 |
| ui/ApplicationUpdatePresentation.kt:37 | 更新已准备好，点击安装继续。 | 清晰度 | 更新已准备好，请点“安装更新”继续。 | P0 |
| ui/ApplicationUpdatePresentation.kt:38 | 更新没有完成，请稍后重试，或展开详情查看原因。 | 清晰度 | 更新失败，请重试。仍失败时可查看问题详情。 | P0 |
| ui/ApplicationUpdatePresentation.kt:39 | 更新任务已取消，可以重新检查。 | 保留 | 更新任务已取消，可以重新检查。 | P0 |

### [ui/BrowserChrome.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/BrowserChrome.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/BrowserChrome.kt:28 | 页内查找 | 保留 | 页内查找 | P1 |
| ui/BrowserChrome.kt:29 | 网页缩放 | 保留 | 网页缩放 | P1 |
| ui/BrowserChrome.kt:30 | 截取网页画面 | 清晰度 | 整页截图 | P1 |
| ui/BrowserChrome.kt:31 | 页面显示模式 | 保留 | 页面显示模式 | P1 |
| ui/BrowserChrome.kt:33 | 收藏夹 | 保留 | 收藏夹 | P1 |
| ui/BrowserChrome.kt:34 | 历史记录 | 保留 | 历史记录 | P1 |
| ui/BrowserChrome.kt:35 | 下载与截图 | 保留 | 下载与截图 | P1 |
| ui/BrowserChrome.kt:36 | 收藏当前页 | 保留 | 收藏当前页 | P1 |
| ui/BrowserChrome.kt:37 | 工具箱 | 保留 | 工具箱 | P1 |
| ui/BrowserChrome.kt:38 | 浏览器设置 | 保留 | 浏览器设置 | P1 |
| ui/BrowserChrome.kt:41 | 返回浏览器菜单 | 清晰度 | 返回菜单 | P1 |
| ui/BrowserChrome.kt:42 | 工具箱 | 保留 | 工具箱 | P1 |
| ui/BrowserChrome.kt:42 | 浏览器 | 保留 | 浏览器 | P1 |
| ui/BrowserChrome.kt:43 | 关闭浏览器菜单 | 清晰度 | 关闭菜单 | P1 |
| ui/BrowserChrome.kt:59 | 权限与后台 | 保留 | 权限与后台 | P0 |
| ui/BrowserChrome.kt:60 | 管理操作权限与后台 | 保留 | 管理操作权限与后台 | P0 |
| ui/BrowserChrome.kt:71 | 返回浏览器 | 保留 | 返回浏览器 | P1 |

### [ui/BrowserControlButton.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/BrowserControlButton.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/BrowserControlButton.kt:26 | 浏览器不可用 | 保留 | 浏览器不可用 | P1 |
| ui/BrowserControlButton.kt:38 | 浏览器控制 | 清晰度 | 浏览器操作权 | P1 |
| ui/BrowserControlButton.kt:40 | 交回 AI | 保留 | 交回 AI | P1 |
| ui/BrowserControlButton.kt:40 | 撤销 AI 操作权限 | 清晰度 | 收回操作权 | P0 |

### [ui/BrowserFloatingWindow.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/BrowserFloatingWindow.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/BrowserFloatingWindow.kt:45 | 浏览器小窗 | 保留 | 浏览器小窗 | P1 |
| ui/BrowserFloatingWindow.kt:46 | 拖动浏览器小窗 | 保留 | 拖动浏览器小窗 | P1 |
| ui/BrowserFloatingWindow.kt:52 | 展开浏览器小窗 | 清晰度 | 展开小窗 | P1 |
| ui/BrowserFloatingWindow.kt:52 | 收起浏览器小窗 | 清晰度 | 收起小窗 | P1 |
| ui/BrowserFloatingWindow.kt:53 | 展开为完整浏览器 | 清晰度 | 打开浏览器 | P1 |
| ui/BrowserFloatingWindow.kt:54 | 关闭浏览器小窗 | 清晰度 | 关闭小窗 | P1 |

### [ui/DshApprovalController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshApprovalController.kt:32 | 无法判断影响范围 | 清晰度 | 影响范围尚未确认，请查看详情后决定。 | P0 |
| ui/DshApprovalController.kt:110 | DSH 服务地址已变化，$dropped 个旧待审批已失效；请到官方界面重新处理。 | 术语不一致 | 连接已变化，旧授权请求已失效。请在网页界面重新处理。 | P1 |
| ui/DshApprovalController.kt:154 | 无法跳过非审批请求：${failure.message} | 清晰度 | 此请求无法跳过，请在网页界面处理。 | P0 |
| ui/DshApprovalController.kt:181 | 连接已断开，未能回答；可重试 | 保留 | 连接已断开，未能回答；可重试 | P1 |
| ui/DshApprovalController.kt:243 | 回答已中断，可重试 | 保留 | 回答已中断，可重试 | P0 |
| ui/DshApprovalController.kt:259 | 审批通道尚未就绪，请重试 | 工程痕迹 | 授权请求尚未连接，请重试。 | P1 |
| ui/DshApprovalController.kt:267 | 未知审批类型只能拒绝 | 被动/实现说明 | 无法识别此操作，只能拒绝。 | P0 |
| ui/DshApprovalController.kt:284 | 审批通道已关闭 | 工程痕迹 | 授权连接已关闭，请重新连接。 | P1 |
| ui/DshApprovalController.kt:351 | 未知审批类型；只能拒绝 | 被动/实现说明 | 无法识别此操作，只能拒绝。 | P0 |
| ui/DshApprovalController.kt:401 | 未知审批类型；只能拒绝 | 被动/实现说明 | 无法识别此操作，只能拒绝。 | P0 |
| ui/DshApprovalController.kt:424 | 未读取到该调用的参数；风险未知 | 被动/实现说明 | 未能读取操作内容，请谨慎决定。 | P1 |
| ui/DshApprovalController.kt:472 | 审批回答失败 | 清晰度 | 授权结果发送失败，请重试。 | P0 |

### [ui/DshApprovalPanel.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshApprovalPanel.kt:62 | 会话 ${it.take(8)} | 术语不一致 | 未命名对话 | P1 |
| ui/DshApprovalPanel.kt:63 | 当前会话 | 术语不一致 | 当前对话 | P1 |
| ui/DshApprovalPanel.kt:69 | 审批请求 | 清晰度 | 待授权操作 | P1 |
| ui/DshApprovalPanel.kt:72 | ${pending.size} 条待处理 | 清晰度 | {数量} 条待处理 | P1 |
| ui/DshApprovalPanel.kt:77 | 审批通道已连接 | 工程痕迹 | 授权请求已连接 | P1 |
| ui/DshApprovalPanel.kt:78 | 审批通道连接中 | 工程痕迹 | 正在连接授权请求 | P1 |
| ui/DshApprovalPanel.kt:79 | 审批通道未连接 | 工程痕迹 | 授权请求未连接 | P1 |
| ui/DshApprovalPanel.kt:85 | 清除记住的选择 | 清晰度 | 清除已记选择 | P1 |
| ui/DshApprovalPanel.kt:97 | 审批通道异常 | 工程痕迹 | 授权连接异常 | P0 |
| ui/DshApprovalPanel.kt:101 | 待审批项仍保留；恢复连接后可继续回答。 | 清晰度 | 请求已保留，重新连接后可继续处理。 | P1 |
| ui/DshApprovalPanel.kt:106 | 重试连接 | 保留 | 重试连接 | P1 |
| ui/DshApprovalPanel.kt:123 | 知道了 | 保留 | 知道了 | P1 |
| ui/DshApprovalPanel.kt:135 | 其他会话还有 $others 个待审批；切换会话后可处理。 | 术语不一致 | 其他对话还有 {数量} 个待授权操作，切换后可处理。 | P1 |
| ui/DshApprovalPanel.kt:146 | 待审批共 ${pending.size} 条；每张卡片独立回答，允许/拒绝只作用于本卡片。 | 清晰度 | 共 {数量} 项操作，请逐条决定是否允许。 | P0 |
| ui/DshApprovalPanel.kt:170 | 正在连接官方审批通道… | 工程痕迹 | 正在连接授权请求… | P1 |
| ui/DshApprovalPanel.kt:200 | 已记住 ${choices.size} 项选择 | 清晰度 | 已记住 {数量} 项选择 | P1 |
| ui/DshApprovalPanel.kt:205 | 仅本次原生运行有效；撤销后相同审批不会自动回答。 | 工程痕迹、术语不一致 | 仅在本次运行中记住选择，撤销后会再次询问。 | P1 |
| ui/DshApprovalPanel.kt:210 | 全部清除 | 保留 | 全部清除 | P1 |
| ui/DshApprovalPanel.kt:221 | ${choice.toolName} · ${choice.outcomeLabel} | 清晰度 | {名称} · {名称} | P1 |
| ui/DshApprovalPanel.kt:227 | 风险 ${choice.risk.label} · 会话 ${choice.sessionId.take(8)} | 工程痕迹、术语不一致 | 风险：{风险等级}。对话：{对话名称} | P1 |
| ui/DshApprovalPanel.kt:235 | 撤销 | 保留 | 撤销 | P1 |
| ui/DshApprovalPanel.kt:276 | 第  | 清晰度 | 第 | P1 |
| ui/DshApprovalPanel.kt:280 |  条 ·  | 清晰度 | 条 · | P1 |
| ui/DshApprovalPanel.kt:284 | 正在回传官方审批… | 清晰度 | 正在发送授权结果… | P1 |
| ui/DshApprovalPanel.kt:285 | 按记住的选择自动回答 | 保留 | 按记住的选择自动回答 | P1 |
| ui/DshApprovalPanel.kt:286 | 等待审批 | 保留 | 等待审批 | P1 |
| ui/DshApprovalPanel.kt:293 | 官方工具调用 | 清晰度 | 助手工具 | P1 |
| ui/DshApprovalPanel.kt:293 | 来源：${source ?: "官方工具调用"} · $sessionLabel | 清晰度 | 操作来源：{来源}。对话：{对话名称} | P1 |
| ui/DshApprovalPanel.kt:318 | 风险等级：${item.risk.level.label} · ${item.risk.summary} | 清晰度 | 风险等级：{名称} · {内容} | P1 |
| ui/DshApprovalPanel.kt:324 | 工具 ${item.request.toolName} 请求权限；请确认是否允许。 | 清晰度 | 工具“{工具名}”请求操作，请查看内容后决定。 | P0 |
| ui/DshApprovalPanel.kt:329 | 官方未提供可识别的工具类型；只能拒绝，不能按记忆自动允许。 | 清晰度 | 无法识别此操作，只能拒绝。 | P0 |
| ui/DshApprovalPanel.kt:336 | 正在按该审批所属会话读取工具参数与风险… | 术语不一致 | 正在读取操作内容和风险… | P1 |
| ui/DshApprovalPanel.kt:350 | 参数摘要（${summary.field}）：${summary.headline} | 清晰度 | 操作摘要：{摘要} | P1 |
| ui/DshApprovalPanel.kt:366 | 完整参数 | 清晰度 | 操作详情 | P1 |
| ui/DshApprovalPanel.kt:379 | 展开完整参数 | 清晰度 | 查看详情 | P1 |
| ui/DshApprovalPanel.kt:379 | 收起参数 | 清晰度 | 收起详情 | P1 |
| ui/DshApprovalPanel.kt:386 | 官方请求未附带参数；可在对应工具卡片中查看调用详情。 | 清晰度 | 未提供操作内容，可在对应工具卡片查看详情。 | P1 |
| ui/DshApprovalPanel.kt:398 | 本次运行内记住此工具与风险等级 | 清晰度 | 本次运行记住同类选择 | P1 |
| ui/DshApprovalPanel.kt:404 | 高风险工具被记住后，相同会话/工具/风险等级会自动允许；请确认这是有意选择。 | 术语不一致 | 记住后，同一对话中同工具、同风险操作将自动允许。<br>高风险操作也会如此，请谨慎选择。 | P0 |
| ui/DshApprovalPanel.kt:411 | 这是高风险操作。再次点击“确认允许高风险”才会回传官方；点“拒绝”不会执行。 | 清晰度 | 这是高风险操作，请再次确认。<br>点“拒绝”将不会执行。 | P0 |
| ui/DshApprovalPanel.kt:425 | 已按记住的选择自动回答；若失败会保留此卡片并显示错误。 | 清晰度 | 已按记住的选择回答，发送失败时可重试。 | P0 |
| ui/DshApprovalPanel.kt:438 | 拒绝 | 保留 | 拒绝 | P0 |
| ui/DshApprovalPanel.kt:451 | 允许一次 | 保留 | 允许一次 | P0 |
| ui/DshApprovalPanel.kt:452 | 确认允许高风险 | 清晰度 | 仍要允许 | P0 |
| ui/DshApprovalPanel.kt:453 | 允许一次 | 保留 | 允许一次 | P0 |

### [ui/DshApprovalSheet.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshApprovalSheet.kt:56 | ⚑ 待审批 $pendingCount 项 | 清晰度 | 待授权 {数量} 项 | P1 |
| ui/DshApprovalSheet.kt:57 | 审批通道异常 | 工程痕迹 | 授权连接异常 | P0 |
| ui/DshApprovalSheet.kt:58 | 审批记忆 $rememberedCount 项 | 清晰度 | 已记选择 {数量} 项 | P1 |
| ui/DshApprovalSheet.kt:59 | 审批提示 | 清晰度 | 授权提示 | P1 |
| ui/DshApprovalSheet.kt:108 | 打开审批：${entry.label} | 清晰度 | 查看授权：{操作名称} | P1 |
| ui/DshApprovalSheet.kt:118 | 高风险 ${entry.highRiskCount} | 清晰度 | 高风险 {数量} | P1 |
| ui/DshApprovalSheet.kt:124 | 逐条处理 | 保留 | 逐条处理 | P1 |
| ui/DshApprovalSheet.kt:126 | 打开 | 保留 | 打开 | P1 |
| ui/DshApprovalSheet.kt:183 | 有新的审批请求，计数已更新；当前处理项不会跳转。 | 清晰度 | 有新操作待授权，当前处理项保持不变。 | P0 |

### [ui/DshAttachmentReader.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshAttachmentReader.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshAttachmentReader.kt:56 | 附件总大小不能超过 ${DshComposerPolicy.formatBytes(budget.totalLabelBytes)} | 清晰度 | 附件总大小不能超过 {大小} | P1 |
| ui/DshAttachmentReader.kt:58 | 单个附件不能超过 ${DshComposerPolicy.formatBytes(limit)} | 清晰度 | 单个附件不能超过 {大小} | P1 |
| ui/DshAttachmentReader.kt:65 | 附件读取失败 | 保留 | 附件读取失败 | P0 |
| ui/DshAttachmentReader.kt:73 | 当前系统不支持读取剪贴板 | 保留 | 当前系统不支持读取剪贴板 | P1 |
| ui/DshAttachmentReader.kt:75 | 无法读取剪贴板，请改用图片选择器 | 保留 | 无法读取剪贴板，请改用图片选择器 | P0 |
| ui/DshAttachmentReader.kt:76 | 剪贴板没有可用的图片 | 保留 | 剪贴板没有可用的图片 | P0 |
| ui/DshAttachmentReader.kt:110 | 附件总大小不能超过 ${DshComposerPolicy.formatBytes(budget.totalLabelBytes)} | 清晰度 | 附件总大小不能超过 {大小} | P1 |
| ui/DshAttachmentReader.kt:112 | 单张图片不能超过 ${DshComposerPolicy.formatBytes(limit)} | 清晰度 | 单张图片不能超过 {大小} | P1 |
| ui/DshAttachmentReader.kt:120 | 粘贴的图片 | 保留 | 粘贴的图片 | P1 |
| ui/DshAttachmentReader.kt:144 | 剪贴板没有可用的图片 | 保留 | 剪贴板没有可用的图片 | P0 |
| ui/DshAttachmentReader.kt:183 | 只能读取文档选择器提供的本地内容 | 清晰度 | 请通过系统文件选择器添加附件。 | P1 |
| ui/DshAttachmentReader.kt:187 | 图片 | 保留 | 图片 | P1 |
| ui/DshAttachmentReader.kt:187 | 附件 | 保留 | 附件 | P1 |
| ui/DshAttachmentReader.kt:191 | 附件内容为空 | 保留 | 附件内容为空 | P0 |
| ui/DshAttachmentReader.kt:194 | 不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF | 保留 | 不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF | P1 |
| ui/DshAttachmentReader.kt:197 | 无法读取图片尺寸，请更换图片 | 保留 | 无法读取图片尺寸，请更换图片 | P0 |
| ui/DshAttachmentReader.kt:218 | 无法打开附件 | 保留 | 无法打开附件 | P0 |

### [ui/DshChatRender.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshChatRender.kt:169 | 将在系统浏览器中打开此链接。 | 保留 | 将在系统浏览器中打开此链接。 | P1 |
| ui/DshChatRender.kt:170 | 将调用系统邮件应用。 | 保留 | 将调用系统邮件应用。 | P1 |
| ui/DshChatRender.kt:171 | 将调用系统拨号界面。 | 保留 | 将调用系统拨号界面。 | P1 |
| ui/DshChatRender.kt:172 | 附件/工作区链接需要额外授权，原生消息暂不直接打开。 | 工程痕迹、术语不一致 | 此链接需额外权限，请在网页界面打开。 | P0 |
| ui/DshChatRender.kt:173 | 出于安全原因，原生消息不打开该链接。 | 工程痕迹、术语不一致 | 此链接无法安全打开，可复制地址查看。 | P1 |
| ui/DshChatRender.kt:186 | 链接地址为空 | 保留 | 链接地址为空 | P0 |
| ui/DshChatRender.kt:187 | 链接地址过长，已拒绝打开 | 保留 | 链接地址过长，已拒绝打开 | P0 |
| ui/DshChatRender.kt:188 | 链接包含不可见控制字符，已拒绝打开 | 清晰度 | 链接含有不可见字符，请检查地址后重试。 | P0 |
| ui/DshChatRender.kt:191 | 出于安全原因不打开该链接 | 清晰度 | 此链接无法安全打开，可复制地址查看。 | P1 |
| ui/DshChatRender.kt:197 | 附件链接需要 DSH 工作区授权 | 术语不一致 | 请在网页界面打开此附件。 | P0 |
| ui/DshChatRender.kt:198 | 相对路径需要工作区授权 | 清晰度 | 请在文件页打开此文件。 | P0 |
| ui/DshChatRender.kt:199 | 暂不支持打开该类型的链接 | 被动/实现说明 | 此链接类型无法打开，可复制地址查看。 | P1 |
| ui/DshChatRender.kt:223 | 图片地址为空，无法加载 | 保留 | 图片地址为空，无法加载 | P0 |
| ui/DshChatRender.kt:225 | 图片数据过大，无法在消息内预览 | 保留 | 图片数据过大，无法在消息内预览 | P0 |
| ui/DshChatRender.kt:232 | 内嵌图片 | 保留 | 内嵌图片 | P1 |
| ui/DshChatRender.kt:237 | 本地图片，点击查看；读取失败时会显示中文原因 | 清晰度 | 点此查看本机图片。 | P0 |
| ui/DshChatRender.kt:242 | 远程图片未自动加载；点击查看地址，避免绕过 DSH 会话授权 | 术语不一致 | 远程图片未自动加载，点此查看图片地址。 | P0 |
| ui/DshChatRender.kt:248 | 该图片需要 DSH 会话授权，原生消息暂不直接加载 | 工程痕迹、术语不一致 | 请在网页界面查看此图片。 | P0 |
| ui/DshChatRender.kt:250 | 图片地址格式不支持，无法加载 | 保留 | 图片地址格式不支持，无法加载 | P0 |
| ui/DshChatRender.kt:265 | 未命名附件 | 保留 | 未命名附件 | P1 |
| ui/DshChatRender.kt:268 | 网络附件，点击确认后查看地址 | 保留 | 网络附件，点击确认后查看地址 | P1 |
| ui/DshChatRender.kt:269 | 附件需要工作区或会话授权，原生消息暂不直接打开 | 工程痕迹、术语不一致 | 请在网页界面打开此附件。 | P0 |
| ui/DshChatRender.kt:270 | 附件地址无效 | 清晰度 | 附件地址无法使用 | P1 |
| ui/DshChatRender.kt:289 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |
| ui/DshChatRender.kt:292 | 认证 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshChatRender.kt:293 | 切换到官方界面重新连接后再试。 | 术语不一致 | 请切换到网页界面，重新连接。 | P1 |
| ui/DshChatRender.kt:293 | 连接认证已失效 | 清晰度 | 页面登录已过期 | P1 |
| ui/DshChatRender.kt:294 | 正在处理 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshChatRender.kt:295 | DSH 正在处理其他消息 | 术语不一致 | 助手正在处理其他消息 | P1 |
| ui/DshChatRender.kt:295 | 等待当前回合结束后再重试。 | 清晰度 | 请等待本次回复结束后重试。 | P1 |
| ui/DshChatRender.kt:296 | 超时 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshChatRender.kt:297 | 检查 DSH 服务后点刷新重试。 | 术语不一致 | 请确认助手正在运行，再刷新重试。 | P1 |
| ui/DshChatRender.kt:297 | 连接超时 | 保留 | 连接超时 | P0 |
| ui/DshChatRender.kt:299 | 网络 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshChatRender.kt:300 | 检查 DSH 服务状态后重试。 | 术语不一致 | 请确认助手正在运行，再重试。 | P1 |
| ui/DshChatRender.kt:300 | 网络连接中断 | 保留 | 网络连接中断 | P0 |
| ui/DshChatRender.kt:301 | 响应 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshChatRender.kt:301 | 格式 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshChatRender.kt:302 | 可复制错误信息，或切换官方界面查看。 | 术语不一致 | 可切换到网页界面查看，或复制问题详情反馈。 | P0 |
| ui/DshChatRender.kt:302 | 官方响应格式异常 | 清晰度 | 回复暂时无法显示 | P0 |
| ui/DshChatRender.kt:303 | 取消 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshChatRender.kt:304 | 如仍需要结果，请重新发送。 | 保留 | 如仍需要结果，请重新发送。 | P1 |
| ui/DshChatRender.kt:304 | 操作已取消 | 保留 | 操作已取消 | P0 |
| ui/DshChatRender.kt:305 | 可复制错误信息，或切换官方界面处理。 | 术语不一致 | 可复制错误信息，或切换网页界面处理。 | P0 |
| ui/DshChatRender.kt:305 | 操作未完成 | 保留 | 操作未完成 | P1 |
| ui/DshChatRender.kt:431 | 发送失败 | 保留 | 发送失败 | P0 |
| ui/DshChatRender.kt:478 | 发送响应格式不正确 | 清晰度 | 消息发送结果无法确认，请查看对话后再试。 | P1 |
| ui/DshChatRender.kt:479 | 官方接口未接受该消息 | 工程痕迹 | 消息未发送，请重试。 | P1 |
| ui/DshChatRender.kt:481 | 未找到该斜杠命令 | 清晰度 | 找不到这条命令，请重新选择。 | P0 |
| ui/DshChatRender.kt:482 | 斜杠命令返回格式不正确 | 清晰度 | 命令结果无法显示，请在网页界面查看。 | P1 |
| ui/DshChatRender.kt:485 | 斜杠命令执行失败 | 清晰度 | 命令执行失败，请重试。 | P0 |
| ui/DshChatRender.kt:486 | 斜杠命令返回格式不正确 | 清晰度 | 命令结果无法显示，请在网页界面查看。 | P1 |
| ui/DshChatRender.kt:507 | 失败消息包含附件；原生输入栏暂不支持按快照重发附件，请复制内容并重新添加附件。 | 工程痕迹、被动/实现说明、术语不一致、长度 | 请重新添加附件后再发送，文字内容可复制使用。 | P0 |
| ui/DshChatRender.kt:509 | 当前草稿已修改；请先回填失败内容，或复制后重新发送。 | 清晰度 | 草稿已有新内容，请复制失败消息后再编辑发送。 | P0 |
| ui/DshChatRender.kt:510 | 失败消息没有可重发的文本，请重新编辑后发送。 | 保留 | 失败消息没有可重发的文本，请重新编辑后发送。 | P0 |

### [ui/DshComposerBar.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshComposerBar.kt:235 | 图片 | 保留 | 图片 | P1 |
| ui/DshComposerBar.kt:247 | 文件 | 保留 | 文件 | P1 |
| ui/DshComposerBar.kt:255 | 无法确认附件所属会话，已丢弃选择结果 | 术语不一致 | 无法确认附件对应的对话，请重新选择附件。 | P0 |
| ui/DshComposerBar.kt:259 | 会话已切换，已丢弃上次附件选择 | 术语不一致 | 已切换对话，请重新选择附件。 | P1 |
| ui/DshComposerBar.kt:274 | 无法确认附件所属会话，已丢弃选择结果 | 术语不一致 | 无法确认附件对应的对话，请重新选择附件。 | P0 |
| ui/DshComposerBar.kt:293 | 无法确认附件所属会话，已丢弃选择结果 | 术语不一致 | 无法确认附件对应的对话，请重新选择附件。 | P0 |
| ui/DshComposerBar.kt:361 | 会话已切换，已丢弃上次拖入的附件 | 术语不一致 | 已切换对话，请重新添加拖入的附件。 | P1 |
| ui/DshComposerBar.kt:371 | 拖入内容没有可读取的文件 | 保留 | 拖入内容没有可读取的文件 | P0 |
| ui/DshComposerBar.kt:392 | composer-corner | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/DshComposerBar.kt:421 | 关闭 | 保留 | 关闭 | P1 |
| ui/DshComposerBar.kt:431 | 正在停止，等待官方确认 | 清晰度 | 正在停止，请稍候。 | P1 |
| ui/DshComposerBar.kt:432 | 停止请求已发送，仍在收尾 | 清晰度 | 已请求停止，正在收尾。 | P1 |
| ui/DshComposerBar.kt:433 | 已停止当前回合 | 清晰度 | 本次回复已停止。 | P1 |
| ui/DshComposerBar.kt:434 | 停止失败，可重试 | 保留 | 停止失败，可重试 | P0 |
| ui/DshComposerBar.kt:487 | 添加附件 | 保留 | 添加附件 | P1 |
| ui/DshComposerBar.kt:492 | + | 保留 | + | P1 |
| ui/DshComposerBar.kt:496 | 选择图片 | 保留 | 选择图片 | P1 |
| ui/DshComposerBar.kt:504 | 选择文件 | 保留 | 选择文件 | P1 |
| ui/DshComposerBar.kt:512 | 粘贴图片 | 保留 | 粘贴图片 | P1 |
| ui/DshComposerBar.kt:604 | 输入消息，@ 引用会话或文件，/ 查看命令 | 术语不一致 | 输入你想让助手完成的事 | P1 |
| ui/DshComposerBar.kt:616 | 停止生成 | 保留 | 停止生成 | P1 |
| ui/DshComposerBar.kt:616 | 发送 | 保留 | 发送 | P1 |
| ui/DshComposerBar.kt:636 | @ 引用 | 清晰度 | 引用内容 | P1 |
| ui/DshComposerBar.kt:637 | / 命令 | 清晰度 | 选择命令 | P1 |
| ui/DshComposerBar.kt:640 | 继续发送 | 保留 | 继续发送 | P1 |
| ui/DshComposerBar.kt:647 | 正在读取附件… | 保留 | 正在读取附件… | P1 |
| ui/DshComposerBar.kt:648 | 正在停止… | 保留 | 正在停止… | P1 |
| ui/DshComposerBar.kt:649 | 附件上传中（可取消） | 清晰度 | 正在上传附件… | P0 |
| ui/DshComposerBar.kt:650 | 停止请求已发送，仍在收尾 | 清晰度 | 已请求停止，正在收尾。 | P1 |
| ui/DshComposerBar.kt:651 | 已停止当前回合 | 清晰度 | 本次回复已停止。 | P1 |
| ui/DshComposerBar.kt:652 | 停止失败，可重试 | 保留 | 停止失败，可重试 | P0 |
| ui/DshComposerBar.kt:653 | 发送失败，内容已保留，可重发 | 保留 | 发送失败，内容已保留，可重发 | P0 |
| ui/DshComposerBar.kt:654 | 发送中… | 保留 | 发送中… | P1 |
| ui/DshComposerBar.kt:655 | 长时间无运行事件；可继续发送，停止仍可用 | 清晰度 | 回复长时间未更新，可检查对话状态或停止任务。 | P1 |
| ui/DshComposerBar.kt:656 | Ctrl/⌘+Enter 发送 | 清晰度 | Ctrl/⌘ + Enter 发送 | P1 |
| ui/DshComposerBar.kt:779 | 待发送 | 保留 | 待发送 | P1 |
| ui/DshComposerBar.kt:780 | 上传中（可取消） | 清晰度 | 正在上传… | P0 |
| ui/DshComposerBar.kt:781 | 已上传 | 保留 | 已上传 | P1 |
| ui/DshComposerBar.kt:782 | 上传失败 | 保留 | 上传失败 | P0 |
| ui/DshComposerBar.kt:798 | 重试 | 保留 | 重试 | P1 |
| ui/DshComposerBar.kt:802 | 取消 | 保留 | 取消 | P0 |
| ui/DshComposerBar.kt:824 | 图 | 保留 | 图 | P1 |
| ui/DshComposerBar.kt:843 | 正在查找引用… | 保留 | 正在查找引用… | P1 |
| ui/DshComposerBar.kt:846 | 没有匹配的文件或会话，继续输入可搜索 | 术语不一致 | 没有匹配内容，试试其他关键词。 | P0 |
| ui/DshComposerBar.kt:868 | 引用 | 保留 | 引用 | P1 |
| ui/DshComposerBar.kt:900 | /${command.name} | 清晰度 | /{名称} | P1 |
| ui/DshComposerBar.kt:910 | 可带附件 | 保留 | 可带附件 | P1 |
| ui/DshComposerBar.kt:947 | ${attachment.mediaType} · ${attachment.sizeLabel} | 清晰度 | {内容} · {数量} | P1 |
| ui/DshComposerBar.kt:952 | 关闭 | 保留 | 关闭 | P1 |

### [ui/DshComposerController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshComposerController.kt:82 | 文件 | 保留 | 文件 | P1 |
| ui/DshComposerController.kt:82 | 目录 | 保留 | 目录 | P1 |
| ui/DshComposerController.kt:239 | 请先选择会话 | 术语不一致 | 请先选择对话 | P1 |
| ui/DshComposerController.kt:250 | 不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF | 保留 | 不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF | P1 |
| ui/DshComposerController.kt:257 | 每条消息最多添加 ${DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE} 个附件 | 清晰度 | 每条消息最多添加 {内容} 个附件 | P1 |
| ui/DshComposerController.kt:275 | 每条消息最多添加 ${DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE} 个附件 | 清晰度 | 每条消息最多添加 {内容} 个附件 | P1 |
| ui/DshComposerController.kt:298 | 图片 | 保留 | 图片 | P1 |
| ui/DshComposerController.kt:298 | 附件 | 保留 | 附件 | P1 |
| ui/DshComposerController.kt:310 | 已添加 $accepted 个附件 | 清晰度 | 已添加 {内容} 个附件 | P1 |
| ui/DshComposerController.kt:344 | 已取消上传，未保留残缺文件 | 保留 | 已取消上传，未保留残缺文件 | P0 |
| ui/DshComposerController.kt:365 | 请先选择会话 | 术语不一致 | 请先选择对话 | P1 |
| ui/DshComposerController.kt:371 | 有附件上传失败，请重试或移除后再发送 | 保留 | 有附件上传失败，请重试或移除后再发送 | P0 |
| ui/DshComposerController.kt:375 | 文件仍在上传，请稍候再发送 | 保留 | 文件仍在上传，请稍候再发送 | P1 |
| ui/DshComposerController.kt:380 | 正在读取斜杠命令，请稍候再发送 | 清晰度 | 正在读取命令，请稍后再发送。 | P1 |
| ui/DshComposerController.kt:385 | 该斜杠命令不接受附件，请移除附件或改用普通消息 | 清晰度 | 此命令不接受附件，请移除附件或发送普通消息。 | P1 |
| ui/DshComposerController.kt:424 | 文件凭证缺失，请重新上传 | 清晰度 | 附件信息已失效，请重新上传。 | P1 |
| ui/DshComposerController.kt:483 | 已发送 | 保留 | 已发送 | P1 |
| ui/DshComposerController.kt:483 | 已发送；发送期间的新编辑已保留 | 清晰度 | 已发送，你刚编辑的内容仍保留在输入框。 | P1 |
| ui/DshComposerController.kt:529 | 已停止当前回合 | 清晰度 | 本次回复已停止。 | P1 |
| ui/DshComposerController.kt:544 | 请先选择会话 | 术语不一致 | 请先选择对话 | P1 |
| ui/DshComposerController.kt:549 | 当前没有运行中的回合 | 清晰度 | 当前没有正在生成的回复。 | P0 |
| ui/DshComposerController.kt:587 | 正在停止，等待官方确认 | 清晰度 | 正在停止，请稍候。 | P1 |
| ui/DshComposerController.kt:596 | 已停止当前回合 | 清晰度 | 本次回复已停止。 | P1 |
| ui/DshComposerController.kt:612 | 停止失败：${messageOf(error)} | 清晰度 | 停止失败，请重试。 | P0 |
| ui/DshComposerController.kt:613 | 回合已结束 | 清晰度 | 本次回复已结束。 | P1 |
| ui/DshComposerController.kt:637 | 长时间未收到运行事件，可继续发送；停止仍可用 | 清晰度 | 回复长时间未更新，请检查状态后再继续发送。 | P1 |
| ui/DshComposerController.kt:652 | 停止请求已发送，仍在收尾 | 清晰度 | 已请求停止，正在收尾。 | P1 |
| ui/DshComposerController.kt:825 | 附件超过官方大小限制 | 清晰度 | 附件过大，请缩小后重试。 | P1 |
| ui/DshComposerController.kt:827 | 官方接口不支持该附件类型 | 工程痕迹 | 此附件类型无法上传，请更换文件。 | P1 |
| ui/DshComposerController.kt:828 | 会话或附件不存在 | 术语不一致 | 找不到对话或附件，请重新选择。 | P1 |
| ui/DshComposerController.kt:829 | 官方接口返回 ${error.code} | 工程痕迹 | 请求未完成，请稍后重试。 | P1 |
| ui/DshComposerController.kt:831 | 斜杠命令执行失败 | 清晰度 | 命令执行失败，请重试。 | P0 |
| ui/DshComposerController.kt:832 | 输入内容无效 | 清晰度 | 输入内容无法使用 | P1 |
| ui/DshComposerController.kt:833 | 网络请求失败 | 保留 | 网络请求失败 | P0 |

### [ui/DshDock.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshDock.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshDock.kt:37 | 审批 | 清晰度 | 授权 | P1 |
| ui/DshDock.kt:38 | 目标 | 保留 | 目标 | P1 |
| ui/DshDock.kt:39 | 待办 | 保留 | 待办 | P1 |
| ui/DshDock.kt:277 | 收起其余 $count 条停靠条 | 清晰度 | 收起其余 {数量} 项 | P1 |
| ui/DshDock.kt:279 | 展开其余 $count 条停靠条 | 清晰度 | 展开其余 {数量} 项 | P1 |
| ui/DshDock.kt:290 | 收起 | 保留 | 收起 | P1 |

### [ui/DshGoalDock.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshGoalDock.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshGoalDock.kt:39 | 进行中 | 保留 | 进行中 | P1 |
| ui/DshGoalDock.kt:40 | 已暂停 | 保留 | 已暂停 | P1 |
| ui/DshGoalDock.kt:41 | 受阻 | 保留 | 受阻 | P1 |
| ui/DshGoalDock.kt:74 | 暂停 | 保留 | 暂停 | P1 |
| ui/DshGoalDock.kt:75 | 恢复 | 保留 | 恢复 | P1 |
| ui/DshGoalDock.kt:76 | 编辑 | 保留 | 编辑 | P1 |
| ui/DshGoalDock.kt:77 | 清除 | 保留 | 清除 | P1 |
| ui/DshGoalDock.kt:91 | 本会话尚未接入官方目标操作通道 | 工程痕迹、被动/实现说明、术语不一致 | 请在网页界面管理此对话的目标。 | P1 |
| ui/DshGoalDock.kt:120 | 目标已暂停，无需再次暂停 | 保留 | 目标已暂停，无需再次暂停 | P1 |
| ui/DshGoalDock.kt:121 | 目标已受阻，请先恢复或编辑目标 | 清晰度 | 目标需要处理，请先修改后继续。 | P1 |
| ui/DshGoalDock.kt:130 | 目标正在进行中，无需恢复 | 保留 | 目标正在进行中，无需恢复 | P1 |
| ui/DshGoalDock.kt:131 | 目标受阻，恢复前请先编辑目标 | 清晰度 | 目标需要处理，请先修改后恢复。 | P1 |
| ui/DshGoalDock.kt:194 | 目标：${state.oneLineSummary}；状态：${state.statusLabel}；点击收起操作面板 | 清晰度 | 目标：{目标摘要}。状态：{状态}。收起操作。 | P1 |
| ui/DshGoalDock.kt:196 | 目标：${state.oneLineSummary}；状态：${state.statusLabel}；点击展开操作面板 | 清晰度 | 目标：{目标摘要}。状态：{状态}。展开操作。 | P1 |
| ui/DshGoalDock.kt:203 | ⚑ 目标 | 保留 | ⚑ 目标 | P1 |
| ui/DshGoalDock.kt:251 | ${actions.joinToString("/") { it.action.label }}不可用：$reason | 清晰度 | {名称}不可用：{原因} | P1 |
| ui/DshGoalDock.kt:263 | 目标正在进行中。 | 保留 | 目标正在进行中。 | P1 |
| ui/DshGoalDock.kt:264 | 目标已暂停，恢复后继续执行。 | 保留 | 目标已暂停，恢复后继续执行。 | P1 |
| ui/DshGoalDock.kt:265 | 目标受阻，请先处理后继续。 | 保留 | 目标受阻，请先处理后继续。 | P1 |
| ui/DshGoalDock.kt:299 | ${availability.action.label}不可用：${availability.reason.orEmpty()} | 清晰度 | {名称}不可用：{原因} | P1 |

### [ui/DshMarkdown.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshMarkdown.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshMarkdown.kt:254 | 内容过长，仅渲染前 ${DshChatRenderPolicy.MAX_MARKDOWN_BLOCKS} 段；可复制整条消息查看全文。 | 工程痕迹 | 消息较长，仅显示前 {段数} 段。可复制整条消息查看全文。 | P1 |
| ui/DshMarkdown.kt:604 | 打开链接？ | 保留 | 打开链接？ | P1 |
| ui/DshMarkdown.kt:604 | 无法打开链接 | 保留 | 无法打开链接 | P0 |
| ui/DshMarkdown.kt:630 | 打开失败：${it.message?.take(80) ?: "系统没有可处理该链接的应用"} | 清晰度 | 无法打开链接，请复制地址后使用其他应用打开。 | P0 |
| ui/DshMarkdown.kt:630 | 系统没有可处理该链接的应用 | 保留 | 系统没有可处理该链接的应用 | P0 |
| ui/DshMarkdown.kt:638 | 复制地址 | 保留 | 复制地址 | P1 |
| ui/DshMarkdown.kt:638 | 打开 | 保留 | 打开 | P1 |
| ui/DshMarkdown.kt:641 | 关闭 | 保留 | 关闭 | P1 |
| ui/DshMarkdown.kt:641 | 取消 | 保留 | 取消 | P0 |
| ui/DshMarkdown.kt:743 | 仅显示前 ${view.rows.size} 行（共 ${view.totalRows} 行） | 清晰度 | 仅显示前 {数量} 行（共 {数量} 行） | P1 |
| ui/DshMarkdown.kt:745 | 仅显示前 ${view.headers.size} 列 | 清晰度 | 仅显示前 {数量} 列 | P1 |
| ui/DshMarkdown.kt:746 | ；完整表格请在官方界面查看。 | 术语不一致 | 完整表格请在网页界面查看。 | P1 |
| ui/DshMarkdown.kt:799 | 代码 | 保留 | 代码 | P1 |
| ui/DshMarkdown.kt:805 | 行号 | 保留 | 行号 | P1 |
| ui/DshMarkdown.kt:805 | 隐藏行号 | 保留 | 隐藏行号 | P1 |
| ui/DshMarkdown.kt:807 | 复制 | 保留 | 复制 | P1 |
| ui/DshMarkdown.kt:826 | 仅显示前 ${view.lines.size} 行（共 ${view.totalLines} 行）；复制可获取完整代码。 | 清晰度 | 仅显示前 {行数} 行，复制可获取完整代码。 | P1 |
| ui/DshMarkdown.kt:851 | 图 | 保留 | 图 | P1 |
| ui/DshMarkdown.kt:855 | 图片：${image.alt.ifBlank { "未命名" }} | 清晰度 | 图片：{内容} | P1 |
| ui/DshMarkdown.kt:855 | 未命名 | 保留 | 未命名 | P1 |
| ui/DshMarkdown.kt:869 | 查看 | 保留 | 查看 | P1 |
| ui/DshMarkdown.kt:891 | 附 | 保留 | 附 | P1 |
| ui/DshMarkdown.kt:895 | 附件：${info.name} | 清晰度 | 附件：{名称} | P1 |
| ui/DshMarkdown.kt:909 | 查看 | 保留 | 查看 | P1 |
| ui/DshMarkdown.kt:932 | 图片加载失败：文件不可读、已损坏或格式不支持 | 清晰度 | 图片无法读取，请重新选择图片或用其他应用打开。 | P0 |
| ui/DshMarkdown.kt:938 | 图片：${image.alt.ifBlank { "未命名" }} | 清晰度 | 图片：{内容} | P1 |
| ui/DshMarkdown.kt:938 | 未命名 | 保留 | 未命名 | P1 |
| ui/DshMarkdown.kt:947 | 消息图片 | 保留 | 消息图片 | P1 |
| ui/DshMarkdown.kt:976 | 复制地址 | 保留 | 复制地址 | P1 |
| ui/DshMarkdown.kt:978 | 关闭 | 保留 | 关闭 | P1 |
| ui/DshMarkdown.kt:990 | 附件：${info.name} | 清晰度 | 附件：{名称} | P1 |
| ui/DshMarkdown.kt:1009 | 复制地址 | 保留 | 复制地址 | P1 |
| ui/DshMarkdown.kt:1011 | 关闭 | 保留 | 关闭 | P1 |

### [ui/DshNativeController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshNativeController.kt:153 | 通知会话 ${id.take(8)} | 术语不一致 | 通知对应的对话 | P1 |
| ui/DshNativeController.kt:198 | 当前无法新建会话，请检查 DSH 是否在运行 | 术语不一致 | 请先启动助手，再新建对话。 | P0 |
| ui/DshNativeController.kt:207 | 新会话 | 术语不一致 | 新对话 | P1 |
| ui/DshNativeController.kt:215 | 已创建会话 | 术语不一致 | 已创建对话 | P1 |
| ui/DshNativeController.kt:215 | 已创建并进入新会话 | 术语不一致 | 已创建并进入新对话 | P1 |
| ui/DshNativeController.kt:222 | 新建会话失败：${messageOf(error)} | 术语不一致 | 新建对话失败，请重试。 | P0 |
| ui/DshNativeController.kt:231 | 当前无法重命名会话 | 术语不一致 | 当前无法重命名对话 | P0 |
| ui/DshNativeController.kt:234 | 标题不能为空、不能超过 200 字或包含控制字符 | 清晰度 | 名称需为 1–200 个字，不能含有不可见字符。 | P0 |
| ui/DshNativeController.kt:236 | 会话已切换，请重新打开标题 | 术语不一致 | 对话已切换，请重新打开标题 | P1 |
| ui/DshNativeController.kt:241 | 会话已切换，请刷新查看标题 | 术语不一致 | 对话已切换，请刷新查看标题 | P1 |
| ui/DshNativeController.kt:325 | 消息过长（最多 $MAX_PROMPT_CHARS 字） | 清晰度 | 消息过长（最多 {内容} 字） | P1 |
| ui/DshNativeController.kt:352 | 已交给官方 DSH | 术语不一致 | 消息已发送给助手。 | P1 |
| ui/DshNativeController.kt:400 | 已请求停止当前回合 | 清晰度 | 已请求停止本次回复。 | P1 |
| ui/DshNativeController.kt:405 | 停止请求已发送；若仍在运行可再次点击 | 清晰度 | 已请求停止，请等待完成后检查任务状态。 | P1 |
| ui/DshNativeController.kt:785 | 官方接口返回 ${error.code} | 工程痕迹 | 请求未完成，请稍后重试。 | P1 |
| ui/DshNativeController.kt:786 | 官方接口响应格式不正确 | 工程痕迹 | 内容暂时无法显示，请刷新或切换到网页界面。 | P1 |
| ui/DshNativeController.kt:787 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |

### [ui/DshNativePage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshNativePage.kt:158 | 原生界面读取失败 | 工程痕迹、术语不一致 | 对话读取失败 | P0 |
| ui/DshNativePage.kt:159 | 操作未完成 | 保留 | 操作未完成 | P1 |
| ui/DshNativePage.kt:161 | 实时连接提示 | 清晰度 | 对话连接中断 | P1 |
| ui/DshNativePage.kt:161 | 重新连接 | 保留 | 重新连接 | P1 |
| ui/DshNativePage.kt:171 | 官方 DSH 尚无会话 | 术语不一致 | 还没有对话 | P0 |
| ui/DshNativePage.kt:172 | 可以直接新建一个会话，也可以到官方界面创建。原生页不另建会话库。 | 工程痕迹、被动/实现说明、术语不一致 | 新建一段对话，告诉助手你想完成的事。 | P1 |
| ui/DshNativePage.kt:215 | 重发上一条 | 保留 | 重发上一条 | P1 |
| ui/DshNativePage.kt:221 | 停止中… | 保留 | 停止中… | P1 |
| ui/DshNativePage.kt:221 | 停止生成 | 保留 | 停止生成 | P1 |
| ui/DshNativePage.kt:227 | 发送未完成 | 保留 | 发送未完成 | P1 |
| ui/DshNativePage.kt:251 | 重命名会话 | 术语不一致 | 重命名对话 | P1 |
| ui/DshNativePage.kt:257 | 会话名称 | 术语不一致 | 对话名称 | P1 |
| ui/DshNativePage.kt:274 | 重命名失败，请重试 | 保留 | 重命名失败，请重试 | P0 |
| ui/DshNativePage.kt:279 | 保存 | 保留 | 保存 | P1 |
| ui/DshNativePage.kt:279 | 保存中… | 保留 | 保存中… | P1 |
| ui/DshNativePage.kt:282 | 取消 | 保留 | 取消 | P0 |
| ui/DshNativePage.kt:319 | 重命名会话 | 术语不一致 | 重命名对话 | P1 |
| ui/DshNativePage.kt:330 | 重命名 | 保留 | 重命名 | P1 |
| ui/DshNativePage.kt:335 | 原生界面 | 工程痕迹、术语不一致 | 手机界面 | P1 |
| ui/DshNativePage.kt:336 | 实时 | 清晰度 | 正在同步 | P1 |
| ui/DshNativePage.kt:336 | 未实时 | 清晰度 | 同步已中断 | P1 |
| ui/DshNativePage.kt:337 | 运行中 | 保留 | 运行中 | P1 |
| ui/DshNativePage.kt:340 | 和官方界面是同一份会话，随时可切换 | 重复/啰嗦、工程痕迹 | 删除此处重复说明；界面切换规则保留在设置页。 | P1 |
| ui/DshNativePage.kt:351 | 新建中… | 保留 | 新建中… | P1 |
| ui/DshNativePage.kt:355 | 新建会话 | 术语不一致 | 新建对话 | P1 |
| ui/DshNativePage.kt:360 | 刷新官方会话 | 术语不一致 | 刷新对话 | P1 |
| ui/DshNativePage.kt:362 | 官方界面 | 术语不一致 | 网页界面 | P1 |
| ui/DshNativePage.kt:525 | 开始对话 | 保留 | 开始对话 | P1 |
| ui/DshNativePage.kt:526 | 这个会话还没有消息，在下面输入就能开始 | 术语不一致 | 输入第一条消息，告诉助手你想完成的事。 | P0 |
| ui/DshNativePage.kt:589 | DSH 正在处理；回复出现后会实时显示。 | 术语不一致 | 助手正在处理，回复会自动显示。 | P1 |
| ui/DshNativePage.kt:601 | $unseen 条新消息 | 清晰度 | {内容} 条新消息 | P1 |
| ui/DshNativePage.kt:601 | ↓ 最新 | 保留 | ↓ 最新 | P1 |
| ui/DshNativePage.kt:635 | 以下为新消息 | 保留 | 以下为新消息 | P1 |
| ui/DshNativePage.kt:652 | 正在加载更早历史… | 保留 | 正在加载更早历史… | P1 |
| ui/DshNativePage.kt:654 | 加载更早历史 | 清晰度 | 查看更早消息 | P1 |
| ui/DshNativePage.kt:656 | 已到会话最早一条 | 术语不一致 | 已到最早消息 | P1 |
| ui/DshNativePage.kt:659 | 更早历史加载失败：$it | 清晰度 | 更早消息读取失败，请重试。 | P0 |
| ui/DshNativePage.kt:661 | 已释放 ${state.releasedOlder} 条更早消息，向上滚动会重新读取 | 清晰度 | 向上滚动可继续查看更早的消息。 | P1 |
| ui/DshNativePage.kt:676 | 你 · 发送中 | 保留 | 你 · 发送中 | P1 |
| ui/DshNativePage.kt:681 | 正在发送附件… | 保留 | 正在发送附件… | P1 |
| ui/DshNativePage.kt:714 | 已结束 | 保留 | 已结束 | P1 |
| ui/DshNativePage.kt:714 | 运行中 | 保留 | 运行中 | P1 |
| ui/DshNativePage.kt:747 | 你 · 发送中 | 保留 | 你 · 发送中 | P1 |
| ui/DshNativePage.kt:760 | 复制 | 保留 | 复制 | P1 |
| ui/DshNativePage.kt:777 | DSH 正在回复 | 术语不一致 | 助手正在回复 | P1 |
| ui/DshNativePage.kt:805 | 发送失败 | 保留 | 发送失败 | P0 |
| ui/DshNativePage.kt:810 | （附件消息） | 保留 | （附件消息） | P1 |
| ui/DshNativePage.kt:823 | 重试 | 保留 | 重试 | P1 |
| ui/DshNativePage.kt:824 | 填入输入框 | 保留 | 填入输入框 | P1 |
| ui/DshNativePage.kt:828 | 复制内容 | 保留 | 复制内容 | P1 |
| ui/DshNativePage.kt:838 | 重试 | 保留 | 重试 | P1 |
| ui/DshNativePage.kt:855 | 复制错误 | 保留 | 复制错误 | P0 |
| ui/DshNativePage.kt:896 | ＋ 新建会话 | 术语不一致 | ＋ 新建对话 | P1 |
| ui/DshNativePage.kt:901 | 刷新 | 保留 | 刷新 | P1 |
| ui/DshNativePage.kt:942 | 时间未知 | 被动/实现说明 | 时间未知 | P1 |
| ui/DshNativePage.kt:945 | 时间未知 | 被动/实现说明 | 时间未知 | P1 |

### [ui/DshPlanCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshPlanCard.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshPlanCard.kt:58 | 执行计划 | 保留 | 执行计划 | P1 |
| ui/DshPlanCard.kt:58 | 计划审阅 | 清晰度 | 确认执行计划 | P1 |
| ui/DshPlanCard.kt:79 | 进度未知 · 共 ${plan.total} 步 | 被动/实现说明 | 共 {步数} 步，正在同步进度。 | P1 |
| ui/DshPlanCard.kt:81 | 进度 ${plan.completed}/${plan.total} | 清晰度 | 进度 {数量}/{数量} | P1 |
| ui/DshPlanCard.kt:88 | 当前：$title | 清晰度 | 当前：{名称} | P1 |
| ui/DshPlanCard.kt:98 | 计划进度未知，共 ${plan.total} 步 | 被动/实现说明 | 共 {步数} 步，正在同步进度。 | P1 |
| ui/DshPlanCard.kt:105 | 计划进度 ${plan.completed}/${plan.total} | 清晰度 | 计划进度 {数量}/{数量} | P1 |
| ui/DshPlanCard.kt:113 | 仅显示前 ${plan.steps.size} 个步骤；完整计划请到官方界面查看。 | 术语不一致 | 当前仅显示部分步骤，完整计划请在网页界面查看。 | P1 |
| ui/DshPlanCard.kt:119 | 同意执行会按当前计划继续；要求修改不会提交批准。 | 清晰度 | 同意后将按此计划执行。需要调整时请选择“要求修改”。 | P1 |
| ui/DshPlanCard.kt:126 | 要求修改 | 保留 | 要求修改 | P1 |
| ui/DshPlanCard.kt:130 | 同意执行 | 保留 | 同意执行 | P1 |
| ui/DshPlanCard.kt:130 | 提交中… | 保留 | 提交中… | P1 |
| ui/DshPlanCard.kt:134 | 计划待审阅；当前版本尚未接入审阅操作，请到官方界面处理。 | 被动/实现说明、术语不一致 | 请在网页界面确认或修改计划。 | P1 |
| ui/DshPlanCard.kt:138 | 已同意执行；后续步骤状态会随官方事件更新。 | 清晰度 | 已同意执行，将自动更新步骤进度。 | P0 |
| ui/DshPlanCard.kt:141 | 已要求修改；计划不会按当前版本执行。 | 清晰度 | 已要求修改，当前计划不会执行。 | P1 |
| ui/DshPlanCard.kt:168 | ${step.index + 1}. ${step.title} | 清晰度 | {内容}. {名称} | P1 |

### [ui/DshQuestionCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshQuestionCard.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshQuestionCard.kt:57 | 用户提问 | 清晰度 | 请你回答 | P1 |
| ui/DshQuestionCard.kt:62 | 请选择后提交；回答会走官方协议。 | 清晰度 | 请选择后提交回答。 | P1 |
| ui/DshQuestionCard.kt:66 | 待协议确认 | 被动/实现说明 | 请在网页回答 | P1 |
| ui/DshQuestionCard.kt:66 | 待回答 | 保留 | 待回答 | P1 |
| ui/DshQuestionCard.kt:83 | 回答入口待协议确认：当前只展示提问与本地选择，不会发送官方回答。 | 被动/实现说明 | 请在网页界面回答，此处选择暂不发送。 | P1 |
| ui/DshQuestionCard.kt:91 | 提交中… | 保留 | 提交中… | P1 |
| ui/DshQuestionCard.kt:91 | 提交回答 | 保留 | 提交回答 | P1 |
| ui/DshQuestionCard.kt:116 |  * | 清晰度 | * | P1 |
| ui/DshQuestionCard.kt:121 | ，可多选 | 保留 | ，可多选 | P1 |
| ui/DshQuestionCard.kt:159 | 其他说明（可选） | 保留 | 其他说明（可选） | P1 |
| ui/DshQuestionCard.kt:159 | 其他说明（必填） | 保留 | 其他说明（必填） | P1 |

### [ui/DshSessionManagerController.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshSessionManagerController.kt:25 | 全部 | 保留 | 全部 | P1 |
| ui/DshSessionManagerController.kt:25 | 未读 | 保留 | 未读 | P1 |
| ui/DshSessionManagerController.kt:25 | 运行中 | 保留 | 运行中 | P1 |
| ui/DshSessionManagerController.kt:235 | 目标会话不在当前列表，可能已归档 | 术语不一致 | 找不到这段对话，可检查是否已归档。 | P1 |
| ui/DshSessionManagerController.kt:294 | 置顶保存失败：${messageOf(stored.exceptionOrNull()!!)} | 工程痕迹 | 置顶未保存，请重试。 | P0 |
| ui/DshSessionManagerController.kt:301 | 已取消置顶 | 保留 | 已取消置顶 | P0 |
| ui/DshSessionManagerController.kt:301 | 已置顶（仅本机，不写回官方） | 清晰度 | 已在本机置顶。 | P1 |
| ui/DshSessionManagerController.kt:344 | 标题不能超过 200 字或包含控制字符 | 清晰度 | 名称最多 200 个字，不能含有不可见字符。 | P1 |
| ui/DshSessionManagerController.kt:350 | 已创建会话 | 术语不一致 | 已创建对话 | P1 |
| ui/DshSessionManagerController.kt:353 | 已创建并命名会话 | 术语不一致 | 已创建并命名对话 | P1 |
| ui/DshSessionManagerController.kt:354 | 会话已创建，但重命名失败：${messageOf(it)} | 术语不一致 | 对话已创建，但名称未保存。请重新命名。 | P0 |
| ui/DshSessionManagerController.kt:371 | 标题不能为空、不能超过 200 字或包含控制字符 | 清晰度 | 名称需为 1–200 个字，不能含有不可见字符。 | P0 |
| ui/DshSessionManagerController.kt:377 | 已重命名 | 保留 | 已重命名 | P1 |
| ui/DshSessionManagerController.kt:397 | 已停止运行并归档 | 保留 | 已停止运行并归档 | P1 |
| ui/DshSessionManagerController.kt:397 | 已归档 | 保留 | 已归档 | P1 |
| ui/DshSessionManagerController.kt:420 | 一次最多归档 $MAX_BATCH_ARCHIVE 个会话，请减少选择后重试；当前选择已保留 | 术语不一致 | 一次最多归档 {数量} 段对话，请减少选择后重试。 | P1 |
| ui/DshSessionManagerController.kt:439 | 已归档 ${archived.size} 个会话 | 术语不一致 | 已归档 {数量} 个对话 | P1 |
| ui/DshSessionManagerController.kt:441 | 已归档 ${archived.size} 个会话，${failures.size} 个失败 | 术语不一致 | 已归档 {数量} 个对话，{数量} 个失败 | P0 |
| ui/DshSessionManagerController.kt:449 | 未归档：$summary$suffix | 清晰度 | 未归档：{内容}{内容} | P1 |
| ui/DshSessionManagerController.kt:550 | 技能目录读取失败：${messageOf(skillsResult.exceptionOrNull()!!)} | 工程痕迹 | 技能列表读取失败，请重试。 | P0 |
| ui/DshSessionManagerController.kt:551 | 命令目录读取失败：${messageOf(commandsResult.exceptionOrNull()!!)} | 工程痕迹 | 命令列表读取失败，请重试。 | P0 |
| ui/DshSessionManagerController.kt:570 | 已切换模型 | 保留 | 已切换模型 | P1 |
| ui/DshSessionManagerController.kt:580 | 会话 ${sessionId.take(8)} 模型切换失败：$message | 工程痕迹、术语不一致 | 模型切换失败，请在这段对话中重试。 | P0 |
| ui/DshSessionManagerController.kt:595 | 权限模式不可选择 | 保留 | 权限模式不可选择 | P0 |
| ui/DshSessionManagerController.kt:599 | 完全权限需要用户明确确认 | 保留 | 完全权限需要用户明确确认 | P0 |
| ui/DshSessionManagerController.kt:605 | 已切换权限模式 | 保留 | 已切换权限模式 | P0 |
| ui/DshSessionManagerController.kt:615 | 会话 ${sessionId.take(8)} 权限切换失败：$message | 工程痕迹、术语不一致 | 权限切换失败，请在这段对话中重试。 | P0 |
| ui/DshSessionManagerController.kt:719 | 安全模式下会话管理为只读，不能修改会话 | 术语不一致 | 安全模式下只能查看对话，恢复正常模式后可修改。 | P1 |
| ui/DshSessionManagerController.kt:759 | 操作已完成，但列表刷新失败：${messageOf(error)} | 清晰度 | 操作已完成，列表刷新失败。请刷新查看结果。 | P0 |
| ui/DshSessionManagerController.kt:813 | 会话仍在运行，请确认停止当前工作后再归档 | 术语不一致 | 对话仍在运行，请确认停止当前工作后再归档 | P1 |
| ui/DshSessionManagerController.kt:814 | 会话目录冲突，未执行操作 | 术语不一致 | 对话目录冲突，未执行操作 | P1 |
| ui/DshSessionManagerController.kt:815 | 会话正被其他 Agent 使用 | 术语不一致 | 其他助手正在使用这段对话，请稍后重试。 | P1 |
| ui/DshSessionManagerController.kt:816 | 会话标题不合法 | 术语不一致 | 对话标题不合法 | P1 |
| ui/DshSessionManagerController.kt:817 | 模型服务凭据不可用 | 清晰度 | 模型服务登录信息不可用，请在网页界面检查模型设置。 | P1 |
| ui/DshSessionManagerController.kt:818 | 模型目录暂时不可用 | 清晰度 | 模型列表暂不可用，请稍后重试。 | P1 |
| ui/DshSessionManagerController.kt:819 | 所选模型不可用 | 保留 | 所选模型不可用 | P1 |
| ui/DshSessionManagerController.kt:820 | 当前会话不支持所选模型 | 术语不一致 | 当前对话不支持所选模型 | P1 |
| ui/DshSessionManagerController.kt:821 | 会话需要迁移，原生管理暂不可用 | 工程痕迹、术语不一致 | 请在网页界面完成这段对话的升级后再管理。 | P1 |
| ui/DshSessionManagerController.kt:822 | 官方不支持该权限预设 | 清晰度 | 当前权限选项无法使用，请选择其他选项。 | P0 |
| ui/DshSessionManagerController.kt:823 | 官方拒绝了权限切换 | 清晰度 | 权限切换未成功，请检查权限设置后重试。 | P0 |
| ui/DshSessionManagerController.kt:824 | 官方未识别该命令 | 清晰度 | 找不到这条命令，请重新选择。 | P1 |
| ui/DshSessionManagerController.kt:825 | 会话不存在或已被归档 | 术语不一致 | 对话不存在或已被归档 | P1 |
| ui/DshSessionManagerController.kt:826 | 官方接口返回 ${error.code} | 工程痕迹 | 请求未完成，请稍后重试。 | P1 |
| ui/DshSessionManagerController.kt:830 | 官方接口响应格式不正确 | 工程痕迹 | 内容暂时无法显示，请刷新或切换到网页界面。 | P1 |
| ui/DshSessionManagerController.kt:831 | 未知错误 | 被动/实现说明 | 未知错误 | P0 |

### [ui/DshSessionManagerPage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshSessionManagerPage.kt:108 | 概览 | 保留 | 概览 | P1 |
| ui/DshSessionManagerPage.kt:109 | 进度 | 保留 | 进度 | P1 |
| ui/DshSessionManagerPage.kt:110 | 产物 | 清晰度 | 生成文件 | P1 |
| ui/DshSessionManagerPage.kt:111 | 文件 | 保留 | 文件 | P1 |
| ui/DshSessionManagerPage.kt:226 | 新建会话 | 术语不一致 | 新建对话 | P1 |
| ui/DshSessionManagerPage.kt:260 | 刷新失败，仍显示上次结果：$message | 清晰度 | 刷新失败，当前显示上次结果。请重试。 | P0 |
| ui/DshSessionManagerPage.kt:301 | DSH 未运行 | 术语不一致 | 助手未启动 | P1 |
| ui/DshSessionManagerPage.kt:302 | 会话管理复用当前 DSH 实例的 loopback 接口。请先在应用内启动 DSH，再回到这里读取官方会话。 | 工程痕迹、术语不一致、长度 | 请先在工作台启动助手，再回来查看对话。 | P1 |
| ui/DshSessionManagerPage.kt:311 | 官方 DSH 尚无会话 | 术语不一致 | 还没有对话 | P0 |
| ui/DshSessionManagerPage.kt:311 | 尚未读取会话 | 术语不一致 | 尚未读取对话 | P1 |
| ui/DshSessionManagerPage.kt:313 | 原生页不另建会话库；新建会话会直接写入官方 DSH。 | 工程痕迹、被动/实现说明、术语不一致 | 新建的对话会与网页界面同步。 | P1 |
| ui/DshSessionManagerPage.kt:315 | 点击读取，从当前 DSH 实例拉取官方会话列表。 | 术语不一致 | 点“读取对话”查看当前助手的对话列表。 | P1 |
| ui/DshSessionManagerPage.kt:318 | 读取会话 | 术语不一致 | 读取对话 | P1 |
| ui/DshSessionManagerPage.kt:320 | 新建会话 | 术语不一致 | 新建对话 | P1 |
| ui/DshSessionManagerPage.kt:325 | 刷新列表 | 保留 | 刷新列表 | P1 |
| ui/DshSessionManagerPage.kt:362 | 没有匹配的会话 | 术语不一致 | 没有匹配对话，试试其他关键词或清除筛选。 | P0 |
| ui/DshSessionManagerPage.kt:363 | 没有未读会话 | 术语不一致 | 没有未读对话 | P0 |
| ui/DshSessionManagerPage.kt:364 | 没有运行中的会话 | 术语不一致 | 没有正在处理的对话 | P0 |
| ui/DshSessionManagerPage.kt:365 | 没有可显示的会话 | 术语不一致 | 还没有对话 | P0 |
| ui/DshSessionManagerPage.kt:367 | 标题、会话 ID 会随输入即时过滤；消息内容搜索需要单独点击搜索图标。 | 术语不一致 | 输入名称可筛选对话。要搜索消息，请点搜索图标。 | P1 |
| ui/DshSessionManagerPage.kt:368 | 清除搜索与筛选 | 清晰度 | 清除筛选 | P1 |
| ui/DshSessionManagerPage.kt:433 | 新建会话 | 术语不一致 | 新建对话 | P1 |
| ui/DshSessionManagerPage.kt:434 | 标题可留空；创建立即调用官方 session/create，非空标题随后调用官方重命名。 | 长度 | 名称可留空，创建后也能随时重命名。 | P1 |
| ui/DshSessionManagerPage.kt:441 | 标题（可留空） | 保留 | 标题（可留空） | P1 |
| ui/DshSessionManagerPage.kt:447 | 取消 | 保留 | 取消 | P0 |
| ui/DshSessionManagerPage.kt:450 | 创建 | 保留 | 创建 | P1 |
| ui/DshSessionManagerPage.kt:450 | 创建中… | 保留 | 创建中… | P1 |
| ui/DshSessionManagerPage.kt:464 | 重命名会话 | 术语不一致 | 重命名对话 | P1 |
| ui/DshSessionManagerPage.kt:470 | 新标题 | 保留 | 新标题 | P1 |
| ui/DshSessionManagerPage.kt:475 | 最多 200 字；保存后以官方返回的标题为准。 | 清晰度 | 最多 200 个字。 | P1 |
| ui/DshSessionManagerPage.kt:480 | 取消 | 保留 | 取消 | P0 |
| ui/DshSessionManagerPage.kt:488 | 保存 | 保留 | 保存 | P1 |
| ui/DshSessionManagerPage.kt:505 | 归档会话 | 术语不一致 | 归档对话 | P1 |
| ui/DshSessionManagerPage.kt:509 | 该会话正在运行。归档会请求官方停止当前回合、子代理、后台任务与提醒，然后提交归档；会话会从原生列表移除，项目文件不会删除。本页不提供撤销按钮，请确认后继续。 | 工程痕迹、被动/实现说明、术语不一致、长度 | 归档会停止这段对话的当前任务，并从列表移除。<br>项目文件保留，此页无法撤销归档。 | P0 |
| ui/DshSessionManagerPage.kt:511 | 归档后该会话会从原生列表移除，项目文件不会删除。官方接口当前没有会话硬删除端点，本页不提供删除，也不提供撤销按钮；请确认后继续。 | 工程痕迹、被动/实现说明、术语不一致、长度 | 归档会把对话从列表移除，项目文件保留。<br>此页无法撤销归档。 | P0 |
| ui/DshSessionManagerPage.kt:517 | 取消 | 保留 | 取消 | P0 |
| ui/DshSessionManagerPage.kt:524 | 停止并归档 | 保留 | 停止并归档 | P1 |
| ui/DshSessionManagerPage.kt:524 | 归档 | 保留 | 归档 | P1 |
| ui/DshSessionManagerPage.kt:541 | 批量归档 ${batchTargets?.size ?: 0} 个会话 | 术语不一致 | 归档所选对话？ | P1 |
| ui/DshSessionManagerPage.kt:547 | 已选择 ${ids.size} 个会话 | 术语不一致 | 已选择 {数量} 个对话 | P1 |
| ui/DshSessionManagerPage.kt:548 | ，其中 $running 个正在运行 | 清晰度 | ，其中 {内容} 个正在运行 | P1 |
| ui/DshSessionManagerPage.kt:549 | 。归档会逐个调用官方归档接口 | 清晰度 | 将逐段归档所选对话 | P1 |
| ui/DshSessionManagerPage.kt:550 | ，并先请求停止运行中的会话 | 术语不一致 | ，并先请求停止运行中的对话 | P1 |
| ui/DshSessionManagerPage.kt:551 | ；会话会从列表移除，项目文件不会删除。 | 术语不一致 | 归档后从列表移除，项目文件保留。 | P0 |
| ui/DshSessionManagerPage.kt:552 | 官方没有会话硬删除端点，本页不提供批量删除，也不提供撤销按钮。 | 被动/实现说明、术语不一致 | 此页无法撤销归档。 | P0 |
| ui/DshSessionManagerPage.kt:557 | 取消 | 保留 | 取消 | P0 |
| ui/DshSessionManagerPage.kt:559 | 停止并归档 | 保留 | 停止并归档 | P1 |
| ui/DshSessionManagerPage.kt:559 | 归档 | 保留 | 归档 | P1 |
| ui/DshSessionManagerPage.kt:577 | 一次最多归档 ${DshSessionManagerController.MAX_BATCH_ARCHIVE} 个会话 | 术语不一致 | 一次最多归档 {内容} 个对话 | P1 |
| ui/DshSessionManagerPage.kt:580 | 当前已选择 ${selectedIdSet.size} 个会话，超过单次上限。请取消部分选择后再归档；当前选择会保留，不会静默截断。 | 术语不一致、长度 | 已选数量超过单次上限，请取消部分选择后再归档。 | P0 |
| ui/DshSessionManagerPage.kt:584 | 知道了 | 保留 | 知道了 | P1 |
| ui/DshSessionManagerPage.kt:594 | 完全权限二次确认 | 清晰度 | 允许更大访问范围？ | P0 |
| ui/DshSessionManagerPage.kt:601 | 你正在把会话切到完全权限。这是高于“工作区写入”的第二档风险，请逐条确认后果： | 术语不一致 | 完全权限允许助手访问工作区外的文件。<br>请阅读以下影响，再决定是否切换。 | P0 |
| ui/DshSessionManagerPage.kt:606 | • | 保留 | • | P1 |
| ui/DshSessionManagerPage.kt:620 | 我已阅读并理解上述风险，仍要切换 | 清晰度 | 我已了解风险，仍要切换 | P1 |
| ui/DshSessionManagerPage.kt:623 | Tanuki 不会自动切换该模式；切换通过官方 /permission 命令提交。 | 重复/啰嗦、工程痕迹 | 删除命令实现说明；保留完全权限确认和风险。 | P1 |
| ui/DshSessionManagerPage.kt:631 | 保持当前权限 | 保留 | 保持当前权限 | P0 |
| ui/DshSessionManagerPage.kt:638 | 仍要切换为完全权限 | 清晰度 | 切换权限 | P0 |
| ui/DshSessionManagerPage.kt:676 | 返回会话详情 | 术语不一致 | 返回对话详情 | P1 |
| ui/DshSessionManagerPage.kt:681 | 会话详情 | 术语不一致 | 对话详情 | P1 |
| ui/DshSessionManagerPage.kt:682 | 模型与推理强度 | 清晰度 | 模型与思考深度 | P1 |
| ui/DshSessionManagerPage.kt:683 | 权限模式 | 保留 | 权限模式 | P0 |
| ui/DshSessionManagerPage.kt:684 | Skills 与斜杠命令 | 清晰度 | 技能与命令 | P1 |
| ui/DshSessionManagerPage.kt:694 | 关闭详情 | 保留 | 关闭详情 | P1 |
| ui/DshSessionManagerPage.kt:696 | 操作未完成 | 保留 | 操作未完成 | P1 |
| ui/DshSessionManagerPage.kt:736 | 已复制会话 ID | 术语不一致 | 对话编号已复制 | P1 |
| ui/DshSessionManagerPage.kt:745 | 已复制工作目录 | 保留 | 已复制工作目录 | P1 |
| ui/DshSessionManagerPage.kt:800 | 返回 | 保留 | 返回 | P1 |
| ui/DshSessionManagerPage.kt:802 | 原生会话 | 工程痕迹、术语不一致 | 对话管理 | P1 |
| ui/DshSessionManagerPage.kt:808 | DSH 未运行 | 术语不一致 | 助手未启动 | P1 |
| ui/DshSessionManagerPage.kt:809 | 安全模式 · 只读 | 保留 | 安全模式 · 只读 | P1 |
| ui/DshSessionManagerPage.kt:810 | 远程 DSH · 已连接 | 术语不一致 | 电脑助手已连接 | P1 |
| ui/DshSessionManagerPage.kt:811 | $runningCount 个会话运行中 | 术语不一致 | {数量} 个对话运行中 | P1 |
| ui/DshSessionManagerPage.kt:812 | 已连接官方 DSH | 术语不一致 | 已连接官方 助手 | P1 |
| ui/DshSessionManagerPage.kt:820 | 刷新官方会话 | 术语不一致 | 刷新对话 | P1 |
| ui/DshSessionManagerPage.kt:824 | 取消选择 | 清晰度 | 已连接助手 | P0 |
| ui/DshSessionManagerPage.kt:824 | 选择 | 保留 | 选择 | P1 |
| ui/DshSessionManagerPage.kt:849 | ${host.label} · ${host.detail} | 清晰度 | {名称} · {来源} | P1 |
| ui/DshSessionManagerPage.kt:850 | 切换主机 | 清晰度 | 切换设备 | P1 |
| ui/DshSessionManagerPage.kt:869 | 切换主机 | 清晰度 | 切换设备 | P1 |
| ui/DshSessionManagerPage.kt:871 | 会话列表、模型与权限会读取当前选中主机的 DSH；切换不会删除本地或远程的会话与项目文件。 | 术语不一致、长度 | 切换后将显示所选设备上的对话、模型和权限。<br>本机和电脑上的对话、项目保留。 | P0 |
| ui/DshSessionManagerPage.kt:902 | 当前不可用。 | 保留 | 当前不可用。 | P1 |
| ui/DshSessionManagerPage.kt:902 | 请先在设置 → 远程主机保存并测试连接。 | 术语不一致 | 请先在设置的“远程电脑”中保存并测试连接。 | P1 |
| ui/DshSessionManagerPage.kt:913 | 远程连接失败会自动回退本机；切回本机不会自动停止远程正在运行的任务。 | 清晰度 | 电脑连接失败时会回到本机。<br>切回本机后，电脑上的任务仍会继续。 | P0 |
| ui/DshSessionManagerPage.kt:917 | 关闭 | 保留 | 关闭 | P1 |
| ui/DshSessionManagerPage.kt:943 | 搜索标题或会话 ID | 术语不一致 | 搜索对话名称 | P1 |
| ui/DshSessionManagerPage.kt:947 | 清除搜索 | 保留 | 清除搜索 | P1 |
| ui/DshSessionManagerPage.kt:952 | 搜索会话内容 | 术语不一致 | 搜索对话内容 | P1 |
| ui/DshSessionManagerPage.kt:972 | 等待读取 | 保留 | 等待读取 | P1 |
| ui/DshSessionManagerPage.kt:973 | 0 个会话 | 术语不一致 | 0 个对话 | P1 |
| ui/DshSessionManagerPage.kt:974 | ${state.sessions.size} 个会话 | 术语不一致 | {数量} 个对话 | P1 |
| ui/DshSessionManagerPage.kt:975 | 筛选 $visibleCount / ${state.sessions.size} | 清晰度 | 筛选 {数量} / {数量} | P1 |
| ui/DshSessionManagerPage.kt:983 | 输入即过滤标题与 ID，不阻断列表；消息内容需点搜索图标。 | 清晰度 | 输入名称即可筛选，搜索消息请点右侧图标。 | P1 |
| ui/DshSessionManagerPage.kt:989 | 未读仅本机记录 | 清晰度 | 未读状态仅保存在本机。 | P1 |
| ui/DshSessionManagerPage.kt:1044 | 取消置顶 | 保留 | 取消置顶 | P0 |
| ui/DshSessionManagerPage.kt:1044 | 置顶 | 保留 | 置顶 | P1 |
| ui/DshSessionManagerPage.kt:1066 | 未读 | 保留 | 未读 | P1 |
| ui/DshSessionManagerPage.kt:1067 | 置顶 | 保留 | 置顶 | P1 |
| ui/DshSessionManagerPage.kt:1068 | 空白会话 | 术语不一致 | 空白对话 | P1 |
| ui/DshSessionManagerPage.kt:1069 | 子代理 | 清晰度 | 子任务助手 | P1 |
| ui/DshSessionManagerPage.kt:1071 | 需迁移 | 清晰度 | 需要升级 | P1 |
| ui/DshSessionManagerPage.kt:1086 | Agent 在线 | 清晰度 | 助手在线 | P1 |
| ui/DshSessionManagerPage.kt:1099 | 运行中 | 保留 | 运行中 | P1 |
| ui/DshSessionManagerPage.kt:1100 | 未读 | 保留 | 未读 | P1 |
| ui/DshSessionManagerPage.kt:1101 | 置顶 | 保留 | 置顶 | P1 |
| ui/DshSessionManagerPage.kt:1102 | 空白会话 | 术语不一致 | 空白对话 | P1 |
| ui/DshSessionManagerPage.kt:1103 | 子代理 | 清晰度 | 子任务助手 | P1 |
| ui/DshSessionManagerPage.kt:1104 | Agent 在线 | 清晰度 | 助手在线 | P1 |
| ui/DshSessionManagerPage.kt:1105 | 需迁移 | 清晰度 | 需要升级 | P1 |
| ui/DshSessionManagerPage.kt:1106 | 当前 | 保留 | 当前 | P1 |
| ui/DshSessionManagerPage.kt:1159 | 已选 $selectedCount 个 | 清晰度 | 已选 {数量} 个 | P1 |
| ui/DshSessionManagerPage.kt:1160 |  · $runningCount 个运行中 | 清晰度 | · {数量} 个运行中 | P1 |
| ui/DshSessionManagerPage.kt:1166 | 停止并归档 | 保留 | 停止并归档 | P1 |
| ui/DshSessionManagerPage.kt:1166 | 归档 | 保留 | 归档 | P1 |
| ui/DshSessionManagerPage.kt:1173 | 取消 | 保留 | 取消 | P0 |
| ui/DshSessionManagerPage.kt:1180 | 删除：官方无会话硬删除端点，暂不支持；归档只从列表移除，不删除项目文件。 | 被动/实现说明、术语不一致 | 归档后从列表移除，项目文件保留。 | P0 |
| ui/DshSessionManagerPage.kt:1201 | 正在批量归档 | 保留 | 正在批量归档 | P1 |
| ui/DshSessionManagerPage.kt:1204 | 已完成 $done / $total；请保持页面打开，全部完成后会刷新列表。 | 清晰度 | 已完成 {内容} / {数量}；请保持页面打开，全部完成后会刷新列表。 | P1 |
| ui/DshSessionManagerPage.kt:1227 | 消息内容搜索 | 保留 | 消息内容搜索 | P1 |
| ui/DshSessionManagerPage.kt:1228 | 清除 | 保留 | 清除 | P1 |
| ui/DshSessionManagerPage.kt:1232 | 点击搜索框右侧的搜索图标，调用官方接口搜索消息内容。 | 工程痕迹 | 点搜索框右侧的图标，搜索对话消息。 | P1 |
| ui/DshSessionManagerPage.kt:1235 | 没有匹配的消息。 | 保留 | 没有匹配的消息。 | P0 |
| ui/DshSessionManagerPage.kt:1239 | 会话 ${hit.sessionId.take(8)} | 工程痕迹、术语不一致 | 未命名对话 | P1 |
| ui/DshSessionManagerPage.kt:1258 | 仅显示官方返回的前 20 条，请缩小搜索词。 | 清晰度 | 最多显示 20 条结果，可换更具体的关键词。 | P1 |
| ui/DshSessionManagerPage.kt:1281 | 关闭 | 保留 | 关闭 | P1 |
| ui/DshSessionManagerPage.kt:1338 | 会话 | 术语不一致 | 对话 | P1 |
| ui/DshSessionManagerPage.kt:1339 | 只读取当前 DSH 实例，不会新建或改写会话。 | 重复/啰嗦、工程痕迹 | 删除此处实现说明；保留对话列表。 | P1 |
| ui/DshSessionManagerPage.kt:1373 | 读取官方会话失败 | 术语不一致 | 对话读取失败 | P0 |
| ui/DshSessionManagerPage.kt:1375 | 官方接口没有返回可用的会话列表。 | 工程痕迹、术语不一致 | 暂时无法读取对话列表。 | P0 |
| ui/DshSessionManagerPage.kt:1376 | \n请确认 DSH 正在运行、远程隧道已连接，或稍后重试。 | 术语不一致 | 请确认助手已启动、电脑已连接，再重试。 | P1 |
| ui/DshSessionManagerPage.kt:1378 | 重试 | 保留 | 重试 | P1 |
| ui/DshSessionManagerPage.kt:1407 | 本机置顶 | 保留 | 本机置顶 | P1 |
| ui/DshSessionManagerPage.kt:1409 | ID：${session.id} | 清晰度 | 对话编号：{编号} | P1 |
| ui/DshSessionManagerPage.kt:1410 | 更新时间：${timeLabel(session.updatedAt)} | 清晰度 | 更新时间：{名称} | P0 |
| ui/DshSessionManagerPage.kt:1411 | 工作目录：$it | 清晰度 | 工作目录：{内容} | P1 |
| ui/DshSessionManagerPage.kt:1413 | 会话设置 | 术语不一致 | 对话设置 | P1 |
| ui/DshSessionManagerPage.kt:1414 | 模型与推理强度 | 清晰度 | 模型与思考深度 | P1 |
| ui/DshSessionManagerPage.kt:1416 | 权限模式 | 保留 | 权限模式 | P0 |
| ui/DshSessionManagerPage.kt:1417 | 未知 | 被动/实现说明 | 未知 | P1 |
| ui/DshSessionManagerPage.kt:1420 | Skills 与斜杠命令 | 清晰度 | 技能与命令 | P1 |
| ui/DshSessionManagerPage.kt:1420 | 查看官方按当前会话返回的目录 | 术语不一致 | 查看这段对话可用的技能和命令。 | P1 |
| ui/DshSessionManagerPage.kt:1422 | 取消本机置顶 | 保留 | 取消本机置顶 | P0 |
| ui/DshSessionManagerPage.kt:1422 | 置顶会话 | 术语不一致 | 置顶对话 | P1 |
| ui/DshSessionManagerPage.kt:1423 | 仅调整本机列表顺序，不写回官方 | 清晰度 | 仅调整本机列表顺序。 | P1 |
| ui/DshSessionManagerPage.kt:1423 | 恢复按更新时间排序 | 保留 | 恢复按更新时间排序 | P0 |
| ui/DshSessionManagerPage.kt:1428 | 安全模式/试用模式下只读，不能重命名、归档或切换会话设置。可在设置中尝试恢复正常模式后再修改。 | 术语不一致、长度 | 安全模式下只能查看，恢复正常模式后可修改。 | P1 |
| ui/DshSessionManagerPage.kt:1430 | 最多 200 字，保存后以官方返回标题为准 | 清晰度 | 最多 200 个字。 | P1 |
| ui/DshSessionManagerPage.kt:1430 | 重命名会话 | 术语不一致 | 重命名对话 | P1 |
| ui/DshSessionManagerPage.kt:1432 | 归档会话 | 术语不一致 | 归档对话 | P1 |
| ui/DshSessionManagerPage.kt:1433 | 从原生列表移除；不删除项目文件 | 工程痕迹、术语不一致 | 从列表移除，项目文件保留。 | P0 |
| ui/DshSessionManagerPage.kt:1433 | 会先请求停止当前回合、子代理、后台任务与提醒，再从列表移除 | 清晰度 | 先停止当前任务，再从列表移除，项目文件保留。 | P1 |
| ui/DshSessionManagerPage.kt:1438 | 删除会话 | 术语不一致 | 删除对话 | P0 |
| ui/DshSessionManagerPage.kt:1443 | 复制会话 ID | 术语不一致 | 复制对话编号 | P1 |
| ui/DshSessionManagerPage.kt:1481 | $title（官方接口不支持） | 工程痕迹 | {功能名称}，请在网页界面使用。 | P1 |
| ui/DshSessionManagerPage.kt:1496 | 运行状态 | 保留 | 运行状态 | P1 |
| ui/DshSessionManagerPage.kt:1497 | 空闲 | 保留 | 空闲 | P1 |
| ui/DshSessionManagerPage.kt:1497 | 运行中 | 保留 | 运行中 | P1 |
| ui/DshSessionManagerPage.kt:1499 | Agent 不在线 | 清晰度 | 助手离线 | P1 |
| ui/DshSessionManagerPage.kt:1499 | Agent 在线 | 清晰度 | 助手在线 | P1 |
| ui/DshSessionManagerPage.kt:1506 | 待办与目标暂不支持 | 被动/实现说明 | 查看任务进度 | P1 |
| ui/DshSessionManagerPage.kt:1507 | 官方 goals/todos 协议尚未接入原生会话管理；本页不会伪造进度百分比或待办数量。需要查看目标与待办时请使用官方 DSH 页面。 | 工程痕迹、被动/实现说明、术语不一致、长度 | 请在网页界面查看这段对话的目标和待办。 | P1 |
| ui/DshSessionManagerPage.kt:1514 | 产物列表与预览暂不支持 | 被动/实现说明 | 查看生成文件 | P1 |
| ui/DshSessionManagerPage.kt:1515 | 官方 artifacts/workspaceFiles 字段契约尚未核对接入；当前运行时也没有向会话管理暴露本地 artifacts 目录。本页不会生成假产物或空预览；后续拿到只读目录后只读列出、不复制。 | 被动/实现说明、术语不一致、长度 | 请在文件页查看本机文件，或在网页界面查看生成文件。 | P0 |
| ui/DshSessionManagerPage.kt:1528 | 工作目录 | 保留 | 工作目录 | P1 |
| ui/DshSessionManagerPage.kt:1529 | 官方未返回工作目录 | 清晰度 | 未提供文件夹位置。 | P1 |
| ui/DshSessionManagerPage.kt:1531 | 复制工作目录 | 保留 | 复制工作目录 | P1 |
| ui/DshSessionManagerPage.kt:1536 | 文件浏览与预览暂不支持 | 被动/实现说明 | 查看文件 | P1 |
| ui/DshSessionManagerPage.kt:1537 | 会话详情内的文件列表、只读预览与产物打开需要 workspaceFiles 接口；当前未接入，因此不提供假文件树。请使用文件页查看本机工作区。 | 被动/实现说明、术语不一致、长度 | 请在文件页查看本机工作区，或在网页界面查看电脑文件。 | P1 |
| ui/DshSessionManagerPage.kt:1576 | 当前选择 | 保留 | 当前选择 | P1 |
| ui/DshSessionManagerPage.kt:1579 | 切换会写回当前官方会话；目录来自 session/modelCatalog。 | 术语不一致 | 切换后会用于当前对话。 | P1 |
| ui/DshSessionManagerPage.kt:1586 | 模型切换失败 | 保留 | 模型切换失败 | P0 |
| ui/DshSessionManagerPage.kt:1590 | 模型目录读取失败 | 清晰度 | 模型列表读取失败 | P0 |
| ui/DshSessionManagerPage.kt:1591 | 重新读取模型目录 | 清晰度 | 刷新模型 | P1 |
| ui/DshSessionManagerPage.kt:1595 | 尚未读取模型目录。 | 清晰度 | 请先读取可用模型。 | P1 |
| ui/DshSessionManagerPage.kt:1596 | 读取模型目录 | 清晰度 | 读取模型 | P1 |
| ui/DshSessionManagerPage.kt:1602 | 安全模式/试用模式下只读，不能切换模型。 | 清晰度 | 恢复正常模式后可切换模型。 | P1 |
| ui/DshSessionManagerPage.kt:1602 | 正在执行其他会话操作，完成后再切换模型。 | 术语不一致 | 正在执行其他对话操作，完成后再切换模型。 | P1 |
| ui/DshSessionManagerPage.kt:1609 | 官方默认：${DshModelPresentation.selectionLabel(catalog, it)} | 清晰度 | 默认模型：{模型名称} | P1 |
| ui/DshSessionManagerPage.kt:1651 | 推理强度 | 清晰度 | 思考深度 | P1 |
| ui/DshSessionManagerPage.kt:1673 | 切换到此模型 | 保留 | 切换到此模型 | P1 |
| ui/DshSessionManagerPage.kt:1674 | 应用所选推理强度 | 清晰度 | 应用思考深度 | P1 |
| ui/DshSessionManagerPage.kt:1675 | 重新应用当前选择 | 清晰度 | 重新应用 | P1 |
| ui/DshSessionManagerPage.kt:1686 | 部分模型不可用 | 保留 | 部分模型不可用 | P1 |
| ui/DshSessionManagerPage.kt:1690 | 模型目录读取失败 | 清晰度 | 模型列表读取失败 | P0 |
| ui/DshSessionManagerPage.kt:1692 | ${failure.name.ifBlank { failure.id }}：$reason | 清晰度 | {名称}：{原因} | P1 |
| ui/DshSessionManagerPage.kt:1700 | 官方未返回可选模型。请先在官方设置中配置模型服务，再回到这里重试。 | 清晰度 | 还没有可用模型，请先在网页界面设置模型服务，再重试。 | P1 |
| ui/DshSessionManagerPage.kt:1703 | 重新读取模型目录 | 清晰度 | 刷新模型 | P1 |
| ui/DshSessionManagerPage.kt:1756 | 当前权限 | 保留 | 当前权限 | P0 |
| ui/DshSessionManagerPage.kt:1758 | 未知 | 被动/实现说明 | 未知 | P1 |
| ui/DshSessionManagerPage.kt:1762 | 权限通过官方 /permission 命令提交，不改写 sandbox/approval 事件。 | 长度 | 切换后会用于当前对话。 | P0 |
| ui/DshSessionManagerPage.kt:1769 | 权限切换失败 | 保留 | 权限切换失败 | P0 |
| ui/DshSessionManagerPage.kt:1773 | 权限目录读取失败 | 清晰度 | 权限选项读取失败 | P0 |
| ui/DshSessionManagerPage.kt:1774 | 重新读取权限目录 | 清晰度 | 刷新权限 | P0 |
| ui/DshSessionManagerPage.kt:1778 | 尚未读取权限目录。 | 清晰度 | 请先读取权限选项。 | P0 |
| ui/DshSessionManagerPage.kt:1779 | 读取权限目录 | 清晰度 | 读取权限 | P0 |
| ui/DshSessionManagerPage.kt:1785 | 安全模式/试用模式下只读，不能切换权限。 | 清晰度 | 恢复正常模式后可切换权限。 | P0 |
| ui/DshSessionManagerPage.kt:1785 | 正在执行其他会话操作，完成后再切换权限。 | 术语不一致 | 正在执行其他对话操作，完成后再切换权限。 | P0 |
| ui/DshSessionManagerPage.kt:1813 | 仅展示 | 保留 | 仅展示 | P1 |
| ui/DshSessionManagerPage.kt:1818 | 官方说明：${it.take(300)} | 清晰度 | 权限说明见详情。 | P1 |
| ui/DshSessionManagerPage.kt:1825 | 需要二次确认并勾选风险确认；Tanuki 不会自动切换。 | 清晰度 | 请选择风险确认后，再切换权限。 | P1 |
| ui/DshSessionManagerPage.kt:1832 | 自定义预设由官方配置决定，本页不能直接切换。 | 清晰度 | 此权限由自定义设置决定，请在网页界面修改。 | P1 |
| ui/DshSessionManagerPage.kt:1842 | 官方未返回可选权限预设。 | 清晰度 | 还没有可选权限，请在网页界面检查设置。 | P0 |
| ui/DshSessionManagerPage.kt:1843 | 重新读取权限目录 | 清晰度 | 刷新权限 | P0 |
| ui/DshSessionManagerPage.kt:1846 | 完全权限会放宽工作区边界，只在明确需要时选择；切换失败会保留当前权限并显示原因。 | 清晰度 | 完全权限允许访问工作区外的文件，请谨慎选择。 | P0 |
| ui/DshSessionManagerPage.kt:1858 | 当前会话没有可展示的 Skills。 | 术语不一致 | 还没有可用技能，请在网页界面检查技能设置。 | P0 |
| ui/DshSessionManagerPage.kt:1862 | 斜杠命令 | 清晰度 | 快捷命令 | P1 |
| ui/DshSessionManagerPage.kt:1864 | 当前会话没有可展示的斜杠命令。 | 术语不一致 | 还没有可用命令，可在对话中直接输入消息。 | P0 |
| ui/DshSessionManagerPage.kt:1867 | 重试 | 保留 | 重试 | P1 |
| ui/DshSessionManagerPage.kt:1868 | 目录由官方按当前会话返回；斜杠命令在本页只展示，不在原生侧执行。 | 工程痕迹、术语不一致 | 这里可查看命令，请在对话输入框中使用。 | P1 |
| ui/DshSessionManagerPage.kt:1885 | /${skill.name} | 清晰度 | /{名称} | P1 |
| ui/DshSessionManagerPage.kt:1886 | 仅人工 | 清晰度 | 仅手动使用 | P1 |
| ui/DshSessionManagerPage.kt:1886 | 模型可用 | 保留 | 模型可用 | P1 |
| ui/DshSessionManagerPage.kt:1889 | 适用：$it | 清晰度 | 适用：{内容} | P1 |
| ui/DshSessionManagerPage.kt:1899 | /${command.name} | 清晰度 | /{名称} | P1 |
| ui/DshSessionManagerPage.kt:1900 | 支持附件 | 保留 | 支持附件 | P1 |
| ui/DshSessionManagerPage.kt:1904 | 输入：$it | 清晰度 | 输入：{内容} | P1 |
| ui/DshSessionManagerPage.kt:1982 | 只读 | 保留 | 只读 | P1 |
| ui/DshSessionManagerPage.kt:1983 | 工作区写入 | 保留 | 工作区写入 | P1 |
| ui/DshSessionManagerPage.kt:1984 | 完全权限 | 保留 | 完全权限 | P0 |
| ui/DshSessionManagerPage.kt:1985 | 自动 | 保留 | 自动 | P1 |
| ui/DshSessionManagerPage.kt:1986 | 自定义 | 保留 | 自定义 | P1 |
| ui/DshSessionManagerPage.kt:1991 | 时间未知 | 被动/实现说明 | 时间未知 | P1 |
| ui/DshSessionManagerPage.kt:1994 | 时间未知 | 被动/实现说明 | 时间未知 | P1 |

### [ui/DshSessionPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshSessionPresentation.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshSessionPresentation.kt:15 | 远程主机连接失败：$error | 术语不一致 | 电脑连接失败，请按提示重试。 | P0 |
| ui/DshSessionPresentation.kt:16 | 远程主机：$message | 术语不一致 | 远程电脑：{状态} | P1 |
| ui/DshSessionPresentation.kt:17 | 正在连接远程主机… | 术语不一致 | 正在连接远程电脑… | P1 |
| ui/DshSessionPresentation.kt:55 | 本机 | 保留 | 本机 | P1 |
| ui/DshSessionPresentation.kt:56 | 未运行 | 保留 | 未运行 | P1 |
| ui/DshSessionPresentation.kt:56 | 运行中 | 保留 | 运行中 | P1 |
| ui/DshSessionPresentation.kt:67 | 远程主机 | 术语不一致 | 远程电脑 | P1 |
| ui/DshSessionPresentation.kt:69 | 未保存配置 | 保留 | 未保存配置 | P1 |
| ui/DshSessionPresentation.kt:70 | 等待主机密钥确认 | 清晰度 | 请核对电脑安全指纹 | P1 |
| ui/DshSessionPresentation.kt:71 | 连接中 | 保留 | 连接中 | P1 |
| ui/DshSessionPresentation.kt:72 | 已连接 | 保留 | 已连接 | P1 |
| ui/DshSessionPresentation.kt:73 | 连接失败，已回退本地 | 清晰度 | 电脑连接失败，已回到本机。 | P0 |
| ui/DshSessionPresentation.kt:74 | 未连接 | 保留 | 未连接 | P1 |
| ui/DshSessionPresentation.kt:112 | 官方未返回工作目录 | 清晰度 | 未提供文件夹位置。 | P1 |
| ui/DshSessionPresentation.kt:129 | 未分组 | 保留 | 未分组 | P1 |
| ui/DshSessionPresentation.kt:144 | 只读：只能查看会话、文件与目录；写入和执行请求会被拒绝。 | 术语不一致 | 只能查看文件和对话，不能修改文件或执行命令。 | P0 |
| ui/DshSessionPresentation.kt:145 | 工作区写入：可修改当前工作区内的文件并执行命令；工作区外默认不放开。 | 清晰度 | 可修改工作区文件；命令能否执行取决于设备支持。 | P1 |
| ui/DshSessionPresentation.kt:146 | 完全权限：取消工作区边界限制，工具可访问应用有权限读写的更多位置；风险最高。 | 清晰度 | 可访问工作区外、应用有权读写的文件，风险较高。 | P0 |
| ui/DshSessionPresentation.kt:147 | 自动：由官方预设决定审批与确认策略；本页不额外放大权限，具体以 DSH 说明为准。 | 术语不一致 | 使用助手当前的自动权限设置，操作前按设置询问。 | P0 |
| ui/DshSessionPresentation.kt:148 | 自定义：由官方配置组合而成，本页只展示，不能直接切换。 | 清晰度 | 使用自定义权限，需在网页界面修改。 | P1 |
| ui/DshSessionPresentation.kt:150 | 官方预设「$it」；具体行为以 DSH 返回的说明为准。 | 术语不一致 | 使用自定义权限，行为见详情。 | P1 |
| ui/DshSessionPresentation.kt:151 | 官方预设；具体行为以 DSH 返回的说明为准。 | 术语不一致 | 当前权限说明见详情。 | P1 |
| ui/DshSessionPresentation.kt:156 | 工具可以读写工作区之外、应用有权访问的文件。 | 保留 | 工具可以读写工作区之外、应用有权访问的文件。 | P1 |
| ui/DshSessionPresentation.kt:157 | 误操作、提示注入或恶意脚本造成的影响不再被工作区边界限制。 | 清晰度 | 误操作或恶意指令可能影响工作区之外的文件。 | P1 |
| ui/DshSessionPresentation.kt:158 | Tanuki 不会自动切换该模式；切回其他预设仍通过官方命令提交。 | 清晰度 | 仅在你确认后才切换，可随时在权限设置中改回。 | P1 |
| ui/DshSessionPresentation.kt:169 | 官方 DSH 当前没有会话硬删除接口。归档只会把会话从原生列表移除，不会删除项目文件，也不能在本页找回。 | 工程痕迹、术语不一致、长度 | 归档会从列表移除对话，保留项目文件。<br>此页无法撤销归档。 | P0 |
| ui/DshSessionPresentation.kt:191 | 未选择 | 保留 | 未选择 | P1 |

### [ui/DshTodoDock.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshTodoDock.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshTodoDock.kt:114 | $prefix · 已全部完成 | 清晰度 | {内容} · 已全部完成 | P1 |
| ui/DshTodoDock.kt:115 | 下一步 | 保留 | 下一步 | P1 |
| ui/DshTodoDock.kt:115 | 正在 | 保留 | 正在 | P1 |
| ui/DshTodoDock.kt:195 | 待办 ${state.completed}/${state.total}，点击收起清单 | 清晰度 | 待办 {数量}/{数量}，点击收起清单 | P1 |
| ui/DshTodoDock.kt:197 | 待办 ${state.completed}/${state.total}，点击展开清单 | 清晰度 | 待办 {数量}/{数量}，点击展开清单 | P1 |
| ui/DshTodoDock.kt:203 | ☑ ${state.completed}/${state.total} | 清晰度 | ☑ {数量}/{数量} | P1 |
| ui/DshTodoDock.kt:207 |  ·  | 清晰度 | · | P1 |
| ui/DshTodoDock.kt:217 | 展开 | 保留 | 展开 | P1 |
| ui/DshTodoDock.kt:217 | 收起 | 保留 | 收起 | P1 |
| ui/DshTodoDock.kt:263 | 待办进度 ${state.completed}/${state.total} | 清晰度 | 待办进度 {数量}/{数量} | P1 |
| ui/DshTodoDock.kt:302 | （未命名待办） | 保留 | （未命名待办） | P1 |
| ui/DshTodoDock.kt:316 | 已全部完成 | 保留 | 已全部完成 | P1 |
| ui/DshTodoDock.kt:317 | 下一步 | 保留 | 下一步 | P1 |
| ui/DshTodoDock.kt:317 | 正在 | 保留 | 正在 | P1 |
| ui/DshTodoDock.kt:318 | $label：${current.oneLineText.ifBlank { "（未命名待办）" }} | 清晰度 | {名称}：{名称} | P1 |
| ui/DshTodoDock.kt:318 | （未命名待办） | 保留 | （未命名待办） | P1 |

### [ui/DshToolCard.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshToolCard.kt:87 |  · 耗时未知 | 被动/实现说明 | 耗时未记录 | P1 |
| ui/DshToolCard.kt:92 |  个文件 | 清晰度 | 个文件 | P1 |
| ui/DshToolCard.kt:102 | 展开 | 保留 | 展开 | P1 |
| ui/DshToolCard.kt:102 | 收起 | 保留 | 收起 | P1 |
| ui/DshToolCard.kt:110 | 未读取到官方结果：可能仍在运行，或历史页被截断。 | 清晰度 | 尚未收到操作结果，可稍后刷新或在网页界面查看。 | P1 |
| ui/DshToolCard.kt:124 | 参数 | 保留 | 参数 | P1 |
| ui/DshToolCard.kt:144 | 错误 | 保留 | 错误 | P0 |
| ui/DshToolCard.kt:149 | 复制错误 | 保留 | 复制错误 | P0 |
| ui/DshToolCard.kt:160 | 输出 | 保留 | 输出 | P1 |
| ui/DshToolCard.kt:163 | 复制输出 | 保留 | 复制输出 | P1 |
| ui/DshToolCard.kt:183 | 输出已截断，仅显示前 12,000 字符。 | 清晰度 | 当前仅显示前 12,000 个字，完整结果请在网页界面查看。 | P1 |
| ui/DshToolCard.kt:192 | 输出已折叠 · 点击卡片展开 | 保留 | 输出已折叠 · 点击卡片展开 | P1 |
| ui/DshToolCard.kt:242 | 风险 ${level.label}：$summary | 清晰度 | 风险 {名称}：{内容} | P1 |
| ui/DshToolCard.kt:245 | 风险 ${level.label} | 清晰度 | 风险 {名称} | P1 |
| ui/DshToolCard.kt:261 | 命令 | 保留 | 命令 | P1 |
| ui/DshToolCard.kt:267 | $ $command | 清晰度 | $ {内容} | P1 |
| ui/DshToolCard.kt:274 | 工作目录：$cwd | 清晰度 | 工作目录：{内容} | P1 |
| ui/DshToolCard.kt:299 | +$added | 清晰度 | +{内容} | P1 |
| ui/DshToolCard.kt:305 | -$removed | 清晰度 | -{内容} | P1 |
| ui/DshToolCard.kt:324 | 展开完整 diff（${lines.size} 行） | 清晰度 | 查看全部变更 | P1 |
| ui/DshToolCard.kt:332 | 收起 diff | 清晰度 | 收起变更 | P1 |
| ui/DshToolCard.kt:362 | $prefix${line.text} | 清晰度 | {内容}{名称} | P1 |
| ui/DshToolCard.kt:372 | 文件变更（${files.size}） | 清晰度 | 文件变更（{数量}） | P1 |

### [ui/DshVersionManagerDialog.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/DshVersionManagerDialog.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/DshVersionManagerDialog.kt:62 | 读取已安装版本失败 | 保留 | 读取已安装版本失败 | P0 |
| ui/DshVersionManagerDialog.kt:67 | 已安装 DSH 版本 | 术语不一致 | 助手版本 | P0 |
| ui/DshVersionManagerDialog.kt:75 | 正在读取版本、校验状态与占用… | 保留 | 正在读取版本、校验状态与占用… | P1 |
| ui/DshVersionManagerDialog.kt:78 | 尚未安装 DSH 版本。 | 术语不一致 | 还没有安装助手，请先选择一个版本安装。 | P0 |
| ui/DshVersionManagerDialog.kt:79 | 先通过“选择版本安装”安装程序；安装后这里会显示体积、安装时间与校验状态。 | 清晰度 | 先点“选择版本”，安装后可在这里管理。 | P0 |
| ui/DshVersionManagerDialog.kt:82 | 共 ${current.versions.size} 个版本，合计占用 ${formatDshBytes(current.totalBytes)} | 清晰度 | 共 {版本} 个版本，合计占用 {大小} | P1 |
| ui/DshVersionManagerDialog.kt:84 | “当前使用”是应用正在使用的版本，禁止卸载；“备用”可切换或卸载。卸载只删除程序目录，不删除配置、会话、项目、快照与其他版本。 | 术语不一致、长度 | 当前版本不能卸载，请先切换其他版本。<br>卸载备用版本只删除程序，保留对话和项目。 | P0 |
| ui/DshVersionManagerDialog.kt:98 | 当前安装元数据缺少有效 DSH 版本，无法确认哪个版本正在使用；列表仅供查看，已禁止切换与卸载。 | 工程痕迹、术语不一致、长度 | 无法确认当前版本，暂时只能查看。请恢复对应的数据备份。 | P0 |
| ui/DshVersionManagerDialog.kt:101 | 当前安装元数据指向的 DSH $activeVersion 不在清单中；请重装该版本或恢复匹配的数据快照。 | 工程痕迹、术语不一致、长度 | 当前版本程序缺失，请重新安装该版本或恢复对应备份。 | P0 |
| ui/DshVersionManagerDialog.kt:106 | 服务运行中或正在执行操作：切换与卸载已禁用，请先停止 DSH 并等待操作完成。 | 术语不一致 | 请先停止助手，并等待当前操作完成，再切换或卸载。 | P1 |
| ui/DshVersionManagerDialog.kt:110 | 刷新 | 保留 | 刷新 | P1 |
| ui/DshVersionManagerDialog.kt:112 | 关闭 | 保留 | 关闭 | P1 |
| ui/DshVersionManagerDialog.kt:118 | 切换到 DSH $version | 术语不一致 | 切换助手版本？ | P1 |
| ui/DshVersionManagerDialog.kt:119 | 切换前会保存当前版本的数据快照；不重新下载，也不删除任何已安装版本。旧版需先恢复匹配的数据快照。请确认 DSH 服务已停止。 | 术语不一致、长度 | 切换前会备份当前数据。使用旧版前需恢复对应备份。<br>请先停止助手，版本号另行显示。 | P0 |
| ui/DshVersionManagerDialog.kt:121 | 切换 | 保留 | 切换 | P1 |
| ui/DshVersionManagerDialog.kt:123 | 取消 | 保留 | 取消 | P0 |
| ui/DshVersionManagerDialog.kt:130 | 确认卸载 DSH ${item.version}？ | 术语不一致 | 卸载备用版本？ | P1 |
| ui/DshVersionManagerDialog.kt:132 | 将删除备用版本的程序目录：占用 ${formatDshBytes(item.bytes)}，安装于 ${formatDshTime(item.installedAtMillis)}。\n\n | 工程痕迹、长度 | 将删除所选备用版本程序，释放 {空间}。版本和安装时间另行显示。 | P0 |
| ui/DshVersionManagerDialog.kt:133 | 不会删除配置、会话、扩展、项目、用户数据、数据快照、共享下载缓存与其他已安装版本； | 术语不一致、长度 | 对话、项目、数据备份和其他版本保留。 | P0 |
| ui/DshVersionManagerDialog.kt:134 | 如需再回滚到该版本，需要重新安装匹配程序。当前使用版本不能在此卸载。 | 清晰度 | 再次使用此版本时需重新安装。 | P0 |
| ui/DshVersionManagerDialog.kt:140 | 确认卸载 | 保留 | 确认卸载 | P1 |
| ui/DshVersionManagerDialog.kt:142 | 取消 | 保留 | 取消 | P0 |
| ui/DshVersionManagerDialog.kt:160 | 备用 | 保留 | 备用 | P1 |
| ui/DshVersionManagerDialog.kt:160 | 当前使用 | 保留 | 当前使用 | P1 |
| ui/DshVersionManagerDialog.kt:161 | 已校验 | 保留 | 已校验 | P1 |
| ui/DshVersionManagerDialog.kt:161 | 未校验 | 保留 | 未校验 | P1 |
| ui/DshVersionManagerDialog.kt:163 | 占用 ${formatDshBytes(item.bytes)} · 安装于 ${formatDshTime(item.installedAtMillis)} | 工程痕迹 | 占用 {大小} · 安装于 {内容} | P0 |
| ui/DshVersionManagerDialog.kt:166 | 当前使用中，禁止卸载；如需清理请先切换到其他版本。 | 保留 | 当前使用中，禁止卸载；如需清理请先切换到其他版本。 | P1 |
| ui/DshVersionManagerDialog.kt:168 | 当前使用的版本未知，已禁止切换与卸载。 | 被动/实现说明 | 当前使用的版本未知，已禁止切换与卸载。 | P1 |
| ui/DshVersionManagerDialog.kt:171 | 切换 | 保留 | 切换 | P1 |
| ui/DshVersionManagerDialog.kt:173 | 卸载 | 保留 | 卸载 | P1 |
| ui/DshVersionManagerDialog.kt:177 | ；当前数据可能与旧版不兼容。 | 清晰度 | 现有对话可能无法在旧版打开，请先恢复对应数据备份。 | P1 |
| ui/DshVersionManagerDialog.kt:181 | 依赖锁缺失或与 .installed 不一致，无法切换；仍可卸载后重装该版本。 | 工程痕迹 | 所选版本文件不完整，请卸载该备用版本后重新安装。 | P0 |
| ui/DshVersionManagerDialog.kt:207 | 未知 | 被动/实现说明 | 未知 | P1 |

### [ui/FailureUi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FailureUi.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/FailureUi.kt:108 | ${failure.title}：${failure.reason} | 清晰度 | {名称}：{原因} | P0 |
| ui/FailureUi.kt:115 | 处理 | 保留 | 处理 | P0 |
| ui/FailureUi.kt:120 | 关闭 | 保留 | 关闭 | P0 |
| ui/FailureUi.kt:136 | 技术细节 | 清晰度 | 问题详情 | P0 |
| ui/FailureUi.kt:138 | 已尝试 $attempt 次 | 清晰度 | 已尝试 {数量} 次 | P0 |
| ui/FailureUi.kt:165 | 复制错误信息 | 清晰度 | 复制问题详情 | P0 |
| ui/FailureUi.kt:165 | 已复制 | 保留 | 已复制 | P0 |
| ui/FailureUi.kt:188 | 关闭 | 保留 | 关闭 | P0 |

### [ui/FileAccessPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileAccessPresentation.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/FileAccessPresentation.kt:38 | 只读 | 保留 | 只读 | P0 |
| ui/FileAccessPresentation.kt:39 | 环境操作中，修改已暂停 | 清晰度 | 环境准备中，只能查看文件。 | P0 |
| ui/FileAccessPresentation.kt:43 | 只读 | 保留 | 只读 | P0 |
| ui/FileAccessPresentation.kt:44 | DSH 运行中，修改已暂停 | 术语不一致 | 助手运行中，暂时只能查看。 | P0 |
| ui/FileAccessPresentation.kt:45 | 工作区本身可写，但 DSH 运行期间会暂停新建、重命名、编辑、复制、移动和删除。停止 DSH 后恢复写入；浏览、打开、导入和导出不受影响。 | 术语不一致、长度 | 助手运行环境暂时无法在此修改工作区文件。<br>停止助手后可修改，浏览、导入和导出仍可使用。 | P0 |
| ui/FileAccessPresentation.kt:49 | 可写 | 保留 | 可写 | P0 |
| ui/FileAccessPresentation.kt:50 | 工作区可写 | 清晰度 | 可在工作区修改文件。 | P0 |
| ui/FileAccessPresentation.kt:51 | 可以在工作区新建文件夹或文本文件，也可以重命名、编辑、复制、移动和删除文件。同名文件不会被覆盖。 | 长度 | 可新建、编辑、移动或删除工作区文件。<br>遇到同名文件时会先询问。 | P0 |
| ui/FileAccessPresentation.kt:57 | 只读 | 保留 | 只读 | P0 |
| ui/FileAccessPresentation.kt:58 | 尚未授权手机存储 | 保留 | 尚未授权手机存储 | P0 |
| ui/FileAccessPresentation.kt:59 | 授权后可浏览手机共享存储和可用存储卡中的文件。DSH 默认只能读取；写入需要在本次运行中单独授权。 | 术语不一致、长度 | 授权后可浏览手机文件。助手默认只能读取，写入需另行授权。 | P0 |
| ui/FileAccessPresentation.kt:64 | 可写 | 保留 | 可写 | P0 |
| ui/FileAccessPresentation.kt:65 | 本次 DSH 运行已获写入权限 | 术语不一致 | 本次已允许助手写入手机文件。 | P0 |
| ui/FileAccessPresentation.kt:66 | 手机共享存储由 Android 保护。写入授权只对本次 DSH 运行有效，停止 DSH 或撤销后恢复只读。 | 术语不一致、长度 | 授权只对本次助手运行有效，停止助手或撤销授权后恢复只读。 | P0 |
| ui/FileAccessPresentation.kt:71 | 只读 | 保留 | 只读 | P0 |
| ui/FileAccessPresentation.kt:72 | 手机存储当前只读 | 保留 | 手机存储当前只读 | P0 |
| ui/FileAccessPresentation.kt:74 | DSH 默认只能读取手机文件。写入需要在本次运行中单独授权，停止 DSH 或撤销后恢复只读。 | 术语不一致、长度 | 助手默认只能读取。写入需另行授权，停止后恢复只读。 | P0 |
| ui/FileAccessPresentation.kt:76 | DSH 默认只能读取手机文件。启动 DSH 后可单独授权写入；当前未运行，所以不能授权。 | 术语不一致、长度 | 请先启动助手，再授权本次手机文件写入。 | P0 |
| ui/FileAccessPresentation.kt:82 | 只读 | 保留 | 只读 | P0 |
| ui/FileAccessPresentation.kt:83 | 此位置按只读方式展示 | 保留 | 此位置按只读方式展示 | P0 |
| ui/FileAccessPresentation.kt:84 | 该位置按 Agent 实际挂载路径只读展示，可打开或导出文件，不能在这里修改。需要修改时请复制到工作区。 | 长度 | 此位置只能查看、打开和导出。要修改文件，请先复制到工作区。 | P0 |

### [ui/FileOperationOutcome.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileOperationOutcome.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/FileOperationOutcome.kt:25 | 同名已存在 | 保留 | 同名已存在 | P1 |
| ui/FileOperationOutcome.kt:25 | 查看 | 保留 | 查看 | P1 |
| ui/FileOperationOutcome.kt:26 | 清理空间 | 保留 | 清理空间 | P1 |
| ui/FileOperationOutcome.kt:26 | 空间不足 | 保留 | 空间不足 | P0 |
| ui/FileOperationOutcome.kt:27 | 去授权 | 保留 | 去授权 | P0 |
| ui/FileOperationOutcome.kt:27 | 没有写入权限 | 保留 | 没有写入权限 | P0 |
| ui/FileOperationOutcome.kt:28 | 其他原因 | 保留 | 其他原因 | P1 |
| ui/FileOperationOutcome.kt:28 | 查看 | 保留 | 查看 | P1 |
| ui/FileOperationOutcome.kt:89 | 查看 | 保留 | 查看 | P1 |
| ui/FileOperationOutcome.kt:94 | 已完成 $completed 项 | 清晰度 | 已完成 {数量} 项 | P1 |
| ui/FileOperationOutcome.kt:98 | ${group.failures.size} 个文件同名已存在，已跳过（未覆盖） | 清晰度 | {数量} 个同名文件已跳过，原文件未覆盖。 | P0 |
| ui/FileOperationOutcome.kt:100 | ${group.failures.size} 个文件因空间不足未处理，请清理空间后重试 | 清晰度 | {数量} 个文件因空间不足未处理，请清理空间后重试 | P0 |
| ui/FileOperationOutcome.kt:102 | ${group.failures.size} 个文件因没有写入权限未处理，请授权后重试 | 清晰度 | {数量} 个文件因没有写入权限未处理，请授权后重试 | P0 |
| ui/FileOperationOutcome.kt:104 | ${group.failures.size} 个文件未处理，原因见详情 | 清晰度 | {数量} 个文件未处理，原因见详情 | P1 |
| ui/FileOperationOutcome.kt:111 | 已完成 $completed 项 | 清晰度 | 已完成 {数量} 项 | P1 |
| ui/FileOperationOutcome.kt:111 | 没有完成项 | 保留 | 没有完成项 | P0 |
| ui/FileOperationOutcome.kt:112 | ，共 $total 项 | 清晰度 | ，共 {数量} 项 | P1 |
| ui/FileOperationOutcome.kt:123 | 同名 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:123 | 未覆盖 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:126 | 剩余空间 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:126 | 容量不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:126 | 容量预算 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:126 | 空间不足 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:129 | 只读 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:129 | 权限 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/FileOperationOutcome.kt:140 | 复制 | 保留 | 复制 | P1 |
| ui/FileOperationOutcome.kt:140 | 移动 | 保留 | 移动 | P1 |
| ui/FileOperationOutcome.kt:141 | 工作区根目录 | 保留 | 工作区根目录 | P1 |
| ui/FileOperationOutcome.kt:142 | 工作区「${report.targetDirectory}」 | 清晰度 | 工作区「{端口}」 | P1 |
| ui/FileOperationOutcome.kt:148 | 已在冲突处理中选择跳过 | 保留 | 已在冲突处理中选择跳过 | P1 |

### [ui/FileOperationOverlay.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileOperationOverlay.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/FileOperationOverlay.kt:20 | 环境操作正在进行，文件可继续浏览；请等待操作完成后再写入。 | 清晰度 | 正在安装或更新，仍可浏览文件。完成后可修改文件。 | P0 |

### [ui/FileTransferPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/FileTransferPresentation.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/FileTransferPresentation.kt:52 | 已传输 ${bytes(received)} · 总大小未知 | 被动/实现说明 | 已传输 {大小} | P1 |
| ui/FileTransferPresentation.kt:53 | 正在准备文件 · 总大小未知 | 被动/实现说明 | 正在准备文件 | P1 |
| ui/FileTransferPresentation.kt:69 | 剩余时间未知 | 被动/实现说明 | 正在传输 | P1 |
| ui/FileTransferPresentation.kt:70 | 即将完成 | 保留 | 即将完成 | P1 |
| ui/FileTransferPresentation.kt:71 | 剩余约 ${duration(it)} | 清晰度 | 剩余约 {耗时} | P1 |
| ui/FileTransferPresentation.kt:71 | 正在估算剩余时间 | 清晰度 | 正在传输 | P1 |
| ui/FileTransferPresentation.kt:75 | ${seconds / 3600}时${seconds % 3600 / 60}分${seconds % 60}秒 | 清晰度 | {时间}时{时间}分{时间}秒 | P1 |
| ui/FileTransferPresentation.kt:76 | ${seconds / 60}分${seconds % 60}秒 | 清晰度 | {时间}分{时间}秒 | P1 |
| ui/FileTransferPresentation.kt:77 | ${seconds}秒 | 清晰度 | {时间}秒 | P1 |

### [ui/GlassNavigationBar.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/GlassNavigationBar.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/GlassNavigationBar.kt:76 | 液态导航位置 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/GlassNavigationBar.kt:80 | 液态导航形变 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/GlassNavigationBar.kt:83 | 液态导航可见度 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/Labels.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/Labels.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/Labels.kt:7 | 下载中 | 保留 | 下载中 | P0 |
| ui/Labels.kt:7 | 安装中 | 保留 | 安装中 | P0 |
| ui/Labels.kt:7 | 安装失败 | 保留 | 安装失败 | P0 |
| ui/Labels.kt:7 | 已取消 | 保留 | 已取消 | P0 |
| ui/Labels.kt:7 | 已就绪 | 保留 | 已就绪 | P0 |
| ui/Labels.kt:7 | 未完成安装 | 清晰度 | 等待安装 | P0 |
| ui/Labels.kt:7 | 检查中 | 保留 | 检查中 | P0 |
| ui/Labels.kt:7 | 需要继续安装或修复 | 清晰度 | 继续准备环境 | P0 |
| ui/Labels.kt:7 | 验证中 | 清晰度 | 正在检查 | P0 |
| ui/Labels.kt:9 | 上游报告部分支持 | 被动/实现说明 | 部分命令可用 | P0 |
| ui/Labels.kt:9 | 上游探测通过 | 被动/实现说明 | 命令执行可用 | P0 |
| ui/Labels.kt:9 | 不可用 | 保留 | 不可用 | P0 |
| ui/Labels.kt:9 | 尚未检查 | 保留 | 尚未检查 | P0 |
| ui/Labels.kt:10 | 异常 | 保留 | 异常 | P0 |
| ui/Labels.kt:10 | 待检查 | 保留 | 待检查 | P0 |
| ui/Labels.kt:10 | 检查中 | 保留 | 检查中 | P0 |
| ui/Labels.kt:10 | 正常 | 保留 | 正常 | P0 |
| ui/Labels.kt:11 | 停止中 | 保留 | 停止中 | P0 |
| ui/Labels.kt:11 | 启动中 | 保留 | 启动中 | P0 |
| ui/Labels.kt:11 | 启动失败 | 保留 | 启动失败 | P0 |
| ui/Labels.kt:11 | 已中断，任务结果待确认 | 清晰度 | 任务已中断，请查看结果。 | P0 |
| ui/Labels.kt:11 | 已停止 | 保留 | 已停止 | P0 |
| ui/Labels.kt:11 | 运行中 | 保留 | 运行中 | P0 |
| ui/Labels.kt:12 | WebView 需要更新 | 工程痕迹 | 请更新网页组件 | P0 |
| ui/Labels.kt:12 | 未连接 | 保留 | 未连接 | P0 |
| ui/Labels.kt:12 | 渲染进程异常 | 工程痕迹 | 网页显示中断 | P0 |
| ui/Labels.kt:12 | 认证中 | 清晰度 | 正在确认登录 | P0 |
| ui/Labels.kt:12 | 认证失效 | 清晰度 | 登录已过期 | P0 |
| ui/Labels.kt:12 | 认证已连接 | 清晰度 | 已连接 | P0 |
| ui/Labels.kt:12 | 连接中 | 保留 | 连接中 | P0 |
| ui/Labels.kt:14 | 切换中 | 保留 | 切换中 | P0 |
| ui/Labels.kt:14 | 备份中 | 保留 | 备份中 | P0 |
| ui/Labels.kt:14 | 恢复中 | 保留 | 恢复中 | P0 |
| ui/Labels.kt:14 | 成功 | 清晰度 | 已完成 | P0 |
| ui/Labels.kt:14 | 数据已恢复，待启动检查 | 清晰度 | 数据已恢复，请启动检查。 | P0 |
| ui/Labels.kt:14 | 暂存中 | 清晰度 | 正在准备更新 | P0 |
| ui/Labels.kt:14 | 更新失败 | 保留 | 更新失败 | P0 |
| ui/Labels.kt:14 | 有候选版本 | 清晰度 | 有新版本 | P0 |
| ui/Labels.kt:14 | 检查中 | 保留 | 检查中 | P0 |
| ui/Labels.kt:14 | 检查失败 | 保留 | 检查失败 | P0 |
| ui/Labels.kt:14 | 没有更新 | 保留 | 没有更新 | P0 |
| ui/Labels.kt:14 | 没有检查 | 清晰度 | 尚未检查 | P0 |
| ui/Labels.kt:14 | 等待停止 | 清晰度 | 请先停止助手 | P0 |
| ui/Labels.kt:14 | 隔离检查中 | 清晰度 | 正在检查新版本 | P0 |
| ui/Labels.kt:14 | 需要恢复 | 保留 | 需要恢复 | P0 |
| ui/Labels.kt:17 | 正常 | 保留 | 正常 | P0 |
| ui/Labels.kt:18 | 轻度降级 | 清晰度 | 温度偏高 | P0 |
| ui/Labels.kt:19 | 过热 | 保留 | 过热 | P0 |
| ui/Labels.kt:20 | 严重过热 | 保留 | 严重过热 | P0 |
| ui/Labels.kt:21 | 低电 | 清晰度 | 电量偏低 | P0 |
| ui/Labels.kt:25 | 启动命令失败 | 清晰度 | 助手启动失败。 | P0 |
| ui/Labels.kt:26 | 启动未就绪 | 清晰度 | 助手启动失败 | P0 |
| ui/Labels.kt:27 | 就绪后 2 分钟内崩溃 | 清晰度 | 助手启动后停止 | P0 |
| ui/Labels.kt:28 | 运行满 2 分钟后崩溃 | 清晰度 | 助手运行中停止 | P0 |
| ui/Labels.kt:29 | 进程被终止或低内存 | 清晰度 | 助手已停止 | P0 |
| ui/Labels.kt:30 | 输出或磁盘空间不足 | 清晰度 | 存储空间不足。 | P0 |
| ui/Labels.kt:33 | 正常模式 | 保留 | 正常模式 | P0 |
| ui/Labels.kt:34 | 安全模式 | 保留 | 安全模式 | P0 |
| ui/Labels.kt:35 | 正常模式试用 | 清晰度 | 正在恢复正常模式 | P0 |

### [ui/OnboardingScreen.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/OnboardingScreen.kt:142 | TANUKI · 首次设置 | 清晰度 | 首次设置 | P0 |
| ui/OnboardingScreen.kt:143 | ${step.ordinal + 1} / ${OnboardingStep.entries.size} | 清晰度 | {内容} / {数量} | P0 |
| ui/OnboardingScreen.kt:168 | 第 ${item.ordinal + 1} 步，共 ${OnboardingStep.entries.size} 步 | 清晰度 | 第 {内容} 步，共 {数量} 步 | P0 |
| ui/OnboardingScreen.kt:169 | ，已完成 | 保留 | ，已完成 | P0 |
| ui/OnboardingScreen.kt:169 | ，当前步骤 | 保留 | ，当前步骤 | P0 |
| ui/OnboardingScreen.kt:190 | 当前设备不满足最低要求；可以先跳过，稍后改用连接远程电脑。 | 清晰度 | 此设备无法在本机运行助手，可跳过后连接远程电脑。 | P0 |
| ui/OnboardingScreen.kt:201 | 稍后再说 | 保留 | 稍后再说 | P0 |
| ui/OnboardingScreen.kt:201 | 跳过 | 保留 | 跳过 | P0 |
| ui/OnboardingScreen.kt:211 | 完成 | 保留 | 完成 | P0 |
| ui/OnboardingScreen.kt:212 | 继续 | 保留 | 继续 | P0 |
| ui/OnboardingScreen.kt:213 | 继续 | 保留 | 继续 | P0 |
| ui/OnboardingScreen.kt:224 | 先花一分钟，把这里准备好。 | 清晰度 | 准备开始使用 | P0 |
| ui/OnboardingScreen.kt:226 | 你可以在手机本机运行开发助手，也可以连接你电脑上的环境；对话、文件和项目都在一个应用里完成。 | 长度 | 在手机上运行助手，或连接自己的电脑。<br>对话、文件和项目都可在 Tanuki 中管理。 | P0 |
| ui/OnboardingScreen.kt:231 | 不需要电脑，准备完成后直接在手机上开始。 | 保留 | 不需要电脑，准备完成后直接在手机上开始。 | P0 |
| ui/OnboardingScreen.kt:231 | 在本机运行 | 保留 | 在本机运行 | P0 |
| ui/OnboardingScreen.kt:233 | 已经在电脑上配置好环境时，可以直接连过去使用。 | 保留 | 已经在电脑上配置好环境时，可以直接连过去使用。 | P0 |
| ui/OnboardingScreen.kt:233 | 连接远程电脑 | 保留 | 连接远程电脑 | P0 |
| ui/OnboardingScreen.kt:235 | 每一步都能跳过；之后可以从设置重新打开这份引导。 | 清晰度 | 可跳过任一步，之后在设置中继续。 | P0 |
| ui/OnboardingScreen.kt:235 | 随时可以回来 | 保留 | 随时可以回来 | P0 |
| ui/OnboardingScreen.kt:256 | 准备运行环境 | 保留 | 准备运行环境 | P0 |
| ui/OnboardingScreen.kt:258 | 先检查这台手机是否满足最低要求。满足后点“一键准备”，应用会下载、解压并校验必要组件；期间可以离开此页。 | 长度 | 检查完成后，点“一键准备”安装运行环境。<br>准备期间可离开此页。 | P0 |
| ui/OnboardingScreen.kt:268 | 重新检测 | 保留 | 重新检测 | P0 |
| ui/OnboardingScreen.kt:306 | 满足 | 保留 | 满足 | P0 |
| ui/OnboardingScreen.kt:307 | 偏低 | 保留 | 偏低 | P0 |
| ui/OnboardingScreen.kt:308 | 不满足 | 保留 | 不满足 | P0 |
| ui/OnboardingScreen.kt:315 | 实测 ${requirement.actual} · 要求 ${requirement.requirement} | 清晰度 | 实测 {内容} · 要求 {内容} | P0 |
| ui/OnboardingScreen.kt:330 | 一键准备 | 保留 | 一键准备 | P0 |
| ui/OnboardingScreen.kt:337 | 会下载并解压必要组件，建议连接 Wi-Fi，并预留 4 GB 空间。准备期间会申请通知权限用于显示进度；拒绝不影响继续安装。 | 长度 | 建议连接 Wi-Fi 并预留 4 GB 空间。<br>开启通知可在后台查看进度，拒绝也能继续安装。 | P0 |
| ui/OnboardingScreen.kt:343 | 请先按上面的建议满足最低要求；也可以跳过，改用连接远程电脑。 | 清晰度 | 请按设备检查提示处理，或跳过后连接远程电脑。 | P0 |
| ui/OnboardingScreen.kt:353 | 一键准备 | 保留 | 一键准备 | P0 |
| ui/OnboardingScreen.kt:353 | 重新准备 | 保留 | 重新准备 | P0 |
| ui/OnboardingScreen.kt:373 | ${(it * 100).toInt()}% | 清晰度 | {内容}% | P0 |
| ui/OnboardingScreen.kt:377 | 下载 | 保留 | 下载 | P0 |
| ui/OnboardingScreen.kt:377 | 校验 | 保留 | 校验 | P0 |
| ui/OnboardingScreen.kt:377 | 补齐组件 | 保留 | 补齐组件 | P0 |
| ui/OnboardingScreen.kt:377 | 解压 | 保留 | 解压 | P0 |
| ui/OnboardingScreen.kt:402 | 正在准备，请稍候。 | 清晰度 | 正在准备… | P0 |
| ui/OnboardingScreen.kt:407 | 进度来自实际下载量；解压与校验无法预估总量时显示不定进度。 | 重复/啰嗦、工程痕迹 | 删除此处说明；阶段后续操作仅在全局进度详情说明。 | P0 |
| ui/OnboardingScreen.kt:408 | 当前阶段无法预估总量，因此不显示百分比；上方会给出真实写入量或阶段说明。 | 重复/啰嗦、工程痕迹 | 删除此处说明；保留当前阶段和实际耗时。 | P0 |
| ui/OnboardingScreen.kt:412 | 取消准备 | 保留 | 取消准备 | P0 |
| ui/OnboardingScreen.kt:421 | 准备未完成 | 保留 | 准备未完成 | P0 |
| ui/OnboardingScreen.kt:423 | 准备没有完成。请重试。 | 保留 | 准备没有完成。请重试。 | P0 |
| ui/OnboardingScreen.kt:432 | 当前设备仍不满足最低要求，请按上方建议处理后再重试。 | 清晰度 | 设备条件仍未满足，请按上方提示处理后重试。 | P0 |
| ui/OnboardingScreen.kt:442 | 重试 | 保留 | 重试 | P0 |
| ui/OnboardingScreen.kt:452 | 环境已就绪 | 保留 | 环境已就绪 | P0 |
| ui/OnboardingScreen.kt:454 | 安装与自检已完成，可以继续下一步。 | 清晰度 | 环境已准备好，继续下一步。 | P0 |
| ui/OnboardingScreen.kt:464 | 选择界面 | 保留 | 选择界面 | P0 |
| ui/OnboardingScreen.kt:466 | 先选一个开始，之后可以在设置里随时切换。 | 保留 | 先选一个开始，之后可以在设置里随时切换。 | P0 |
| ui/OnboardingScreen.kt:472 | 官方网页界面 | 清晰度 | 网页界面 | P0 |
| ui/OnboardingScreen.kt:473 | 功能最全，也是官方默认界面；适合大多数使用。 | 清晰度 | 提供完整功能，首次使用推荐选择。 | P0 |
| ui/OnboardingScreen.kt:478 | 原生界面 | 工程痕迹、术语不一致 | 手机界面 | P0 |
| ui/OnboardingScreen.kt:479 | 更贴近手机操作的会话列表与消息视图；高级功能仍会回到网页。 | 术语不一致 | 更适合手机操作，高级功能可在网页界面使用。 | P0 |
| ui/OnboardingScreen.kt:485 | 稍后可在 设置 → 网页与兼容性 中修改。接口不可用时会自动回到官方界面，不会中断正在运行的任务。 | 术语不一致、长度 | 可在设置的“网页与兼容性”中切换。<br>手机界面无法使用时，将自动显示网页界面。 | P0 |
| ui/OnboardingScreen.kt:507 | ✓ | 保留 | ✓ | P0 |
| ui/OnboardingScreen.kt:519 | 设置完成 | 保留 | 设置完成 | P0 |
| ui/OnboardingScreen.kt:521 | 可以马上开始；环境还没准备好时，进入后仍会看到准备入口。 | 清晰度 | 进入后即可开始；环境未准备好时可继续安装。 | P0 |
| ui/OnboardingScreen.kt:529 | 发第一条消息 | 清晰度 | 开始对话 | P0 |
| ui/OnboardingScreen.kt:532 | 进入对话，直接描述你想让助手完成的事情。 | 保留 | 进入对话，直接描述你想让助手完成的事情。 | P0 |
| ui/OnboardingScreen.kt:540 | 连接远程电脑 | 保留 | 连接远程电脑 | P0 |
| ui/OnboardingScreen.kt:543 | 用 SSH 连接你已经在使用的电脑，不需要在本机安装运行环境。 | 清晰度 | 连接已运行助手的电脑，无需在手机安装环境。 | P0 |
| ui/OnboardingScreen.kt:585 | 完成设置 | 保留 | 完成设置 | P0 |
| ui/OnboardingScreen.kt:585 | 已更新到新版本 | 保留 | 已更新到新版本 | P0 |
| ui/OnboardingScreen.kt:589 | 可以重新查看引导，了解当前设置。 | 保留 | 可以重新查看引导，了解当前设置。 | P0 |
| ui/OnboardingScreen.kt:590 | 已完成 ${banner.completedSteps} / ${banner.totalSteps} 步 | 清晰度 | 已完成 {数量} / {数量} 步 | P0 |
| ui/OnboardingScreen.kt:591 |  · 下一步：${stepTitle(it)} | 清晰度 | · 下一步：{名称} | P0 |
| ui/OnboardingScreen.kt:596 | 查看 | 保留 | 查看 | P0 |
| ui/OnboardingScreen.kt:596 | 继续 | 保留 | 继续 | P0 |
| ui/OnboardingScreen.kt:599 | 关闭 | 保留 | 关闭 | P0 |
| ui/OnboardingScreen.kt:606 | 欢迎 | 保留 | 欢迎 | P0 |
| ui/OnboardingScreen.kt:607 | 准备环境 | 保留 | 准备环境 | P0 |
| ui/OnboardingScreen.kt:608 | 选择界面 | 保留 | 选择界面 | P0 |
| ui/OnboardingScreen.kt:609 | 完成 | 保留 | 完成 | P0 |

### [ui/OperationProgress.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/OperationProgress.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/OperationProgress.kt:73 | 查看详情 | 保留 | 查看详情 | P0 |
| ui/OperationProgress.kt:75 | 传输中 | 保留 | 传输中 | P0 |
| ui/OperationProgress.kt:75 | 传输中 $p% | 清晰度 | 传输中 {内容}% | P0 |
| ui/OperationProgress.kt:77 | 文件 | 保留 | 文件 | P0 |
| ui/OperationProgress.kt:88 | 操作进度 | 保留 | 操作进度 | P0 |
| ui/OperationProgress.kt:91 | $title，上次操作没有完成 | 清晰度 | {名称}，上次操作没有完成 | P0 |
| ui/OperationProgress.kt:93 | $title，正在准备 | 清晰度 | {名称}，正在准备 | P0 |
| ui/OperationProgress.kt:158 | 不定进度 | 保留 | 不定进度 | P0 |
| ui/OperationProgress.kt:166 | 不定进度位置 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgress.kt:200 | 操作失败 | 保留 | 操作失败 | P0 |
| ui/OperationProgress.kt:200 | 操作进度 | 保留 | 操作进度 | P0 |
| ui/OperationProgress.kt:205 | 关闭进度详情 | 保留 | 关闭进度详情 | P0 |
| ui/OperationProgress.kt:221 | 当前阶段进度 | 保留 | 当前阶段进度 | P0 |
| ui/OperationProgress.kt:223 | 上次操作没有完成 | 保留 | 上次操作没有完成 | P0 |
| ui/OperationProgress.kt:224 | 当前阶段 ${state.percent}% | 清晰度 | 当前阶段 {进度}% | P0 |
| ui/OperationProgress.kt:225 | 进行中，未提供可信总量 | 清晰度 | 正在处理，请稍候。 | P0 |
| ui/OperationProgress.kt:234 | 当前阶段：${state.stage.label} | 工程痕迹 | 当前阶段：{名称} | P0 |
| ui/OperationProgress.kt:251 | 百分比只代表当前阶段；下载完成后仍需解压、校验和安装。 | 清晰度 | 下载完成后，将继续解压、检查和安装。 | P0 |
| ui/OperationProgress.kt:259 | 取消当前操作 | 保留 | 取消当前操作 | P0 |
| ui/OperationProgress.kt:266 | 返回组件入口重试 | 清晰度 | 选择组件 | P0 |
| ui/OperationProgress.kt:266 | 重试 | 保留 | 重试 | P0 |
| ui/OperationProgress.kt:270 | 文件传输 · ${transfers.size} 项 | 清晰度 | 文件传输 · {数量} 项 | P0 |
| ui/OperationProgress.kt:279 | 文件进度按实际复制或下载的字节计算；总大小未知时使用不定进度。 | 重复/啰嗦、工程痕迹 | 删除此处说明；保留各文件当前动作和实际传输量。 | P0 |

### [ui/OperationProgressPresentation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/OperationProgressPresentation.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/OperationProgressPresentation.kt:11 | 准备 | 保留 | 准备 | P0 |
| ui/OperationProgressPresentation.kt:12 | 下载 | 保留 | 下载 | P0 |
| ui/OperationProgressPresentation.kt:13 | 解压 | 保留 | 解压 | P0 |
| ui/OperationProgressPresentation.kt:14 | 校验 | 保留 | 校验 | P0 |
| ui/OperationProgressPresentation.kt:15 | 安装 | 保留 | 安装 | P0 |
| ui/OperationProgressPresentation.kt:16 | 卸载 | 保留 | 卸载 | P0 |
| ui/OperationProgressPresentation.kt:17 | 启动 | 保留 | 启动 | P0 |
| ui/OperationProgressPresentation.kt:18 | 检查 | 保留 | 检查 | P0 |
| ui/OperationProgressPresentation.kt:72 | 展开 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:72 | 解压 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:74 | 下载 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:75 | 校验 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:75 | 验证 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:76 | 写入 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:76 | 安装 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:76 | 补齐 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:77 | 卸载 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:77 | 移除 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:78 | 启动 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:79 | 准备 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:79 | 检查 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:79 | 检测 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:79 | 测速 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:79 | 自检 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:79 | 解析 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/OperationProgressPresentation.kt:102 | 操作失败 | 保留 | 操作失败 | P0 |
| ui/OperationProgressPresentation.kt:102 | 操作已取消 | 保留 | 操作已取消 | P0 |
| ui/OperationProgressPresentation.kt:104 | 操作已停止，可以重试并复用已校验的下载。 | 保留 | 操作已停止，可以重试并复用已校验的下载。 | P0 |
| ui/OperationProgressPresentation.kt:104 | 操作没有完成，请检查当前阶段后重试。 | 保留 | 操作没有完成，请检查当前阶段后重试。 | P0 |
| ui/OperationProgressPresentation.kt:105 |  组件版本已更改，请从组件入口重新发起安装。 | 清晰度 | 所选版本已变化，请回到组件页重新安装。 | P0 |
| ui/OperationProgressPresentation.kt:110 | 更新失败 | 保留 | 更新失败 | P0 |
| ui/OperationProgressPresentation.kt:111 | 候选版本没有通过检查；可以查看日志后重试。 | 清晰度 | 新版本未通过检查，请查看问题详情后重试。 | P0 |
| ui/OperationProgressPresentation.kt:116 | 启动失败 | 保留 | 启动失败 | P0 |
| ui/OperationProgressPresentation.kt:117 | DSH 没有正常启动；可以检查运行环境后重试。 | 术语不一致 | 助手启动失败，请重新启动。 | P0 |
| ui/OperationProgressPresentation.kt:121 | 运行检查未通过 | 保留 | 运行检查未通过 | P0 |
| ui/OperationProgressPresentation.kt:122 | 后台自检没有通过；可以重新检查运行环境。 | 保留 | 后台自检没有通过；可以重新检查运行环境。 | P0 |
| ui/OperationProgressPresentation.kt:126 | 安装或检查失败 | 清晰度 | 安装失败 | P0 |
| ui/OperationProgressPresentation.kt:127 | 操作没有完成；请返回组件入口选择需要重试的项目。 | 清晰度 | 请回到组件页面，选择要重试的安装。 | P0 |
| ui/OperationProgressPresentation.kt:131 | 安装已取消 | 保留 | 安装已取消 | P0 |
| ui/OperationProgressPresentation.kt:132 | 取消后可以重试，已校验的下载会尽量复用。 | 清晰度 | 安装已取消，可随时重新开始。 | P0 |
| ui/OperationProgressPresentation.kt:169 | 已用  | 清晰度 | 已用 | P0 |
| ui/OperationProgressPresentation.kt:170 | 操作已结束 | 保留 | 操作已结束 | P0 |
| ui/OperationProgressPresentation.kt:187 | 正在准备 | 保留 | 正在准备 | P0 |
| ui/OperationProgressPresentation.kt:190 | 已用  | 清晰度 | 已用 | P0 |
| ui/OperationProgressPresentation.kt:192 | 已 ${duration(quietSeconds)}没有新进展。网络、解压或安装脚本可能仍在等待；请检查网络，必要时取消后重试。 | 长度 | 已有 {耗时} 没有新进展，请检查网络。<br>若仍无变化，可取消后重试。 | P0 |
| ui/OperationProgressPresentation.kt:209 | 预计还需要几分钟 | 清晰度 | 正在准备 | P0 |
| ui/OperationProgressPresentation.kt:210 | 即将完成 | 保留 | 即将完成 | P0 |
| ui/OperationProgressPresentation.kt:214 | 即将完成 | 保留 | 即将完成 | P0 |
| ui/OperationProgressPresentation.kt:216 | 预计还需要 | 保留 | 预计还需要 | P0 |
| ui/OperationProgressPresentation.kt:218 | 预计还需要几分钟 | 清晰度 | 正在准备 | P0 |
| ui/OperationProgressPresentation.kt:223 | 正在执行，完成后会自动更新 | 清晰度 | 正在处理，完成后会自动显示结果。 | P0 |
| ui/OperationProgressPresentation.kt:225 | 已下载 ${bytes(download.received)} · 总大小未知 | 被动/实现说明 | 已下载 {大小} | P0 |
| ui/OperationProgressPresentation.kt:226 | 正在连接下载源 · 总大小未知 | 被动/实现说明 | 正在连接下载源 | P0 |
| ui/OperationProgressPresentation.kt:227 |  · ${bytes(download.bytesPerSecond)}/秒 | 清晰度 | · {大小}/秒 | P0 |
| ui/OperationProgressPresentation.kt:228 |  · 已复用并通过校验 | 清晰度 | 使用已下载文件 | P0 |
| ui/OperationProgressPresentation.kt:233 | ${seconds / 3600}时${seconds % 3600 / 60}分${seconds % 60}秒 | 清晰度 | {时间}时{时间}分{时间}秒 | P0 |
| ui/OperationProgressPresentation.kt:234 | ${seconds / 60}分${seconds % 60}秒 | 清晰度 | {时间}分{时间}秒 | P0 |
| ui/OperationProgressPresentation.kt:235 | ${seconds}秒 | 清晰度 | {时间}秒 | P0 |

### [ui/PdfJumpPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfJumpPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/PdfJumpPolicy.kt:19 | 第 | 保留 | 第 | P1 |
| ui/PdfJumpPolicy.kt:20 | 页 | 保留 | 页 | P1 |
| ui/PdfJumpPolicy.kt:30 | 正在读取页数… | 保留 | 正在读取页数… | P1 |
| ui/PdfJumpPolicy.kt:30 | 第 ${pageIndex + 1} / $pageCount 页 | 清晰度 | 第 {内容} / {数量} 页 | P1 |
| ui/PdfJumpPolicy.kt:35 | 请输入 1–$pageCount 之间的页码 | 清晰度 | 请输入 1–{数量} 之间的页码 | P1 |
| ui/PdfJumpPolicy.kt:35 | 请输入页码 | 保留 | 请输入页码 | P1 |

### [ui/PdfMemoryPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfMemoryPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/PdfMemoryPolicy.kt:23 | PDF 页位图尺寸无效 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/PdfPageCache.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfPageCache.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/PdfPageCache.kt:14 | PDF 页缓存预算必须为正数 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/PdfPageCache.kt:37 | PDF 页位图字节数必须为正数 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/PdfPageMemory.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfPageMemory.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/PdfPageMemory.kt:48 | PDF 页预留字节必须为正数 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/PdfPreloadPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfPreloadPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/PdfPreloadPolicy.kt:21 | PDF 预加载页数不能为负数 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/PdfPreloadPolicy.kt:55 | PDF 预加载页数不能为负数 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/PdfRenderPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/PdfRenderPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/PdfRenderPolicy.kt:23 | PDF 渲染上限无效 | 工程痕迹 | PDF 显示上限无法使用 | P1 |
| ui/PdfRenderPolicy.kt:33 | PDF 页面尺寸无效 | 清晰度 | PDF 页面尺寸无法使用 | P1 |
| ui/PdfRenderPolicy.kt:34 | PDF 渲染上限无效 | 工程痕迹 | PDF 显示上限无法使用 | P1 |
| ui/PdfRenderPolicy.kt:56 | PDF 页面尺寸无效 | 清晰度 | PDF 页面尺寸无法使用 | P1 |
| ui/PdfRenderPolicy.kt:57 | PDF 渲染上限无效 | 工程痕迹 | PDF 显示上限无法使用 | P1 |

### [ui/ReaderState.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/ReaderState.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/ReaderState.kt:14 | 自动 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/ReaderState.kt:30 | 自动 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/ReaderState.kt:36 | 自动 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/ReaderState.kt:102 | 自动 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/RemoteHostSettings.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/RemoteHostSettings.kt:245 | 连接正在进行，请完成当前连接或指纹确认后再添加。 | 保留 | 连接正在进行，请完成当前连接或指纹确认后再添加。 | P0 |
| ui/RemoteHostSettings.kt:271 | 扫码已取消，可继续粘贴配对链接。 | 保留 | 扫码已取消，可继续粘贴配对链接。 | P0 |
| ui/RemoteHostSettings.kt:273 | 这个二维码不是有效的电脑配对链接，请扫描电脑端终端显示的二维码。 | 清晰度 | 这不是电脑配对二维码，请扫描电脑连接程序中显示的二维码。 | P1 |
| ui/RemoteHostSettings.kt:283 | 扫描电脑端的配对二维码 | 保留 | 扫描电脑端的配对二维码 | P1 |
| ui/RemoteHostSettings.kt:285 | 未开启相机权限，可以返回后粘贴配对链接。 | 清晰度 | 相机未开启，可返回后粘贴配对链接。 | P0 |
| ui/RemoteHostSettings.kt:286 | 暂时无法打开相机，可以粘贴配对链接。 | 保留 | 暂时无法打开相机，可以粘贴配对链接。 | P0 |
| ui/RemoteHostSettings.kt:349 | 连接「${host.name}」？ | 清晰度 | 连接「{名称}」？ | P1 |
| ui/RemoteHostSettings.kt:352 | ${host.address}:${host.sshPort} | 清晰度 | {来源}:{端口} | P1 |
| ui/RemoteHostSettings.kt:359 | 电脑标识：${host.hostId.take(8)}…（${host.fingerprint}） | 工程痕迹 | 电脑安全指纹：{指纹} | P1 |
| ui/RemoteHostSettings.kt:364 | 发现信息只用来找到电脑。继续后会进入现有向导；首次连接会显示 SSH 主机密钥指纹，需要你手动核对后再信任。 | 长度 | 发现电脑后仍需配对，首次连接请核对电脑安全指纹。 | P0 |
| ui/RemoteHostSettings.kt:374 | 继续配对 | 保留 | 继续配对 | P1 |
| ui/RemoteHostSettings.kt:376 | 取消 | 保留 | 取消 | P0 |
| ui/RemoteHostSettings.kt:392 | 切换到远程连接？ | 保留 | 切换到远程连接？ | P1 |
| ui/RemoteHostSettings.kt:395 | 连接“${profile.name}”后会切换到远程 DSH 页面，当前本地 DSH 页面会断开；本地运行时继续在后台运行。 | 术语不一致、长度 | 连接后显示电脑上的助手，本机任务继续在后台运行。 | P1 |
| ui/RemoteHostSettings.kt:396 | 远程连接失败会自动回退本地。 | 清晰度 | 连接失败时将回到本机。 | P0 |
| ui/RemoteHostSettings.kt:403 | 连接 | 保留 | 连接 | P1 |
| ui/RemoteHostSettings.kt:405 | 取消 | 保留 | 取消 | P0 |
| ui/RemoteHostSettings.kt:412 | 删除连接“${profile.name}”？ | 清晰度 | 删除这条连接？ | P0 |
| ui/RemoteHostSettings.kt:413 | 会删除本机加密保存的密码/私钥/配对密钥和该连接的配置；不会修改远程主机。当前已建立的远程会话不受影响。 | 术语不一致、长度 | 将删除手机保存的这条连接和登录信息。<br>电脑上的数据保留，当前连接继续使用。 | P0 |
| ui/RemoteHostSettings.kt:418 | 删除 | 保留 | 删除 | P0 |
| ui/RemoteHostSettings.kt:420 | 取消 | 保留 | 取消 | P0 |
| ui/RemoteHostSettings.kt:427 | 清除当前活动配置？ | 清晰度 | 清除当前连接？ | P1 |
| ui/RemoteHostSettings.kt:428 | 将删除当前活动远程配置和本机保存的主机密钥信任记录，并断开当前远程连接。命名连接库中的其他连接不受影响。 | 长度 | 将断开电脑，并删除当前连接配置和信任记录。<br>其他已保存的连接保留。 | P0 |
| ui/RemoteHostSettings.kt:433 | 清除 | 保留 | 清除 | P1 |
| ui/RemoteHostSettings.kt:435 | 取消 | 保留 | 取消 | P0 |
| ui/RemoteHostSettings.kt:462 | 未开启权限 | 保留 | 未开启权限 | P0 |
| ui/RemoteHostSettings.kt:463 | 正在查找同一 Wi-Fi 下的电脑… | 保留 | 正在查找同一 Wi-Fi 下的电脑… | P1 |
| ui/RemoteHostSettings.kt:464 | 未发现电脑 | 清晰度 | 没有找到电脑 | P1 |
| ui/RemoteHostSettings.kt:465 | 已发现 ${state.hosts.size} 台 · 每 5 秒自动刷新 | 清晰度 | 已发现 {数量} 台，列表每 5 秒刷新。 | P1 |
| ui/RemoteHostSettings.kt:471 | 刷新 | 保留 | 刷新 | P1 |
| ui/RemoteHostSettings.kt:498 | 扫码连接 | 保留 | 扫码连接 | P1 |
| ui/RemoteHostSettings.kt:499 | 粘贴配对链接 | 保留 | 粘贴配对链接 | P1 |
| ui/RemoteHostSettings.kt:585 | 扫码连接 | 保留 | 扫码连接 | P1 |
| ui/RemoteHostSettings.kt:587 | 粘贴配对链接 | 保留 | 粘贴配对链接 | P1 |
| ui/RemoteHostSettings.kt:592 | 向导会自动读取剪贴板里的配对链接；读不到时仍可手动粘贴。 | 清晰度 | 可直接粘贴电脑生成的配对链接。 | P1 |
| ui/RemoteHostSettings.kt:607 | 了解详情 | 保留 | 了解详情 | P1 |
| ui/RemoteHostSettings.kt:607 | 收起详情 | 保留 | 收起详情 | P1 |
| ui/RemoteHostSettings.kt:612 | 首次连接会显示 SSH 主机密钥的 SHA256 指纹，需要你手动核对；以后主机密钥变化时会直接拒绝连接。 | 工程痕迹、长度 | 首次连接请核对电脑安全指纹，指纹变化时会拒绝连接。 | P0 |
| ui/RemoteHostSettings.kt:617 | 局域网发现只用 mDNS 广播电脑名、端口、电脑标识和协议版本，不包含配对 token；同一网络里的其他人也能看到这些信息。发现只用于找到电脑，不能替代配对和指纹确认。 | 工程痕迹、长度 | 同一 Wi-Fi 的人可能看到电脑名和连接地址。<br>发现电脑不等于信任电脑，连接前仍需配对并核对指纹。 | P0 |
| ui/RemoteHostSettings.kt:622 | 密码、私钥和口令使用 Android Keystore 加密保存，不写日志、不导出。中继只转发 SSH 密文，中继证书按配对链接中的指纹或系统信任证书校验，没有关闭校验的开关。 | 长度 | 密码和私钥加密保存在手机，不写入日志或导出。<br>连接服务只转发加密数据，连接前会核对服务身份。 | P0 |
| ui/RemoteHostSettings.kt:649 | 当前主机状态 | 清晰度 | 当前设备 | P1 |
| ui/RemoteHostSettings.kt:662 | 远程主机 | 术语不一致 | 远程电脑 | P1 |
| ui/RemoteHostSettings.kt:663 | SSH 隧道 | 清晰度 | 远程连接 | P1 |
| ui/RemoteHostSettings.kt:664 | 主机密钥 | 清晰度 | 电脑安全指纹 | P1 |
| ui/RemoteHostSettings.kt:668 | TLS 中继 · ${saved.relay.endpointLabel()} | 工程痕迹 | 通过连接服务 | P1 |
| ui/RemoteHostSettings.kt:668 | 当前配置 | 保留 | 当前配置 | P1 |
| ui/RemoteHostSettings.kt:669 | 远程 DSH | 术语不一致 | 电脑助手 | P1 |
| ui/RemoteHostSettings.kt:671 | 当前配置 | 保留 | 当前配置 | P1 |
| ui/RemoteHostSettings.kt:672 | 远程 DSH | 术语不一致 | 电脑助手 | P1 |
| ui/RemoteHostSettings.kt:679 | 正在${model.remoteStage.label}… | 工程痕迹 | 正在{名称}… | P1 |
| ui/RemoteHostSettings.kt:705 | 连接当前配置 | 保留 | 连接当前配置 | P1 |
| ui/RemoteHostSettings.kt:708 | 查看/编辑当前配置 | 清晰度 | 编辑连接 | P1 |
| ui/RemoteHostSettings.kt:711 | 清除当前活动配置与主机密钥 | 清晰度 | 清除当前连接 | P1 |
| ui/RemoteHostSettings.kt:731 | 我的连接 | 保留 | 我的连接 | P1 |
| ui/RemoteHostSettings.kt:732 | ${model.remoteProfiles.size} 个 | 清晰度 | {数量} 个 | P1 |
| ui/RemoteHostSettings.kt:736 | 还没有保存的连接。完成向导的“测试连接”后，可以命名保存，下次一键连接。 | 清晰度 | 还没有连接。测试连接后保存，下次可直接连接。 | P0 |
| ui/RemoteHostSettings.kt:743 | TLS 中继 · ${it.endpointLabel()} · ${profile.config.user} | 清晰度 | 通过连接服务 | P1 |
| ui/RemoteHostSettings.kt:754 | 上次使用 | 保留 | 上次使用 | P1 |
| ui/RemoteHostSettings.kt:759 | 远程 DSH 127.0.0.1:${profile.config.remoteDshPort} | 术语不一致 | 助手端口：{端口} | P1 |
| ui/RemoteHostSettings.kt:764 | 连接 | 保留 | 连接 | P1 |
| ui/RemoteHostSettings.kt:765 | 编辑 | 保留 | 编辑 | P1 |
| ui/RemoteHostSettings.kt:766 | 删除 | 保留 | 删除 | P0 |
| ui/RemoteHostSettings.kt:786 | 首次连接：核对远程主机密钥 | 术语不一致 | 首次连接，请核对指纹 | P1 |
| ui/RemoteHostSettings.kt:787 | 远程主机：$host | 术语不一致 | 远程电脑：{来源} | P1 |
| ui/RemoteHostSettings.kt:792 | 请在远程主机执行 ssh-keygen -lf /etc/ssh/ssh_host_rsa_key.pub（ECDSA 用对应文件）并逐字核对。指纹不一致时不要信任。 | 术语不一致、长度 | 请通过电脑屏幕或可信渠道核对安全指纹。<br>指纹不一致时请停止连接。检查命令放在详情中。 | P0 |
| ui/RemoteHostSettings.kt:797 | 我已通过独立渠道核对上述 SHA256 指纹 | 工程痕迹 | 我已核对电脑上的安全指纹 | P0 |
| ui/RemoteHostSettings.kt:800 | 核对无误，信任并继续 | 清晰度 | 信任并继续 | P1 |
| ui/RemoteHostSettings.kt:801 | 取消 | 保留 | 取消 | P0 |
| ui/RemoteHostSettings.kt:843 | 已选择私钥文件：${picked.name}（内容不会显示） | 清晰度 | 已选择私钥文件：{文件名} | P1 |
| ui/RemoteHostSettings.kt:845 | 私钥读取完成，但草稿或步骤已变化，未应用；请重新选择 | 清晰度 | 填写内容已变化，请重新选择私钥文件。 | P1 |
| ui/RemoteHostSettings.kt:848 | 无法读取私钥文件，请重新选择 | 保留 | 无法读取私钥文件，请重新选择 | P0 |
| ui/RemoteHostSettings.kt:885 | 请先完成当前连接或指纹确认，再测试这条连接。 | 保留 | 请先完成当前连接或指纹确认，再测试这条连接。 | P0 |
| ui/RemoteHostSettings.kt:891 | 正在读取私钥文件，请稍候再开始测试 | 保留 | 正在读取私钥文件，请稍候再开始测试 | P1 |
| ui/RemoteHostSettings.kt:897 | 连接配置不完整，请返回上一步检查主机、账号和认证信息 | 清晰度 | 连接信息不完整，请返回检查电脑、账号和登录方式。 | P1 |
| ui/RemoteHostSettings.kt:901 | 当前另有连接正在进行，请完成后重新测试 | 保留 | 当前另有连接正在进行，请完成后重新测试 | P1 |
| ui/RemoteHostSettings.kt:913 | 正在读取私钥文件，请稍候再继续 | 保留 | 正在读取私钥文件，请稍候再继续 | P1 |
| ui/RemoteHostSettings.kt:935 | 请先重新测试当前连接，再保存。 | 保留 | 请先重新测试当前连接，再保存。 | P1 |
| ui/RemoteHostSettings.kt:959 | 编辑远程连接 | 保留 | 编辑远程连接 | P1 |
| ui/RemoteHostSettings.kt:959 | 远程连接向导 | 保留 | 远程连接向导 | P1 |
| ui/RemoteHostSettings.kt:963 | 第 ${state.stepNumber}/${state.stepTotal} 步 · ${state.step.titleFor(state.method)} | 清晰度 | 第 {状态}/{数量} 步 · {名称} | P1 |
| ui/RemoteHostSettings.kt:968 | 关闭 | 保留 | 关闭 | P1 |
| ui/RemoteHostSettings.kt:1032 | 下一步：填写主机与账号 | 清晰度 | 下一步 | P1 |
| ui/RemoteHostSettings.kt:1035 | 上一步 | 保留 | 上一步 | P1 |
| ui/RemoteHostSettings.kt:1039 | 下一步：确认这台电脑 | 清晰度 | 下一步 | P1 |
| ui/RemoteHostSettings.kt:1041 | 下一步：选择认证 | 清晰度 | 下一步 | P1 |
| ui/RemoteHostSettings.kt:1047 | 返回修改 | 保留 | 返回修改 | P1 |
| ui/RemoteHostSettings.kt:1052 | 这是你的电脑 | 保留 | 这是你的电脑 | P1 |
| ui/RemoteHostSettings.kt:1055 | 上一步 | 保留 | 上一步 | P1 |
| ui/RemoteHostSettings.kt:1060 | 开始测试连接 | 保留 | 开始测试连接 | P1 |
| ui/RemoteHostSettings.kt:1060 | 正在读取私钥… | 保留 | 正在读取私钥… | P1 |
| ui/RemoteHostSettings.kt:1063 | 返回修改 | 保留 | 返回修改 | P1 |
| ui/RemoteHostSettings.kt:1070 | 核对无误，继续测试 | 清晰度 | 继续测试 | P1 |
| ui/RemoteHostSettings.kt:1076 | 返回修改 | 保留 | 返回修改 | P1 |
| ui/RemoteHostSettings.kt:1082 | 重试 | 保留 | 重试 | P1 |
| ui/RemoteHostSettings.kt:1087 | 返回修改 | 保留 | 返回修改 | P1 |
| ui/RemoteHostSettings.kt:1092 | 正在测试… | 保留 | 正在测试… | P1 |
| ui/RemoteHostSettings.kt:1092 | 重新测试 | 保留 | 重新测试 | P1 |
| ui/RemoteHostSettings.kt:1097 | 重新测试 | 保留 | 重新测试 | P1 |
| ui/RemoteHostSettings.kt:1100 | 仅保存 | 保留 | 仅保存 | P1 |
| ui/RemoteHostSettings.kt:1100 | 仅保存修改 | 保留 | 仅保存修改 | P1 |
| ui/RemoteHostSettings.kt:1103 | 保存并连接 | 保留 | 保存并连接 | P1 |
| ui/RemoteHostSettings.kt:1117 | 先选择你现在的网络情况。三种方式最终都走同一套 SSH 主机密钥校验和加密存储。 | 清晰度 | 先选择适合当前网络的连接方式。 | P1 |
| ui/RemoteHostSettings.kt:1150 | 连接远程会切换到远程 DSH 页面，当前本地 DSH 页面会断开；本地运行时继续在后台运行。远程连接失败会自动回退本地。 | 术语不一致、长度 | 连接后显示电脑上的助手，本机任务继续运行。<br>连接失败时将回到本机。 | P0 |
| ui/RemoteHostSettings.kt:1163 | 手机已尝试从剪贴板读取配对链接；识别成功会自动填入并算好 6 位码，你只需确认。链接包含一次性密钥，不要截图或转发。 | 长度 | 请粘贴电脑生成的配对链接，并核对 6 位码。<br>链接含有登录信息，请勿截图或转发。 | P1 |
| ui/RemoteHostSettings.kt:1165 | 填写远程主机的地址和登录账号。端口不确定时先保留默认值。 | 术语不一致 | 填写远程电脑的地址和登录账号。端口不确定时先保留默认值。 | P1 |
| ui/RemoteHostSettings.kt:1173 | 配对链接 | 清晰度 | 配对连接 | P1 |
| ui/RemoteHostSettings.kt:1174 | tanuki-relay://v1?relay=…&amp;room=…&amp;token=…&amp;host=… | 工程痕迹、长度 | 粘贴电脑生成的完整链接 | P1 |
| ui/RemoteHostSettings.kt:1194 | 改用剪贴板里的配对链接 | 清晰度 | 更换配对链接 | P1 |
| ui/RemoteHostSettings.kt:1205 | 电脑上应显示 | 保留 | 电脑上应显示 | P1 |
| ui/RemoteHostSettings.kt:1214 | 手机已按配对链接自动算出这个 6 位码，不需要手输。请和电脑屏幕上显示的值对照。 | 清晰度 | 请核对手机和电脑显示的 6 位码是否一致。 | P1 |
| ui/RemoteHostSettings.kt:1218 | 如果电脑显示的不是这个数，说明链接不是同一次配对：不要继续，返回电脑重新复制完整链接。 | 长度 | 配对码不一致时请停止连接，重新复制电脑上的链接。 | P1 |
| ui/RemoteHostSettings.kt:1229 | 6 位配对码（可选校对） | 清晰度 | 6 位配对码 | P1 |
| ui/RemoteHostSettings.kt:1237 | 已自动填入上面的码；只有想和电脑核对时才需要改这里。 | 清晰度 | 已自动填入，请核对电脑显示的数字。 | P1 |
| ui/RemoteHostSettings.kt:1244 | 填入自动计算的 ${preview.code} | 清晰度 | 填入配对码 | P1 |
| ui/RemoteHostSettings.kt:1254 | 6 位配对码（手动回退） | 清晰度 | 6 位配对码 | P1 |
| ui/RemoteHostSettings.kt:1255 | 链接解析成功后会自动填入 | 保留 | 链接解析成功后会自动填入 | P1 |
| ui/RemoteHostSettings.kt:1264 | 配对码只是链接的人工核对值，不能单独恢复中继地址/房间/token，因此仍需完整的配对链接。 | 工程痕迹、长度 | 配对码用于核对电脑，连接仍需完整配对链接。 | P1 |
| ui/RemoteHostSettings.kt:1269 | 配对链接由远程电脑生成；手机只经 TLS 中继转发 SSH 密文，仍会独立校验 SSH 主机密钥。 | 长度 | 连接使用加密传输，并会单独核对电脑安全指纹。 | P1 |
| ui/RemoteHostSettings.kt:1278 | 主机地址 | 保留 | 主机地址 | P1 |
| ui/RemoteHostSettings.kt:1278 | 展示名（可选） | 清晰度 | 电脑名称 | P1 |
| ui/RemoteHostSettings.kt:1287 | 示例：192.168.1.20（局域网）、dev.example.com（域名）、[2001:db8::10]（IPv6）；不要带 http:// 或端口。 | 长度 | 例如 192.168.1.20。只填地址，端口填在下方。<br>更多地址格式见详情。 | P1 |
| ui/RemoteHostSettings.kt:1297 | SSH 端口 | 保留 | SSH 端口 | P1 |
| ui/RemoteHostSettings.kt:1298 | 22 | 保留 | 22 | P1 |
| ui/RemoteHostSettings.kt:1307 | 用户名 | 保留 | 用户名 | P1 |
| ui/RemoteHostSettings.kt:1308 | tanuki | 保留 | tanuki | P1 |
| ui/RemoteHostSettings.kt:1316 | 用户名是远程电脑上的登录账号，可用 whoami 查看；不是 DSH 的显示名。 | 术语不一致 | 填写电脑登录账号，不确定时可在电脑上查看。 | P1 |
| ui/RemoteHostSettings.kt:1325 | 远程 DSH 端口 | 术语不一致 | 助手端口 | P1 |
| ui/RemoteHostSettings.kt:1326 | 3080 | 保留 | 3080 | P1 |
| ui/RemoteHostSettings.kt:1335 | DSH token（可选） | 工程痕迹、术语不一致 | 登录验证码 | P1 |
| ui/RemoteHostSettings.kt:1344 | 远程 DSH 端口是 DSH 在远程电脑 127.0.0.1 上监听的端口，默认 3080；SSH 隧道只转发到这个回环端口。 | 术语不一致、长度 | 填写电脑上助手使用的端口，默认 3080。<br>连接设置详情中可查看如何确认。 | P1 |
| ui/RemoteHostSettings.kt:1355 | 配对链接已失效或无法解析，请返回上一步重新复制。 | 保留 | 配对链接已失效或无法解析，请返回上一步重新复制。 | P0 |
| ui/RemoteHostSettings.kt:1363 | 链接解析成功。请确认下面这台电脑确实是你自己的，再继续登录。 | 保留 | 链接解析成功。请确认下面这台电脑确实是你自己的，再继续登录。 | P1 |
| ui/RemoteHostSettings.kt:1372 | 电脑名 | 保留 | 电脑名 | P1 |
| ui/RemoteHostSettings.kt:1380 | 中继地址 | 清晰度 | 连接服务地址 | P1 |
| ui/RemoteHostSettings.kt:1385 | 中继证书指纹 | 清晰度 | 服务安全指纹 | P0 |
| ui/RemoteHostSettings.kt:1395 | 展开完整指纹 | 清晰度 | 查看指纹 | P0 |
| ui/RemoteHostSettings.kt:1395 | 收起完整指纹 | 清晰度 | 收起指纹 | P0 |
| ui/RemoteHostSettings.kt:1398 | 系统 CA（链接未固定证书） | 清晰度 | 使用系统信任的证书 | P0 |
| ui/RemoteHostSettings.kt:1400 | 手机会用系统 CA 和主机名校验中继；如果电脑使用的是自签名证书，请让电脑重新生成带证书指纹的链接。 | 长度 | 将使用系统信任的证书核对连接服务身份。<br>自签名证书需在电脑上重新生成带安全指纹的链接。 | P0 |
| ui/RemoteHostSettings.kt:1406 | SSH 目标 | 清晰度 | 目标电脑 | P1 |
| ui/RemoteHostSettings.kt:1409 | 中继只转发 SSH 密文；手机仍会单独核对远程主机的 SSH 密钥指纹。 | 术语不一致 | 数据以加密方式传输，连接时仍需核对电脑指纹。 | P0 |
| ui/RemoteHostSettings.kt:1414 | 远程 DSH | 术语不一致 | 电脑助手 | P1 |
| ui/RemoteHostSettings.kt:1417 | 6 位配对码 | 保留 | 6 位配对码 | P1 |
| ui/RemoteHostSettings.kt:1426 | 电脑上应显示同一个 ${preview.code}；手机按链接自动算出，不需要手输。 | 清晰度 | 请确认电脑显示相同的 6 位码，无需手动输入。 | P1 |
| ui/RemoteHostSettings.kt:1432 | 如果电脑名、中继地址或证书指纹与电脑上显示的不一致，请返回修改，不要点“这是你的电脑”。 | 长度 | 电脑名、连接地址或安全指纹不一致时，请返回重新配对。 | P0 |
| ui/RemoteHostSettings.kt:1447 | 选择 SSH 认证方式。密码和私钥只会传给 SSH，并加密保存在本机。 | 清晰度 | 选择电脑登录方式。密码和私钥加密保存在手机。 | P1 |
| ui/RemoteHostSettings.kt:1455 | 私钥 | 保留 | 私钥 | P1 |
| ui/RemoteHostSettings.kt:1461 | 密码 | 保留 | 密码 | P1 |
| ui/RemoteHostSettings.kt:1469 | SSH 密码 | 保留 | SSH 密码 | P1 |
| ui/RemoteHostSettings.kt:1477 | 密码认证需要远程 sshd 允许 PasswordAuthentication；更推荐使用私钥。 | 长度 | 电脑需允许密码登录，也可选择私钥登录。 | P0 |
| ui/RemoteHostSettings.kt:1482 | 私钥文件 | 保留 | 私钥文件 | P1 |
| ui/RemoteHostSettings.kt:1484 | 选择私钥文件 | 保留 | 选择私钥文件 | P1 |
| ui/RemoteHostSettings.kt:1486 | 未选择文件 | 保留 | 未选择文件 | P1 |
| ui/RemoteHostSettings.kt:1495 | 正在读取私钥文件… | 清晰度 | 正在读取私钥… | P1 |
| ui/RemoteHostSettings.kt:1502 | 或粘贴 OpenSSH/PEM 私钥全文 | 清晰度 | 或粘贴完整私钥 | P1 |
| ui/RemoteHostSettings.kt:1514 | 私钥口令（可选） | 保留 | 私钥口令（可选） | P1 |
| ui/RemoteHostSettings.kt:1522 | 私钥留在手机本地，不会上传；当前支持 RSA/ECDSA 私钥，Ed25519 暂不支持。带口令的私钥必须填写口令。 | 被动/实现说明、长度 | 支持 RSA 或 ECDSA 私钥。带口令的私钥需填写口令。<br>私钥文件只保存在本机。 | P1 |
| ui/RemoteHostSettings.kt:1536 | 这是首次连接该主机（或本机还没有信任记录）。请先核对指纹，再决定是否信任。 | 清晰度 | 首次连接，请核对电脑安全指纹，再决定是否信任。 | P0 |
| ui/RemoteHostSettings.kt:1545 | 远程主机 | 术语不一致 | 远程电脑 | P1 |
| ui/RemoteHostSettings.kt:1546 | 由配对链接指定 | 保留 | 由配对链接指定 | P1 |
| ui/RemoteHostSettings.kt:1547 | 主机密钥 SHA256 | 工程痕迹 | 电脑安全指纹 | P1 |
| ui/RemoteHostSettings.kt:1558 | 在远程主机执行：ssh-keygen -lf /etc/ssh/ssh_host_rsa_key.pub（ECDSA 用 ssh_host_ecdsa_key.pub），逐字核对上面的 SHA256。 | 工程痕迹、术语不一致、长度 | 请在电脑上查看登录密钥的安全指纹，并逐字核对。<br>检查命令放在连接详情中。 | P1 |
| ui/RemoteHostSettings.kt:1567 | 我已通过独立渠道核对上述 SHA256 指纹，确认信任此主机 | 工程痕迹 | 我已通过可信渠道核对电脑安全指纹 | P0 |
| ui/RemoteHostSettings.kt:1572 | 不要仅因为页面提示就点信任。指纹不一致可能意味着主机被重装，也可能意味着中间人攻击；无法核对时应停止连接。 | 长度 | 指纹变化可能来自电脑重装，也可能是连接被冒用。<br>无法确认时，请停止连接。 | P0 |
| ui/RemoteHostSettings.kt:1581 | 正在分阶段验证连接。每一阶段失败都会指出原因和下一步，不会只显示“连接失败”。 | 清晰度 | 正在连接电脑并检查助手。 | P0 |
| ui/RemoteHostSettings.kt:1598 | 正在测试，请稍候… | 保留 | 正在测试，请稍候… | P1 |
| ui/RemoteHostSettings.kt:1612 | ✓ | 保留 | ✓ | P1 |
| ui/RemoteHostSettings.kt:1618 | ✕ | 保留 | ✕ | P1 |
| ui/RemoteHostSettings.kt:1624 | ○ | 保留 | ○ | P1 |
| ui/RemoteHostSettings.kt:1632 | 已完成 | 保留 | 已完成 | P1 |
| ui/RemoteHostSettings.kt:1633 | 进行中 | 保留 | 进行中 | P1 |
| ui/RemoteHostSettings.kt:1634 | 失败 | 保留 | 失败 | P0 |
| ui/RemoteHostSettings.kt:1654 | 测试通过 | 保留 | 测试通过 | P1 |
| ui/RemoteHostSettings.kt:1655 | SSH 认证与远程 DSH 探测均成功。 | 术语不一致 | 电脑登录成功，助手可以连接。 | P1 |
| ui/RemoteHostSettings.kt:1656 | 本机隧道 | 清晰度 | 连接端口 | P1 |
| ui/RemoteHostSettings.kt:1657 | 主机密钥 | 清晰度 | 电脑安全指纹 | P1 |
| ui/RemoteHostSettings.kt:1664 | 连接名称 | 保留 | 连接名称 | P1 |
| ui/RemoteHostSettings.kt:1665 | 例如：家里电脑、公司开发机 | 保留 | 例如：家里电脑、公司开发机 | P1 |
| ui/RemoteHostSettings.kt:1672 | 保存并连接会切换到远程 DSH 页面，当前本地 DSH 页面会断开；本地运行时继续在后台运行。之后可在“我的连接”里随时编辑或删除。 | 术语不一致、长度 | 保存后即可连接电脑。可在“我的连接”中编辑或删除。<br>本机任务继续在后台运行。 | P0 |
| ui/RemoteHostSettings.kt:1702 | ${info.stageLabel}失败 | 工程痕迹 | 连接失败 | P0 |
| ui/RemoteHostSettings.kt:1704 | 下一步：${info.suggestion} | 清晰度 | 下一步：{内容} | P1 |
| ui/RemoteHostSettings.kt:1708 | 重试 | 保留 | 重试 | P1 |
| ui/RemoteHostSettings.kt:1711 | 返回修改 | 保留 | 返回修改 | P1 |
| ui/RemoteHostSettings.kt:1741 | 切换中… | 保留 | 切换中… | P1 |
| ui/RemoteHostSettings.kt:1742 | 电脑·已连接 | 保留 | 电脑·已连接 | P1 |
| ui/RemoteHostSettings.kt:1743 | 本机·已回退 | 保留 | 本机·已回退 | P1 |
| ui/RemoteHostSettings.kt:1745 | 本机·运行中 | 保留 | 本机·运行中 | P1 |
| ui/RemoteHostSettings.kt:1747 | 本机·未运行 | 保留 | 本机·未运行 | P1 |
| ui/RemoteHostSettings.kt:1816 | 切换主机 | 清晰度 | 切换设备 | P1 |
| ui/RemoteHostSettings.kt:1825 | 正在切换，请稍候… | 保留 | 正在切换，请稍候… | P1 |
| ui/RemoteHostSettings.kt:1829 | 本机 | 保留 | 本机 | P1 |
| ui/RemoteHostSettings.kt:1830 | 本地 DSH · 未运行 | 术语不一致 | 本机助手未启动 | P1 |
| ui/RemoteHostSettings.kt:1830 | 本地 DSH · 运行中 | 术语不一致 | 本机助手正在运行 | P1 |
| ui/RemoteHostSettings.kt:1838 | TLS 中继 · ${it.endpointLabel()} | 清晰度 | 通过连接服务 | P1 |
| ui/RemoteHostSettings.kt:1853 | 切换失败，已回退本机 | 保留 | 切换失败，已回退本机 | P0 |
| ui/RemoteHostSettings.kt:1856 | 返回原主机 | 保留 | 返回原主机 | P1 |
| ui/RemoteHostSettings.kt:1857 | 留在本机 | 保留 | 留在本机 | P1 |
| ui/RemoteHostSettings.kt:1886 | 当前 | 保留 | 当前 | P1 |
| ui/RemoteHostSettings.kt:1892 | 本地运行时 | 术语不一致 | 本机助手 | P1 |
| ui/RemoteHostSettings.kt:1893 | 正在连接 | 保留 | 正在连接 | P1 |
| ui/RemoteHostSettings.kt:1894 | 远程已连接 | 清晰度 | 电脑已连接 | P1 |
| ui/RemoteHostSettings.kt:1895 | 已回退本地 | 清晰度 | 已回到本机 | P1 |
| ui/RemoteHostSettings.kt:1900 | 当前使用手机上的 DSH。选择下面的电脑即可开始连接。 | 术语不一致 | 当前使用本机助手，选择电脑即可连接。 | P1 |
| ui/RemoteHostSettings.kt:1902 | 正在连接电脑，请稍候。 | 保留 | 正在连接电脑，请稍候。 | P1 |
| ui/RemoteHostSettings.kt:1904 | 当前使用电脑上的 DSH。断开后会回到手机。 | 术语不一致 | 当前使用电脑上的助手，断开后会回到本机。 | P1 |
| ui/RemoteHostSettings.kt:1906 | 未能连接电脑，已回到手机。可以按下方提示重试。 | 保留 | 未能连接电脑，已回到手机。可以按下方提示重试。 | P1 |
| ui/RemoteHostSettings.kt:1932 | 所选私钥文件 | 保留 | 所选私钥文件 | P1 |
| ui/RemoteHostSettings.kt:1942 | 私钥文件过大（上限 64 KiB），请选择正确的私钥 | 清晰度 | 私钥文件过大，请选择不超过 64 KiB 的文件。 | P1 |
| ui/RemoteHostSettings.kt:1947 | 无法读取所选文件，请重新选择 | 保留 | 无法读取所选文件，请重新选择 | P0 |
| ui/RemoteHostSettings.kt:1949 | 私钥文件为空，请重新选择 | 保留 | 私钥文件为空，请重新选择 | P0 |

### [ui/RuntimeComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/RuntimeComponents.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/RuntimeComponents.kt:112 | chevronRotation | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/RuntimeComponents.kt:120 | 展开 | 保留 | 展开 | P0 |
| ui/RuntimeComponents.kt:120 | 收起 | 保留 | 收起 | P0 |
| ui/RuntimeComponents.kt:125 | 已展开 | 保留 | 已展开 | P0 |
| ui/RuntimeComponents.kt:125 | 已收起 | 保留 | 已收起 | P0 |
| ui/RuntimeComponents.kt:151 | 安装没有完成 | 清晰度 | 安装失败 | P0 |
| ui/RuntimeComponents.kt:152 | 已安装 | 保留 | 已安装 | P0 |
| ui/RuntimeComponents.kt:153 | 未安装 | 保留 | 未安装 | P0 |
| ui/RuntimeComponents.kt:211 | Node.js 版本 | 保留 | Node.js 版本 | P0 |
| ui/RuntimeComponents.kt:211 | Ubuntu 镜像 | 保留 | Ubuntu 镜像 | P0 |
| ui/RuntimeComponents.kt:239 | 取消下载 | 保留 | 取消下载 | P0 |
| ui/RuntimeComponents.kt:246 | 安装所选版本 | 保留 | 安装所选版本 | P0 |
| ui/RuntimeComponents.kt:246 | 重新安装 | 保留 | 重新安装 | P0 |
| ui/RuntimeComponents.kt:248 | 已安装 ${model.title} ${model.version} | 清晰度 | 已安装 {名称} {版本} | P0 |
| ui/RuntimeComponents.kt:267 | 卸载 | 保留 | 卸载 | P0 |
| ui/RuntimeComponents.kt:277 | 安装 | 保留 | 安装 | P0 |
| ui/RuntimeComponents.kt:277 | 安装 ${model.title} | 清晰度 | 安装组件 | P0 |
| ui/RuntimeComponents.kt:277 | 进行中… | 保留 | 进行中… | P0 |
| ui/RuntimeComponents.kt:303 | $label：$value | 清晰度 | {名称}：{内容} | P0 |
| ui/RuntimeComponents.kt:389 | 当前已安装：$installedVersion。 | 清晰度 | 当前已安装：{版本}。 | P0 |
| ui/RuntimeComponents.kt:411 | 选择下载源 | 保留 | 选择下载源 | P0 |
| ui/RuntimeComponents.kt:414 | 下载源 | 保留 | 下载源 | P0 |
| ui/RuntimeComponents.kt:464 | 使用已校验的本地缓存 | 清晰度 | 使用已校验的本机缓存 | P0 |
| ui/RuntimeComponents.kt:467 | $percent% | 清晰度 | 使用已下载文件 | P0 |
| ui/RuntimeComponents.kt:470 | 下载进度 $percent%，已接收 ${byteSize(p.received)}，共 ${byteSize(p.total)} | 清晰度 | 下载进度 {进度}%，已接收 {大小}，共 {大小} | P0 |
| ui/RuntimeComponents.kt:472 | ${byteSize(p.received)} / ${byteSize(p.total)} | 清晰度 | {大小} / {大小} | P0 |
| ui/RuntimeComponents.kt:475 | 下载进行中，总大小未知 | 被动/实现说明 | 正在下载 | P0 |
| ui/RuntimeComponents.kt:477 | 总大小未知 · 已接收 ${byteSize(p.received)} | 被动/实现说明 | 已下载 {大小} | P0 |
| ui/RuntimeComponents.kt:481 |  · 约剩余 ${etaText(seconds)} | 清晰度 | · 约剩余 {时间} | P0 |
| ui/RuntimeComponents.kt:481 | ${byteSize(p.bytesPerSecond)}/s | 清晰度 | {大小}/s | P0 |
| ui/RuntimeComponents.kt:496 | ${seconds / 3600}时${(seconds % 3600) / 60}分 | 清晰度 | {时间}时{时间}分 | P0 |
| ui/RuntimeComponents.kt:497 | ${seconds / 60}分${seconds % 60}秒 | 清晰度 | {时间}分{时间}秒 | P0 |
| ui/RuntimeComponents.kt:498 | ${seconds}秒 | 清晰度 | {时间}秒 | P0 |

### [ui/SetupGuide.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/SetupGuide.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/SetupGuide.kt:65 | TANUKI · 本地开发空间 | 清晰度 | 准备本机环境 | P0 |
| ui/SetupGuide.kt:67 | 让开发环境，\n随灵感就位。 | 清晰度 | 在手机上开始工作 | P0 |
| ui/SetupGuide.kt:69 | 准备本地环境，开始创造。 | 重复/啰嗦、工程痕迹 | 删除副标题；保留本页主标题和安装步骤。 | P1 |
| ui/SetupGuide.kt:86 | stepIndicatorSize | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/SetupGuide.kt:95 | stepIndicatorColor | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/SetupGuide.kt:100 | stepLabelColor | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/SetupGuide.kt:106 | 第 ${position + 1} 步，${step.title} | 清晰度 | 第 {内容} 步，{名称} | P0 |
| ui/SetupGuide.kt:109 | 先完成前一步 | 保留 | 先完成前一步 | P0 |
| ui/SetupGuide.kt:109 | 已完成 | 保留 | 已完成 | P0 |
| ui/SetupGuide.kt:109 | 待完成 | 保留 | 待完成 | P0 |
| ui/SetupGuide.kt:126 | ${position + 1} | 清晰度 | {内容} | P0 |
| ui/SetupGuide.kt:152 | 安装 ${current.title} | 清晰度 | 安装 {名称} | P0 |
| ui/SetupGuide.kt:152 | 补齐基础工具 | 保留 | 补齐基础工具 | P0 |
| ui/SetupGuide.kt:152 | 运行环境已就绪 | 保留 | 运行环境已就绪 | P0 |
| ui/SetupGuide.kt:156 | ${index + 1} / ${steps.size} | 清晰度 | {内容} / {数量} | P0 |
| ui/SetupGuide.kt:166 | 进入 DSH | 术语不一致 | 进入对话 | P0 |
| ui/SetupGuide.kt:168 | 上一步 | 保留 | 上一步 | P0 |
| ui/SetupGuide.kt:174 | 下一步：${steps[index + 1].title} | 清晰度 | 下一步 | P0 |
| ui/SetupGuide.kt:174 | 查看下一步 | 保留 | 查看下一步 | P0 |

### [ui/SetupScreens.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/SetupScreens.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/SetupScreens.kt:39 | 返回工作台 | 保留 | 返回工作台 | P0 |
| ui/SetupScreens.kt:48 | ubuntu | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/SetupScreens.kt:48 | 提供 DSH 所需的 Linux 系统与基础目录。 | 术语不一致 | 为助手提供所需的系统组件。 | P0 |
| ui/SetupScreens.kt:49 | node | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/SetupScreens.kt:49 | 为 DSH 提供 JavaScript 运行环境。 | 术语不一致 | 为助手提供运行所需组件。 | P0 |
| ui/SetupScreens.kt:50 | tools | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/SetupScreens.kt:50 | 基础工具 | 保留 | 基础工具 | P0 |
| ui/SetupScreens.kt:50 | 检查基础工具，只安装缺失的组件。 | 保留 | 检查基础工具，只安装缺失的组件。 | P0 |
| ui/SetupScreens.kt:51 | dsh | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/SetupScreens.kt:51 | 安装完成后，即可配置模型并开始使用。 | 清晰度 | 安装后设置模型，即可开始对话。 | P0 |
| ui/SetupScreens.kt:62 | DSH 正在运行。停止服务后才能安装或更换组件，停止会中断当前任务。 | 术语不一致 | 请先停止助手，再安装或更换组件。停止会中断当前任务。 | P0 |
| ui/SetupScreens.kt:64 | 先完成 ${steps[prerequisite].title} | 清晰度 | 先完成 {名称} | P0 |
| ui/SetupScreens.kt:65 | 前一步就绪后才能安装${steps[step].title}。查看其他步骤不会中断当前安装。 | 清晰度 | 请先完成前一步安装，查看其他步骤不会中断安装。 | P0 |
| ui/SetupScreens.kt:67 | 返回${steps[prerequisite].title}步骤 | 清晰度 | 返回上一步 | P0 |
| ui/SetupScreens.kt:72 | 安装仍在继续 | 保留 | 安装仍在继续 | P0 |
| ui/SetupScreens.kt:76 | 查看正在安装的步骤 | 清晰度 | 查看安装进度 | P0 |
| ui/SetupScreens.kt:91 | 上次更新需要确认 | 保留 | 上次更新需要确认 | P0 |
| ui/SetupScreens.kt:92 | 检查当前版本或恢复匹配快照后，再继续安装和启动。 | 术语不一致 | 请检查当前版本，或恢复对应数据备份后继续。 | P0 |
| ui/SetupScreens.kt:93 | 检查并保留当前版本 | 清晰度 | 检查当前版本 | P0 |
| ui/SetupScreens.kt:131 | 当前正在使用安全模式 | 保留 | 当前正在使用安全模式 | P0 |
| ui/SetupScreens.kt:132 | 正在试用正常模式 | 清晰度 | 正在恢复正常模式 | P0 |
| ui/SetupScreens.kt:133 | 服务恢复 | 清晰度 | 恢复助手 | P0 |
| ui/SetupScreens.kt:140 | 上次发生在 ${recoveryTime(recovery.lastFailureAtMillis)} | 清晰度 | 上次发生在 {内容} | P0 |
| ui/SetupScreens.kt:141 |  · 已自动重试 ${recovery.restartAttempts} 次 | 清晰度 | · 已自动重试 {数量} 次 | P0 |
| ui/SetupScreens.kt:163 | 安全模式只加载最小配置，不加载你的插件和配置；项目、会话、程序和配置均保留。安全模式不会自动切回正常模式。 | 术语不一致、长度 | 安全模式不加载你的插件和设置，对话和项目保留。<br>请手动恢复正常模式。 | P0 |
| ui/SetupScreens.kt:164 | 恢复的数据尚未在正常模式加载；安全模式启动不会提交恢复检查。 | 清晰度 | 请在正常模式启动助手，完成数据恢复检查。 | P0 |
| ui/SetupScreens.kt:167 | 试用正常模式（进入 2 分钟观察） | 清晰度 | 恢复正常模式 | P0 |
| ui/SetupScreens.kt:174 | 试用后 2 分钟内再次失败会自动回到安全模式；已中断的任务不会自动重放。 | 清晰度 | 两分钟内再次失败时会回到安全模式。<br>中断的任务请确认状态后再继续。 | P0 |
| ui/SetupScreens.kt:177 | 上次正常模式试用未在运行。可退出试用回到安全模式，或手动启动继续试用；已中断任务不会自动重放。 | 长度 | 恢复正常模式已中断。可返回安全模式，或手动启动继续尝试。 | P0 |
| ui/SetupScreens.kt:177 | 正在加载正常模式的插件与配置。2 分钟内再次失败会自动回到安全模式；已中断任务不会自动重放。 | 长度 | 正在加载你的插件和设置。<br>两分钟内再次失败时会回到安全模式。 | P0 |
| ui/SetupScreens.kt:180 | 退出试用，回到安全模式 | 清晰度 | 返回安全模式 | P0 |
| ui/SetupScreens.kt:189 | 当前服务未运行，可能正在等待有限次自动重启或已停止自动重启；已中断任务不会自动重放。 | 长度 | 助手尚未运行，请查看当前状态后手动启动。 | P0 |
| ui/SetupScreens.kt:189 | 服务已按正常模式运行。自动重启不会重放已中断的任务；若反复失败，可清理下载缓存或切换已安装版本。 | 长度 | 助手已正常运行，请确认中断任务的状态后再继续。 | P0 |
| ui/SetupScreens.kt:199 | 清理下载缓存 | 保留 | 清理下载缓存 | P0 |
| ui/SetupScreens.kt:207 | 切换已安装版本 | 保留 | 切换已安装版本 | P0 |
| ui/SetupScreens.kt:228 | 较早时间 | 保留 | 较早时间 | P0 |
| ui/SetupScreens.kt:235 | 你的移动开发空间 | 清晰度 | 你的开发工作台 | P0 |
| ui/SetupScreens.kt:236 | 管理本机项目，在 DeepSeek Harness 中开始工作。 | 清晰度 | 管理手机上的项目，告诉助手你想完成的事。 | P0 |
| ui/SetupScreens.kt:254 | DeepSeek Harness | 清晰度 | 开发助手 | P0 |
| ui/SetupScreens.kt:255 | DSH ${s.dsh} | 术语不一致 | 助手版本 {版本} | P0 |
| ui/SetupScreens.kt:262 | 启动 DSH | 术语不一致 | 启动助手 | P0 |
| ui/SetupScreens.kt:262 | 打开 DSH | 术语不一致 | 打开对话 | P0 |
| ui/SetupScreens.kt:262 | 正在启动… | 保留 | 正在启动… | P0 |
| ui/SetupScreens.kt:275 | 运行环境已就绪 | 保留 | 运行环境已就绪 | P0 |
| ui/SetupScreens.kt:276 | Ubuntu ${model.components.ubuntuVersion} · Node.js ${model.components.nodeVersion} | 清晰度 | 运行环境已安装，版本见详情。 | P0 |
| ui/SetupScreens.kt:277 | 查看安装步骤与版本 | 保留 | 查看安装步骤与版本 | P0 |
| ui/SetupScreens.kt:283 | 文件与命令权限说明 | 清晰度 | 权限说明 | P0 |
| ui/SetupScreens.kt:298 | 设备状态 · ${state.condition.label} | 清晰度 | {设备状态}，后台活动已调整。详细参数可在设置中查看。 | P0 |
| ui/SetupScreens.kt:300 | ${state.statusText()} · 下载并发 ${state.workerCap} 路 · 日志 ${state.logLineLimit} 行 · 预览上限 ${state.previewMaxDimension} 像素 | 工程痕迹、长度 | 后台更新已延后，设备状态恢复后会继续。当前任务保持运行。 | P0 |
| ui/SetupScreens.kt:302 | 后台更新检查已延后，状态恢复后自动补跑；正在运行的 DSH 任务和用户下载不会被暂停或取消。 | 术语不一致、长度 | 可在设置的“通知与后台”中调整省电选项。 | P0 |
| ui/SetupScreens.kt:304 | DSH 任务和前台操作不受影响；可在“设置 · 运行与后台”中调整开关与低电阈值。 | 术语不一致 | 版本管理与网络工具 | P0 |
| ui/SetupScreens.kt:314 | Git、curl 与 HTTPS 证书 | 清晰度 | 脚本与开发工具 | P0 |
| ui/SetupScreens.kt:315 | Python、SSL 与虚拟环境 | 清晰度 | 远程连接工具 | P0 |
| ui/SetupScreens.kt:316 | SSH 客户端与 OpenSSL | 清晰度 | 代码搜索工具 | P0 |
| ui/SetupScreens.kt:317 | 代码搜索与 JSON | 清晰度 | 文件压缩工具 | P0 |
| ui/SetupScreens.kt:318 | xz、ZIP 解压与压缩 | 清晰度 | 文件查看与同步工具 | P0 |
| ui/SetupScreens.kt:319 | 文件识别、查看与同步 | 清晰度 | 项目构建工具 | P0 |
| ui/SetupScreens.kt:320 | 补丁与轻量构建工具 | 清晰度 | 工作区支持工具 | P0 |
| ui/SetupScreens.kt:321 | 工作区目录支持 | 清晰度 | 运行支持组件 | P0 |
| ui/SetupScreens.kt:322 | Node.js 运行库 | 保留 | Node.js 运行库 | P0 |
| ui/SetupScreens.kt:329 | 已通过 | 保留 | 已通过 | P0 |
| ui/SetupScreens.kt:329 | 待检查 | 保留 | 待检查 | P0 |
| ui/SetupScreens.kt:329 | 需要补齐 | 保留 | 需要补齐 | P0 |
| ui/SetupScreens.kt:335 | 只安装缺失项，项目与 DSH 配置保留；首次升级会下载新增工具，请留意下方进度。 | 术语不一致 | 将安装缺少的基础工具，保留项目和助手设置。 | P0 |
| ui/SetupScreens.kt:335 | 检查已通过。安装 Ubuntu 和 Node 时也会自动补齐基础工具。 | 清晰度 | 基础工具已准备好。 | P0 |
| ui/SetupScreens.kt:336 | 只安装 SSH 客户端，不启动 SSH 服务或其他后台进程，也不会自动扩大 DSH 命令权限。 | 术语不一致、长度 | 基础工具不会自动开放远程登录或扩大助手权限。 | P0 |
| ui/SetupScreens.kt:338 | ${s.phasePercent}% | 清晰度 | {进度}% | P0 |
| ui/SetupScreens.kt:344 | 检查并补齐 | 保留 | 检查并补齐 | P0 |
| ui/SetupScreens.kt:344 | 正在检查与补齐… | 保留 | 正在检查与补齐… | P0 |
| ui/SetupScreens.kt:344 | 重新检查 | 保留 | 重新检查 | P0 |
| ui/SetupScreens.kt:346 | 先停止 DSH，再检查和修复运行组件。 | 术语不一致 | 先停止 助手，再检查和修复运行组件。 | P0 |
| ui/SetupScreens.kt:353 | 文件与命令权限 | 保留 | 文件与命令权限 | P0 |
| ui/SetupScreens.kt:356 | 本机没有可用的命令沙箱。workspace-write 的文件工具可用，但 bash 会被阻止。 | 长度 | 当前权限下无法执行命令，文件工具仍可使用。 | P0 |
| ui/SetupScreens.kt:357 | 需要执行命令时，在官方 DSH 的文件策略中明确选择 danger-full-access。该模式允许命令访问 App 权限范围内的其他文件与网络，仍是普通 Android App 权限。 | 术语不一致、长度 | 如需执行命令，请在助手权限设置中查看可用选项。<br>完全权限可能访问工作区外的文件，请先了解风险再选择。 | P0 |
| ui/SetupScreens.kt:359 | 本机沙箱只有部分能力，请按 DSH 权限设置中的能力提示选择文件与命令策略。 | 术语不一致 | 本机命令能力有限，请在助手权限设置中查看可用选项。 | P0 |
| ui/SetupScreens.kt:360 | 本机命令沙箱已检测可用，工具执行继续遵循 DSH 中的权限设置。 | 术语不一致 | 本机可以执行命令，仍遵循你选择的权限设置。 | P0 |
| ui/SetupScreens.kt:361 | 沙箱能力尚未确认。若 DSH 提示 workspace-write 无法执行 bash，请先检查其文件策略与可用后端。 | 术语不一致、长度 | 命令能力尚未确认，请重新检查运行环境。 | P0 |

### [ui/SourceSpeedControls.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/SourceSpeedControls.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/SourceSpeedControls.kt:68 | 正在自动测速 · ${snapshot.results.values.count { it.status != SourceProbeStatus.TESTING }} / ${sources.size} | 长度 | 正在检测下载速度：{已完成}/{总数} | P1 |
| ui/SourceSpeedControls.kt:69 | 上次测速已过期，安装前会重新测速 | 清晰度 | 上次测速已过期，安装前会重新检测。 | P0 |
| ui/SourceSpeedControls.kt:70 | 已自动选择：${resolved.title} | 清晰度 | 已自动选择：{名称} | P1 |
| ui/SourceSpeedControls.kt:71 | 测速暂不可用，下载时逐个尝试 | 清晰度 | 正在按顺序尝试下载源。 | P1 |
| ui/SourceSpeedControls.kt:72 | 自动测速并选择最快可用源 | 清晰度 | 自动选择较快的下载源 | P1 |
| ui/SourceSpeedControls.kt:73 | 手动优先：${resolved?.title ?: selectedSource} | 清晰度 | 手动优先：{名称} | P1 |
| ui/SourceSpeedControls.kt:76 | $componentTitle 下载源测速进行中 | 清晰度 | {名称} 下载源测速进行中 | P1 |
| ui/SourceSpeedControls.kt:79 | ${if(fresh)"本次采样" else "上次采样"} ${mbps(sample.bytesPerSecond)} MB/s · 连接 ${sample.latencyMs} ms | 长度 | {内容} {大小} MB/s · 连接 {内容} ms | P2 |
| ui/SourceSpeedControls.kt:79 | 上次采样 | 清晰度 | 上次测速 | P2 |
| ui/SourceSpeedControls.kt:79 | 本次采样 | 清晰度 | 本次测速 | P2 |
| ui/SourceSpeedControls.kt:86 | 查看测速与下载源 | 清晰度 | 查看下载源 | P1 |
| ui/SourceSpeedControls.kt:97 | $componentTitle · 下载源测速 | 清晰度 | 下载源测速 | P1 |
| ui/SourceSpeedControls.kt:100 | 以下为上次采样结果，已超过 10 分钟。安装时会重新测速。 | 清晰度 | 上次测速超过 10 分钟，安装时会重新检测。 | P0 |
| ui/SourceSpeedControls.kt:103 | 恢复自动择优 | 清晰度 | 自动选择 | P1 |
| ui/SourceSpeedControls.kt:107 | 正在测速 · ${completed.size} / ${sources.size} 个源 | 清晰度 | 正在测速 · {数量} / {数量} 个源 | P1 |
| ui/SourceSpeedControls.kt:130 | 重新测速 | 保留 | 重新测速 | P1 |
| ui/SourceSpeedControls.kt:131 | 关闭 | 保留 | 关闭 | P1 |
| ui/SourceSpeedControls.kt:134 | 每个源最多采样 256 KiB，比较首字节延迟和传输耗时。结果随当前网络变化。 | 清晰度 | 每个下载源最多测试 256 KiB，速度随网络变化。 | P2 |
| ui/SourceSpeedControls.kt:137 | 优先使用你选择的源，下载失败后尝试其他可用源。 | 清晰度 | 优先使用所选下载源，失败后会尝试其他下载源。 | P0 |
| ui/SourceSpeedControls.kt:137 | 自动模式会使用本次最快可用源。点击“使用”可改为手动优先。 | 清晰度 | 自动选择本次较快的下载源，也可点“使用”手动选择。 | P1 |
| ui/SourceSpeedControls.kt:144 | 开始测速 | 保留 | 开始测速 | P1 |
| ui/SourceSpeedControls.kt:149 | 取消 | 保留 | 取消 | P0 |
| ui/SourceSpeedControls.kt:151 | 关闭 | 保留 | 关闭 | P1 |
| ui/SourceSpeedControls.kt:179 | 当前下载源 | 保留 | 当前下载源 | P1 |
| ui/SourceSpeedControls.kt:179 | 当前下载源 · 本次最快 | 保留 | 当前下载源 · 本次最快 | P1 |
| ui/SourceSpeedControls.kt:179 | 本次最快 | 保留 | 本次最快 | P1 |
| ui/SourceSpeedControls.kt:184 | 使用 | 保留 | 使用 | P1 |
| ui/SourceSpeedControls.kt:184 | 使用${option.title} | 清晰度 | 使用此下载源 | P1 |
| ui/SourceSpeedControls.kt:184 | 选定 | 保留 | 选定 | P1 |
| ui/SourceSpeedControls.kt:194 | 连接 ${result.latencyMs} ms · ${mbps(result.bytesPerSecond)} MB/s | 清晰度 | 连接 {内容} ms · {大小} MB/s | P2 |
| ui/SourceSpeedControls.kt:198 | 连接失败 | 保留 | 连接失败 | P0 |
| ui/SourceSpeedControls.kt:201 | 测试中… | 保留 | 测试中… | P1 |
| ui/SourceSpeedControls.kt:202 | 未测试 | 保留 | 未测试 | P1 |
| ui/SourceSpeedControls.kt:202 | 等待测试 | 保留 | 等待测试 | P1 |

### [ui/TanukiAccessibility.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiAccessibility.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiAccessibility.kt:37 | 操作 | 保留 | 操作 | P1 |
| ui/TanukiAccessibility.kt:125 | 操作 | 保留 | 操作 | P1 |

### [ui/TanukiAdaptiveComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveComponents.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiAdaptiveComponents.kt:103 | 列表滚动下标不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiAdaptiveComponents.kt:104 | 列表滚动偏移不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiAdaptiveComponents.kt:122 | 列表状态 key 不能为空白 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiAdaptiveComponents.kt:127 | 列表状态 key 不能为空白 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/TanukiLayoutPolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiLayoutPolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiLayoutPolicy.kt:19 | 视口宽度必须为正 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiLayoutPolicy.kt:20 | 视口高度必须为正 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiLayoutPolicy.kt:21 | 字体缩放必须为正 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/TanukiListKeys.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiListKeys.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiListKeys.kt:25 | 列表 key 不能为空白 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiListKeys.kt:26 | 列表 key 必须唯一 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiListKeys.kt:32 | 列表 id 不能为空 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiListKeys.kt:33 | 列表 id 超出上限 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiListKeys.kt:38 | 官方事件序号不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiListKeys.kt:43 | 待发送消息序号必须为负数 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/TanukiMotionFeedback.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiMotionFeedback.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiMotionFeedback.kt:55 | 图标状态 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiMotionFeedback.kt:71 | 区域状态 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiMotionFeedback.kt:118 | 已复制 | 保留 | 已复制 | P1 |
| ui/TanukiMotionFeedback.kt:130 | 已复制 | 保留 | 已复制 | P1 |
| ui/TanukiMotionFeedback.kt:132 | 已复制 | 保留 | 已复制 | P1 |
| ui/TanukiMotionFeedback.kt:133 | 复制确认 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiMotionFeedback.kt:134 | 已复制 | 保留 | 已复制 | P1 |

### [ui/TanukiNavigation.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiNavigation.kt:106 | 列表滚动下标不能为负 | 保留 | 列表滚动下标不能为负 | P1 |
| ui/TanukiNavigation.kt:107 | 列表滚动偏移不能为负 | 保留 | 列表滚动偏移不能为负 | P1 |
| ui/TanukiNavigation.kt:143 | 初始页不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiNavigation.kt:144 | 返回历史至少保留一项 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiNavigation.kt:156 | 目标页不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiNavigation.kt:169 | 兜底页不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiNavigation.kt:175 | 重置页不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiNavigation.kt:197 | 列表滚动下标不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiNavigation.kt:198 | 列表滚动偏移不能为负 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiNavigation.kt:199 | 草稿长度超出上限 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/TanukiPageVisuals.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiPageVisuals.kt:59 | 传输中 | 保留 | 传输中 | P0 |
| ui/TanukiPageVisuals.kt:69 | 暂无匹配文件 | 清晰度 | 没有匹配文件 | P0 |
| ui/TanukiPageVisuals.kt:70 | 试试更短的文件名，或清除搜索。 | 保留 | 试试更短的文件名，或清除搜索。 | P0 |
| ui/TanukiPageVisuals.kt:71 | 清除搜索 | 保留 | 清除搜索 | P0 |
| ui/TanukiPageVisuals.kt:74 | 暂无可见文件 | 清晰度 | 文件已隐藏 | P0 |
| ui/TanukiPageVisuals.kt:75 | 当前只有隐藏文件；在“排序与显示”中显示隐藏文件。 | 清晰度 | 这里只剩隐藏文件，可点“显示隐藏”查看。 | P0 |
| ui/TanukiPageVisuals.kt:76 | 显示隐藏文件 | 清晰度 | 显示隐藏 | P0 |
| ui/TanukiPageVisuals.kt:79 | 暂无文件 | 清晰度 | 还没有文件 | P0 |
| ui/TanukiPageVisuals.kt:80 | 从手机导入文件，或新建文件夹开始整理。 | 清晰度 | 从手机导入文件，或新建文件夹。 | P0 |
| ui/TanukiPageVisuals.kt:81 | 从手机导入 | 保留 | 从手机导入 | P0 |
| ui/TanukiPageVisuals.kt:84 | 暂无文件 | 清晰度 | 还没有文件 | P0 |
| ui/TanukiPageVisuals.kt:85 | 此位置暂无内容，可以刷新后重试。 | 清晰度 | 此位置还没有文件，可刷新查看。 | P0 |
| ui/TanukiPageVisuals.kt:86 | 刷新 | 保留 | 刷新 | P0 |
| ui/TanukiPageVisuals.kt:97 | 清除搜索 | 保留 | 清除搜索 | P0 |
| ui/TanukiPageVisuals.kt:98 | 显示隐藏文件 | 清晰度 | 显示隐藏 | P0 |
| ui/TanukiPageVisuals.kt:100 | 从手机导入 | 保留 | 从手机导入 | P0 |
| ui/TanukiPageVisuals.kt:102 | 新建文件夹 | 保留 | 新建文件夹 | P0 |
| ui/TanukiPageVisuals.kt:105 | 刷新 | 保留 | 刷新 | P0 |
| ui/TanukiPageVisuals.kt:122 | 权限：$label，点击管理 | 清晰度 | 权限：{名称}，点击管理 | P0 |
| ui/TanukiPageVisuals.kt:130 | 无法读取大小 | 保留 | 无法读取大小 | P0 |

### [ui/TanukiSaveableState.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiSaveableState.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiSaveableState.kt:78 | 自动 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/TanukiStateViews.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiStateViews.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiStateViews.kt:46 | 内容 | 保留 | 内容 | P0 |
| ui/TanukiStateViews.kt:50 | 内容 | 保留 | 内容 | P0 |
| ui/TanukiStateViews.kt:55 | 正在加载$cleanSubject | 清晰度 | 正在加载{名称} | P0 |
| ui/TanukiStateViews.kt:56 | 请稍候，加载完成后会自动显示。 | 清晰度 | 正在读取，请稍候。 | P0 |
| ui/TanukiStateViews.kt:60 | 暂无$cleanSubject | 清晰度 | 暂无{内容名称} | P0 |
| ui/TanukiStateViews.kt:61 | 当前没有可显示的$cleanSubject；可以刷新或稍后再试。 | 清晰度 | 还没有{内容名称}，可刷新后再查看。 | P0 |
| ui/TanukiStateViews.kt:62 | 刷新 | 保留 | 刷新 | P0 |
| ui/TanukiStateViews.kt:65 | ${cleanSubject}加载失败 | 清晰度 | {名称}加载失败 | P0 |
| ui/TanukiStateViews.kt:66 | 请检查网络或 DSH 服务是否运行，然后重试。 | 术语不一致 | 请检查网络，并确认助手已启动后重试。 | P0 |
| ui/TanukiStateViews.kt:67 | 重试 | 保留 | 重试 | P0 |
| ui/TanukiStateViews.kt:91 | 内容 | 保留 | 内容 | P0 |
| ui/TanukiStateViews.kt:153 | 重试 | 保留 | 重试 | P0 |
| ui/TanukiStateViews.kt:156 | 重试 | 保留 | 重试 | P0 |
| ui/TanukiStateViews.kt:170 | 重试 | 保留 | 重试 | P0 |
| ui/TanukiStateViews.kt:173 | 重试 | 保留 | 重试 | P0 |

### [ui/TanukiUi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/TanukiUi.kt:214 | DSH | 术语不一致 | 对话 | P1 |
| ui/TanukiUi.kt:214 | 工作台 | 保留 | 工作台 | P1 |
| ui/TanukiUi.kt:215 | 文件 | 保留 | 文件 | P1 |
| ui/TanukiUi.kt:215 | 浏览器 | 保留 | 浏览器 | P1 |
| ui/TanukiUi.kt:215 | 设置 | 保留 | 设置 | P1 |
| ui/TanukiUi.kt:287 | 远程主机 | 术语不一致 | 远程电脑 | P1 |
| ui/TanukiUi.kt:295 | 存储与空间 | 保留 | 存储与空间 | P1 |
| ui/TanukiUi.kt:316 | 组件维护与诊断 | 清晰度 | 环境与维护 | P1 |
| ui/TanukiUi.kt:347 | 组件维护与诊断 | 清晰度 | 环境与维护 | P1 |
| ui/TanukiUi.kt:384 | 设置 | 保留 | 设置 | P1 |
| ui/TanukiUi.kt:392 | 浏览器有待处理请求 | 保留 | 浏览器有待处理请求 | P1 |
| ui/TanukiUi.kt:393 | 查看请求 | 保留 | 查看请求 | P1 |
| ui/TanukiUi.kt:412 | 主导航栏 | 保留 | 主导航栏 | P1 |
| ui/TanukiUi.kt:457 | 远程主机 | 术语不一致 | 远程电脑 | P1 |
| ui/TanukiUi.kt:512 | 返回上一页 | 保留 | 返回上一页 | P1 |
| ui/TanukiUi.kt:523 | 设置 | 保留 | 设置 | P1 |
| ui/TanukiUi.kt:569 | 升级 WebView，打开 DSH | 工程痕迹、术语不一致 | 请更新网页组件 | P1 |
| ui/TanukiUi.kt:570 | WebView 检测未完成 | 工程痕迹 | 网页组件尚未检测 | P1 |
| ui/TanukiUi.kt:571 | 正在检查网页支持 | 清晰度 | 正在检查网页显示 | P1 |
| ui/TanukiUi.kt:572 | 先检测 WebView 能力 | 工程痕迹 | 检测网页组件 | P1 |
| ui/TanukiUi.kt:575 | Chromium ${support.chromium ?: support.version} → 123+ | 清晰度 | 网页组件需要更新，版本见详情。 | P1 |
| ui/TanukiUi.kt:576 | 实测缺失：${support.missing.joinToString("、")} | 清晰度 | 部分网页显示功能缺失，详情中可查看。 | P1 |
| ui/TanukiUi.kt:577 | 先保存任务并停止 DSH；更新内核可能关闭其他使用它的应用。 | 术语不一致 | 更新前请保存草稿并停止助手。<br>更新可能关闭其他正在使用网页组件的应用。 | P0 |
| ui/TanukiUi.kt:578 | 官方更新入口 | 清晰度 | 更新网页组件 | P0 |
| ui/TanukiUi.kt:579 | 停止服务并等待当前操作完成后可更新。 | 保留 | 停止服务并等待当前操作完成后可更新。 | P0 |
| ui/TanukiUi.kt:581 | 请重新检测渲染能力。 | 工程痕迹 | 请重新检测显示能力。 | P1 |
| ui/TanukiUi.kt:581 | 需要 Chromium 123+，并实测支持 subgrid 与 light-dark()。 | 长度 | 当前网页组件不满足要求，请更新后重新检测。 | P1 |
| ui/TanukiUi.kt:582 | 检测 WebView | 工程痕迹 | 检测网页组件 | P1 |
| ui/TanukiUi.kt:594 | 先测速再使用最快可用源，下载失败后自动换源 | 保留 | 先测速再使用最快可用源，下载失败后自动换源 | P0 |
| ui/TanukiUi.kt:594 | 自动择优 | 清晰度 | 自动选择 | P1 |
| ui/TanukiUi.kt:619 | 运行组件 | 保留 | 运行组件 | P1 |
| ui/TanukiUi.kt:627 | Ubuntu Base | 清晰度 | 系统组件 | P1 |
| ui/TanukiUi.kt:627 | 未安装 | 保留 | 未安装 | P0 |
| ui/TanukiUi.kt:630 | 官方 ARM64 精简基础镜像，不含桌面；Node、DSH、项目和配置独立保留 | 术语不一致 | 为助手提供系统环境，项目和对话独立保存。 | P1 |
| ui/TanukiUi.kt:632 | ARM64 · ${it.size/(1024*1024)} MB · 精简基础镜像 | 清晰度 | 系统组件，下载大小 {大小} | P1 |
| ui/TanukiUi.kt:651 | Node.js | 清晰度 | 运行组件 | P1 |
| ui/TanukiUi.kt:651 | 未安装 | 保留 | 未安装 | P0 |
| ui/TanukiUi.kt:654 | 请先安装 Ubuntu 才能安装 Node；已安装 Node 可独立卸载 | 清晰度 | 请先安装系统组件，再安装运行组件。 | P0 |
| ui/TanukiUi.kt:656 |  · 非 LTS | 清晰度 | 非长期支持版 | P1 |
| ui/TanukiUi.kt:656 | v${it.version}${if(it.lts) " · LTS" else " · 非 LTS"} | 清晰度 | 版本 {版本}，{支持类型} | P1 |
| ui/TanukiUi.kt:658 | 安装前验证 Node.js、pnpm 和已安装 DSH 的兼容性。 | 工程痕迹、术语不一致 | 安装前会检查运行组件与助手能否配合使用。 | P0 |
| ui/TanukiUi.kt:658 | 当前 DSH 要求 Node.js ${model.nodeRequirement}；不兼容的版本将保留当前安装。 | 术语不一致 | 所选版本不兼容时，会保留当前组件。版本要求见详情。 | P0 |
| ui/TanukiUi.kt:676 | 官方开发引擎 | 术语不一致 | 开发助手 | P1 |
| ui/TanukiUi.kt:692 | 解析、校验与安装无法准确估算总量；上方显示耗时和当前进展。 | 清晰度 | 正在准备安装，可查看当前阶段和耗时。 | P0 |
| ui/TanukiUi.kt:695 | DSH 依赖 | 术语不一致 | 助手安装文件 | P1 |
| ui/TanukiUi.kt:700 | 依赖归档自动选源，失败时换源；版本与校验信息仍来自 npm 官方。 | 工程痕迹 | 自动选择下载源，并检查文件是否完整。 | P0 |
| ui/TanukiUi.kt:704 | 获取官方版本 | 清晰度 | 读取版本 | P1 |
| ui/TanukiUi.kt:704 | 选择版本安装 | 清晰度 | 选择版本 | P0 |
| ui/TanukiUi.kt:706 | 刷新版本列表 | 清晰度 | 刷新版本 | P1 |
| ui/TanukiUi.kt:711 | 已安装版本（${comps.installedDshVersions.size}） | 工程痕迹 | 已安装版本（{版本}） | P0 |
| ui/TanukiUi.kt:717 | 重新安装 | 保留 | 重新安装 | P0 |
| ui/TanukiUi.kt:719 | 管理版本 | 保留 | 管理版本 | P1 |
| ui/TanukiUi.kt:721 | 重新安装前请先获取官方版本列表。 | 保留 | 重新安装前请先获取官方版本列表。 | P0 |
| ui/TanukiUi.kt:723 | 更新与恢复 | 保留 | 更新与恢复 | P0 |
| ui/TanukiUi.kt:724 | 更新状态 | 保留 | 更新状态 | P0 |
| ui/TanukiUi.kt:725 | 官方候选 | 清晰度 | 可用版本 | P1 |
| ui/TanukiUi.kt:726 | 选择官方发布后确认安装；切换数据版本前会执行检查并保留匹配快照。 | 术语不一致 | 选择版本后安装，切换前会检查并备份数据。 | P0 |
| ui/TanukiUi.kt:728 | 不自动回滚项目 | 保留 | 不自动回滚项目 | P1 |
| ui/TanukiUi.kt:728 | 检查并保留当前版本 | 清晰度 | 检查当前版本 | P1 |
| ui/TanukiUi.kt:730 | 恢复匹配快照 | 术语不一致 | 恢复数据备份 | P1 |
| ui/TanukiUi.kt:730 | 恢复程序、配置和会话；项目不回滚 | 术语不一致 | 恢复对话和设置，项目文件保持不变。 | P1 |
| ui/TanukiUi.kt:738 | 选择 DSH 版本 | 术语不一致 | 选择助手版本 | P1 |
| ui/TanukiUi.kt:749 | 当前 | 保留 | 当前 | P1 |
| ui/TanukiUi.kt:750 | 已安装 | 保留 | 已安装 | P0 |
| ui/TanukiUi.kt:752 | 尚未通过本机启动检查 | 清晰度 | 此版本尚未完成启动检查。 | P1 |
| ui/TanukiUi.kt:752 | 已通过本机启动检查（历史记录） | 清晰度 | 此设备曾成功启动该版本。 | P1 |
| ui/TanukiUi.kt:758 | 所选版本详情 · ${release.version} | 清晰度 | 所选版本详情 · {版本} | P1 |
| ui/TanukiUi.kt:759 | Node.js 要求：${release.node} | 清晰度 | 所需运行组件：{版本要求} | P1 |
| ui/TanukiUi.kt:761 | 来源：${release.source}\n完整性：${release.integrity} | 清晰度 | 安装来源和校验信息在详情中查看。 | P1 |
| ui/TanukiUi.kt:762 | 安装备用只保存程序，不切换当前数据。较旧版本需先恢复匹配的数据快照才能使用。 | 术语不一致 | 备用版本不会替换当前版本。<br>使用旧版前需恢复对应的数据备份。 | P0 |
| ui/TanukiUi.kt:763 | 验证记录不代表所有模型与数据迁移均兼容。 | 被动/实现说明 | 使用其他模型或旧数据前，请先检查是否可正常工作。 | P1 |
| ui/TanukiUi.kt:769 | 安装为备用程序 | 清晰度 | 安装为备用 | P0 |
| ui/TanukiUi.kt:772 | 卸载该备用版本 | 清晰度 | 卸载备用版 | P1 |
| ui/TanukiUi.kt:775 | 当前使用版本不能卸载；如需清理请先在“已安装版本”中切换到其他版本。 | 清晰度 | 当前版本无法卸载，请先切换到其他版本。 | P0 |
| ui/TanukiUi.kt:783 | 安装并使用 | 保留 | 安装并使用 | P0 |
| ui/TanukiUi.kt:786 | 取消 | 保留 | 取消 | P0 |
| ui/TanukiUi.kt:796 | 来源与完整性 | 保留 | 来源与完整性 | P2 |
| ui/TanukiUi.kt:799 | Ubuntu、Node.js 与 DSH 分层安装；Ubuntu 镜像和 Node.js 版本按官方 SHA-256 校验，所有下载源必须提供相同文件。 | 工程痕迹、术语不一致、长度 | 下载文件都会检查完整性，版本和来源可在详情中查看。 | P2 |
| ui/TanukiUi.kt:801 | Ubuntu 附加工具由签名 apt 软件源补齐；PRoot 不提供恶意代码隔离。 | 工程痕迹、被动/实现说明、长度 | 运行环境不会隔离恶意程序，请只运行可信的代码。 | P2 |
| ui/TanukiUi.kt:802 | 验证 Ubuntu 基础启动 | 清晰度 | 检查系统启动 | P2 |
| ui/TanukiUi.kt:805 | 运行诊断 | 保留 | 运行诊断 | P2 |
| ui/TanukiUi.kt:806 | DSH 进程 | 术语不一致 | 助手状态 | P2 |
| ui/TanukiUi.kt:807 | 就绪 | 保留 | 就绪 | P2 |
| ui/TanukiUi.kt:807 | 未就绪 | 保留 | 未就绪 | P2 |
| ui/TanukiUi.kt:807 | 本机 Web 健康 | 清晰度 | 本机连接 | P2 |
| ui/TanukiUi.kt:808 | 网页认证 | 清晰度 | 网页登录 | P1 |
| ui/TanukiUi.kt:809 | 健康检查 | 清晰度 | 运行检查 | P1 |
| ui/TanukiUi.kt:810 | DSH 沙箱探测 | 术语不一致 | 命令能力 | P1 |
| ui/TanukiUi.kt:811 | 启动模式 | 保留 | 启动模式 | P1 |
| ui/TanukiUi.kt:813 | 最近失败 | 保留 | 最近失败 | P0 |
| ui/TanukiUi.kt:815 | 模型上下文会发送至你选择的 API 服务商。工具执行在 DSH 中逐次确认；停止后的任务不会自动重放。 | 术语不一致、长度 | 消息中的相关内容会发送给你选择的模型服务商。<br>工具按当前权限设置执行，部分操作需要你确认。 | P2 |
| ui/TanukiUi.kt:816 | 可能中断当前任务 | 保留 | 可能中断当前任务 | P2 |
| ui/TanukiUi.kt:816 | 重启 DSH | 术语不一致 | 重启助手 | P2 |
| ui/TanukiUi.kt:817 | 固定健康检查 | 清晰度 | 检查运行环境 | P2 |
| ui/TanukiUi.kt:818 | 一分钟后台自检 | 清晰度 | 检查后台运行 | P2 |
| ui/TanukiUi.kt:818 | 专用目录计数 60 次，不调用模型 API | 清晰度 | 持续约一分钟，不使用模型服务。 | P2 |
| ui/TanukiUi.kt:820 | 暂无完整的后台自检结果 | 保留 | 暂无完整的后台自检结果 | P2 |
| ui/TanukiUi.kt:821 | 设备信息 | 清晰度 | 还没有后台检查结果，可先运行检查。 | P2 |
| ui/TanukiUi.kt:822 | 暂无事件 | 保留 | 暂无事件 | P2 |
| ui/TanukiUi.kt:822 | 最近事件 | 保留 | 最近事件 | P2 |
| ui/TanukiUi.kt:823 | 脱敏日志 | 保留 | 脱敏日志 | P2 |
| ui/TanukiUi.kt:826 | 暂无日志 | 清晰度 | 还没有运行日志，启动助手后可查看。 | P0 |
| ui/TanukiUi.kt:841 | 外观与语言 | 保留 | 外观与语言 | P1 |
| ui/TanukiUi.kt:841 | 跟随系统主题、简体中文与显示能力 | 清晰度 | 主题、语言与字体大小 | P1 |
| ui/TanukiUi.kt:842 | 会话提醒、更新通知、电池与自启动 | 术语不一致 | 对话提醒、更新通知和后台运行 | P0 |
| ui/TanukiUi.kt:842 | 通知与后台 | 保留 | 通知与后台 | P1 |
| ui/TanukiUi.kt:843 | 存储与空间 | 保留 | 存储与空间 | P1 |
| ui/TanukiUi.kt:843 | 工作区容量、目录占用、项目导入导出与备份 | 清晰度 | 工作区用量、文件导入导出和数据备份 | P1 |
| ui/TanukiUi.kt:844 | Tanuki 更新、Ubuntu/Node/DSH、WebView 与兼容性 | 工程痕迹、术语不一致 | 应用更新、运行组件和网页显示 | P0 |
| ui/TanukiUi.kt:844 | 引擎与更新 | 术语不一致 | 环境与更新 | P0 |
| ui/TanukiUi.kt:845 | 远程主机 | 术语不一致 | 远程电脑 | P1 |
| ui/TanukiUi.kt:845 | 通过 SSH 隧道连接远程 DSH（直连切片） | 术语不一致 | 连接电脑上的助手 | P1 |
| ui/TanukiUi.kt:846 | 关于与诊断 | 保留 | 关于与诊断 | P1 |
| ui/TanukiUi.kt:846 | 运行诊断、高级选项、许可、缓存与组件来源 | 保留 | 运行诊断、高级选项、许可、缓存与组件来源 | P1 |
| ui/TanukiUi.kt:848 | 工作空间偏好 | 术语不一致 | 设置 | P1 |
| ui/TanukiUi.kt:849 | 按使用场景分组；尚未接入的能力会明确标注“暂不支持”，不放置无效开关。 | 重复/啰嗦、工程痕迹 | 删除此处实现说明；保留设置分类入口。 | P1 |
| ui/TanukiUi.kt:872 | 外观与语言 | 保留 | 外观与语言 | P1 |
| ui/TanukiUi.kt:876 | 通知与后台 | 保留 | 通知与后台 | P1 |
| ui/TanukiUi.kt:880 | 存储与空间 | 保留 | 存储与空间 | P1 |
| ui/TanukiUi.kt:885 | 其他目录占用 | 保留 | 其他目录占用 | P1 |
| ui/TanukiUi.kt:887 | 按实际磁盘分配量统计各目录 | 清晰度 | 查看各文件夹实际占用的空间。 | P1 |
| ui/TanukiUi.kt:887 | 统计分配空间 | 清晰度 | 统计存储占用 | P1 |
| ui/TanukiUi.kt:888 | 保留工具、项目与版权文件 | 保留 | 保留工具、项目与版权文件 | P1 |
| ui/TanukiUi.kt:888 | 整理 Ubuntu 安装缓存 | 清晰度 | 整理安装缓存 | P1 |
| ui/TanukiUi.kt:891 | 项目与备份 | 保留 | 项目与备份 | P1 |
| ui/TanukiUi.kt:892 | 导入、导出与私有快照 | 术语不一致 | 文件与数据备份 | P1 |
| ui/TanukiUi.kt:892 | 项目与备份 | 保留 | 项目与备份 | P1 |
| ui/TanukiUi.kt:892 | 项目文件、会话与配置的备份边界 | 术语不一致 | 导出项目文件，或备份对话和设置。 | P1 |
| ui/TanukiUi.kt:893 | 导入/导出与快照不在本页直接执行，进入子页后可查看真实能力与限制。 | 重复/啰嗦、工程痕迹 | 删除此处重复导航说明；保留项目与备份入口。 | P1 |
| ui/TanukiUi.kt:897 | 引擎与更新 | 术语不一致 | 环境与更新 | P0 |
| ui/TanukiUi.kt:898 | 更新与组件 | 保留 | 更新与组件 | P0 |
| ui/TanukiUi.kt:899 | Tanuki ${model.versionName} · 检查、下载与覆盖安装 | 清晰度 | Tanuki {版本} · 检查、下载与覆盖安装 | P1 |
| ui/TanukiUi.kt:899 | 应用更新 | 保留 | 应用更新 | P0 |
| ui/TanukiUi.kt:899 | 应用更新 | 保留 | 应用更新 | P0 |
| ui/TanukiUi.kt:901 | Ubuntu、Node.js、DSH 的版本、维护与运行诊断 | 术语不一致 | 管理助手及运行组件的版本。 | P1 |
| ui/TanukiUi.kt:901 | 组件维护与诊断 | 清晰度 | 环境与维护 | P1 |
| ui/TanukiUi.kt:901 | 组件维护与诊断 | 清晰度 | 环境与维护 | P1 |
| ui/TanukiUi.kt:903 | WebView 与兼容性 | 工程痕迹 | 网页与兼容性 | P1 |
| ui/TanukiUi.kt:903 | 官方/原生 DSH 界面、WebView 内核与能力检测 | 工程痕迹、术语不一致 | 选择手机或网页界面，检查网页显示问题。 | P1 |
| ui/TanukiUi.kt:903 | 网页与兼容性 | 保留 | 网页与兼容性 | P1 |
| ui/TanukiUi.kt:905 | 引擎与更新域只展示已接入的操作；具体下载、校验与失败原因在对应子页。 | 重复/啰嗦、工程痕迹 | 删除此处实现说明；保留子页入口。 | P1 |
| ui/TanukiUi.kt:908 | 关于与诊断 | 保留 | 关于与诊断 | P1 |
| ui/TanukiUi.kt:909 | 诊断与高级 | 保留 | 诊断与高级 | P1 |
| ui/TanukiUi.kt:910 | 服务、沙箱、健康检查与组件版本 | 清晰度 | 查看运行状态、组件版本和问题详情。 | P1 |
| ui/TanukiUi.kt:910 | 组件维护与诊断 | 清晰度 | 环境与维护 | P1 |
| ui/TanukiUi.kt:910 | 运行诊断与组件状态 | 清晰度 | 环境与维护 | P1 |
| ui/TanukiUi.kt:912 | 旧 WebView 兼容、Blob 导出与许可来源 | 工程痕迹 | 网页兼容设置、网页导出和许可信息 | P1 |
| ui/TanukiUi.kt:912 | 高级与关于 | 保留 | 高级与关于 | P1 |
| ui/TanukiUi.kt:912 | 高级选项与兼容层 | 清晰度 | 高级选项 | P1 |
| ui/TanukiUi.kt:914 | 首次启动引导 | 保留 | 首次启动引导 | P1 |
| ui/TanukiUi.kt:915 | 欢迎、环境检查、界面选择和开始入口 | 清晰度 | 重新查看使用步骤，已完成的设置会保留。 | P1 |
| ui/TanukiUi.kt:915 | 重新查看引导 | 保留 | 重新查看引导 | P1 |
| ui/TanukiUi.kt:916 | 已完成的步骤会保留；从引导里跳过后，首页会再次出现“完成设置”提示条。 | 清晰度 | 跳过后可从工作台继续设置。 | P1 |
| ui/TanukiUi.kt:918 | 关于 | 保留 | 关于 | P1 |
| ui/TanukiUi.kt:919 | 应用版本 | 保留 | 应用版本 | P1 |
| ui/TanukiUi.kt:921 | 架构与许可 | 保留 | 架构与许可 | P1 |
| ui/TanukiUi.kt:923 | 保留运行环境和项目 | 保留 | 保留运行环境和项目 | P1 |
| ui/TanukiUi.kt:923 | 清除下载缓存 | 保留 | 清除下载缓存 | P1 |
| ui/TanukiUi.kt:925 | 导出/备份、远程与设备状态分别在外观、存储、远程和通知域中展示。 | 重复/啰嗦、工程痕迹 | 删除此处重复导航说明；保留对应分类入口。 | P1 |
| ui/TanukiUi.kt:928 | 应用更新 | 保留 | 应用更新 | P0 |
| ui/TanukiUi.kt:937 | 组件维护与诊断 | 清晰度 | 环境与维护 | P1 |
| ui/TanukiUi.kt:938 | 项目与备份 | 保留 | 项目与备份 | P1 |
| ui/TanukiUi.kt:938 | 项目与备份 | 保留 | 项目与备份 | P1 |
| ui/TanukiUi.kt:939 | 保存为新项目 | 保留 | 保存为新项目 | P1 |
| ui/TanukiUi.kt:939 | 导入文件 | 保留 | 导入文件 | P1 |
| ui/TanukiUi.kt:941 | 不含 DSH 会话与密钥；默认排除常见凭据文件 | 术语不一致 | 导出项目文件，默认跳过常见密钥文件。<br>请先检查文件是否含有私人信息。 | P1 |
| ui/TanukiUi.kt:941 | 导出项目 ZIP | 清晰度 | 导出项目 | P1 |
| ui/TanukiUi.kt:943 | 私有快照 | 术语不一致 | 应用数据备份 | P1 |
| ui/TanukiUi.kt:944 | 创建私有快照 | 术语不一致 | 创建数据备份 | P1 |
| ui/TanukiUi.kt:944 | 包含配置、凭据、扩展与会话 | 术语不一致 | 包含设置、账号信息、扩展和对话。 | P1 |
| ui/TanukiUi.kt:945 | 会覆盖快照后的配置与会话，项目不回滚 | 术语不一致 | 覆盖备份之后的设置和对话，项目文件保持不变。 | P1 |
| ui/TanukiUi.kt:945 | 恢复匹配快照 | 术语不一致 | 恢复数据备份 | P1 |
| ui/TanukiUi.kt:946 | 私有快照只保存在应用内，卸载即丢失。导出 ZIP 前请检查源文件中的敏感内容。 | 术语不一致、长度 | 备份只保存在应用内，卸载应用会丢失。<br>导出项目前，请检查文件中的私人信息。 | P1 |
| ui/TanukiUi.kt:949 | 网页与兼容性 | 保留 | 网页与兼容性 | P1 |
| ui/TanukiUi.kt:949 | 网页与兼容性 | 保留 | 网页与兼容性 | P1 |
| ui/TanukiUi.kt:950 | 界面：官方界面 / 原生界面 | 工程痕迹、术语不一致 | 使用手机界面 | P1 |
| ui/TanukiUi.kt:951 | 关闭为官方 DSH 界面；原生界面：可查看会话、发送消息、处理审批。接口不可用时自动回退官方界面，切换不重启服务。 | 工程痕迹、术语不一致、长度 | 关闭后使用网页界面。<br>手机界面无法使用时，会自动显示网页界面。 | P1 |
| ui/TanukiUi.kt:953 | 列表、搜索、新建、重命名、归档、模型与权限 | 保留 | 列表、搜索、新建、重命名、归档、模型与权限 | P0 |
| ui/TanukiUi.kt:953 | 原生会话管理 | 工程痕迹、术语不一致 | 管理对话 | P1 |
| ui/TanukiUi.kt:955 | 官方更新入口 | 清晰度 | 更新网页组件 | P0 |
| ui/TanukiUi.kt:955 | 当前 WebView ${model.support.version} | 工程痕迹 | 网页组件版本 {版本} | P1 |
| ui/TanukiUi.kt:957 | 系统 WebView 设置 | 工程痕迹 | 网页组件设置 | P2 |
| ui/TanukiUi.kt:959 | 会重新加载网页，请先保存草稿 | 保留 | 会重新加载网页，请先保存草稿 | P2 |
| ui/TanukiUi.kt:959 | 重新检测能力 | 清晰度 | 重新检测 | P2 |
| ui/TanukiUi.kt:960 | 检测结果与兼容性说明 | 清晰度 | 检测详情 | P2 |
| ui/TanukiUi.kt:962 | 要求 Chromium 123+；${cssResult("subgrid", model.support.subgrid)} · ${cssResult("light-dark()", model.support.lightDark)} | 长度 | 网页组件版本与功能检测结果见详情。 | P2 |
| ui/TanukiUi.kt:964 | JavaScript 兼容层不能升级系统内核或补齐 CSS 渲染能力。 | 工程痕迹 | 兼容选项无法替代网页组件更新。 | P2 |
| ui/TanukiUi.kt:967 | 高级与关于 | 保留 | 高级与关于 | P2 |
| ui/TanukiUi.kt:967 | 高级与关于 | 保留 | 高级与关于 | P2 |
| ui/TanukiUi.kt:968 | 高级选项 | 保留 | 高级选项 | P2 |
| ui/TanukiUi.kt:969 | 旧 WebView 标准 API 兼容层 | 工程痕迹 | 旧版网页兼容 | P2 |
| ui/TanukiUi.kt:970 | 仅适用于当前固定 DSH 与旧 WebView；不补齐 CSS。修改后请明确重新连接。 | 工程痕迹、术语不一致 | 仅用于旧版网页组件的兼容处理。<br>修改后请重新连接网页，显示异常时仍需更新组件。 | P2 |
| ui/TanukiUi.kt:971 | 官方页面 Blob 导出 | 清晰度 | 允许网页导出 | P2 |
| ui/TanukiUi.kt:972 | 仅接收当前官方页面的一次性传输，需系统文件选择器确认，上限 8 MiB；重新连接后生效。 | 长度 | 网页导出文件需你确认保存位置，单个最多 8 MiB。<br>重新连接后生效。 | P2 |
| ui/TanukiUi.kt:975 | 许可与组件来源 | 保留 | 许可与组件来源 | P1 |
| ui/TanukiUi.kt:977 | 保留运行环境和项目 | 保留 | 保留运行环境和项目 | P1 |
| ui/TanukiUi.kt:977 | 清除下载缓存 | 保留 | 清除下载缓存 | P1 |
| ui/TanukiUi.kt:983 | 主题 | 保留 | 主题 | P1 |
| ui/TanukiUi.kt:984 | 当前主题 | 保留 | 当前主题 | P1 |
| ui/TanukiUi.kt:984 | 跟随系统 | 保留 | 跟随系统 | P1 |
| ui/TanukiUi.kt:985 | 浅色/深色由系统决定；手动主题切换暂不支持，因此这里不提供开关。官方 DSH 网页主题仍由 DSH 页面自身设置控制。 | 被动/实现说明、术语不一致、长度 | 界面主题跟随系统，网页界面的主题需在网页中设置。 | P1 |
| ui/TanukiUi.kt:987 | 语言 | 保留 | 语言 | P1 |
| ui/TanukiUi.kt:988 | 界面语言 | 保留 | 界面语言 | P1 |
| ui/TanukiUi.kt:988 | 简体中文 | 保留 | 简体中文 | P1 |
| ui/TanukiUi.kt:989 | 原生界面目前只提供简体中文，语言切换暂不支持；官方 DSH 网页语言由 DSH 自身设置控制。 | 工程痕迹、被动/实现说明、术语不一致、长度 | 手机界面使用简体中文，网页语言可在网页设置中调整。 | P1 |
| ui/TanukiUi.kt:991 | 显示与常用入口 | 保留 | 显示与常用入口 | P1 |
| ui/TanukiUi.kt:992 | 字体大小 | 保留 | 字体大小 | P1 |
| ui/TanukiUi.kt:992 | 跟随系统 | 保留 | 跟随系统 | P1 |
| ui/TanukiUi.kt:993 | 原生界面跟随系统字号缩放；独立字号调节暂不支持。 | 工程痕迹、被动/实现说明、术语不一致 | 字体大小跟随系统设置。 | P1 |
| ui/TanukiUi.kt:995 | 会话提醒、更新通知、电池与自启动 | 术语不一致 | 对话提醒、更新通知和后台运行 | P0 |
| ui/TanukiUi.kt:995 | 通知与后台 | 保留 | 通知与后台 | P1 |
| ui/TanukiUi.kt:995 | 通知与后台 | 保留 | 通知与后台 | P1 |
| ui/TanukiUi.kt:997 | 存储与空间 | 保留 | 存储与空间 | P1 |
| ui/TanukiUi.kt:997 | 存储与空间 | 保留 | 存储与空间 | P1 |
| ui/TanukiUi.kt:997 | 工作区容量、目录占用与项目备份 | 保留 | 工作区容量、目录占用与项目备份 | P1 |
| ui/TanukiUi.kt:1003 | 通知 | 保留 | 通知 | P1 |
| ui/TanukiUi.kt:1004 | DSH 聊天通知 | 术语不一致 | 对话通知 | P1 |
| ui/TanukiUi.kt:1005 | 回合完成、等待确认、错误或权限请求时提醒；只在对应会话不可见时发送，正文不含会话内容。 | 术语不一致、长度 | 回复完成或需要处理时提醒。<br>只在你未查看该对话时通知，正文不显示对话内容。 | P0 |
| ui/TanukiUi.kt:1007 | 聊天通知权限与系统入口 | 清晰度 | 对话通知设置 | P0 |
| ui/TanukiUi.kt:1009 | 后台发现新版本时提示；手动检查时申请 | 保留 | 后台发现新版本时提示；手动检查时申请 | P1 |
| ui/TanukiUi.kt:1009 | 更新通知权限 | 保留 | 更新通知权限 | P0 |
| ui/TanukiUi.kt:1011 | 后台与启动 | 保留 | 后台与启动 | P1 |
| ui/TanukiUi.kt:1012 | 启动应用时自动运行 DSH | 术语不一致 | 打开应用时启动助手 | P1 |
| ui/TanukiUi.kt:1013 | 只自动运行上次正常停止的实例；启动中、运行中、安装中或上次任务中断时不会自动启动，仍可手动运行。 | 长度 | 上次正常停止的助手会自动启动。<br>安装中或上次任务中断时，请手动启动。 | P1 |
| ui/TanukiUi.kt:1015 | 电池与应用设置 | 保留 | 电池与应用设置 | P1 |
| ui/TanukiUi.kt:1015 | 管理后台与锁屏行为 | 保留 | 管理后台与锁屏行为 | P1 |
| ui/TanukiUi.kt:1018 | 后台运行说明 | 保留 | 后台运行说明 | P1 |
| ui/TanukiUi.kt:1019 | 关闭自动运行后，服务只在你主动点击“启动 DSH”时运行；唤醒锁在运行期间自动续期，停止后释放。后台和息屏运行仍受系统限制。 | 术语不一致、长度 | 关闭自动启动后，请从工作台启动助手。<br>后台运行可能受系统电池设置限制。 | P1 |
| ui/TanukiUi.kt:1020 | 部分厂商系统还需要在系统的通知、电池或自启动设置中允许 Tanuki，否则后台提醒可能被延迟或拦截。 | 长度 | 如提醒延迟，请在系统中允许 Tanuki 发送通知和后台运行。 | P1 |
| ui/TanukiUi.kt:1099 | 远程 DSH 已连接 | 术语不一致 | 已连接电脑助手 | P1 |
| ui/TanukiUi.kt:1100 | 工作空间尚未启动 | 术语不一致 | 助手尚未启动 | P1 |
| ui/TanukiUi.kt:1101 | 网页渲染已中断 | 工程痕迹 | 网页显示已中断 | P0 |
| ui/TanukiUi.kt:1102 | 先准备兼容的网页内核 | 清晰度 | 请更新网页组件 | P1 |
| ui/TanukiUi.kt:1105 | 远程主机 | 术语不一致 | 远程电脑 | P1 |
| ui/TanukiUi.kt:1106 | 127.0.0.1:${s.remote.localPort ?: "分配中"} | 清晰度 | 127.0.0.1:{端口} | P1 |
| ui/TanukiUi.kt:1106 | SSH 隧道 | 清晰度 | 远程连接 | P1 |
| ui/TanukiUi.kt:1106 | 分配中 | 保留 | 分配中 | P1 |
| ui/TanukiUi.kt:1107 | 主机密钥 | 清晰度 | 电脑安全指纹 | P1 |
| ui/TanukiUi.kt:1108 | 远程 token 只通过本机 SSH 隧道传输；本地运行时保持原状。 | 工程痕迹、术语不一致 | 登录信息通过加密连接传输，本机任务继续运行。 | P1 |
| ui/TanukiUi.kt:1110 | DSH 进程 | 术语不一致 | 助手状态 | P1 |
| ui/TanukiUi.kt:1111 | 就绪 | 保留 | 就绪 | P1 |
| ui/TanukiUi.kt:1111 | 未就绪 | 保留 | 未就绪 | P1 |
| ui/TanukiUi.kt:1111 | 本机 Web 服务 | 清晰度 | 本机助手连接 | P1 |
| ui/TanukiUi.kt:1112 | 启动模式 | 保留 | 启动模式 | P1 |
| ui/TanukiUi.kt:1114 | 最近失败 | 保留 | 最近失败 | P0 |
| ui/TanukiUi.kt:1117 | 网页状态：${s.web.label} | 清晰度 | 网页状态：{名称} | P1 |
| ui/TanukiUi.kt:1119 | 下载并准备环境 | 保留 | 下载并准备环境 | P1 |
| ui/TanukiUi.kt:1119 | 启动 DSH | 术语不一致 | 启动助手 | P1 |
| ui/TanukiUi.kt:1120 | 断开远程并切回本地 | 清晰度 | 回到本机 | P1 |
| ui/TanukiUi.kt:1121 | 恢复网页连接 | 保留 | 恢复网页连接 | P1 |
| ui/TanukiUi.kt:1136 | 原生界面不可用，已显示官方界面 | 工程痕迹、术语不一致 | 手机界面暂不可用，已切换网页界面。 | P1 |
| ui/TanukiUi.kt:1139 | 重试 | 保留 | 重试 | P1 |
| ui/TanukiUi.kt:1145 | 返回概览 | 保留 | 返回概览 | P1 |
| ui/TanukiUi.kt:1147 | DSH | 术语不一致 | 助手 | P1 |
| ui/TanukiUi.kt:1150 | 原生界面 | 工程痕迹、术语不一致 | 手机界面 | P1 |
| ui/TanukiUi.kt:1151 | 远程 · ${s.remote.host} | 清晰度 | 远程 · {来源} | P1 |
| ui/TanukiUi.kt:1158 | 浏览器小窗 | 保留 | 浏览器小窗 | P1 |
| ui/TanukiUi.kt:1159 | 进入全屏 | 清晰度 | 全屏 | P1 |
| ui/TanukiUi.kt:1162 | 刷新原生会话 | 工程痕迹、术语不一致 | 刷新对话 | P1 |
| ui/TanukiUi.kt:1170 | 重新连接 DSH 网页 | 术语不一致 | 重新连接网页 | P1 |
| ui/TanukiUi.kt:1173 | 设置 | 保留 | 设置 | P1 |
| ui/TanukiUi.kt:1183 | 浏览器小窗 | 保留 | 浏览器小窗 | P1 |
| ui/TanukiUi.kt:1185 | 退出全屏 | 保留 | 退出全屏 | P1 |
| ui/TanukiUi.kt:1195 | 尚未检查 | 保留 | 尚未检查 | P1 |
| ui/TanukiUi.kt:1197 | 未知 | 被动/实现说明 | 未知 | P1 |
| ui/TanukiUi.kt:1203 | 版本更新 | 保留 | 版本更新 | P0 |
| ui/TanukiUi.kt:1207 | 尚未安装 DSH；安装后可比较官方最新版本。 | 术语不一致 | 请先安装助手，再检查更新。 | P0 |
| ui/TanukiUi.kt:1208 | 已安装 ${state.installedVersion} · 最新 ${state.latestVersion} | 工程痕迹 | 已安装 {版本} · 最新 {版本} | P0 |
| ui/TanukiUi.kt:1209 | 已安装 ${state.installedVersion} · 已是最新版本 | 工程痕迹 | 已安装 {版本} · 已是最新版本 | P0 |
| ui/TanukiUi.kt:1212 | 后台每 24 小时检查一次（需联网） | 清晰度 | 联网时约每 24 小时检查更新。 | P1 |
| ui/TanukiUi.kt:1213 |  · 上次 $lastChecked | 清晰度 | · 上次 {内容} | P1 |
| ui/TanukiUi.kt:1215 |  · 上次未成功，将按周期重试 | 清晰度 | · 上次未成功，将按周期重试 | P1 |
| ui/TanukiUi.kt:1219 | 检查 DSH 更新 | 术语不一致 | 检查助手更新 | P0 |
| ui/TanukiUi.kt:1220 | 检查中… | 保留 | 检查中… | P1 |
| ui/TanukiUi.kt:1220 | 检查更新 | 保留 | 检查更新 | P0 |
| ui/TanukiUi.kt:1233 | componentChevron | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiUi.kt:1236 | 展开$title | 清晰度 | 展开{名称} | P1 |
| ui/TanukiUi.kt:1236 | 收起$title | 清晰度 | 收起{名称} | P1 |
| ui/TanukiUi.kt:1237 | 已展开 | 保留 | 已展开 | P1 |
| ui/TanukiUi.kt:1237 | 已收起 | 保留 | 已收起 | P1 |
| ui/TanukiUi.kt:1243 | 安装没有完成 | 清晰度 | 安装失败 | P0 |
| ui/TanukiUi.kt:1243 | 已安装 | 保留 | 已安装 | P0 |
| ui/TanukiUi.kt:1243 | 未安装 | 保留 | 未安装 | P0 |
| ui/TanukiUi.kt:1311 | 停止 DSH 服务 | 术语不一致 | 停止助手 | P1 |
| ui/TanukiUi.kt:1312 | 停止 | 保留 | 停止 | P1 |
| ui/TanukiUi.kt:1348 | detailsChevron | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/TanukiUi.kt:1351 | 展开 | 保留 | 展开 | P1 |
| ui/TanukiUi.kt:1351 | 收起 | 保留 | 收起 | P1 |
| ui/TanukiUi.kt:1352 | 已展开 | 保留 | 已展开 | P1 |
| ui/TanukiUi.kt:1352 | 已收起 | 保留 | 已收起 | P1 |
| ui/TanukiUi.kt:1398 | $name ${when (supported) { true -&gt; "支持"; false -&gt; "缺失"; null -&gt; "待检测" }} | 工程痕迹 | {名称} {端口} | P1 |
| ui/TanukiUi.kt:1398 | 待检测 | 保留 | 待检测 | P1 |
| ui/TanukiUi.kt:1398 | 支持 | 保留 | 支持 | P1 |
| ui/TanukiUi.kt:1398 | 缺失 | 保留 | 缺失 | P1 |
| ui/TanukiUi.kt:1400 | 未知 | 被动/实现说明 | 未知 | P1 |
| ui/TanukiUi.kt:1487 | 温控与省电策略 | 清晰度 | 温控与省电 | P1 |
| ui/TanukiUi.kt:1488 | 温度、电量或内存紧张时降低后台更新、下载并发、日志与预览渲染；不暂停正在运行的 DSH 任务。 | 工程痕迹、术语不一致、长度 | 手机过热、低电或内存不足时，会减少后台活动。<br>当前助手任务继续运行。 | P0 |
| ui/TanukiUi.kt:1489 | 低电阈值 | 保留 | 低电阈值 | P1 |
| ui/TanukiUi.kt:1498 | ${percent}% | 清晰度 | {进度}% | P1 |
| ui/TanukiUi.kt:1502 | 低于阈值且未充电时暂停后台更新检查并把下载并发降到 4 路；退出阈值比进入阈值高 5 个百分点。 | 长度 | 电量低于设定值且未充电时，会延后后台更新并降低下载速度。<br>电量回升 5 个百分点后恢复。 | P0 |
| ui/TanukiUi.kt:1503 | 当前状态：${state.condition.label} · ${state.reasonText()} | 清晰度 | 当前状态：{名称} · {名称} | P1 |
| ui/TanukiUi.kt:1504 | 下载并发 ${state.workerCap} 路 · 日志 ${state.logLineLimit} 行 · PDF 预览上限 ${state.previewMaxDimension} 像素 | 工程痕迹、长度 | 已根据设备状态调整下载、日志和预览。具体参数见详情。 | P1 |
| ui/TanukiUi.kt:1506 | 后台更新检查已延后；状态恢复后由 WorkManager 自动补跑。 | 清晰度 | 后台更新已延后，设备状态恢复后会继续。 | P0 |

### [ui/WorkspaceDocxPreview.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/WorkspaceDocxPreview.kt:105 | 无法读取此 DOCX，文件可能已损坏或加密。请用其他应用打开或重新导出。 | 清晰度 | 文档无法读取，请用其他应用打开或重新导出。 | P0 |
| ui/WorkspaceDocxPreview.kt:136 | 只读预览 | 保留 | 只读预览 | P1 |
| ui/WorkspaceDocxPreview.kt:139 | ${document.paragraphCount} 段 · ${document.tableCount} 表 · ${document.imageCount} 图 | 清晰度 | {数量} 段 · {数量} 表 · {数量} 图 | P1 |
| ui/WorkspaceDocxPreview.kt:144 | − | 保留 | − | P1 |
| ui/WorkspaceDocxPreview.kt:145 | ${(zoom * 100).roundToInt()}% | 清晰度 | {内容}% | P1 |
| ui/WorkspaceDocxPreview.kt:146 | + | 保留 | + | P1 |
| ui/WorkspaceDocxPreview.kt:147 | 适应宽度 | 保留 | 适应宽度 | P1 |
| ui/WorkspaceDocxPreview.kt:152 | 复制文本 | 保留 | 复制文本 | P1 |
| ui/WorkspaceDocxPreview.kt:152 | 已复制 | 保留 | 已复制 | P1 |
| ui/WorkspaceDocxPreview.kt:153 | 支持范围 | 保留 | 支持范围 | P1 |
| ui/WorkspaceDocxPreview.kt:158 | 文档超过内置预览上限，仅显示前面部分；完整内容请用其他应用打开。 | 清晰度 | 文档较长，仅显示部分内容。请用其他应用查看全文。 | P1 |
| ui/WorkspaceDocxPreview.kt:232 | 文档较长，预览仅显示前 ${measured.atoms.size} 个排版块；完整内容请用其他应用打开。 | 清晰度 | 文档较长，仅显示前面部分。请用其他应用查看全文。 | P1 |
| ui/WorkspaceDocxPreview.kt:245 | 内置 DOCX 预览范围 | 清晰度 | 文档预览说明 | P1 |
| ui/WorkspaceDocxPreview.kt:248 | 支持：段落、标题、列表编号、粗体/斜体/下划线/字号/颜色、常用表格、内嵌图片、纸张尺寸与页边距、近似分页。 | 长度 | 可查看文字、列表、常用表格和内嵌图片。<br>详细支持范围在下方展开。 | P1 |
| ui/WorkspaceDocxPreview.kt:249 | 不支持：复杂域、宏与脚本、嵌入对象、文本框/艺术字、页眉页脚、脚注尾注、批注与修订痕迹、多栏排版、精确分页与跨页表头重复。 | 长度 | 部分复杂排版和内容无法显示，宏和脚本不会执行。<br>需要完整内容时，请用其他应用打开。 | P1 |
| ui/WorkspaceDocxPreview.kt:250 | 为便于手机阅读，正文字号相对纸张比例放大，因此页数多于 Word 的精确排版；原文件不会被修改。 | 长度 | 已调整字号以便手机阅读，页数可能与原文不同，原文件保持不变。 | P1 |
| ui/WorkspaceDocxPreview.kt:253 | 本文档提示 | 保留 | 本文档提示 | P1 |
| ui/WorkspaceDocxPreview.kt:254 | · $warning | 清晰度 | · {内容} | P1 |
| ui/WorkspaceDocxPreview.kt:258 | 知道了 | 保留 | 知道了 | P1 |
| ui/WorkspaceDocxPreview.kt:300 | ${pageIndex + 1} / $pageCount | 清晰度 | {内容} / {数量} | P1 |
| ui/WorkspaceDocxPreview.kt:307 | 本页内容超出纸张，已截断 | 清晰度 | 本页仅显示部分内容，请用其他应用查看完整文档。 | P1 |
| ui/WorkspaceDocxPreview.kt:397 | 文档图片 | 保留 | 文档图片 | P1 |
| ui/WorkspaceDocxPreview.kt:408 | 图片无法显示 | 保留 | 图片无法显示 | P0 |
| ui/WorkspaceDocxPreview.kt:433 | 表格图片 | 保留 | 表格图片 | P1 |
| ui/WorkspaceDocxPreview.kt:760 | 嵌套表格未内置显示 | 清晰度 | 嵌套表格无法完整显示，请用其他应用查看。 | P1 |

### [ui/WorkspaceFileViewer.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/WorkspaceFileViewer.kt:99 | 无法读取此文件。它可能已被修改或移除，请返回后刷新。 | 保留 | 无法读取此文件。它可能已被修改或移除，请返回后刷新。 | P0 |
| ui/WorkspaceFileViewer.kt:111 | 打开方式 | 保留 | 打开方式 | P1 |
| ui/WorkspaceFileViewer.kt:113 | 没有可打开此格式的应用，请安装相应播放器或文档查看器后重试。 | 保留 | 没有可打开此格式的应用，请安装相应播放器或文档查看器后重试。 | P0 |
| ui/WorkspaceFileViewer.kt:126 | 此图片格式无法内置预览，请使用其他应用打开。 | 保留 | 此图片格式无法内置预览，请使用其他应用打开。 | P0 |
| ui/WorkspaceFileViewer.kt:160 | 关闭文件 | 保留 | 关闭文件 | P1 |
| ui/WorkspaceFileViewer.kt:168 | 读取文件信息… | 保留 | 读取文件信息… | P1 |
| ui/WorkspaceFileViewer.kt:177 | Aa | 保留 | Aa | P1 |
| ui/WorkspaceFileViewer.kt:177 | 阅读工具 | 保留 | 阅读工具 | P1 |
| ui/WorkspaceFileViewer.kt:179 | 编辑 | 保留 | 编辑 | P1 |
| ui/WorkspaceFileViewer.kt:184 | 准备打开 · ${elapsed}秒 | 清晰度 | 准备打开 · {耗时}秒 | P1 |
| ui/WorkspaceFileViewer.kt:194 | 重新选择文件 | 保留 | 重新选择文件 | P1 |
| ui/WorkspaceFileViewer.kt:195 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| ui/WorkspaceFileViewer.kt:202 | 按文本方式查看（只读） | 清晰度 | 作为文本查看 | P1 |
| ui/WorkspaceFileViewer.kt:203 | 返回类型说明 | 保留 | 返回类型说明 | P1 |
| ui/WorkspaceFileViewer.kt:265 | 旧版 Word 文档（.doc） | 清晰度 | 旧版文档 | P1 |
| ui/WorkspaceFileViewer.kt:266 | 暂不支持内置预览，可用其他应用打开；外部文件也可复制到工作区。 | 被动/实现说明 | 请用其他应用打开，也可先复制到工作区。 | P1 |
| ui/WorkspaceFileViewer.kt:267 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| ui/WorkspaceFileViewer.kt:283 | 无法读取文件类型信息，请返回后重试。 | 保留 | 无法读取文件类型信息，请返回后重试。 | P0 |
| ui/WorkspaceFileViewer.kt:300 | 导出此文件 | 保留 | 导出此文件 | P1 |
| ui/WorkspaceFileViewer.kt:304 | 使用其他应用打开 | 清晰度 | 其他应用打开 | P1 |
| ui/WorkspaceFileViewer.kt:310 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| ui/WorkspaceFileViewer.kt:371 | 自动 | 保留 | 自动 | P1 |
| ui/WorkspaceFileViewer.kt:372 | 自动 | 保留 | 自动 | P1 |
| ui/WorkspaceFileViewer.kt:373 | 自动 | 保留 | 自动 | P1 |
| ui/WorkspaceFileViewer.kt:378 | 自动 | 保留 | 自动 | P1 |
| ui/WorkspaceFileViewer.kt:390 | 无法读取此文本分段，请重试或使用其他应用打开。 | 保留 | 无法读取此文本分段，请重试或使用其他应用打开。 | P0 |
| ui/WorkspaceFileViewer.kt:436 | 二进制文件 | 保留 | 二进制文件 | P1 |
| ui/WorkspaceFileViewer.kt:447 | 复制路径 | 保留 | 复制路径 | P1 |
| ui/WorkspaceFileViewer.kt:447 | 已复制路径 | 保留 | 已复制路径 | P1 |
| ui/WorkspaceFileViewer.kt:502 | 自动 | 保留 | 自动 | P1 |
| ui/WorkspaceFileViewer.kt:514 |  · 已截断 | 清晰度 | · 已截断 | P1 |
| ui/WorkspaceFileViewer.kt:515 |  · 第 $startLine–${startLine + numbered.lineCount - 1} 行 | 清晰度 | · 第 {内容}–{数量} 行 | P1 |
| ui/WorkspaceFileViewer.kt:523 | 关闭搜索 | 保留 | 关闭搜索 | P1 |
| ui/WorkspaceFileViewer.kt:523 | 搜索当前段 | 保留 | 搜索当前段 | P1 |
| ui/WorkspaceFileViewer.kt:527 | 字号 ${fontSize}sp | 清晰度 | 字号 {数量}sp | P1 |
| ui/WorkspaceFileViewer.kt:529 | 减小字号 | 清晰度 | 缩小字号 | P1 |
| ui/WorkspaceFileViewer.kt:534 | 增大字号 | 清晰度 | 放大字号 | P1 |
| ui/WorkspaceFileViewer.kt:539 | 显示行号 | 保留 | 显示行号 | P1 |
| ui/WorkspaceFileViewer.kt:543 | 自动换行 | 保留 | 自动换行 | P1 |
| ui/WorkspaceFileViewer.kt:548 | 文本编码：${shortEncodingLabel(requestedEncoding)} | 清晰度 | 文本编码：{名称} | P1 |
| ui/WorkspaceFileViewer.kt:552 | 复制本段 | 保留 | 复制本段 | P1 |
| ui/WorkspaceFileViewer.kt:552 | 已复制本段 | 保留 | 已复制本段 | P1 |
| ui/WorkspaceFileViewer.kt:605 | 搜索当前段 | 保留 | 搜索当前段 | P1 |
| ui/WorkspaceFileViewer.kt:608 | 0 处 | 保留 | 0 处 | P1 |
| ui/WorkspaceFileViewer.kt:611 | 上一个匹配 | 保留 | 上一个匹配 | P1 |
| ui/WorkspaceFileViewer.kt:614 | 下一个匹配 | 保留 | 下一个匹配 | P1 |
| ui/WorkspaceFileViewer.kt:616 | 关闭搜索 | 保留 | 关闭搜索 | P1 |
| ui/WorkspaceFileViewer.kt:628 | 下一段 | 保留 | 下一段 | P1 |
| ui/WorkspaceFileViewer.kt:628 | 已截断 | 保留 | 已截断 | P1 |
| ui/WorkspaceFileViewer.kt:641 | 复制本段 | 保留 | 复制本段 | P1 |
| ui/WorkspaceFileViewer.kt:641 | 已复制本段 | 保留 | 已复制本段 | P1 |
| ui/WorkspaceFileViewer.kt:652 | 已按 ${shortEncodingLabel(detectedEncoding.orEmpty())} 显示，如有乱码可切换编码 | 清晰度 | 已按 {名称} 显示，如有乱码可切换编码 | P1 |
| ui/WorkspaceFileViewer.kt:656 | 切换编码 | 保留 | 切换编码 | P1 |
| ui/WorkspaceFileViewer.kt:657 | 关闭编码提示 | 保留 | 关闭编码提示 | P1 |
| ui/WorkspaceFileViewer.kt:662 | 查找文本 | 保留 | 查找文本 | P1 |
| ui/WorkspaceFileViewer.kt:663 | A− | 保留 | A− | P1 |
| ui/WorkspaceFileViewer.kt:663 | 减小字号 | 清晰度 | 缩小字号 | P1 |
| ui/WorkspaceFileViewer.kt:664 | ${fontSize}sp | 清晰度 | {数量}sp | P1 |
| ui/WorkspaceFileViewer.kt:665 | A+ | 保留 | A+ | P1 |
| ui/WorkspaceFileViewer.kt:665 | 增大字号 | 清晰度 | 放大字号 | P1 |
| ui/WorkspaceFileViewer.kt:666 | 行号 | 保留 | 行号 | P1 |
| ui/WorkspaceFileViewer.kt:667 | 自动换行 | 保留 | 自动换行 | P1 |
| ui/WorkspaceFileViewer.kt:670 | 文本编码 | 保留 | 文本编码 | P1 |
| ui/WorkspaceFileViewer.kt:687 | 复制本段 | 保留 | 复制本段 | P1 |
| ui/WorkspaceFileViewer.kt:687 | 已复制本段 | 保留 | 已复制本段 | P1 |
| ui/WorkspaceFileViewer.kt:691 | ${matchIndex + 1} / ${matches.size} 处匹配 | 清晰度 | {内容} / {数量} 处匹配 | P1 |
| ui/WorkspaceFileViewer.kt:691 | ${matches.size} 处匹配 | 清晰度 | {数量} 处匹配 | P1 |
| ui/WorkspaceFileViewer.kt:691 | 搜索当前段 | 保留 | 搜索当前段 | P1 |
| ui/WorkspaceFileViewer.kt:693 | 上一个匹配 | 保留 | 上一个匹配 | P1 |
| ui/WorkspaceFileViewer.kt:694 | 下一个匹配 | 保留 | 下一个匹配 | P1 |
| ui/WorkspaceFileViewer.kt:705 | ${shortEncodingLabel(page?.encoding ?: requestedEncoding)} · 第 $startLine–${startLine + numbered.lineCount - 1} 行 | 长度 | {名称} · 第 {内容}–{数量} 行 | P1 |
| ui/WorkspaceFileViewer.kt:706 |  · 已显示 ${viewerBytes(it.nextOffset)} / ${viewerBytes(it.totalBytes)} | 清晰度 | · 已显示 {大小} / {大小} | P1 |
| ui/WorkspaceFileViewer.kt:711 | 文件较大（${viewerBytes(page?.totalBytes ?: 0)}），仅显示前 ${viewerBytes(TextPageLayout.MAX_VIEW_BYTES)}；已截断。可导出或使用其他应用打开完整文件。 | 长度 | 文件较大，仅显示部分内容。<br>可导出或用其他应用查看完整文件。 | P1 |
| ui/WorkspaceFileViewer.kt:716 | 上一段 | 保留 | 上一段 | P1 |
| ui/WorkspaceFileViewer.kt:724 | 下一段 | 保留 | 下一段 | P1 |
| ui/WorkspaceFileViewer.kt:724 | 已截断 | 保留 | 已截断 | P1 |
| ui/WorkspaceFileViewer.kt:738 | $label · 如有乱码可切换编码 | 清晰度 | {名称} · 如有乱码可切换编码 | P1 |
| ui/WorkspaceFileViewer.kt:746 | 切换 | 保留 | 切换 | P1 |
| ui/WorkspaceFileViewer.kt:753 | × | 保留 | × | P1 |
| ui/WorkspaceFileViewer.kt:755 | 关闭编码提示 | 保留 | 关闭编码提示 | P1 |
| ui/WorkspaceFileViewer.kt:794 | 上一段 | 保留 | 上一段 | P1 |
| ui/WorkspaceFileViewer.kt:815 | 导出 | 保留 | 导出 | P1 |
| ui/WorkspaceFileViewer.kt:818 | 更多操作 | 保留 | 更多操作 | P1 |
| ui/WorkspaceFileViewer.kt:821 | 使用其他应用打开 | 清晰度 | 其他应用打开 | P1 |
| ui/WorkspaceFileViewer.kt:823 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| ui/WorkspaceFileViewer.kt:849 | 这是二进制文件 | 保留 | 这是二进制文件 | P1 |
| ui/WorkspaceFileViewer.kt:851 | 文件内容不是可读文本，内置查看器不会按文本解码，也不会修改原文件。 | 清晰度 | 这不是可读文本，请用适合此格式的应用打开。 | P1 |
| ui/WorkspaceFileViewer.kt:856 | 用其他应用打开 | 清晰度 | 其他应用打开 | P1 |
| ui/WorkspaceFileViewer.kt:857 | 复制路径 | 保留 | 复制路径 | P1 |
| ui/WorkspaceFileViewer.kt:857 | 已复制路径 | 保留 | 已复制路径 | P1 |
| ui/WorkspaceFileViewer.kt:865 | 自动 | 保留 | 自动 | P1 |
| ui/WorkspaceFileViewer.kt:865 | 自动检测 | 保留 | 自动检测 | P1 |
| ui/WorkspaceFileViewer.kt:867 | UTF-16（自动端序） | 清晰度 | 自动识别文字顺序 | P1 |
| ui/WorkspaceFileViewer.kt:874 | 自动 | 保留 | 自动 | P1 |
| ui/WorkspaceFileViewer.kt:874 | 自动检测 | 保留 | 自动检测 | P1 |
| ui/WorkspaceFileViewer.kt:894 | 暂不支持内置预览 | 被动/实现说明 | 用其他应用查看 | P1 |
| ui/WorkspaceFileViewer.kt:896 | 文件类型：${mime.ifBlank { "未知" }} | 被动/实现说明 | 文件类型：{内容} | P1 |
| ui/WorkspaceFileViewer.kt:896 | 未知 | 被动/实现说明 | 未知 | P1 |
| ui/WorkspaceFileViewer.kt:898 | 该格式没有内置查看器。可以按文本方式打开（只读，适合代码、配置和日志），或复制路径后用其他应用打开；原文件不会被修改。 | 长度 | 可尝试作为文本查看，或用其他应用打开。<br>原文件保持不变。 | P0 |
| ui/WorkspaceFileViewer.kt:902 | 用文本打开 | 清晰度 | 作为文本查看 | P1 |
| ui/WorkspaceFileViewer.kt:903 | 复制路径 | 保留 | 复制路径 | P1 |
| ui/WorkspaceFileViewer.kt:903 | 已复制路径 | 保留 | 已复制路径 | P1 |
| ui/WorkspaceFileViewer.kt:904 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| ui/WorkspaceFileViewer.kt:938 | 设备无法解码此音频，请使用其他应用打开。 | 保留 | 设备无法解码此音频，请使用其他应用打开。 | P0 |
| ui/WorkspaceFileViewer.kt:944 | ${mediaTime(position)} / ${mediaTime(duration)} | 清晰度 | {内容} / {耗时} | P1 |
| ui/WorkspaceFileViewer.kt:948 | 播放 | 保留 | 播放 | P1 |
| ui/WorkspaceFileViewer.kt:948 | 暂停 | 保留 | 暂停 | P1 |
| ui/WorkspaceFileViewer.kt:974 | 大小未知 | 被动/实现说明 | 正在读取文件大小 | P1 |

### [ui/WorkspaceFilesPage.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/WorkspaceFilesPage.kt:123 | 文件传输尚未连接，请返回后重试 | 清晰度 | 文件传输未连接，请返回后重试。 | P1 |
| ui/WorkspaceFilesPage.kt:208 | 打开 | 保留 | 打开 | P1 |
| ui/WorkspaceFilesPage.kt:234 | 操作失败，请检查文件是否仍存在、空间是否充足，然后重试。 | 清晰度 | 文件操作失败，请检查文件和可用空间后重试。 | P0 |
| ui/WorkspaceFilesPage.kt:255 | 请先停止 DSH，再修改文件。浏览、导入和导出不受影响。 | 术语不一致 | 请先停止助手，再修改文件。浏览、导入和导出仍可使用。 | P1 |
| ui/WorkspaceFilesPage.kt:258 | 复制 | 保留 | 复制 | P1 |
| ui/WorkspaceFilesPage.kt:258 | 移动 | 保留 | 移动 | P1 |
| ui/WorkspaceFilesPage.kt:261 | 新建文本文件 | 保留 | 新建文本文件 | P1 |
| ui/WorkspaceFilesPage.kt:261 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:263 | 已复制 Agent 可用的路径 | 清晰度 | 助手文件路径已复制 | P1 |
| ui/WorkspaceFilesPage.kt:263 | 已复制手机工具路径 | 清晰度 | 助手文件路径已复制 | P1 |
| ui/WorkspaceFilesPage.kt:320 | 正在准备传输 | 保留 | 正在准备传输 | P1 |
| ui/WorkspaceFilesPage.kt:328 | 已${result.action} ${result.completed} 项到${result.target} | 清晰度 | 已{内容} {数量} 项到{内容} | P1 |
| ui/WorkspaceFilesPage.kt:335 | 传输已取消；已完成项保留，未完成项的源文件保留。 | 保留 | 传输已取消；已完成项保留，未完成项的源文件保留。 | P0 |
| ui/WorkspaceFilesPage.kt:341 | 复制 | 保留 | 复制 | P1 |
| ui/WorkspaceFilesPage.kt:341 | 移动 | 保留 | 移动 | P1 |
| ui/WorkspaceFilesPage.kt:342 | 工作区「${draft.target}」 | 清晰度 | 工作区「{内容}」 | P1 |
| ui/WorkspaceFilesPage.kt:342 | 工作区根目录 | 保留 | 工作区根目录 | P1 |
| ui/WorkspaceFilesPage.kt:393 | 已导入 ${event.fileCount} 个文件到「${event.projectName}」 | 清晰度 | 已导入 {数量} 个文件到「{名称}」 | P1 |
| ui/WorkspaceFilesPage.kt:394 | 打开 | 保留 | 打开 | P1 |
| ui/WorkspaceFilesPage.kt:433 | 允许 DSH 写入手机文件？ | 术语不一致 | 允许助手写入手机文件？ | P0 |
| ui/WorkspaceFilesPage.kt:434 | 允许本次 DSH 运行期间新建、修改、重命名和删除系统已授权的手机共享文件。可以随时撤销，停止 DSH 后自动恢复只读。 | 术语不一致、长度 | 允许本次运行中新增、修改或删除已授权的手机文件。<br>可随时撤销，停止助手后恢复只读。 | P0 |
| ui/WorkspaceFilesPage.kt:435 | 保持只读 | 保留 | 保持只读 | P1 |
| ui/WorkspaceFilesPage.kt:436 | 允许本次写入 | 保留 | 允许本次写入 | P0 |
| ui/WorkspaceFilesPage.kt:476 | 此文件不能直接编辑，请使用其他应用打开。 | 保留 | 此文件不能直接编辑，请使用其他应用打开。 | P1 |
| ui/WorkspaceFilesPage.kt:477 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:514 |  · 只读 | 清晰度 | · 只读 | P1 |
| ui/WorkspaceFilesPage.kt:521 | ＋ 导入 | 保留 | ＋ 导入 | P1 |
| ui/WorkspaceFilesPage.kt:527 | 文件操作 | 保留 | 文件操作 | P1 |
| ui/WorkspaceFilesPage.kt:530 | 关闭搜索 | 保留 | 关闭搜索 | P1 |
| ui/WorkspaceFilesPage.kt:530 | 搜索当前文件夹 | 保留 | 搜索当前文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:532 | 刷新文件 | 保留 | 刷新文件 | P1 |
| ui/WorkspaceFilesPage.kt:534 | 排序与显示 | 保留 | 排序与显示 | P1 |
| ui/WorkspaceFilesPage.kt:538 | 新建文件夹 | 保留 | 新建文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:538 | 新建文本文件 | 保留 | 新建文本文件 | P1 |
| ui/WorkspaceFilesPage.kt:542 | 导出整个工作区 | 清晰度 | 导出工作区 | P1 |
| ui/WorkspaceFilesPage.kt:545 | 打开手机/其他应用文件 | 清晰度 | 打开外部文件 | P1 |
| ui/WorkspaceFilesPage.kt:549 | 导入为新项目 | 保留 | 导入为新项目 | P1 |
| ui/WorkspaceFilesPage.kt:550 | 会创建一个新文件夹，不覆盖现有项目 | 保留 | 会创建一个新文件夹，不覆盖现有项目 | P0 |
| ui/WorkspaceFilesPage.kt:553 | 复制当前目录路径 | 清晰度 | 复制当前路径 | P1 |
| ui/WorkspaceFilesPage.kt:557 | 名称 | 保留 | 名称 | P1 |
| ui/WorkspaceFilesPage.kt:557 | 文件大小 | 保留 | 文件大小 | P1 |
| ui/WorkspaceFilesPage.kt:557 | 最近修改 | 保留 | 最近修改 | P1 |
| ui/WorkspaceFilesPage.kt:562 | 切换为列表 | 保留 | 切换为列表 | P1 |
| ui/WorkspaceFilesPage.kt:562 | 切换为网格 | 保留 | 切换为网格 | P1 |
| ui/WorkspaceFilesPage.kt:564 | 显示隐藏文件 | 清晰度 | 显示隐藏 | P1 |
| ui/WorkspaceFilesPage.kt:564 | 隐藏点号文件 | 清晰度 | 隐藏文件 | P1 |
| ui/WorkspaceFilesPage.kt:573 | 刚刚导入：$project | 清晰度 | 刚刚导入：{内容} | P1 |
| ui/WorkspaceFilesPage.kt:577 | 打开 | 保留 | 打开 | P1 |
| ui/WorkspaceFilesPage.kt:578 | 忽略 | 保留 | 忽略 | P1 |
| ui/WorkspaceFilesPage.kt:589 | 取消选择 | 清晰度 | 已连接助手 | P0 |
| ui/WorkspaceFilesPage.kt:590 | 已选 ${selected.size} | 清晰度 | 已选 {数量} | P1 |
| ui/WorkspaceFilesPage.kt:592 | 筛选：$query | 清晰度 | 筛选：{内容} | P1 |
| ui/WorkspaceFilesPage.kt:596 | 全选 | 保留 | 全选 | P1 |
| ui/WorkspaceFilesPage.kt:597 | 删除 | 保留 | 删除 | P0 |
| ui/WorkspaceFilesPage.kt:597 | 复制 | 保留 | 复制 | P1 |
| ui/WorkspaceFilesPage.kt:597 | 移动 | 保留 | 移动 | P1 |
| ui/WorkspaceFilesPage.kt:605 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| ui/WorkspaceFilesPage.kt:606 | 移动到工作区 | 保留 | 移动到工作区 | P1 |
| ui/WorkspaceFilesPage.kt:636 | 搜索当前文件夹 | 保留 | 搜索当前文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:640 | 关闭搜索 | 保留 | 关闭搜索 | P1 |
| ui/WorkspaceFilesPage.kt:652 | 返回上一级 | 保留 | 返回上一级 | P1 |
| ui/WorkspaceFilesPage.kt:659 | / | 保留 | / | P1 |
| ui/WorkspaceFilesPage.kt:666 | ${shown.size} 项 | 清晰度 | {数量} 项 | P1 |
| ui/WorkspaceFilesPage.kt:678 | fileSelection | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/WorkspaceFilesPage.kt:698 | ${entry.name}的操作 | 清晰度 | {名称}的操作 | P1 |
| ui/WorkspaceFilesPage.kt:700 | 复制 Agent 路径 | 清晰度 | 复制助手路径 | P1 |
| ui/WorkspaceFilesPage.kt:700 | 复制手机工具路径 | 保留 | 复制手机工具路径 | P1 |
| ui/WorkspaceFilesPage.kt:701 | 导出此文件 | 保留 | 导出此文件 | P1 |
| ui/WorkspaceFilesPage.kt:702 | 选择 | 保留 | 选择 | P1 |
| ui/WorkspaceFilesPage.kt:703 | 删除 | 保留 | 删除 | P0 |
| ui/WorkspaceFilesPage.kt:703 | 复制 | 保留 | 复制 | P1 |
| ui/WorkspaceFilesPage.kt:703 | 移动 | 保留 | 移动 | P1 |
| ui/WorkspaceFilesPage.kt:703 | 重命名 | 保留 | 重命名 | P1 |
| ui/WorkspaceFilesPage.kt:705 | 复制到工作区 | 保留 | 复制到工作区 | P1 |
| ui/WorkspaceFilesPage.kt:706 | 移动到工作区 | 保留 | 移动到工作区 | P1 |
| ui/WorkspaceFilesPage.kt:730 | 浏览手机文件 | 保留 | 浏览手机文件 | P1 |
| ui/WorkspaceFilesPage.kt:731 | 授权后可查看手机共享存储和可用存储卡中的文件，并打开音频、视频和文档。其他应用的私有目录与系统限制目录仍由 Android 保护。 | 长度 | 授权后可浏览手机文件和可用存储卡。<br>其他应用的私有文件及系统受限目录仍无法访问。 | P0 |
| ui/WorkspaceFilesPage.kt:733 | 授权访问手机文件 | 清晰度 | 授权文件访问 | P0 |
| ui/WorkspaceFilesPage.kt:737 | 不授权也可通过文件操作菜单，从系统文件选择器导入文件。 | 保留 | 不授权也可通过文件操作菜单，从系统文件选择器导入文件。 | P0 |
| ui/WorkspaceFilesPage.kt:752 | fileState | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/WorkspaceFilesPage.kt:766 | 新建文件夹 | 保留 | 新建文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:834 | 文件夹 | 保留 | 文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:844 | 复制 | 保留 | 复制 | P1 |
| ui/WorkspaceFilesPage.kt:853 | 当前位置 | 保留 | 当前位置 | P1 |
| ui/WorkspaceFilesPage.kt:855 | 已完成 $completed 项 | 清晰度 | 已完成 {数量} 项 | P1 |
| ui/WorkspaceFilesPage.kt:860 | 编辑 ${actionEntries.single().name} | 清晰度 | 编辑 {数量} | P1 |
| ui/WorkspaceFilesPage.kt:860 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:862 | 删除 | 保留 | 删除 | P0 |
| ui/WorkspaceFilesPage.kt:862 | 删除 ${actionEntries.size} 项${if (actionEntries.any { it.directory }) "及其中的内容" else ""}？无法撤销；链接只删除条目本身。\n\n${actionEntries.take(6).joinToString("\n") { it.name }} | 长度 | 将删除 {数量} 项及其中内容，无法撤销。<br>链接只删除链接本身。待删除文件另行列出。 | P0 |
| ui/WorkspaceFilesPage.kt:862 | 及其中的内容 | 保留 | 及其中的内容 | P1 |
| ui/WorkspaceFilesPage.kt:864 | 名称 | 保留 | 名称 | P1 |
| ui/WorkspaceFilesPage.kt:864 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:865 | 新建文本文件 | 保留 | 新建文本文件 | P1 |
| ui/WorkspaceFilesPage.kt:865 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:866 | UTF-8 文本 · 最多 256 KiB | 清晰度 | 文本文件，最多 256 KiB。 | P1 |
| ui/WorkspaceFilesPage.kt:868 | 保存前会检查原文件是否被修改，避免覆盖他人的更改。 | 保留 | 保存前会检查原文件是否被修改，避免覆盖他人的更改。 | P0 |
| ui/WorkspaceFilesPage.kt:868 | 同名文件不会被覆盖。 | 保留 | 同名文件不会被覆盖。 | P0 |
| ui/WorkspaceFilesPage.kt:868 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:872 | 取消 | 保留 | 取消 | P0 |
| ui/WorkspaceFilesPage.kt:873 | 删除 | 保留 | 删除 | P0 |
| ui/WorkspaceFilesPage.kt:873 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:875 | 新建文件夹 | 保留 | 新建文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:876 | 新建文本文件 | 保留 | 新建文本文件 | P1 |
| ui/WorkspaceFilesPage.kt:877 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:878 | 重命名 | 保留 | 重命名 | P1 |
| ui/WorkspaceFilesPage.kt:879 | 删除 | 保留 | 删除 | P0 |
| ui/WorkspaceFilesPage.kt:885 | 已删除 $completed 项；后续删除未完成，请刷新检查。${e.message.orEmpty()} | 清晰度 | 已删除 {数量} 项，部分删除失败。请刷新检查。 | P0 |
| ui/WorkspaceFilesPage.kt:889 | 已保存 | 保留 | 已保存 | P1 |
| ui/WorkspaceFilesPage.kt:889 | 操作完成 | 保留 | 操作完成 | P1 |
| ui/WorkspaceFilesPage.kt:889 | 编辑文本 | 保留 | 编辑文本 | P1 |
| ui/WorkspaceFilesPage.kt:890 | 保存 | 保留 | 保存 | P1 |
| ui/WorkspaceFilesPage.kt:890 | 删除 | 保留 | 删除 | P0 |
| ui/WorkspaceFilesPage.kt:890 | 处理中… | 保留 | 处理中… | P1 |
| ui/WorkspaceFilesPage.kt:890 | 确认删除 | 清晰度 | 删除文件 | P0 |
| ui/WorkspaceFilesPage.kt:897 | 移动需要手机存储写权限 | 保留 | 移动需要手机存储写权限 | P0 |
| ui/WorkspaceFilesPage.kt:898 | 移动会在工作区副本复制并校验成功后删除手机源文件。请先授予系统写权限；这不会授权 DSH 修改手机文件。 | 术语不一致、长度 | 移动后会删除手机源文件，请先授权手机文件写入。<br>这不会授予助手写入权限。 | P0 |
| ui/WorkspaceFilesPage.kt:899 | 取消 | 保留 | 取消 | P0 |
| ui/WorkspaceFilesPage.kt:900 | 授权手机存储写入 | 清晰度 | 授权文件写入 | P0 |
| ui/WorkspaceFilesPage.kt:903 | 复制 | 保留 | 复制 | P1 |
| ui/WorkspaceFilesPage.kt:903 | 移动 | 保留 | 移动 | P1 |
| ui/WorkspaceFilesPage.kt:907 | 工作区 | 保留 | 工作区 | P1 |
| ui/WorkspaceFilesPage.kt:926 | 放弃未保存的内容？ | 保留 | 放弃未保存的内容？ | P1 |
| ui/WorkspaceFilesPage.kt:927 | 原文件没有改变，当前草稿将丢失。 | 保留 | 原文件没有改变，当前草稿将丢失。 | P0 |
| ui/WorkspaceFilesPage.kt:927 | 继续编辑 | 保留 | 继续编辑 | P1 |
| ui/WorkspaceFilesPage.kt:928 | 放弃草稿 | 保留 | 放弃草稿 | P1 |
| ui/WorkspaceFilesPage.kt:961 | 文件 | 保留 | 文件 | P1 |
| ui/WorkspaceFilesPage.kt:962 | 正在读取当前文件夹，请稍候。 | 保留 | 正在读取当前文件夹，请稍候。 | P1 |
| ui/WorkspaceFilesPage.kt:967 | 文件 | 保留 | 文件 | P1 |
| ui/WorkspaceFilesPage.kt:968 | 文件列表加载失败 | 保留 | 文件列表加载失败 | P0 |
| ui/WorkspaceFilesPage.kt:969 | 请检查文件是否仍存在、空间是否充足，然后重试。 | 保留 | 请检查文件是否仍存在、空间是否充足，然后重试。 | P1 |
| ui/WorkspaceFilesPage.kt:970 | 重试 | 保留 | 重试 | P1 |
| ui/WorkspaceFilesPage.kt:988 | 正在导入文件 | 保留 | 正在导入文件 | P1 |
| ui/WorkspaceFilesPage.kt:989 | 文件正在复制到 /workspaces，完成后会刷新文件列表。 | 清晰度 | 文件正在复制到工作区，完成后会自动显示。 | P1 |
| ui/WorkspaceFilesPage.kt:1002 | 文件 | 保留 | 文件 | P1 |
| ui/WorkspaceFilesPage.kt:1005 | 导入文件会按源文件名创建新项目，不覆盖已有项目。 | 清晰度 | 导入后会创建新项目，保留已有项目。 | P0 |
| ui/WorkspaceFilesPage.kt:1038 | $phase · 已用 $elapsed 秒 | 清晰度 | {内容} · 已用 {耗时} 秒 | P1 |
| ui/WorkspaceFilesPage.kt:1129 | 权限与写入 | 保留 | 权限与写入 | P0 |
| ui/WorkspaceFilesPage.kt:1130 | 关闭权限说明 | 保留 | 关闭权限说明 | P0 |
| ui/WorkspaceFilesPage.kt:1140 | 当前：${state.label} | 清晰度 | 当前：{名称} | P1 |
| ui/WorkspaceFilesPage.kt:1147 | 授权访问手机文件 | 清晰度 | 授权文件访问 | P0 |
| ui/WorkspaceFilesPage.kt:1153 | 授权本次 DSH 写入 | 术语不一致 | 允许本次写入 | P0 |
| ui/WorkspaceFilesPage.kt:1160 | 撤销 DSH 写入权限 | 术语不一致 | 撤销本次写入 | P0 |
| ui/WorkspaceFilesPage.kt:1168 | 授权只在本次 DSH 运行期间有效；停止 DSH 后自动恢复只读。 | 术语不一致 | 授权只对本次助手运行有效，停止后恢复只读。 | P0 |
| ui/WorkspaceFilesPage.kt:1173 | 文件页对手机存储只读；如需放入工作区，请使用条目的「复制到工作区」或「移动到工作区」。 | 长度 | 可查看手机文件。要放入工作区，请选择复制或移动。 | P1 |
| ui/WorkspaceFilesPage.kt:1179 | 停止 DSH 后，工作区写入会自动恢复；正在运行的会话也会结束。 | 术语不一致 | 停止助手后可修改工作区文件，当前任务会中断。 | P1 |
| ui/WorkspaceFilesPage.kt:1183 | 停止 DSH | 术语不一致 | 停止 助手 | P1 |
| ui/WorkspaceFilesPage.kt:1190 | 手机存储的写入授权在该位置的权限 chip 中单独管理。 | 清晰度 | 点当前位置的权限标记，管理手机文件写入权限。 | P0 |
| ui/WorkspaceFilesPage.kt:1217 | 文件传输$label，共 ${summary.count} 项，点击查看详情 | 清晰度 | 文件传输{名称}，共 {数量} 项，点击查看详情 | P1 |
| ui/WorkspaceFilesPage.kt:1234 | · ${summary.count} | 清晰度 | · {数量} | P1 |
| ui/WorkspaceFilesPage.kt:1239 | 取消全部文件传输 | 清晰度 | 取消全部 | P0 |
| ui/WorkspaceFilesPage.kt:1239 | 取消文件传输 | 清晰度 | 取消传输 | P0 |
| ui/WorkspaceFilesPage.kt:1269 | 传输进度 | 保留 | 传输进度 | P1 |
| ui/WorkspaceFilesPage.kt:1270 | 关闭传输详情 | 保留 | 关闭传输详情 | P1 |
| ui/WorkspaceFilesPage.kt:1274 | 总进度 ${summary.percent}% · ${summary.count} 个文件任务 | 清晰度 | 总进度 {进度}% · {数量} 个文件任务 | P1 |
| ui/WorkspaceFilesPage.kt:1275 | ${summary.count} 个文件任务 · 总大小未知 | 被动/实现说明 | 正在传输 {数量} 项 | P1 |
| ui/WorkspaceFilesPage.kt:1285 | 进度按实际复制或下载的字节计算；总大小未知时显示不定进度，不估造百分比。 | 重复/啰嗦、工程痕迹 | 删除此处说明；保留各文件当前动作和实际传输量。 | P1 |
| ui/WorkspaceFilesPage.kt:1300 | ${item.label}的传输进度 | 清晰度 | {名称}的传输进度 | P1 |
| ui/WorkspaceFilesPage.kt:1301 | 已完成 $it% | 清晰度 | 已完成 {内容}% | P1 |
| ui/WorkspaceFilesPage.kt:1301 | 总大小未知，进度无法估算 | 被动/实现说明 | 正在传输 | P0 |
| ui/WorkspaceFilesPage.kt:1307 | 进行中 | 保留 | 进行中 | P1 |
| ui/WorkspaceFilesPage.kt:1320 | 取消此文件 | 保留 | 取消此文件 | P0 |
| ui/WorkspaceFilesPage.kt:1332 | 文件夹 | 保留 | 文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:1332 | 链接 ·  | 清晰度 | 链接 · | P1 |
| ui/WorkspaceFilesPage.kt:1356 | 无法打开此目录，请返回上一级 | 保留 | 无法打开此目录，请返回上一级 | P0 |
| ui/WorkspaceFilesPage.kt:1359 | ${operation}到$destinationLabel | 清晰度 | {内容}到{名称} | P1 |
| ui/WorkspaceFilesPage.kt:1363 | 工作区 / $current | 清晰度 | 工作区 / {内容} | P1 |
| ui/WorkspaceFilesPage.kt:1363 | 工作区 / 根目录 | 保留 | 工作区 / 根目录 | P1 |
| ui/WorkspaceFilesPage.kt:1366 | 返回上一级 | 保留 | 返回上一级 | P1 |
| ui/WorkspaceFilesPage.kt:1375 | 没有可进入的子文件夹 | 清晰度 | 没有子文件夹，可选择当前位置或返回上一级。 | P0 |
| ui/WorkspaceFilesPage.kt:1378 | 复制并校验成功后删除手机源文件；同名文件会先询问如何处理。 | 保留 | 复制并校验成功后删除手机源文件；同名文件会先询问如何处理。 | P0 |
| ui/WorkspaceFilesPage.kt:1378 | 移动 | 保留 | 移动 | P1 |
| ui/WorkspaceFilesPage.kt:1379 | 同名文件会先询问保留两者、替换或跳过。 | 保留 | 同名文件会先询问保留两者、替换或跳过。 | P1 |
| ui/WorkspaceFilesPage.kt:1380 | 选择当前位置；同名条目不会覆盖。 | 保留 | 选择当前位置；同名条目不会覆盖。 | P0 |
| ui/WorkspaceFilesPage.kt:1382 | 取消 | 保留 | 取消 | P0 |
| ui/WorkspaceFilesPage.kt:1383 | ${operation}到这里 | 清晰度 | {操作}到这里 | P1 |
| ui/WorkspaceFilesPage.kt:1383 | 处理中… | 保留 | 处理中… | P1 |
| ui/WorkspaceFilesPage.kt:1399 | 保留两者（新文件夹自动改名） | 清晰度 | 保留两者 | P1 |
| ui/WorkspaceFilesPage.kt:1400 | 跳过 | 保留 | 跳过 | P1 |
| ui/WorkspaceFilesPage.kt:1402 | 保留两者（新文件自动改名） | 清晰度 | 保留两者 | P1 |
| ui/WorkspaceFilesPage.kt:1403 | 替换同名文件 | 清晰度 | 替换文件 | P1 |
| ui/WorkspaceFilesPage.kt:1404 | 跳过 | 保留 | 跳过 | P1 |
| ui/WorkspaceFilesPage.kt:1408 | 已有同名${if (conflict.directory) "文件夹" else "文件"} | 清晰度 | 已有同名{内容} | P1 |
| ui/WorkspaceFilesPage.kt:1408 | 文件 | 保留 | 文件 | P1 |
| ui/WorkspaceFilesPage.kt:1408 | 文件夹 | 保留 | 文件夹 | P1 |
| ui/WorkspaceFilesPage.kt:1412 | 工作区中已存在「${conflict.name}」。 | 清晰度 | 工作区中已存在「{名称}」。 | P1 |
| ui/WorkspaceFilesPage.kt:1413 | 还有 ${remaining - 1} 个冲突。 | 清晰度 | 还有 {时间} 个冲突。 | P1 |
| ui/WorkspaceFilesPage.kt:1428 | 只有同类型的普通文件可替换；文件夹或链接请选择保留两者或跳过。 | 清晰度 | 文件夹和链接无法直接替换，请选择“保留两者”或“跳过”。 | P1 |
| ui/WorkspaceFilesPage.kt:1436 | 全部应用到这个批次 | 清晰度 | 应用到同批次 | P1 |
| ui/WorkspaceFilesPage.kt:1440 | 取消 | 保留 | 取消 | P0 |
| ui/WorkspaceFilesPage.kt:1442 | 继续 | 保留 | 继续 | P1 |
| ui/WorkspaceFilesPage.kt:1461 | ${outcome.action}结果 | 清晰度 | {内容}结果 | P1 |
| ui/WorkspaceFilesPage.kt:1468 | 目标：${outcome.target} | 清晰度 | 目标：{内容} | P1 |
| ui/WorkspaceFilesPage.kt:1472 | ${group.reason.title}（${group.failures.size} 个） | 清晰度 | {名称}（{数量} 个） | P1 |
| ui/WorkspaceFilesPage.kt:1474 | 同名文件已跳过，原文件没有被覆盖。 | 保留 | 同名文件已跳过，原文件没有被覆盖。 | P0 |
| ui/WorkspaceFilesPage.kt:1476 | 清理空间后可以重新选择这些文件。 | 保留 | 清理空间后可以重新选择这些文件。 | P1 |
| ui/WorkspaceFilesPage.kt:1478 | 授权后再试；原文件保持未处理。 | 保留 | 授权后再试；原文件保持未处理。 | P0 |
| ui/WorkspaceFilesPage.kt:1480 | 下面是系统返回的具体原因。 | 清晰度 | 可展开详情查看具体原因。 | P1 |
| ui/WorkspaceFilesPage.kt:1486 | · ${failure.name} | 清晰度 | · {名称} | P1 |
| ui/WorkspaceFilesPage.kt:1490 | …另有 ${group.failures.size - 12} 个 | 清晰度 | …另有 {数量} 个 | P1 |
| ui/WorkspaceFilesPage.kt:1498 | 关闭 | 保留 | 关闭 | P1 |

### [ui/WorkspacePdfPreview.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/WorkspacePdfPreview.kt:128 | 此 PDF 已关闭，请重新打开。 | 保留 | 此 PDF 已关闭，请重新打开。 | P1 |
| ui/WorkspacePdfPreview.kt:144 | 内存不足，无法渲染第 ${index + 1} 页；请关闭其他应用后重试。 | 工程痕迹 | 运行内存不足，无法显示这一页。请关闭闲置应用后重试。 | P0 |
| ui/WorkspacePdfPreview.kt:189 | 内存不足，无法打开此 PDF；请关闭其他应用后重试。 | 清晰度 | 运行内存不足，无法打开文件。请关闭闲置应用后重试。 | P0 |
| ui/WorkspacePdfPreview.kt:197 | 此 PDF 没有可显示的页面。 | 清晰度 | 这个 PDF 没有页面，请检查原文件。 | P0 |
| ui/WorkspacePdfPreview.kt:210 | 此 PDF 已加密（需要密码），暂不支持内置预览；可使用其他应用打开。 | 被动/实现说明 | 此 PDF 需要密码，请用其他应用打开。 | P1 |
| ui/WorkspacePdfPreview.kt:212 | 此 PDF 没有可显示的页面。 | 清晰度 | 这个 PDF 没有页面，请检查原文件。 | P0 |
| ui/WorkspacePdfPreview.kt:214 | 内存不足，无法打开此 PDF；请关闭其他应用后重试。 | 清晰度 | 运行内存不足，无法打开文件。请关闭闲置应用后重试。 | P0 |
| ui/WorkspacePdfPreview.kt:215 | 此 PDF 无法解析，文件可能已损坏或格式不受支持；可使用其他应用打开。 | 清晰度 | 此 PDF 无法读取，请用其他应用打开或重新导出。 | P0 |
| ui/WorkspacePdfPreview.kt:272 | 此 PDF 为 ${pdfBytesLabel(info.bytes)}，超过内置预览上限  | 清晰度 | 此 PDF 超过预览大小上限。 | P1 |
| ui/WorkspacePdfPreview.kt:273 | ${pdfBytesLabel(PdfOpenPolicy.MAX_FILE_BYTES)}；请使用其他应用打开。 | 清晰度 | {大小}；请使用其他应用打开。 | P1 |
| ui/WorkspacePdfPreview.kt:330 | 此 PDF 没有可显示的页面。 | 清晰度 | 这个 PDF 没有页面，请检查原文件。 | P0 |
| ui/WorkspacePdfPreview.kt:468 | 内存不足，无法渲染第 ${index + 1} 页；请关闭其他应用后重试。 | 工程痕迹 | 运行内存不足，无法显示这一页。请关闭闲置应用后重试。 | P0 |
| ui/WorkspacePdfPreview.kt:470 | 第 ${index + 1} 页无法渲染。 | 工程痕迹 | 这一页无法显示，请重试或用其他应用打开。 | P0 |
| ui/WorkspacePdfPreview.kt:472 | 第 ${index + 1} 页无法渲染：文件可能已损坏。 | 工程痕迹 | 这一页无法读取，请重试或用其他应用打开。 | P0 |
| ui/WorkspacePdfPreview.kt:571 | 跳到页面 | 保留 | 跳到页面 | P1 |
| ui/WorkspacePdfPreview.kt:580 | 页码（1–$pageCount） | 清晰度 | 页码（1–{数量}） | P1 |
| ui/WorkspacePdfPreview.kt:596 | 跳转 | 保留 | 跳转 | P1 |
| ui/WorkspacePdfPreview.kt:599 | 取消 | 保留 | 取消 | P0 |
| ui/WorkspacePdfPreview.kt:632 | PDF 第 ${index + 1} 页 | 清晰度 | PDF 第 {内容} 页 | P1 |
| ui/WorkspacePdfPreview.kt:642 | 第 ${index + 1} 页无法渲染 | 工程痕迹 | 这一页无法显示 | P0 |
| ui/WorkspacePdfPreview.kt:652 | 重试 | 保留 | 重试 | P1 |
| ui/WorkspacePdfPreview.kt:660 | 正在渲染第 ${index + 1} 页 | 工程痕迹 | 正在显示第 {页码} 页 | P1 |
| ui/WorkspacePdfPreview.kt:690 | 适应宽度 | 保留 | 适应宽度 | P1 |
| ui/WorkspacePdfPreview.kt:695 | 适应页面 | 保留 | 适应页面 | P1 |
| ui/WorkspacePdfPreview.kt:699 | 还原 1× | 保留 | 还原 1× | P1 |
| ui/WorkspacePdfPreview.kt:821 | 无法打开此 PDF。 | 保留 | 无法打开此 PDF。 | P0 |
| ui/WorkspacePdfPreview.kt:822 | 此 PDF 已加密（需要密码），暂不支持内置预览；可使用其他应用打开。 | 被动/实现说明 | 此 PDF 需要密码，请用其他应用打开。 | P1 |
| ui/WorkspacePdfPreview.kt:825 | 此 PDF 无法解析，文件可能已损坏；可使用其他应用打开。 | 清晰度 | 此 PDF 无法读取，请用其他应用打开或重新导出。 | P0 |

### [ui/WorkspaceStorageControls.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceStorageControls.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/WorkspaceStorageControls.kt:47 | 项目空间 | 保留 | 项目空间 | P1 |
| ui/WorkspaceStorageControls.kt:49 | 刷新项目用量 | 保留 | 刷新项目用量 | P1 |
| ui/WorkspaceStorageControls.kt:54 | 统计未完成 | 保留 | 统计未完成 | P1 |
| ui/WorkspaceStorageControls.kt:56 | 尚未统计 | 保留 | 尚未统计 | P1 |
| ui/WorkspaceStorageControls.kt:58 | 跟随可用空间 | 清晰度 | 使用可用空间 | P1 |
| ui/WorkspaceStorageControls.kt:58 | 项目已用 · 软上限 ${storage.limitBytes?.let(::storageSize) ?: "跟随可用空间"} | 清晰度 | 工作区已用，限额：{限额} | P1 |
| ui/WorkspaceStorageControls.kt:61 | 可继续使用 | 保留 | 可继续使用 | P1 |
| ui/WorkspaceStorageControls.kt:61 | 待确认 | 保留 | 待确认 | P1 |
| ui/WorkspaceStorageControls.kt:62 | 磁盘剩余 | 清晰度 | 手机可用空间 | P1 |
| ui/WorkspaceStorageControls.kt:65 | 项目容量预算使用进度 | 术语不一致 | 工作区空间使用情况 | P1 |
| ui/WorkspaceStorageControls.kt:67 | 已超出预算；请调高预算或改为跟随可用空间后再启动。 | 术语不一致 | 已超出限额，请调高限额或选择“使用可用空间”。 | P1 |
| ui/WorkspaceStorageControls.kt:71 | 正在统计项目实际占用… | 保留 | 正在统计项目实际占用… | P1 |
| ui/WorkspaceStorageControls.kt:72 | 最近统计 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(storage.measuredAtMillis))} | 长度 | 最近统计 {内容} | P1 |
| ui/WorkspaceStorageControls.kt:73 | 刷新后显示实际占用，统计不会读取文件内容。 | 保留 | 刷新后显示实际占用，统计不会读取文件内容。 | P1 |
| ui/WorkspaceStorageControls.kt:77 | 仅计算项目文件，不包含运行组件和会话；预算不会预占磁盘或删除文件。 | 术语不一致 | 仅计算项目文件，不包含运行组件和对话。设置限额不会预占空间。 | P0 |
| ui/WorkspaceStorageControls.kt:82 | 调整容量预算 | 术语不一致 | 调整空间限额 | P1 |
| ui/WorkspaceStorageControls.kt:83 | 停止 DSH 并完成当前操作后可调整。 | 术语不一致 | 停止 助手 并完成当前操作后可调整。 | P1 |
| ui/WorkspaceStorageControls.kt:85 | 跟随可用空间 | 清晰度 | 使用可用空间 | P1 |
| ui/WorkspaceStorageControls.kt:86 | 自定容量上限 | 保留 | 自定容量上限 | P1 |
| ui/WorkspaceStorageControls.kt:97 | 容量预算（GiB） | 术语不一致 | 空间限额 | P1 |
| ui/WorkspaceStorageControls.kt:99 | 1 GiB = 1024 MiB，至少 0.0625 GiB；不能小于已用空间。 | 清晰度 | 至少 64 MiB，不能小于已用空间。容量换算见详情。 | P1 |
| ui/WorkspaceStorageControls.kt:101 | 当前最多可设 ${storageSize(WorkspaceCapacity.availableForWorkspace(storage.usedBytes!!, storage.freeBytes))}，包含现有项目。 | 长度 | 当前最多可设 {大小}，包含现有项目。 | P1 |
| ui/WorkspaceStorageControls.kt:104 | 当前用量尚未确认，可刷新后设置自定预算，或先选“跟随可用空间”。 | 术语不一致 | 请先刷新用量，再设置限额，或选择“使用可用空间”。 | P1 |
| ui/WorkspaceStorageControls.kt:106 | 这是软上限：导入及启动前检查，运行中定期检查。发现超限后会停止 DSH，可能中断任务；直接写入可能在检查前超额。 | 术语不一致、长度 | 超过限额时会停止助手，当前任务可能中断。<br>限额定期检查，文件写入可能暂时超过限额。 | P0 |
| ui/WorkspaceStorageControls.kt:112 | 取消调整 | 保留 | 取消调整 | P0 |
| ui/WorkspaceStorageControls.kt:122 | 保存失败，请重试 | 保留 | 保存失败，请重试 | P0 |
| ui/WorkspaceStorageControls.kt:127 | 保存预算 | 术语不一致 | 保存限额 | P1 |
| ui/WorkspaceStorageControls.kt:127 | 正在保存… | 保留 | 正在保存… | P1 |

### [ui/WorkspaceVideoPlayer.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceVideoPlayer.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/WorkspaceVideoPlayer.kt:72 | 暂时无法跳转，请稍后重试。 | 保留 | 暂时无法跳转，请稍后重试。 | P0 |
| ui/WorkspaceVideoPlayer.kt:94 | 此设备或视频不支持该倍速，保留原倍速选择。 | 清晰度 | 此视频无法使用所选倍速，已保留原倍速。 | P1 |
| ui/WorkspaceVideoPlayer.kt:191 | 暂时无法恢复所选倍速，请重新选择后重试。 | 保留 | 暂时无法恢复所选倍速，请重新选择后重试。 | P0 |
| ui/WorkspaceVideoPlayer.kt:200 | 视频封面 | 保留 | 视频封面 | P1 |
| ui/WorkspaceVideoPlayer.kt:203 | 正在读取视频封面… | 保留 | 正在读取视频封面… | P1 |
| ui/WorkspaceVideoPlayer.kt:208 | 设备无法解码此视频，请使用其他应用打开。 | 清晰度 | 此视频无法播放，请用其他应用打开。 | P0 |
| ui/WorkspaceVideoPlayer.kt:211 | 正在准备视频… | 保留 | 正在准备视频… | P1 |
| ui/WorkspaceVideoPlayer.kt:211 | 正在跳转… | 保留 | 正在跳转… | P1 |
| ui/WorkspaceVideoPlayer.kt:211 | 视频缓冲中… | 保留 | 视频缓冲中… | P1 |
| ui/WorkspaceVideoPlayer.kt:213 | 无法读取封面，仍可尝试播放视频。 | 保留 | 无法读取封面，仍可尝试播放视频。 | P0 |
| ui/WorkspaceVideoPlayer.kt:220 | 位置${position}毫秒，共${duration}毫秒 | 清晰度 | 已播放 {播放时间}，共 {总时长} | P1 |
| ui/WorkspaceVideoPlayer.kt:220 | 视频进度 | 保留 | 视频进度 | P1 |
| ui/WorkspaceVideoPlayer.kt:227 | 后退10秒 | 保留 | 后退10秒 | P1 |
| ui/WorkspaceVideoPlayer.kt:243 | 暂时无法播放，请稍后重试。 | 保留 | 暂时无法播放，请稍后重试。 | P0 |
| ui/WorkspaceVideoPlayer.kt:245 | 播放视频 | 保留 | 播放视频 | P1 |
| ui/WorkspaceVideoPlayer.kt:245 | 暂停视频 | 保留 | 暂停视频 | P1 |
| ui/WorkspaceVideoPlayer.kt:246 | 前进10秒 | 保留 | 前进10秒 | P1 |
| ui/WorkspaceVideoPlayer.kt:250 | 倍速 ${videoSpeed(speed)} | 清晰度 | 倍速 {内容} | P1 |
| ui/WorkspaceVideoPlayer.kt:259 | 视频全屏 | 保留 | 视频全屏 | P1 |
| ui/WorkspaceVideoPlayer.kt:259 | 退出视频全屏 | 保留 | 退出视频全屏 | P1 |

### [ui/WorkspaceXlsxPreview.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/WorkspaceXlsxPreview.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/WorkspaceXlsxPreview.kt:79 | 无法读取此 XLSX，文件可能已加密或损坏。 | 清晰度 | 表格无法读取，请用其他应用打开或重新导出。 | P0 |
| ui/WorkspaceXlsxPreview.kt:89 | 内置预览只读取工作簿内容，不改动原文件；可用其他应用打开完整表格。 | 清晰度 | 当前为只读预览，原文件保持不变。可用其他应用查看完整表格。 | P1 |
| ui/WorkspaceXlsxPreview.kt:98 | 此工作簿没有可显示的工作表。 | 清晰度 | 这个文件没有工作表，请检查原文件。 | P0 |
| ui/WorkspaceXlsxPreview.kt:112 | ${index + 1}. ${item.name} | 清晰度 | {内容}. {名称} | P1 |
| ui/WorkspaceXlsxPreview.kt:123 | 缩小 | 保留 | 缩小 | P1 |
| ui/WorkspaceXlsxPreview.kt:127 | 放大 | 保留 | 放大 | P1 |
| ui/WorkspaceXlsxPreview.kt:129 | 重置 | 保留 | 重置 | P1 |
| ui/WorkspaceXlsxPreview.kt:131 | ${sheet.rows.size} 行 · ${sheet.columnCount} 列 | 清晰度 | {数量} 行 · {数量} 列 | P1 |
| ui/WorkspaceXlsxPreview.kt:144 | 只读原生预览：支持多工作表、冻结窗格、合并单元格、基础字体/填充/边框和公式缓存值； | 工程痕迹、术语不一致、长度 | 可查看工作表、常用样式和已保存的计算结果。 | P1 |
| ui/WorkspaceXlsxPreview.kt:145 | 公式不重新计算，图表、宏、透视表、外部链接、图片和复杂条件格式不显示，长文本以省略号显示。 | 长度 | 预览不重新计算公式，部分图表、图片和复杂格式无法显示。<br>请用其他应用查看完整表格。 | P1 |
| ui/WorkspaceXlsxPreview.kt:152 | 此工作表超过内置预览上限，仅显示前 ${sheet.rows.size} 行 / ${sheet.columnCount} 列；原文件未被修改。 | 长度 | 表格较大，仅显示部分行列。请用其他应用查看完整表格。 | P1 |
| ui/WorkspaceXlsxPreview.kt:160 | 部分单元格文本超过预览上限，已截断显示。 | 保留 | 部分单元格文本超过预览上限，已截断显示。 | P1 |
| ui/WorkspaceXlsxPreview.kt:170 | 第 ${safeIndex + 1} / ${book.sheets.size} 张工作表 · 只读 | 清晰度 | 第 {内容} / {数量} 张工作表 · 只读 | P1 |
| ui/WorkspaceXlsxPreview.kt:171 |  · 1904 日期系统 | 清晰度 | 使用文件中的日期设置 | P1 |
| ui/WorkspaceXlsxPreview.kt:247 | 此工作表没有单元格数据。 | 清晰度 | 工作表还没有数据，请切换其他工作表。 | P0 |
| ui/WorkspaceXlsxPreview.kt:281 | ${row.index + 1} | 清晰度 | {内容} | P1 |

### [ui/design/TanukiColorMath.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiColorMath.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/design/TanukiColorMath.kt:22 | sRGB 通道必须在 0..255：($red, $green, $blue) | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiColorMath.kt:28 | alpha 必须在 0..255：$alpha | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiColorMath.kt:55 | OKLCH 明度必须在 0..1：$lightness | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiColorMath.kt:56 | OKLCH 彩度必须是非负有限值：$chroma | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiColorMath.kt:57 | OKLCH 色相必须是有限值：$hueDegrees | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiColorMath.kt:90 | alpha 必须在 0..1：$alpha | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/design/TanukiComponents.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiComponents.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/design/TanukiComponents.kt:90 | 按钮按压 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiComponents.kt:119 | 处理中 | 保留 | 处理中 | P1 |
| ui/design/TanukiComponents.kt:128 | … | 保留 | … | P1 |
| ui/design/TanukiComponents.kt:211 | 状态：${tone.label()} | 清晰度 | 状态：{当前任务状态} | P1 |
| ui/design/TanukiComponents.kt:224 | 状态点 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiComponents.kt:235 | 状态点呼吸 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiComponents.kt:253 | 状态颜色 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiComponents.kt:319 | 按压反馈 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiComponents.kt:361 | 正在输出 | 清晰度 | 正在回复 | P1 |
| ui/design/TanukiComponents.kt:374 | 流式光标 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiComponents.kt:385 | 流式光标闪烁 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiComponents.kt:427 | 中性 | 保留 | 中性 | P1 |
| ui/design/TanukiComponents.kt:428 | 成功 | 清晰度 | 已完成 | P1 |
| ui/design/TanukiComponents.kt:429 | 警告 | 保留 | 警告 | P1 |
| ui/design/TanukiComponents.kt:430 | 危险 | 保留 | 危险 | P1 |
| ui/design/TanukiComponents.kt:431 | 信息 | 保留 | 信息 | P1 |

### [ui/design/TanukiDesignTheme.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiDesignTheme.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/design/TanukiDesignTheme.kt:108 | TanukiDesignTheme 未包裹当前组合，无法读取设计令牌 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [ui/design/TanukiPalette.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/ui/design/TanukiPalette.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| ui/design/TanukiPalette.kt:19 | 色阶档位越界：$stop | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:43 | 色阶必须覆盖全部明度档位：${family.name} | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:292 | 正文/画布 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:293 | 正文/表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:294 | 正文/抬升表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:295 | 正文/弹层 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:296 | 次级正文/画布 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:297 | 次级正文/表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:298 | 次级正文/凹陷表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:299 | 三级正文/画布 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:300 | 三级正文/表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:301 | 三级正文/凹陷表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:302 | 停用正文/画布 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:303 | 停用正文/表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:304 | 强边框图标/表面 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:305 | 状态点/画布 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:312 | 玻璃 chrome 正文/画布 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:313 | 玻璃 chrome 次级正文/画布 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:315 | 主操作正文/主色 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:316 | 主容器正文/主容器 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:317 | 成功正文/成功色 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:318 | 成功容器正文/成功容器 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:319 | 警告正文/警告色 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:320 | 警告容器正文/警告容器 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:321 | 危险正文/危险色 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:322 | 危险容器正文/危险容器 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:323 | 信息正文/信息色 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:324 | 信息容器正文/信息容器 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:325 | 中性状态正文/中性状态色 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |
| ui/design/TanukiPalette.kt:326 | 中性容器正文/中性容器 | 非展示：内部匹配/校验/调试标记 | 保留内部常量，不作为用户文案显示。 | P2 |

### [update/ApkInstallAccess.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/ApkInstallAccess.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/ApkInstallAccess.kt:37 | 只允许共享已校验的应用更新缓存 | 保留 | 只允许共享已校验的应用更新缓存 | P0 |
| update/ApkInstallAccess.kt:38 | 安装器读取授权必须有界 | 保留 | 安装器读取授权必须有界 | P0 |
| update/ApkInstallAccess.kt:51 | 没有可用的系统APK安装器 | 清晰度 | 找不到系统安装界面，请检查系统设置后重试。 | P0 |
| update/ApkInstallAccess.kt:60 | 无法记录安装器授权期限 | 保留 | 无法记录安装器授权期限 | P0 |
| update/ApkInstallAccess.kt:105 | 无法清理安装器授权记录 | 保留 | 无法清理安装器授权记录 | P0 |

### [update/AppReleaseManifest.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppReleaseManifest.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/AppReleaseManifest.kt:12 | GitHub 未返回有效的公开发布 Feed | 清晰度 | 更新列表暂不可用，请稍后重试。 | P0 |
| update/AppReleaseManifest.kt:25 | 公开发布 tag 无效 | 清晰度 | 公开发布 tag 无法使用 | P0 |
| update/AppReleaseManifest.kt:27 | 应用更新清单条目数量无效 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:33 | 发布附件地址无效 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:40 | 应用更新清单版本不支持 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:42 | 应用更新清单条目数量无效 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:45 | 应用更新清单存在重复包名 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:50 | Release 附件数量超过限制 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:52 | 更新清单对应的 APK 附件不存在或重复 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:55 | APK 附件大小与清单不符 | 保留 | APK 附件大小与清单不符 | P0 |
| update/AppReleaseManifest.kt:57 | 更新 Android 要求无效 | 清晰度 | 更新 Android 要求无法使用 | P0 |
| update/AppReleaseManifest.kt:65 | 更新清单的数字字段无效 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/AppReleaseManifest.kt:66 | 更新清单的数字超出范围 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |

### [update/AppUpdateFailure.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppUpdateFailure.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/AppUpdateFailure.kt:20 | Tanuki 应用更新 | 保留 | Tanuki 应用更新 | P0 |

### [update/AppUpdateFiles.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppUpdateFiles.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/AppUpdateFiles.kt:12 | 更新缓存目录不能是链接 | 保留 | 更新缓存目录不能是链接 | P0 |

### [update/AppUpdatePolicy.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/AppUpdatePolicy.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/AppUpdatePolicy.kt:28 | 更新包名不同，不能覆盖当前应用 | 清晰度 | 此安装包无法更新当前应用，请选择匹配的更新。 | P0 |
| update/AppUpdatePolicy.kt:29 | 更新版本必须高于当前安装 | 清晰度 | 此更新不是更新的版本，请检查更新列表。 | P0 |
| update/AppUpdatePolicy.kt:30 | 更新版本号格式无效 | 清晰度 | 更新版本号格式无法使用 | P0 |
| update/AppUpdatePolicy.kt:31 | 当前 Android 版本不支持此更新 | 保留 | 当前 Android 版本不支持此更新 | P0 |
| update/AppUpdatePolicy.kt:32 | 更新校验信息不完整 | 清晰度 | 更新文件信息不完整，请重新检查更新。 | P0 |
| update/AppUpdatePolicy.kt:33 | 更新签名与当前应用不同，已保留现有环境 | 清晰度 | 安装包签名不匹配，已停止更新。请使用原安装渠道的更新。 | P0 |
| update/AppUpdatePolicy.kt:34 | 更新大小未知或超过 256 MiB 限制 | 被动/实现说明 | 安装包大小异常，请重新检查更新。 | P0 |
| update/AppUpdatePolicy.kt:35 | 更新来源不是本项目的 HTTPS Release 附件 | 清晰度 | 更新来源无法确认，已停止下载。请重新检查更新。 | P0 |
| update/AppUpdatePolicy.kt:40 | APK 实际包名与发布清单不符 | 清晰度 | 安装包与更新信息不一致，请重新下载。 | P0 |
| update/AppUpdatePolicy.kt:41 | APK 实际版本与发布清单不符 | 清晰度 | 安装包版本与更新信息不一致，请重新下载。 | P0 |
| update/AppUpdatePolicy.kt:42 | APK 实际 Android 要求与清单不符 | 清晰度 | 安装包系统要求与更新信息不一致，请重新检查更新。 | P0 |
| update/AppUpdatePolicy.kt:43 | APK 实际签名与当前应用不符 | 清晰度 | 安装包签名不匹配，已停止更新。请使用原安装渠道的更新。 | P0 |

### [update/ApplicationUpdateManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/ApplicationUpdateManager.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/ApplicationUpdateManager.kt:28 | 检查本项目 GitHub Release，不上传本机环境或账号信息 | 清晰度 | 检查应用更新，不上传本机文件和账号信息。 | P0 |
| update/ApplicationUpdateManager.kt:46 | 正在检查 GitHub 发布列表 | 清晰度 | 正在检查应用更新 | P0 |
| update/ApplicationUpdateManager.kt:64 | 稳定发布未返回明确的版本地址 | 清晰度 | 无法读取更新地址，请稍后重新检查。 | P0 |
| update/ApplicationUpdateManager.kt:71 | 发现可覆盖当前应用的更新 | 清晰度 | 发现可用更新。 | P0 |
| update/ApplicationUpdateManager.kt:72 | 发布清单不完整或没有同包、同签名的兼容 APK；现有环境未改动 | 清晰度 | 没有适合当前应用的安装包，请稍后再检查。 | P0 |
| update/ApplicationUpdateManager.kt:73 | 本次检查的公开发布中没有更高版本的兼容更新 | 清晰度 | 已是最新可用版本。 | P0 |
| update/ApplicationUpdateManager.kt:74 | 暂无可用于应用内更新的 Release；发布时需附带 app-update.json | 长度 | 暂时没有可用更新，稍后可重新检查。 | P0 |
| update/ApplicationUpdateManager.kt:86 | 正在下载 Tanuki 更新 | 保留 | 正在下载 Tanuki 更新 | P0 |
| update/ApplicationUpdateManager.kt:89 | 不能创建更新缓存目录 | 保留 | 不能创建更新缓存目录 | P0 |
| update/ApplicationUpdateManager.kt:90 | 不能清理旧更新缓存 | 保留 | 不能清理旧更新缓存 | P0 |
| update/ApplicationUpdateManager.kt:102 | 正在校验 APK 包名、版本和签名证书 | 清晰度 | 正在检查安装包 | P0 |
| update/ApplicationUpdateManager.kt:105 | 校验通过；由你确认后交给 Android 安装器 | 清晰度 | 更新已准备好，请确认安装。 | P0 |
| update/ApplicationUpdateManager.kt:121 | 请先停止 DSH，并等待环境安装、更新和文件任务结束，再安装 APK | 术语不一致 | 请先停止助手，并等待环境和文件操作完成，再安装更新。 | P0 |
| update/ApplicationUpdateManager.kt:126 | 安装前再次校验更新文件 | 清晰度 | 正在检查更新文件 | P0 |
| update/ApplicationUpdateManager.kt:128 | 运行环境已变为忙碌，请停止 DSH 后重试 | 术语不一致 | 运行环境已变为忙碌，请停止 助手 后重试 | P0 |
| update/ApplicationUpdateManager.kt:132 | 正在等待 Android 安装确认；取消安装不会清除运行环境 | 清晰度 | 请在系统安装界面确认，取消后仍可继续使用当前版本。 | P0 |
| update/ApplicationUpdateManager.kt:141 | 无法打开系统安装器，请检查安装权限后重试 | 保留 | 无法打开系统安装器，请检查安装权限后重试 | P0 |
| update/ApplicationUpdateManager.kt:152 | 无法启动更新前台任务，请在应用前台重试 | 清晰度 | 更新任务无法在后台开始，请返回应用重试。 | P0 |
| update/ApplicationUpdateManager.kt:157 | 网络或校验超时，可重试；现有应用与环境未改动 | 清晰度 | 更新等待超时，请检查网络后重试。 | P0 |
| update/ApplicationUpdateManager.kt:158 | 已取消应用更新，现有环境未改动 | 清晰度 | 应用更新已取消。 | P0 |
| update/ApplicationUpdateManager.kt:159 | 检查或下载失败，请重试 | 保留 | 检查或下载失败，请重试 | P0 |
| update/ApplicationUpdateManager.kt:165 | 更新文件丢失或长度不符，请重新下载 | 保留 | 更新文件丢失或长度不符，请重新下载 | P0 |
| update/ApplicationUpdateManager.kt:166 | 更新文件 SHA-256 不符，请重新下载 | 工程痕迹 | 更新文件校验未通过，请重新下载。 | P0 |
| update/ApplicationUpdateManager.kt:168 | 无法解析更新 APK | 清晰度 | 安装包无法读取，请重新下载。 | P0 |
| update/ApplicationUpdateManager.kt:183 | APK 缺少 Android 兼容信息 | 清晰度 | 安装包信息不完整，请重新检查更新。 | P0 |
| update/ApplicationUpdateManager.kt:186 | GitHub 公开发布页暂不可访问（HTTP $status），可稍后重试 | 清晰度 | 更新服务暂不可用，请稍后重试。 | P0 |
| update/ApplicationUpdateManager.kt:194 | 更新元数据跳转至不可信地址 | 工程痕迹 | 更新地址不安全，已停止下载。请稍后重新检查更新。 | P0 |
| update/ApplicationUpdateManager.kt:205 | 更新元数据重定向过多 | 工程痕迹 | 更新地址异常，请稍后重新检查更新。 | P0 |
| update/ApplicationUpdateManager.kt:206 | 更新元数据缺少跳转地址 | 工程痕迹 | 更新地址不完整，请稍后重新检查更新。 | P0 |
| update/ApplicationUpdateManager.kt:209 | 更新元数据超过大小限制 | 工程痕迹 | 更新信息过大，请稍后重新检查更新。 | P0 |
| update/ApplicationUpdateManager.kt:216 | 更新元数据超过大小限制 | 工程痕迹 | 更新信息过大，请稍后重新检查更新。 | P0 |
| update/ApplicationUpdateManager.kt:224 | 更新元数据重定向过多 | 工程痕迹 | 更新地址异常，请稍后重新检查更新。 | P0 |

### [update/BackgroundUpdateChecks.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/BackgroundUpdateChecks.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/BackgroundUpdateChecks.kt:26 | 有更新：$latestVersion | 清晰度 | 有更新：{版本} | P0 |
| update/BackgroundUpdateChecks.kt:29 | 尚未后台检查 | 保留 | 尚未后台检查 | P0 |
| update/BackgroundUpdateChecks.kt:30 | 正在后台检查 | 保留 | 正在后台检查 | P0 |
| update/BackgroundUpdateChecks.kt:31 | 已是最新版本 | 保留 | 已是最新版本 | P0 |
| update/BackgroundUpdateChecks.kt:32 | 上次后台检查未成功 | 保留 | 上次后台检查未成功 | P0 |
| update/BackgroundUpdateChecks.kt:51 | 有更新：$latestVersion | 清晰度 | 有更新：{版本} | P0 |
| update/BackgroundUpdateChecks.kt:54 | 尚未后台检查 | 保留 | 尚未后台检查 | P0 |
| update/BackgroundUpdateChecks.kt:55 | 正在后台检查 | 保留 | 正在后台检查 | P0 |
| update/BackgroundUpdateChecks.kt:56 | 已是最新版本 | 保留 | 已是最新版本 | P0 |
| update/BackgroundUpdateChecks.kt:57 | 上次后台检查未成功 | 保留 | 上次后台检查未成功 | P0 |
| update/BackgroundUpdateChecks.kt:63 | npmmirror 镜像 | 工程痕迹、错误出口 | 安装文件信息异常，请刷新版本后重试。 | P0 |
| update/BackgroundUpdateChecks.kt:64 | npm 官方 | 工程痕迹 | 官方源 | P0 |
| update/BackgroundUpdateChecks.kt:131 | 更新清单为空 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateChecks.kt:132 | 更新清单格式无效 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateChecks.kt:134 | 更新清单版本不支持 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateChecks.kt:137 | 更新清单缺少 packages | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateChecks.kt:139 | 更新清单条目数量无效 | 错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateChecks.kt:289 | 镜像与官方源均不可用 | 保留 | 镜像与官方源均不可用 | P0 |

### [update/BackgroundUpdateRepositories.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/BackgroundUpdateRepositories.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/BackgroundUpdateRepositories.kt:40 | 更新元数据地址不可信 | 工程痕迹、错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateRepositories.kt:51 | 更新元数据重定向过多 | 工程痕迹 | 更新地址异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateRepositories.kt:52 | 更新元数据缺少跳转地址 | 工程痕迹 | 更新地址不完整，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateRepositories.kt:56 | 更新元数据 HTTP $code | 工程痕迹、错误出口 | 更新信息异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateRepositories.kt:58 | 更新元数据超过大小限制 | 工程痕迹 | 更新信息过大，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateRepositories.kt:64 | 更新元数据重定向过多 | 工程痕迹 | 更新地址异常，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateRepositories.kt:73 | 更新元数据超过大小限制 | 工程痕迹 | 更新信息过大，请稍后重新检查更新。 | P0 |
| update/BackgroundUpdateRepositories.kt:139 | GitHub 发布页不可用 | 保留 | GitHub 发布页不可用 | P0 |
| update/BackgroundUpdateRepositories.kt:248 | DSH 版本检查失败 | 术语不一致 | 助手 版本检查失败 | P0 |

### [update/UpdateManager.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/UpdateManager.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/UpdateManager.kt:36 | 获取官方 DSH 版本列表 | 术语不一致 | 正在读取助手版本 | P0 |
| update/UpdateManager.kt:41 | 官方版本检查 HTTP ${connection.responseCode} | 清晰度 | 版本服务暂不可用，请稍后重试。 | P0 |
| update/UpdateManager.kt:50 | 未声明 | 保留 | 未声明 | P0 |
| update/UpdateManager.kt:50 | 未声明 | 保留 | 未声明 | P0 |
| update/UpdateManager.kt:51 | 官方 npm 发行版；详细变更请查阅官方仓库。${if ('-' in version) "预发布版本。" else ""} | 工程痕迹 | 官方版本，更新说明可在版本详情中查看。 | P0 |
| update/UpdateManager.kt:51 | 预发布版本。 | 清晰度 | 测试版本。 | P0 |
| update/UpdateManager.kt:59 | 官方版本列表已更新 | 清晰度 | 版本列表已更新。 | P0 |
| update/UpdateManager.kt:60 | 官方 npm 版本检查完成：$candidate | 工程痕迹 | 版本列表已更新。 | P0 |
| update/UpdateManager.kt:63 | 版本检查已取消，可重新获取 | 清晰度 | 版本检查已取消，可重新检查。 | P0 |
| update/UpdateManager.kt:68 | 官方版本获取失败，可重试 | 清晰度 | 版本列表读取失败，请重试。 | P0 |
| update/UpdateManager.kt:75 | 请先检查官方版本列表 | 清晰度 | 请先读取版本列表。 | P0 |
| update/UpdateManager.kt:80 | 官方 npm 发行版 | 工程痕迹 | 官方版本 | P0 |
| update/UpdateManager.kt:80 | 未声明 | 保留 | 未声明 | P0 |
| update/UpdateManager.kt:87 | 请确认停止服务后再安装，可能中断任务 | 保留 | 请确认停止服务后再安装，可能中断任务 | P0 |
| update/UpdateManager.kt:88 | 请先处理上次更新恢复 | 保留 | 请先处理上次更新恢复 | P0 |
| update/UpdateManager.kt:91 | 请先安装 Ubuntu 和 Node.js | 保留 | 请先安装 Ubuntu 和 Node.js | P0 |
| update/UpdateManager.kt:95 | 安装元数据不完整，已保留会话；请恢复匹配元数据 | 工程痕迹、术语不一致 | 当前版本信息缺失，请恢复对应的数据备份。 | P0 |
| update/UpdateManager.kt:98 | 现有数据由 DSH $previous 使用过；安装旧版前需恢复该版本的匹配数据快照 | 术语不一致 | 使用旧版本前，需先恢复该版本对应的数据备份。 | P0 |
| update/UpdateManager.kt:99 | 现有会话缺少版本信息，已保留数据；请恢复匹配元数据 | 工程痕迹、术语不一致 | 无法确认对话对应的版本，请恢复对应的数据备份。 | P0 |
| update/UpdateManager.kt:100 | 重新安装仅用于当前版本；其他版本请使用版本安装入口 | 清晰度 | 只能重装当前版本，安装其他版本请点“选择版本”。 | P0 |
| update/UpdateManager.kt:102 | 准备 DSH ${release.version} | 术语不一致 | 正在准备助手安装 | P0 |
| update/UpdateManager.kt:106 | 补齐快照回退所需的 DSH $previous | 术语不一致 | 正在准备恢复所需版本 | P0 |
| update/UpdateManager.kt:110 | 检查 DSH ${release.version} 候选启动 | 术语不一致 | 正在检查新版本能否启动 | P0 |
| update/UpdateManager.kt:114 | 保存版本匹配的私有数据快照 | 术语不一致 | 正在备份对话和设置 | P0 |
| update/UpdateManager.kt:120 | 切换到 DSH ${release.version} | 术语不一致 | 正在切换助手版本 | P0 |
| update/UpdateManager.kt:127 | 新版本使用正式数据启动失败；请恢复匹配快照 | 术语不一致 | 新版本无法使用当前数据启动，请恢复对应的数据备份。 | P0 |
| update/UpdateManager.kt:129 | DSH ${release.version} 安装与本机启动检查完成 | 术语不一致 | 助手已安装，并通过本机启动检查。 | P0 |
| update/UpdateManager.kt:130 | DSH 安装与本机 Web 健康检查通过；官方网页需重新认证。模型和数据迁移兼容性仍需人工验收 | 术语不一致、长度 | 助手已启动，请重新连接网页，检查对话和模型能否使用。 | P0 |
| update/UpdateManager.kt:152 | 请先停止 DSH | 术语不一致 | 请先停止助手 | P0 |
| update/UpdateManager.kt:155 | 请先安装 Ubuntu 和 Node.js | 保留 | 请先安装 Ubuntu 和 Node.js | P0 |
| update/UpdateManager.kt:157 | 安装 DSH ${release.version} 备用程序 | 术语不一致 | 正在安装备用版本 | P0 |
| update/UpdateManager.kt:160 | 检查备用 DSH ${release.version} | 术语不一致 | 正在检查备用版本 | P0 |
| update/UpdateManager.kt:164 | DSH ${release.version} 备用程序已安装，未切换数据版本 | 术语不一致 | 备用版本已安装，当前版本保持不变。 | P0 |
| update/UpdateManager.kt:165 | 备用 DSH ${release.version} 安装与隔离启动检查完成；正式数据未切换 | 术语不一致 | 备用版本可以启动，当前数据保持不变。 | P0 |
| update/UpdateManager.kt:186 | 候选版本就绪前退出 | 清晰度 | 新版本启动失败，请选择其他版本。 | P0 |
| update/UpdateManager.kt:189 | 候选 Web 认证入口未就绪 | 清晰度 | 新版本未能连接，请选择其他版本。 | P0 |
| update/UpdateManager.kt:196 | 候选 DSH 输出未能结束，当前版本与数据未切换 | 术语不一致 | 新版本检查未完成，当前版本保留。请重试。 | P0 |
| update/UpdateManager.kt:209 | 没有中断的更新事务 | 工程痕迹 | 没有需要继续的更新。 | P0 |
| update/UpdateManager.kt:210 | 此事务不在切换阶段，可重新选择版本 | 工程痕迹 | 请重新选择要安装的版本。 | P0 |
| update/UpdateManager.kt:211 | 当前版本尚未切换，请恢复匹配快照或重试安装 | 术语不一致 | 当前版本尚未切换，请重试安装或恢复对应备份。 | P0 |
| update/UpdateManager.kt:212 | 请先完成中断的数据恢复 | 保留 | 请先完成中断的数据恢复 | P0 |
| update/UpdateManager.kt:216 | 当前版本未通过 Web 启动检查，恢复入口已保留 | 清晰度 | 当前版本启动检查未通过，可恢复对应的数据备份。 | P0 |
| update/UpdateManager.kt:219 | 中断更新已通过本机启动检查，保留当前版本与数据；未回滚项目 | 清晰度 | 更新已完成，当前版本和数据保留，项目文件保持不变。 | P0 |
| update/UpdateManager.kt:228 | 请先停止服务 | 保留 | 请先停止服务 | P0 |
| update/UpdateManager.kt:232 | 已恢复匹配程序和数据快照，项目目录未回滚；请启动服务验收 | 术语不一致 | 已恢复版本和数据备份，项目文件保持不变。请启动助手检查。 | P0 |
| update/UpdateManager.kt:239 | 版本元数据超过大小限制 | 工程痕迹 | 版本版本信息超过大小限制 | P0 |

### [update/UpdateNotifier.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/update/UpdateNotifier.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| update/UpdateNotifier.kt:38 | Tanuki 有可用更新 | 清晰度 | Tanuki 有新版本 | P0 |
| update/UpdateNotifier.kt:39 | 新版本 $version 已发布；可在“设置 → 应用更新”查看。不会自动下载或安装。 | 清晰度 | 新版本已发布，可在设置的“应用更新”中查看。<br>下载和安装均由你确认。 | P0 |
| update/UpdateNotifier.kt:47 | DSH 有可用更新 | 术语不一致 | 助手有新版本 | P0 |
| update/UpdateNotifier.kt:48 | 新版本 $version 已发布；可在“组件维护与诊断”查看。不会自动下载或安装。 | 清晰度 | 助手新版本已发布，可在“环境与维护”中查看。<br>下载和安装均由你确认。 | P0 |
| update/UpdateNotifier.kt:55 | 应用与引擎更新 | 术语不一致 | 应用与助手更新 | P0 |
| update/UpdateNotifier.kt:56 | Tanuki APK 与 DSH 引擎的新版本提示 | 术语不一致 | Tanuki 和助手的新版本提醒 | P0 |

### [web/BlobDownloads.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/BlobDownloads.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/BlobDownloads.kt:29 | 网页 Blob 下载超时，请重试 | 清晰度 | 网页文件下载超时，请重试。 | P0 |
| web/BlobDownloads.kt:41 | Blob 下载超过 8MiB 上限，请使用项目导出 | 清晰度 | 网页文件超过 8 MiB，请改用项目导出。 | P1 |
| web/BlobDownloads.kt:45 | 网页无法读取 Blob，或文件超过 8MiB 上限 | 清晰度 | 网页文件无法读取或超过 8 MiB，请改用项目导出。 | P0 |
| web/BlobDownloads.kt:61 | Blob 下载内容无效，未导出文件 | 清晰度 | 网页文件内容无法使用，未能导出。请重试。 | P1 |
| web/BlobDownloads.kt:64 | Blob 下载内容无效，未导出文件 | 清晰度 | 网页文件内容无法使用，未能导出。请重试。 | P1 |
| web/BlobDownloads.kt:69 | 此 WebView 或下载来源不支持 Blob 导出 | 工程痕迹 | 此网页文件无法直接导出，请改用项目导出。 | P1 |
| web/BlobDownloads.kt:70 | 请先完成当前文件下载 | 保留 | 请先完成当前文件下载 | P1 |
| web/BlobDownloads.kt:74 | 准备网页 Blob 下载 | 清晰度 | 正在准备网页文件 | P1 |
| web/BlobDownloads.kt:93 | 超过 8MiB 上限 | 保留 | 超过 8MiB 上限 | P1 |
| web/BlobDownloads.kt:95 | 读取失败 | 保留 | 读取失败 | P0 |
| web/BlobDownloads.kt:98 | 读取失败或超过 8MiB 上限 | 保留 | 读取失败或超过 8MiB 上限 | P0 |

### [web/DshApprovalProtocol.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshApprovalProtocol.kt:173 | 未知审批 | 被动/实现说明 | 无法识别的操作 | P1 |
| web/DshApprovalProtocol.kt:230 | 低 | 保留 | 低 | P1 |
| web/DshApprovalProtocol.kt:231 | 中 | 保留 | 中 | P1 |
| web/DshApprovalProtocol.kt:232 | 高 | 保留 | 高 | P1 |
| web/DshApprovalProtocol.kt:233 | 未知 | 被动/实现说明 | 未知 | P1 |
| web/DshApprovalProtocol.kt:259 | 请求提升沙箱或执行权限 | 清晰度 | 请求扩大文件或命令权限 | P0 |
| web/DshApprovalProtocol.kt:260 | 执行命令或进程操作 | 保留 | 执行命令或进程操作 | P1 |
| web/DshApprovalProtocol.kt:261 | 修改工作区文件 | 保留 | 修改工作区文件 | P1 |
| web/DshApprovalProtocol.kt:262 | 访问网络或浏览器 | 保留 | 访问网络或浏览器 | P1 |
| web/DshApprovalProtocol.kt:263 | 读取工作区内容 | 保留 | 读取工作区内容 | P1 |
| web/DshApprovalProtocol.kt:264 | 无法从工具名判断影响范围 | 保留 | 无法从工具名判断影响范围 | P0 |
| web/DshApprovalProtocol.kt:288 | 参数 | 保留 | 参数 | P1 |
| web/DshApprovalProtocol.kt:306 | 参数 | 保留 | 参数 | P1 |
| web/DshApprovalProtocol.kt:315 | 命令 | 保留 | 命令 | P1 |
| web/DshApprovalProtocol.kt:316 | 路径 | 保留 | 路径 | P1 |
| web/DshApprovalProtocol.kt:317 | 地址 | 保留 | 地址 | P1 |
| web/DshApprovalProtocol.kt:318 | 查询 | 清晰度 | 授权未完成，请重试。 | P1 |
| web/DshApprovalProtocol.kt:319 | 工作目录 | 保留 | 工作目录 | P1 |
| web/DshApprovalProtocol.kt:329 | 官方审批接口返回 ${error.code}，请重试 | 清晰度 | 授权连接超时，请确认助手运行后重试。 | P1 |
| web/DshApprovalProtocol.kt:330 | 审批通道连接超时，请确认 DSH 仍在运行后重试 | 工程痕迹、术语不一致 | 无法连接授权请求，请确认助手运行后重试。 | P0 |
| web/DshApprovalProtocol.kt:331 | 无法连接 DSH 审批通道，请确认服务仍在运行后重试 | 工程痕迹、术语不一致 | 无法连接助手，请重新启动后重试。 | P0 |
| web/DshApprovalProtocol.kt:332 | 无法解析 DSH 服务地址，请重试 | 术语不一致 | 授权连接异常，请重试。 | P0 |
| web/DshApprovalProtocol.kt:341 | 审批通道异常，请重试 | 工程痕迹 | 授权连接失败，请重试。 | P0 |
| web/DshApprovalProtocol.kt:344 | 审批通道连接失败，请重试 | 工程痕迹 | 助手尚未准备好，请先启动助手。 | P0 |
| web/DshApprovalProtocol.kt:359 | 允许一次 | 保留 | 允许一次 | P0 |
| web/DshApprovalProtocol.kt:359 | 拒绝 | 保留 | 拒绝 | P0 |

### [web/DshApprovalSession.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshApprovalSession.kt:56 | DSH 服务地址尚未就绪 | 术语不一致 | 助手连接异常，请重新连接。 | P1 |
| web/DshApprovalSession.kt:60 | DSH 服务地址无效 | 术语不一致 | 授权连接失败，请重试。 | P1 |
| web/DshApprovalSession.kt:80 | 审批通道返回错误（${frame.code}） | 工程痕迹 | 授权连接已断开，请重新连接。 | P0 |
| web/DshApprovalSession.kt:87 | 审批通道已断开 | 工程痕迹 | 授权连接准备失败，请重试。 | P1 |
| web/DshApprovalSession.kt:96 | 审批通道已关闭 | 工程痕迹 | 授权连接已关闭，请重新连接。 | P1 |
| web/DshApprovalSession.kt:107 | 审批通道初始化失败 | 工程痕迹 | 授权连接超时，请重试。 | P0 |
| web/DshApprovalSession.kt:111 | 审批通道未就绪（等待官方 ready 超时） | 工程痕迹 | 授权连接失败，请重试。 | P0 |
| web/DshApprovalSession.kt:113 | 审批通道已关闭 | 工程痕迹 | 授权连接已关闭，请重新连接。 | P1 |
| web/DshApprovalSession.kt:116 | 审批通道已断开 | 工程痕迹 | 授权连接准备失败，请重试。 | P1 |
| web/DshApprovalSession.kt:127 | 审批通道连接失败 | 工程痕迹 | 授权登录未完成，请重新连接。 | P0 |
| web/DshApprovalSession.kt:152 | 审批通道已关闭 | 工程痕迹 | 授权连接已关闭，请重新连接。 | P1 |
| web/DshApprovalSession.kt:153 | DSH 服务地址尚未就绪 | 术语不一致 | 助手连接异常，请重新连接。 | P1 |
| web/DshApprovalSession.kt:154 | DSH 审批通道尚未认证 | 工程痕迹、术语不一致 | 授权请求尚未连接，请重试。 | P1 |
| web/DshApprovalSession.kt:155 | DSH 审批通道尚未就绪 | 工程痕迹、术语不一致 | 授权登录已过期，请重新连接。 | P1 |
| web/DshApprovalSession.kt:170 | 审批认证已失效，需要重新连接 | 清晰度 | 此授权请求已失效，请重新打开对话。 | P1 |
| web/DshApprovalSession.kt:171 | 审批认证已失效，需要重新连接 | 清晰度 | 此授权请求已失效，请重新打开对话。 | P1 |
| web/DshApprovalSession.kt:176 | 官方接口返回 ${error.code} | 工程痕迹 | 请求未完成，请稍后重试。 | P1 |
| web/DshApprovalSession.kt:177 | 审批回答失败 | 清晰度 | 授权结果发送失败，请重试。 | P0 |
| web/DshApprovalSession.kt:216 | 审批请求标识无效 | 清晰度 | 助手连接地址异常，请重新连接。 | P1 |
| web/DshApprovalSession.kt:218 | DSH 接口地址无效 | 术语不一致 | 授权结果未发送，请重试。 | P1 |
| web/DshApprovalSession.kt:233 | 审批回答失败（HTTP $code） | 清晰度 | 助手启动信息无法使用，请重新启动。 | P0 |
| web/DshApprovalSession.kt:246 | DSH 启动地址无效 | 术语不一致 | 授权连接登录失败，请重新连接。 | P1 |
| web/DshApprovalSession.kt:248 | DSH 审批通道认证失败 | 工程痕迹、术语不一致 | 授权连接登录失败，请重新连接。 | P0 |
| web/DshApprovalSession.kt:255 | DSH 审批通道认证失败（HTTP $code） | 工程痕迹、术语不一致 | 授权连接登录失败，请重新连接。 | P0 |
| web/DshApprovalSession.kt:258 | DSH 审批通道认证失败（HTTP $code） | 工程痕迹、术语不一致 | 授权连接登录失败，请重新连接。 | P0 |
| web/DshApprovalSession.kt:264 | DSH 审批通道未返回认证 Cookie | 工程痕迹、术语不一致 | 返回内容过大，请在网页界面查看。 | P1 |
| web/DshApprovalSession.kt:289 | DSH 接口响应过大 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |

### [web/DshComposerClient.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshComposerClient.kt:60 | 文件内容为空 | 清晰度 | 上传结果无法确认，请检查附件后重试。 | P0 |
| web/DshComposerClient.kt:64 | 上传响应格式不正确 | 清晰度 | 上传结果不完整，请重新上传。 | P1 |
| web/DshComposerClient.kt:66 | 上传响应缺少文件信息 | 清晰度 | 附件信息缺失，请重新上传。 | P1 |
| web/DshComposerClient.kt:68 | 上传响应缺少文件凭证 | 清晰度 | 停止结果无法确认，请检查任务状态后重试。 | P1 |
| web/DshComposerClient.kt:72 | 附件 | 保留 | 附件 | P1 |
| web/DshComposerClient.kt:80 | 停止响应格式不正确 | 清晰度 | 停止请求未完成，请检查任务状态后重试。 | P1 |
| web/DshComposerClient.kt:81 | 官方接口未接受停止请求 | 工程痕迹 | 停止请求未完成，请检查任务状态后重试。 | P1 |
| web/DshComposerClient.kt:95 | 发送响应格式不正确 | 清晰度 | 消息发送结果无法确认，请查看对话后再试。 | P1 |
| web/DshComposerClient.kt:96 | 官方接口未接受该消息 | 工程痕迹 | 消息未发送，请重试。 | P1 |
| web/DshComposerClient.kt:109 | 未找到该斜杠命令 | 清晰度 | 找不到这条命令，请重新选择。 | P0 |
| web/DshComposerClient.kt:110 | 斜杠命令返回格式不正确 | 清晰度 | 命令结果无法显示，请在网页界面查看。 | P1 |
| web/DshComposerClient.kt:114 | 斜杠命令执行失败 | 清晰度 | 命令执行失败，请重试。 | P0 |
| web/DshComposerClient.kt:116 | 斜杠命令返回格式不正确 | 清晰度 | 命令结果无法显示，请在网页界面查看。 | P1 |

### [web/DshComposerProtocol.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshComposerProtocol.kt:43 | 附件 | 保留 | 附件 | P1 |
| web/DshComposerProtocol.kt:59 | 附件 | 保留 | 附件 | P1 |
| web/DshComposerProtocol.kt:60 | 还能添加 $typeMaxItems 个附件$suffix | 清晰度 | 还能添加 {内容} 个附件{内容} | P1 |
| web/DshComposerProtocol.kt:62 | 还能添加 $maxItems 个附件 | 清晰度 | 还能添加 {内容} 个附件 | P1 |
| web/DshComposerProtocol.kt:63 | 附件总大小不能超过 ${DshComposerPolicy.formatBytes(totalLabelBytes)} | 清晰度 | 附件总大小不能超过 {大小} | P1 |
| web/DshComposerProtocol.kt:180 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:185 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:192 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:200 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:206 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:211 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:212 | 文件内容为空 | 清晰度 | 上传结果无法确认，请检查附件后重试。 | P0 |
| web/DshComposerProtocol.kt:225 | 请求标识无效 | 清晰度 | 请求已失效，请重试。 | P1 |
| web/DshComposerProtocol.kt:226 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:227 | 投递模式无效 | 清晰度 | 发送方式无法使用，请重新编辑消息后发送。 | P1 |
| web/DshComposerProtocol.kt:228 | 没有可发送的内容 | 保留 | 没有可发送的内容 | P0 |
| web/DshComposerProtocol.kt:239 | 图片类型不受支持 | 保留 | 图片类型不受支持 | P1 |
| web/DshComposerProtocol.kt:240 | 图片内容为空 | 保留 | 图片内容为空 | P0 |
| web/DshComposerProtocol.kt:244 | 文件凭证为空 | 清晰度 | 附件信息缺失，请重新上传。 | P0 |
| web/DshComposerProtocol.kt:250 | 没有可发送的内容 | 保留 | 没有可发送的内容 | P0 |
| web/DshComposerProtocol.kt:261 | 会话标识无效 | 术语不一致 | 对话信息无法使用，请重新打开对话。 | P1 |
| web/DshComposerProtocol.kt:262 | 命令格式无效 | 清晰度 | 命令格式无法使用 | P1 |
| web/DshComposerProtocol.kt:263 | 命令格式无效 | 清晰度 | 命令格式无法使用 | P1 |
| web/DshComposerProtocol.kt:268 | 命令附件不能包含文本块 | 清晰度 | 此命令无法使用这些附件，请移除后重试。 | P1 |
| web/DshComposerProtocol.kt:496 | 不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF | 保留 | 不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF | P1 |
| web/DshComposerProtocol.kt:499 | 每条消息最多添加 ${limits.maxImagesPerMessage} 张图片 | 清晰度 | 每条消息最多添加 {内容} 张图片 | P1 |
| web/DshComposerProtocol.kt:502 | 单张图片不能超过 ${formatBytes(maxBytes)} | 清晰度 | 单张图片不能超过 {大小} | P1 |
| web/DshComposerProtocol.kt:504 | 图片总大小不能超过 ${formatBytes(limits.effectiveMaxMessageImageBytes())}，请移除部分图片 | 清晰度 | 图片总大小不能超过 {大小}，请移除部分图片 | P1 |
| web/DshComposerProtocol.kt:507 | 附件总大小不能超过 ${formatBytes(DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES)} | 清晰度 | 附件总大小不能超过 {大小} | P1 |
| web/DshComposerProtocol.kt:509 | 无法读取图片尺寸，请更换图片 | 保留 | 无法读取图片尺寸，请更换图片 | P0 |
| web/DshComposerProtocol.kt:511 | 图片宽高不能超过 ${limits.maxImageDimension} 像素，请缩小后重试 | 清晰度 | 图片宽高不能超过 {内容} 像素，请缩小后重试 | P1 |
| web/DshComposerProtocol.kt:515 | 图片像素总量不能超过 ${limits.maxImagePixels}，请缩小后重试 | 清晰度 | 图片像素总量不能超过 {内容}，请缩小后重试 | P1 |
| web/DshComposerProtocol.kt:525 | 文件内容为空 | 清晰度 | 上传结果无法确认，请检查附件后重试。 | P0 |
| web/DshComposerProtocol.kt:527 | 单个文件不能超过 ${formatBytes(DshComposerProtocol.MAX_FILE_BYTES)} | 清晰度 | 单个文件不能超过 {大小} | P1 |
| web/DshComposerProtocol.kt:530 | 每条消息最多添加 ${DshComposerProtocol.MAX_FILES_PER_MESSAGE} 个文件 | 清晰度 | 每条消息最多添加 {内容} 个文件 | P1 |
| web/DshComposerProtocol.kt:533 | 附件总大小不能超过 ${formatBytes(DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES)} | 清晰度 | 附件总大小不能超过 {大小} | P1 |

### [web/DshMuxProtocol.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshMuxProtocol.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshMuxProtocol.kt:98 | 流标识无效 | 清晰度 | 连接信息异常，请重新连接。 | P1 |
| web/DshMuxProtocol.kt:99 | 流端点无效 | 清晰度 | 连接信息异常，请重新连接。 | P1 |
| web/DshMuxProtocol.kt:109 | 流标识无效 | 清晰度 | 连接信息异常，请重新连接。 | P1 |
| web/DshMuxProtocol.kt:118 | 流标识无效 | 清晰度 | 连接信息异常，请重新连接。 | P1 |
| web/DshMuxProtocol.kt:123 | 流标识无效 | 清晰度 | 连接信息异常，请重新连接。 | P1 |
| web/DshMuxProtocol.kt:186 | 未知工具 | 被动/实现说明 | 未知工具 | P1 |

### [web/DshNativeApi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshNativeApi.kt:46 | 新对话 | 保留 | 新对话 | P1 |
| web/DshNativeApi.kt:49 | 你 | 保留 | 你 | P1 |
| web/DshNativeApi.kt:70 | DSH 接口返回 $code | 术语不一致 | 助手请求失败，请稍后重试。 | P1 |
| web/DshNativeApi.kt:149 | DSH 接口响应关联不正确 | 术语不一致 | 回复信息不完整，请刷新或在网页界面查看。 | P1 |
| web/DshNativeApi.kt:154 | 会话列表响应格式不正确 | 术语不一致 | 对话列表无法读取，请刷新或在网页界面查看。 | P1 |
| web/DshNativeApi.kt:185 | 消息响应格式不正确 | 清晰度 | 消息无法读取，请刷新或在网页界面查看。 | P1 |
| web/DshNativeApi.kt:255 | DSH 接口响应类型不正确 | 术语不一致 | 回复暂时无法读取，请刷新或在网页界面查看。 | P1 |
| web/DshNativeApi.kt:256 | DSH 接口响应缺少 result | 术语不一致 | 回复内容不完整，请刷新或在网页界面查看。 | P1 |
| web/DshNativeApi.kt:334 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshNativeApi.kt:355 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshNativeApi.kt:356 | 历史游标无效 | 清晰度 | 消息位置已失效，请重新打开对话。 | P1 |
| web/DshNativeApi.kt:379 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshNativeApi.kt:381 | 消息不能为空 | 保留 | 消息不能为空 | P0 |
| web/DshNativeApi.kt:382 | 消息过长（最多 $MAX_PROMPT_CHARS 字） | 清晰度 | 消息过长（最多 {内容} 字） | P1 |
| web/DshNativeApi.kt:397 | 官方接口未接受消息 | 工程痕迹 | 消息未发送，请重试。 | P1 |
| web/DshNativeApi.kt:404 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshNativeApi.kt:409 | 官方接口未接受停止请求 | 工程痕迹 | 停止请求未完成，请检查任务状态后重试。 | P1 |
| web/DshNativeApi.kt:415 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshNativeApi.kt:422 | DSH 服务地址尚未就绪 | 术语不一致 | 助手连接异常，请重新连接。 | P1 |
| web/DshNativeApi.kt:430 | 实时连接已取消 | 清晰度 | 消息同步已取消。 | P0 |
| web/DshNativeApi.kt:475 | 实时消息流已结束，可点刷新重连 | 清晰度 | 消息同步已断开，请刷新重新连接。 | P1 |
| web/DshNativeApi.kt:478 | 实时事件流已结束，审批请到官方界面处理 | 术语不一致 | 消息同步已断开，请在网页界面处理授权请求。 | P1 |
| web/DshNativeApi.kt:532 | 官方 Agent 报告错误，可切换官方界面查看详情 | 术语不一致 | 助手遇到问题，可在网页界面查看详情。 | P0 |
| web/DshNativeApi.kt:546 | 事件流尚未就绪 | 清晰度 | 消息同步尚未连接，请重试。 | P1 |
| web/DshNativeApi.kt:549 | DSH 接口地址无效 | 术语不一致 | 授权结果未发送，请重试。 | P1 |
| web/DshNativeApi.kt:584 | 审批转交失败，已断开聊天实时连接；请刷新重连或切换官方界面处理 | 术语不一致 | 授权请求无法显示，请刷新或切换到网页界面处理。 | P0 |
| web/DshNativeApi.kt:589 | 官方接口返回 ${error.code} | 工程痕迹 | 请求未完成，请稍后重试。 | P1 |
| web/DshNativeApi.kt:590 | 官方实时流格式不正确 | 清晰度 | 消息暂时无法显示，请刷新或切换到网页界面。 | P1 |
| web/DshNativeApi.kt:591 | 实时连接失败 | 保留 | 实时连接失败 | P0 |
| web/DshNativeApi.kt:616 | DSH 服务地址尚未就绪 | 术语不一致 | 助手连接异常，请重新连接。 | P1 |
| web/DshNativeApi.kt:620 | DSH 接口地址无效 | 术语不一致 | 授权结果未发送，请重试。 | P1 |
| web/DshNativeApi.kt:640 | DSH 接口返回 HTTP $code | 术语不一致 | 助手请求失败，请稍后重试。 | P1 |
| web/DshNativeApi.kt:642 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshNativeApi.kt:650 | DSH 接口认证失败 | 术语不一致 | 页面登录失败，请重新连接。 | P0 |
| web/DshNativeApi.kt:655 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshNativeApi.kt:665 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshNativeApi.kt:669 | DSH 启动地址无效 | 术语不一致 | 授权连接登录失败，请重新连接。 | P1 |
| web/DshNativeApi.kt:671 | DSH 接口认证失败 | 术语不一致 | 页面登录失败，请重新连接。 | P0 |
| web/DshNativeApi.kt:677 | DSH 接口认证失败（HTTP $code） | 术语不一致 | 页面登录失败，请重新连接。 | P0 |
| web/DshNativeApi.kt:682 | DSH 接口未返回认证 Cookie | 工程痕迹、术语不一致 | 页面登录失败，请重新连接。 | P1 |
| web/DshNativeApi.kt:684 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshNativeApi.kt:714 | DSH 接口响应过大 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |

### [web/DshPlan.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshPlan.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshPlan.kt:8 | 待开始 | 保留 | 待开始 | P1 |
| web/DshPlan.kt:9 | 进行中 | 保留 | 进行中 | P1 |
| web/DshPlan.kt:10 | 已完成 | 保留 | 已完成 | P1 |
| web/DshPlan.kt:11 | 受阻 | 保留 | 受阻 | P1 |
| web/DshPlan.kt:12 | 已跳过 | 保留 | 已跳过 | P1 |
| web/DshPlan.kt:17 | 执行计划 | 保留 | 执行计划 | P1 |
| web/DshPlan.kt:18 | 待审阅 | 保留 | 待审阅 | P1 |
| web/DshPlan.kt:19 | 已同意执行 | 保留 | 已同意执行 | P1 |
| web/DshPlan.kt:20 | 已要求修改 | 保留 | 已要求修改 | P1 |
| web/DshPlan.kt:65 | 进度未知 | 被动/实现说明 | 正在同步进度 | P1 |
| web/DshPlan.kt:66 | 无步骤 | 清晰度 | 还没有计划步骤。 | P1 |
| web/DshPlan.kt:67 | 执行中 | 保留 | 执行中 | P1 |
| web/DshPlan.kt:68 | 已完成 | 保留 | 已完成 | P1 |
| web/DshPlan.kt:69 | 未开始 | 保留 | 未开始 | P1 |
| web/DshPlan.kt:70 | 执行中 | 保留 | 执行中 | P1 |
| web/DshPlan.kt:214 | 执行计划 | 保留 | 执行计划 | P1 |

### [web/DshQuestion.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshQuestion.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshQuestion.kt:8 | 单选 | 保留 | 单选 | P1 |
| web/DshQuestion.kt:9 | 多选 | 保留 | 多选 | P1 |
| web/DshQuestion.kt:10 | 文本 | 保留 | 文本 | P1 |

### [web/DshSessionAdminApi.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshSessionAdminApi.kt:31 | 会话 ${id.take(8)} | 术语不一致 | 未命名对话 | P1 |
| web/DshSessionAdminApi.kt:103 | 会话列表响应格式不正确 | 术语不一致 | 对话列表无法读取，请刷新或在网页界面查看。 | P1 |
| web/DshSessionAdminApi.kt:137 | 新建会话响应格式不正确 | 术语不一致 | 新建对话结果无法确认，请刷新列表查看。 | P1 |
| web/DshSessionAdminApi.kt:139 | 官方未返回有效会话标识 | 术语不一致 | 对话信息不完整，请刷新列表。 | P1 |
| web/DshSessionAdminApi.kt:144 | 重命名响应格式不正确 | 清晰度 | 名称保存结果无法确认，请刷新列表查看。 | P1 |
| web/DshSessionAdminApi.kt:146 | 官方未返回有效标题 | 清晰度 | 名称未保存，请刷新列表后重试。 | P1 |
| web/DshSessionAdminApi.kt:151 | 归档响应格式不正确 | 清晰度 | 归档结果无法确认，请刷新列表查看。 | P1 |
| web/DshSessionAdminApi.kt:161 | 搜索响应格式不正确 | 清晰度 | 搜索结果无法显示，请重试。 | P1 |
| web/DshSessionAdminApi.kt:174 | 模型目录响应格式不正确 | 清晰度 | 模型列表无法读取，请重试。 | P1 |
| web/DshSessionAdminApi.kt:232 | 模型选择响应格式不正确 | 清晰度 | 模型选择结果无法确认，请刷新查看。 | P1 |
| web/DshSessionAdminApi.kt:234 | 官方未返回已选模型 | 清晰度 | 模型尚未选定，请重新选择。 | P1 |
| web/DshSessionAdminApi.kt:238 | 权限目录响应格式不正确 | 清晰度 | 权限选项无法读取，请重试。 | P0 |
| web/DshSessionAdminApi.kt:247 | 技能目录响应格式不正确 | 清晰度 | 技能列表无法读取，请重试。 | P1 |
| web/DshSessionAdminApi.kt:329 | DSH 接口响应类型不正确 | 术语不一致 | 回复暂时无法读取，请刷新或在网页界面查看。 | P1 |
| web/DshSessionAdminApi.kt:330 | DSH 接口响应缺少 result | 术语不一致 | 回复内容不完整，请刷新或在网页界面查看。 | P1 |
| web/DshSessionAdminApi.kt:408 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshSessionAdminApi.kt:409 | 标题不能为空 | 保留 | 标题不能为空 | P0 |
| web/DshSessionAdminApi.kt:421 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshSessionAdminApi.kt:433 | 搜索内容为空 | 保留 | 搜索内容为空 | P0 |
| web/DshSessionAdminApi.kt:447 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshSessionAdminApi.kt:448 | 模型标识为空 | 清晰度 | 请先选择模型。 | P0 |
| web/DshSessionAdminApi.kt:466 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshSessionAdminApi.kt:467 | 权限模式标识无效 | 清晰度 | 权限选项无法使用，请重新选择。 | P0 |
| web/DshSessionAdminApi.kt:474 | 官方未接受权限切换 | 清晰度 | 权限切换未完成，请检查当前权限后重试。 | P0 |
| web/DshSessionAdminApi.kt:480 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshSessionAdminApi.kt:489 | 会话标识为空 | 术语不一致 | 请先打开一段对话。 | P0 |
| web/DshSessionAdminApi.kt:525 | DSH 服务地址尚未就绪 | 术语不一致 | 助手连接异常，请重新连接。 | P1 |
| web/DshSessionAdminApi.kt:529 | DSH 接口地址无效 | 术语不一致 | 授权结果未发送，请重试。 | P1 |
| web/DshSessionAdminApi.kt:548 | DSH 接口返回 HTTP $code | 术语不一致 | 助手请求失败，请稍后重试。 | P1 |
| web/DshSessionAdminApi.kt:550 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshSessionAdminApi.kt:558 | DSH 接口认证失败 | 术语不一致 | 页面登录失败，请重新连接。 | P0 |
| web/DshSessionAdminApi.kt:562 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshSessionAdminApi.kt:572 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshSessionAdminApi.kt:576 | DSH 启动地址无效 | 术语不一致 | 授权连接登录失败，请重新连接。 | P1 |
| web/DshSessionAdminApi.kt:577 | DSH 接口认证失败 | 术语不一致 | 页面登录失败，请重新连接。 | P0 |
| web/DshSessionAdminApi.kt:583 | DSH 接口认证失败（HTTP $code） | 术语不一致 | 页面登录失败，请重新连接。 | P0 |
| web/DshSessionAdminApi.kt:588 | DSH 接口未返回认证 Cookie | 工程痕迹、术语不一致 | 页面登录失败，请重新连接。 | P1 |
| web/DshSessionAdminApi.kt:590 | DSH 接口认证已取消 | 术语不一致 | 页面登录已取消，请重新连接。 | P0 |
| web/DshSessionAdminApi.kt:634 | DSH 接口响应过大 | 术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |

### [web/DshToolTimeline.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshToolTimeline.kt:9 | 进行中 | 保留 | 进行中 | P1 |
| web/DshToolTimeline.kt:10 | 成功 | 清晰度 | 已完成 | P1 |
| web/DshToolTimeline.kt:11 | 失败 | 保留 | 失败 | P0 |
| web/DshToolTimeline.kt:168 | 未知工具 | 被动/实现说明 | 未知工具 | P1 |
| web/DshToolTimeline.kt:201 | [非文本输出已省略] | 清晰度 | 此结果不是文字，请在网页界面查看。 | P1 |
| web/DshToolTimeline.kt:261 | 未知工具 | 被动/实现说明 | 未知工具 | P1 |
| web/DshToolTimeline.kt:271 | 历史页未包含对应的工具调用 | 清晰度 | 未找到对应操作，请在网页界面查看。 | P1 |
| web/DshToolTimeline.kt:303 | 工具返回错误（$code） | 清晰度 | 工具执行失败，请查看问题详情后重试。 | P0 |
| web/DshToolTimeline.kt:305 | 工具执行失败，官方未返回错误说明 | 清晰度 | 工具执行失败，请在网页界面查看详情。 | P0 |
| web/DshToolTimeline.kt:431 | 未知工具 | 被动/实现说明 | 未知工具 | P1 |
| web/DshToolTimeline.kt:461 | ${durationMs} 毫秒 | 清晰度 | {耗时} 毫秒 | P1 |
| web/DshToolTimeline.kt:462 | %.1f 秒 | 保留 | %.1f 秒 | P1 |
| web/DshToolTimeline.kt:466 | $minutes 分 $seconds 秒 | 清晰度 | {内容} 分 {时间} 秒 | P1 |
| web/DshToolTimeline.kt:466 | $minutes 分钟 | 清晰度 | {内容} 分钟 | P1 |
| web/DshToolTimeline.kt:471 | $hours 小时 | 清晰度 | {内容} 小时 | P1 |
| web/DshToolTimeline.kt:471 | $hours 小时 $minutes 分 | 清晰度 | {内容} 小时 {内容} 分 | P1 |
| web/DshToolTimeline.kt:536 | …（diff 已截断） | 清晰度 | 仅显示部分变更，完整内容请在网页界面查看。 | P1 |

### [web/DshWebSocket.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshWebSocket.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshWebSocket.kt:51 | WebSocket 地址不是本机 loopback | 工程痕迹 | 连接地址不安全，已停止连接。请重新启动助手。 | P1 |
| web/DshWebSocket.kt:53 | WebSocket 认证 Cookie 无效 | 工程痕迹 | 页面登录信息无法使用，请重新连接。 | P1 |
| web/DshWebSocket.kt:81 | WebSocket 握手被拒绝 | 工程痕迹 | 实时连接被拒绝，请重新连接。 | P0 |
| web/DshWebSocket.kt:88 | WebSocket 握手校验失败 | 工程痕迹 | 实时连接校验失败，请重新连接。 | P0 |
| web/DshWebSocket.kt:90 | WebSocket 连接已取消 | 工程痕迹 | 实时连接已取消。 | P0 |
| web/DshWebSocket.kt:97 | WebSocket 连接失败 | 工程痕迹 | 实时连接失败，请重试。 | P0 |
| web/DshWebSocket.kt:103 | WebSocket 文本帧过大 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:163 | WebSocket 帧序无效 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:164 | WebSocket 只接受文本帧 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:167 | WebSocket 消息过大 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:171 | WebSocket 续帧没有起始帧 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P0 |
| web/DshWebSocket.kt:173 | WebSocket 消息过大 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:176 | WebSocket 帧类型不支持 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:182 | WebSocket 尚未连接 | 工程痕迹 | 实时连接尚未建立，请重试。 | P1 |
| web/DshWebSocket.kt:186 | WebSocket 帧头被截断 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:187 | WebSocket 扩展位未协商 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:190 | WebSocket 服务端帧不得掩码 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:199 | WebSocket 帧过大 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:200 | WebSocket 控制帧无效 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:205 | WebSocket 帧被截断 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:213 | WebSocket 帧被截断 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:218 | WebSocket 尚未连接 | 工程痕迹 | 实时连接尚未建立，请重试。 | P1 |
| web/DshWebSocket.kt:231 | WebSocket 握手响应被截断 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:235 | WebSocket 握手响应过大 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:265 | 掩码必须是 4 字节 | 错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocket.kt:266 | 帧载荷过大 | 错误出口 | 连接信息异常，请重新连接。 | P1 |

### [web/DshWebSocketClient.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshWebSocketClient.kt:57 | 审批通道已关闭 | 工程痕迹 | 授权连接已关闭，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:58 | DSH 服务地址无效 | 术语不一致 | 授权连接失败，请重试。 | P1 |
| web/DshWebSocketClient.kt:60 | DSH 审批通道认证无效 | 工程痕迹、术语不一致、错误出口 | 助手连接异常，请重新连接或在网页界面查看。 | P1 |
| web/DshWebSocketClient.kt:63 | DSH 服务地址无效 | 术语不一致 | 授权连接失败，请重试。 | P1 |
| web/DshWebSocketClient.kt:105 | 审批通道已关闭 | 工程痕迹 | 授权连接已关闭，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:106 | DSH 审批通道握手超时 | 工程痕迹、术语不一致 | 授权连接超时，请重试。 | P0 |
| web/DshWebSocketClient.kt:108 | DSH 审批通道连接失败 | 工程痕迹、术语不一致 | 授权连接失败，请重试。 | P0 |
| web/DshWebSocketClient.kt:127 | 审批通道写入失败 | 工程痕迹 | 授权结果发送失败，请重试。 | P0 |
| web/DshWebSocketClient.kt:168 | 审批通道收到意外的续帧 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:170 | 审批通道消息过大 | 工程痕迹 | 审批连接消息过大 | P1 |
| web/DshWebSocketClient.kt:187 | 审批通道收到不支持的控制帧 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:191 | 审批通道已断开 | 工程痕迹 | 授权连接准备失败，请重试。 | P1 |
| web/DshWebSocketClient.kt:193 | 审批通道读取失败 | 工程痕迹 | 授权内容读取失败，请重新连接。 | P0 |
| web/DshWebSocketClient.kt:202 | 审批通道收到非法文本 | 工程痕迹 | 审批连接收到非法文本 | P1 |
| web/DshWebSocketClient.kt:234 | 审批通道握手超时 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P0 |
| web/DshWebSocketClient.kt:237 | 审批通道握手提前结束 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:246 | 审批通道握手响应过大 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:252 | 审批通道握手响应无效 | 工程痕迹 | 授权连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:253 | DSH 审批通道连接失败（HTTP $status） | 工程痕迹、术语不一致 | 授权连接失败，请重试。 | P0 |
| web/DshWebSocketClient.kt:261 | DSH 审批通道未升级为 WebSocket | 工程痕迹、术语不一致、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:264 | DSH 审批通道连接头无效 | 工程痕迹、术语不一致 | 助手 审批连接连接头无法使用 | P1 |
| web/DshWebSocketClient.kt:268 | DSH 审批通道握手校验失败 | 工程痕迹、术语不一致、错误出口 | 连接信息异常，请重新连接。 | P0 |
| web/DshWebSocketClient.kt:283 | 审批通道已关闭（$code） | 工程痕迹 | 授权连接已关闭，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:298 | mask 必须为 4 字节 | 保留 | mask 必须为 4 字节 | P1 |
| web/DshWebSocketClient.kt:336 | 审批通道帧过大 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:337 | 审批通道收到掩码服务端帧 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:342 | 审批通道帧提前结束 | 工程痕迹、错误出口 | 连接信息异常，请重新连接。 | P1 |
| web/DshWebSocketClient.kt:356 | 审批通道提前结束 | 工程痕迹 | 审批连接提前结束 | P1 |

### [web/DshWebView.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/DshWebView.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/DshWebView.kt:88 | Cookie 切换中 | 工程痕迹 | 正在切换页面 | P1 |
| web/DshWebView.kt:92 | 来源不受信任 | 保留 | 来源不受信任 | P1 |
| web/DshWebView.kt:136 | 网页连接失败：${error.description} | 清晰度 | 网页连接失败，请重试。 | P0 |
| web/DshWebView.kt:142 | 网页渲染进程已退出，请重新连接 | 工程痕迹 | 网页显示已中断，请重新连接。 | P1 |
| web/DshWebView.kt:143 | WebView 渲染进程已退出，请返回应用重新连接；DSH 进程状态单独显示 | 工程痕迹、术语不一致 | 网页显示已中断，请重新连接。助手运行状态可在工作台查看。 | P1 |
| web/DshWebView.kt:213 | 此下载来源尚不支持；请从项目导出入口导出文件 | 清晰度 | 此文件无法直接下载，请使用“导出项目”。 | P1 |
| web/DshWebView.kt:284 | 未知 | 被动/实现说明 | 未知 | P1 |
| web/DshWebView.kt:284 | 未知 | 被动/实现说明 | 未知 | P1 |
| web/DshWebView.kt:289 | &lt;!doctype html&gt;&lt;meta charset=utf-8&gt;&lt;title&gt;WebView 能力检测&lt;/title&gt; | 工程痕迹、长度 | 网页组件检测 | P1 |
| web/DshWebView.kt:290 | 渲染能力检测超时 | 工程痕迹 | 网页显示检测超时，请重试。 | P0 |
| web/DshWebView.kt:297 | 无法读取 CSS 能力 | 清晰度 | 无法检测网页显示，请更新网页组件后重试。 | P0 |
| web/DshWebView.kt:308 | 管理页 Cookie 隔离未就绪，已拒绝加载；请重启应用 | 工程痕迹 | 无法安全切换页面，请重启应用。 | P0 |
| web/DshWebView.kt:315 | DSH 网页地址尚未就绪，请先启动服务 | 术语不一致 | 请先启动助手，再连接网页。 | P1 |
| web/DshWebView.kt:345 | 无法在浏览器中打开此链接；DSH 管理页面与文件链接保持隔离 | 术语不一致 | 此链接无法在浏览器打开，请回到助手页面查看。 | P0 |

### [web/LegacyWebCompatibility.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/LegacyWebCompatibility.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/LegacyWebCompatibility.kt:18 | 此 WebView 过旧且不支持启动脚本，请在系统设置更新 Android System WebView | 工程痕迹、长度 | 网页组件版本过旧，请在系统设置中更新后重试。 | P0 |

### [web/WebViewSupport.kt](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/kotlin/com/tanuki/web/WebViewSupport.kt)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| web/WebViewSupport.kt:9 | 尚未检测 | 保留 | 尚未检测 | P1 |
| web/WebViewSupport.kt:10 | 未知 | 被动/实现说明 | 未知 | P1 |
| web/WebViewSupport.kt:18 | WebView 提供方 $provider\n版本 $version（Chromium ${chromium ?: "未知"}） | 工程痕迹、被动/实现说明 | 网页组件：{组件名称}，版本：{版本} | P1 |
| web/WebViewSupport.kt:18 | 未知 | 被动/实现说明 | 未知 | P1 |
| web/WebViewSupport.kt:20 | \n尚未检测渲染能力 | 工程痕迹 | 尚未检查网页显示。 | P1 |
| web/WebViewSupport.kt:21 | \n正在检测实际 CSS 渲染能力… | 工程痕迹 | 正在检查网页显示… | P1 |
| web/WebViewSupport.kt:22 | \n已通过 Chromium 123+ 与 CSS 能力检查 | 清晰度 | 网页显示检查通过。 | P1 |
| web/WebViewSupport.kt:23 | \nDSH 需要 Chromium 123 或更新版本。当前缺失：${missing.joinToString().ifEmpty { "内核版本要求" }}。请通过官方渠道更新系统 WebView 后重新打开 App。 | 工程痕迹、术语不一致、长度 | 系统网页组件版本过旧，请通过官方渠道更新后重开应用。 | P0 |
| web/WebViewSupport.kt:23 | 内核版本要求 | 清晰度 | 网页组件版本过旧 | P1 |
| web/WebViewSupport.kt:24 | \n检测失败：$error | 清晰度 | 网页显示检测失败，请重试。 | P0 |

### [app/src/main/res/values/strings.xml](https://github.com/leezzou/tanuki/blob/4710c5e08ffa8c86d8cdc1bffbec1a1052be0935/app/src/main/res/values/strings.xml)

| 文件:行 | 原文 | 问题类型 | 建议文案 | 优先级 |
|---|---|---|---|---|
| app/src/main/res/values/strings.xml:2 | Tanuki | 保留 | Tanuki | P1 |

## 内嵌程序容器核对

这些字符串是整段程序，不能将整段当作文案改写。其中文错误/输出已拆到上方对应行；注释、日志协议和固定匹配字段保持不变。

| 文件:行 | 容器用途 | 处理 |
|---|---|---|
| runtime/NpmPackageDownloads.kt:428 | 内嵌运行/网页脚本 | 仅改展示层映射与已拆出的消息，不改程序协议 |
| runtime/NpmPackageDownloads.kt:535 | 内嵌运行/网页脚本 | 仅改展示层映射与已拆出的消息，不改程序协议 |
| web/BlobDownloads.kt:88 | 内嵌运行/网页脚本 | 仅改展示层映射与已拆出的消息，不改程序协议 |
| web/DshChatProtocol.kt:93 | 内嵌运行/网页脚本 | 仅改展示层映射与已拆出的消息，不改程序协议 |

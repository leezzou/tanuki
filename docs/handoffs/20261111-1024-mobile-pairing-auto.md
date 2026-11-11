# 交接：20261111-1024-mobile-pairing-auto

- Agent/会话标识：android_agent-66（AO Worker）
- 时间：2026-11-11 10:24 CST
- 状态：已完成（PR 在本提交后创建；未合并、未发布）
- 前一记录：[远程 SSH 六步向导](20261110-1736-remote-ssh-wizard.md)（配对入口来源）

## 用户目标与范围

- 用户本次实际要求：让手机端配对做到“不用手抄、不用手输码”。进入配对链接步骤时自动读剪贴板并解析；解析后自动计算并展示 6 位码；展示包含电脑名、中继地址、证书指纹和 SSH 目标的确认页；失败按链接过期/房间不存在/主机离线/钉扎不匹配/端口不可达给中文原因和下一步；保留手动粘贴与手输码回退；补 JVM 回归；中文注释；按 leezzou 身份提交 PR，不合并、不发布。
- 本次包含：`RemoteHostWizard`/`RemoteHostSettings`/`RemotePairingCode` 及相关 runtime/UI、JVM 回归、REMOTE-CONNECT/REMOTE-HOST/INTERFACES 文档更新。
- 本次不包含：相机扫码（另立项）、协议字段扩展、中继/主机端脚本改动、设备/模拟器仪器测试、合并与发布。
- 用户改变或暂停的要求（需保留）：API Key 保存、真实模型/账号验收、真机验收仍暂停；未获授权不得合并/发布。

## 当前状态

- 已实现：剪贴板自动读取与安全覆盖策略、链接自动算码与可选校对、配对确认步骤、按中继错误分流的中文建议、手动粘贴/输入回退、配对步骤仅链接方式出现。
- 已验证：见下方“验证记录”。
- 待验证：真实 Android 剪贴板行为（Android 10+ 聚焦限制）、Compose 确认页在设备上的布局/大字/深色、真实中继端到端。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki`（modern/debug 变体）；分支 `ao/mobile-pairing/root`，基线 `origin/main=62e4c30b`；编译、JVM、lint 均已结束，无遗留构建任务。
- 关键事实与可定位源码/公共文档：6 位码算法在 `RemotePairingCode`，自动填充/确认在 `RemoteHostWizard`，剪贴板入口在 `RemoteHostSettings.readPairingClipboard`，失败建议在 `RemotePairingGuidance`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/RemotePairingCode.kt` | 更新注释：手机自动算码、不要求手输；删除外部参考实现措辞。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteRelay.kt` | 新增 `RelayFailureKind.EXPIRED` 区分过期与房间不存在；`expired` 映射；`RelayTlsPin.abbreviate` 供确认页截断展示。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteConnectionError.kt` | 新增 `RemotePairingGuidance`，按中继错误类型给出中文原因与下一步；中继异常统一走该映射。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt` | 新增配对确认步骤、剪贴板状态/访问代次、自动填链接与算码、手动链接保护与显式替换、自动码回填、配对码可选校验；直连/局域网跳过确认步骤。 |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` | 进入链接步骤自动读剪贴板；显示“电脑上应显示”大号码与校对框；新增确认页（电脑名/中继/指纹展开/SSH 目标）；失败面板继续复用阶段化建议；手动粘贴回退保留。 |
| `app/src/test/kotlin/com/tanuki/RemoteWizardTest.kt` | 新增/更新 JVM 回归：剪贴板自动填充与算码一致、非链接/无效链接回退、手动链接不被覆盖、可选码校对、确认步骤、五类失败文案。 |
| `docs/REMOTE-CONNECT.md` | 更新手机加入流程：自动剪贴板、自动算码、确认页、按原因建议。 |
| `docs/REMOTE-HOST.md` | 更新向导步骤和配对操作说明，去掉“必须输入 6 位码”的旧描述与“Python 主机端”措辞。 |
| `docs/INTERFACES.md` | 更新配对链接/6 位码的行内契约：自动计算展示、可选校对、剪贴板失败保留手动粘贴。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | 通过（BUILD SUCCESSFUL，2m27s；仅既有 deprecation 警告） | JDK17（主仓库 `.build/jdk/jdk-17.0.21+9`）、Android SDK 36、macOS ARM64 |
| `./gradlew --no-daemon -PtestBuildType=debug -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.RemoteWizardTest' --tests 'com.tanuki.RemoteRelayTest' --tests 'com.tanuki.RemoteHostTest'` | 通过：RemoteWizardTest 46、RemoteRelayTest 22、RemoteHostTest 27，合计 95 项，0 failures/0 errors/0 skipped | 定向 JVM；结果 XML 位于 `app/build/test-results/testModernDebugUnitTest/` |
| `./gradlew --no-daemon -PtestBuildType=debug -x buildNativeRuntime :app:testModernDebugUnitTest` | 通过（BUILD SUCCESSFUL）：1052 tests / 108 结果文件 / 0 failures / 0 errors / 0 skipped | 完整 JVM；不含设备/仪器测试 |
| `./gradlew --no-daemon -PtestBuildType=debug -x buildNativeRuntime :app:lintModernDebug` | 通过（BUILD SUCCESSFUL，2m57s）：0 errors / 104 warnings / 1 hint；改动文件无 lint 发现 | 完整 lint；既有警告未在本任务修复 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1024-mobile-pairing-auto.md` | 通过 | 普通 PR 记录结构检查 |
| `python3 scripts/check-publication.py --git` | 通过：736 个公开文件 / 735 索引项 / 0 findings | 文档链接/敏感路径检查；不输出匹配值 |

- 哪些是前一轮结果，哪些是本次新验证：本次只记录本轮实际执行；前轮完整套件结果不冒充本轮复跑。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：真实设备剪贴板读取、Compose 确认页视觉/无障碍、真实中继与 SSH 端到端未执行。

## 未完成与暂停项

- 未完成：无；PR 待本提交推送后创建。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；合并/发布未授权。

## 下一步

1. 在真实 Android 设备上验证：首次进入链接步骤读剪贴板、剪贴板为空/非链接时粘贴框可用、确认页截断/展开指纹、深色与大字体布局。
2. 用真实中继与 sshd 验证五类失败文案与实际错误码一致，特别是自签名证书钉扎不匹配时不可重试。

## 约束与风险

- 数据/设备/权限边界：剪贴板只在进入链接步骤时读取第一条文本，不写入、不上传、不记录日志；非配对链接内容不显示、不覆盖粘贴框。
- 关键决策及理由：确认页作为独立步骤而非弹窗，保证用户明确确认电脑身份后才进入认证；剪贴板只覆盖空白框或本机上次自动填入的内容，避免悄悄覆盖手动编辑；配对码改为可空白，只在用户主动校对时校验不一致。
- 失败尝试与避免重复踩坑的结论：不扩展配对链接字段（链接本身不含电脑名），确认页显示“手机填写名/主机标识前缀”并明确来源；未做假扫码。手机端已支持 `expired` 专用文案，但当前中继脚本清理 TTL 房间后对查询返回 `room_not_found`，因此真实链路多数会落到“房间不存在/链接失效”文案；要在协议层区分“曾经存在但已过期”，需中继保留短期过期标记，留作后续改动。

## 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：本次不构建/发布 APK，不生成源码 ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本次未发现需要清理的过期交接记录；保留当前记录与基线记录。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。文档或脚本变化后重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

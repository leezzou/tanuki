# 交接：20261111-1300-lan-discovery-ui

- Agent/会话标识：android_agent-72（AO worker）
- 时间：2026-11-11 13:00 CST（开始）
- 状态：实现与宿主验证已完成（PR 交付；独立审查/设备 QA 待执行，不合并、不发布）
- 前一记录：[主机端 mDNS 发现](20261111-1147-mdns-discovery.md)

> 普通 PR 只新增本记录，不修改 `docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json`。

## 用户目标与范围

- 用户本次实际要求：手机接入系统 NSD 局域网发现，解析 name/ssh/dsh/host/ver/fp；可见时每 5 秒重扫、下拉刷新、离开停止；Android 13+ 附近设备权限只申请一次；重做远程页顺序、空态和安全说明，移除重复大标题与无关安装进度，补 JVM 回归并以 PR 交付。
- 本次包含：发现解析/队列/系统适配、权限持久记录、配对预填与既有 SSH 指纹确认、相机扫码与粘贴入口、页面结构、必要文档和许可。起点与实际 fetch 后的 `origin/main` 均为 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1`；功能分支 `ao/lan-discovery/mobile`。
- 本次不包含：修改主机广播格式、真实手机/路由器/相机验收、合并、发布、tag/Release、API Key/真实模型或账号功能。
- 保留要求：第一方新增/修改注释使用中文；author/committer=`leezzou <115924933+leezzou@users.noreply.github.com>`；不在文档/注释引入外部参考实现名称；必要第三方许可保留。

## 当前状态

- 已实现：单一顶栏标题，内容按「当前主机状态 → 局域网发现的电脑 → 我的连接 → 其他方式」排列；空态命令复制和实际扫码入口；安全一句话与折叠技术细节；仅本页隐藏运行时/安装 banner。
- 已实现：`_tanuki-remote._tcp` 发现、串行解析、主机标识去重、在线优先稳定排序、有效期清理、退出后旧解析不更新新页面。广播可见不等于 SSH/DSH 已验证。
- 关键限制：现有 TXT 没有平台/操作系统/DSH 版本，`ver` 为协议版本，列表明确显示“平台未提供 · 协议 v1”；没有从名称猜测平台或改主机端协议。摘要 `fp` 不写入 known_hosts，不冒充 SSH 指纹。
- 已验证：首版完整 JVM 1180 项、119 结果文件，0 失败/错误/跳过；LAN 17、向导 49、进度 7 项通过；debug 组装/AndroidTest 源码编译/lint/依赖清单通过（16m47s）；lint 0 errors / 110 warnings / 1 hint。主机 mDNS 20 项与交接/公开扫描通过。
- 待验证：独立 Android QA、真实同 Wi-Fi mDNS/路由器隔离、相机扫码、系统权限拒绝与设置恢复、页面手势/深色/大字号、真实 sshd/DSH；独立审查与 PR CI 以最终 head 为准。
- 包名：验证构建使用 `com.tanuki.modern.workspacecheck`；未安装、未操作设备、未发布附件。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/lan/LanDiscovery.kt` | TXT 严格解析、发现模型、去重/排序/有效期、单次权限策略、空态文案、带代次的串行解析队列。 |
| `app/src/main/kotlin/com/tanuki/lan/LanDiscoveryController.kt` | NsdManager 发现与解析适配、主线程回调、5 秒重扫、停止回调后重启、多播锁与权限撤销处理。 |
| `app/src/main/AndroidManifest.xml` | 附近设备、多播锁权限；扫码相机为可选硬件。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt` | 局域网点选预填入口、无效剪贴板保留已有输入、统一忙碌/接纳门禁；认证与真实 SSH 指纹确认继续复用。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt` | 测试提交返回是否接纳；忙碌或等待指纹时拒绝新尝试，不改本次配置。 |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` | 页面生命周期/下拉刷新、发现列表、确认、扫码/粘贴、空态与安全折叠。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 远程页独立滚动容器，保留单一顶栏标题，本页隐藏无关运行时 banner；测试回调返回接纳结果。 |
| `app/src/main/kotlin/com/tanuki/ui/OperationProgress.kt` | 按调用页隐藏运行时进度与安装取消；工作台/DSH 保留原入口。 |
| `app/build.gradle.kts`、`THIRD_PARTY_NOTICES`、`licenses/` | 实际扫码依赖与必要许可清单。 |
| `app/src/test/kotlin/com/tanuki/LanDiscoveryTest.kt`、`RemoteWizardTest.kt` | TXT、重复/在线/过期、队列迟到/重入/服务消失、权限降级、空态与向导预填回归。 |
| `docs/INTERFACES.md`、`REMOTE-HOST.md`、`REMOTE-CONNECT.md`、`TEST-REPORT.md` | 手机端消费契约、入口、限制、验证与未验说明。 |
| `docs/handoffs/20261111-1300-lan-discovery-ui.md` | 本轮唯一交接。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin` / `git ls-remote origin refs/heads/main` | 通过；基线 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1` | 首次 fetch 因 worktree 外 Git 元数据写权限受阻，获准后原命令成功；没有以本地 main 代替远端。 |
| `./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin` | 首版通过（4m37s） | JDK17、Android SDK36、macOS ARM64；此后实现继续修正，最终验证见下一行。 |
| `./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -I scripts/dependency-inventory.init.gradle -x buildNativeRuntime :app:assembleModernDebug :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest :app:lintModernDebug :app:tanukiDependencyInventory` | BUILD SUCCESSFUL（16m47s）；1180 tests / 119 结果文件 / 0 failures/errors/skipped；lint 0 errors / 110 warnings / 1 hint | 同一份冻结产品/测试代码；LAN 17、向导 49、进度 7 项全通过。发现控制器有 1 项 UseKtx 风格建议；没有设备执行。 |
| `python3 scripts/collect-component-licenses.py` | 通过；93 个运行归档 | 本轮新增 2 个扫码依赖；根第三方声明保留必要 Apache-2.0 声明，Core 的许可继承父 POM，收集器仍如实标记其直接 POM 未声明。 |
| `python3 scripts/remote-relay/test_mdns.py` | 20 项通过 | 离线固定报文/假进程，无真实手机网络验收。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1300-lan-discovery-ui.md`、`python3 scripts/check-publication.py --git` | 通过；0 findings | 提交前复查暂存与链接；不读被忽略凭据。 |

- 本表只有本轮新执行结果；没有引用历史测试数量冒充重跑。
- 失败保留：首轮 Gradle 命中工作区外缓存锁权限，获准后继续；首轮编译抓到 TXT map 可空字段，已修为缺失拒绝后编译通过。首次分支切换受 Git 元数据权限阻挡，确认 HEAD/改动不变后同命令成功。
- 首次定向 JVM：70 项、1 失败，失败为 `lostInFlightServiceCannotBecomeOnlineAgainFromStaleResult`。构建过程中继续修改了队列和用例，产品类先编译、测试类后编译，导致新用例跑到旧产品类；此轮不计通过。随后冻结产品/测试代码，重新编译并执行完整验证，不把不同快照的局部结果拼成通过。
- 未测试：真实组播网络/真实相机/系统权限交互、页面截图与无障碍、Android 仪器执行、真实 sshd/中继/DSH、真机/低 API/弱网。宿主结果不代表设备结果。

## 未完成与暂停项

- 未完成门禁：独立当前 SHA 审查、Android QA 和 provider CI；审查先请求用户或编排方同意，由编排方安排独立 QA。本次实现与宿主验证已完成。
- 外部阻塞：无。
- 用户暂停：合并、发布、API Key 保存、真实模型/账号和真机验收保持暂停。

## 下一步

1. 在本分支 PR 的当前 head 上核对 provider CI；最终 PR URL 与 SHA 由 provider 和 AO 完成报告记录。
2. 请求编排方确认是否启动 AO 原生独立审查，并安排绑定最终 SHA 的独立 Android QA；本会话不自行占用设备、不合并、不发布。

## 约束与风险

- 数据/设备/权限边界：不读真实凭据、Cookie、用户会话/备份；测试数据为合成；扫码结果只在内存走现有严格配对解析器，不保存图片或写日志。
- 关键决策：发现仅用于展示和预填；真实 SSH 信任仍来自既有公钥探测/显式确认。请求前持久记录已询问，拒绝/进程重建后不会反复弹窗；系统设置授权返回时恢复发现。所有 NSD 回调串行回到主线程，解析队列带可见代次并处理服务消失。
- 网络/平台风险：AP 隔离、组播过滤会导致空态；NSD 的“在线”是近期可见，不能证明 SSH 入站可达。旧 Android 系统没有取消单次解析 API，退出后只丢弃结果，仍需等待系统回调回收占位；API34+ 尝试停止解析。
- 失败尝试的结论：不能用固定延时假定 NSD 已停止；不能每轮重扫先把全部电脑标为离线；不能给“扫码”标签绑定纯粘贴页面。

## 产物与校验

- PR：[#76](https://github.com/leezzou/tanuki/pull/76)，源分支 `ao/lan-discovery/mobile`，目标 `main`；最终完整 head 见本会话 AO 完成报告/provider PR，不合并、不发布。
- APK/源码 ZIP：只做验证构建，未交付 APK/正式源码 ZIP、tag 或 Release；许可清单在验证后按实际解析归档补齐。源码候选归档与校验只在系统临时目录检查，不作为用户取用附件。生成日志/中间件不作为用户产物。
- 覆盖安装：无；没有设备写入或签名迁移。
- 过期交接清理：已核对本任务直接依赖的主机广播与配对交接；仍含独有协议/验证与设备未完成项，本次保留。没有属于本任务且已完全过期可删的记录；不做跨 PR 清理。
- 普通 PR 不刷新共享状态/印章；由集成维护方在合并后处理。

## PR #76 复审修复

- 继续同一分支/PR：已重新登记归属，provider 与本地均为 `45f61218db6e124afb199d00c60e8de88d0d5c25`，干净工作树下 fetch 后按完整 SHA 刷新分支并核对；没有替换 PR。
- `PRRT_kwDOVgRm4s62DIwC`：无效旧剪贴板现在也先检查是否可覆盖；扫码或手动输入保留链接、核对码与字段校验状态，空输入仍展示无效剪贴板错误。新增“有效扫码结果/手动内容 + 残缺旧链接”回归。
- `PRRT_kwDOVgRm4s62DIwF`：LAN、扫码/粘贴/手动和编辑入口禁用并在回调重查；相机返回与确认同样重查。测试接纳返回 Boolean，控制器拒绝时不替换尝试配置，向导不再等待其他连接结果；阶段/指纹/成功/失败按本向导已接纳配置过滤，确认/取消也核对归属。新增延迟 A 的指纹/成功/失败、UI 陈旧空闲但控制器忙碌、只接收自己配置三项回归。
- 新增共 4 项 JVM 回归；冻结代码后完整流程通过（4m02s），1184 tests / 119 结果文件 / 0 failures/errors/skipped，RemoteWizardTest 53 项全通过，lint 0 errors / 110 warnings / 1 hint。上一版 1180 项/CI 通过只属于旧 head，最新 CI/独立审查仍须绑定推送后的新 SHA。
- 线程在修复推送后使用提供的线程 ID 直接解决；新 head 仍需独立审查与 CI，不沿用旧 head 的任何审查结论。
- 设备保持由独立 QA 会话独占，本次没有启动/操作模拟器或真机、没有 adb 安装。未测范围仍为真实 NSD/权限/相机/页面交互与真实 SSH/DSH。
- 清理复核：沿用本任务唯一记录；相关旧广播/配对记录仍有独有协议与未验事项，继续保留。未修改共享状态/印章。

- 复审验证首轮失败保留：产品/仪器源码编译与 debug 组装通过；1184 项中 1 项新回归失败。原因是已信任主机跳过 `probeHostKey`，测试假传输只在该入口延迟，导致“仍忙碌”断言提前完成；把同一延迟信号应用到认证入口，并显式核对三种终态后重新冻结测试重跑。不是沿用失败结果计通过。

- 复审修复最终命令：`./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:assembleModernDebug :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest :app:lintModernDebug`，BUILD SUCCESSFUL（4m02s）；没有更改依赖/许可清单，没有设备执行。提交前重跑 `check-handoff.py --pr`、`check-publication.py --git` 与源码候选校验。修复与文档收尾统一一次提交/推送，再解决两条原线程。

## 按要求同步 main（仅合并，不运行验证）

- 本次起点为 PR #76 head `cd6f1db7d14df3bc5e2e16ffc1ff0672b2111eb3`，工作树干净；实际 fetch 后 main 为 `6b0b0b3fe4e327fb62fd30ddcbd979f2737ab446`，普通 merge，无 rebase/强推。
- 仅两个文本冲突：`TanukiUi.kt` 保留 main 的存储导航/错误恢复接线，同时保留本 PR 的远程页独立滚动与下拉刷新入口；移除旧远程页分支。`TEST-REPORT.md` 整份取 main，`AGENT-STATE.md`/`CURRENT.json` 没有添加本分支改写。文件页/主题/存储实现沿用 main；NSD 与两项已修复 P2 保留。
- 按编排方最新提速要求，本轮没有构建、没有运行测试或 lint，也未操作设备；上文 1184 项属于合并前结果，不能作为本次集成后验证。推送后 CI 由编排方观察，独立审查须绑定新 head。
- 沿用本任务唯一交接；旧广播/配对记录仍有独有协议与未验事项，未做跨 PR 清理。不合并 PR、不发布。

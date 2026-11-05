# 交接：运行中浏览、手机存储与多格式文件查看

- Agent/会话标识：主 Agent / live-file-manager，三名子 Agent 分工实现与回归，根 Agent 汇总
- 时间：2026-11-05 15:03 CST 开始，完成本轮实现和目标验证
- 状态：0.1.7候选与全部目标验证完成；记录在统一提交前冻结，随后新分支推送并固定字节封装
- 前一记录：原1458咨询已被本次实现替代，必要结论归[项目存储](../WORKSPACE-STORAGE.md)后清理；016发布依据归[测试现状](../TEST-REPORT.md)，审批依据归[发布说明](../RELEASE.md)

## 用户目标与范围

- 用户要求 DSH 运行时可浏览、导出和导入；音视频等多格式文件可打开，改进 txt 查看器，移除文件数量限制。用户追加要求浏览手机文件并申请存储权限。
- 已完成原生文件页、共享存储授权、文本查看/音视频播放/系统打开、导入导出和对应回归。修改已有文件的编辑、移动、重命名、删除等继续要求停止 DSH；手机位置为只读浏览，可通过 SAF 导出单文件或导入新项目。
- 不修改官方 DSH；API Key 保存继续暂停。用户追加明确要求完成后push新分支并打包0.1.7，由用户合并和发布Release；版本已确认，沿用workspacecheck开发证书，公开/已装/本地最高code8，本次code9。功能debug及独立release验证已完成，交接已审计清理；随后按序一次提交推送新分支，再verified固定字节封装。0.1.6历史六附件保持不变。

## 当前状态

- 已实现与目标验证：运行中读取/导入导出；手机共享存储多卷入口和用户授权；64 KiB 分段文本阅读、当前段搜索/匹配跳转、字号/换行/编码/复制；原生音视频播放及系统应用打开；目录不再设5000项上限，默认容量统计也移除250000条目上限。排序/过滤在后台且旧请求不能覆盖新目录。
- Android 11+用所有文件访问设置，Android 8–10请求存储读取权限；权限不开放其他App私有数据，也不自动挂载共享存储给DSH/浏览器AI。系统边界依据：[Android官方文档](https://developer.android.com/training/data-storage/manage-all-files)。低API/SD/USB尚未设备验证。
- 导入原子提交到新的UUID项目，不覆盖已有项目；工作区/单文件导出检查文件身份、大小和mtime，读取期间改变则失败，SAF回调尝试删除不完整目标。整个工作区并非原子快照；普通软链/特殊文件仍明确拒绝。Android `/data/user/0` 与 `/data/data` 别名在导出边界检查前规范化。
- 前半轮debug阶段目标 `com.tanuki.modern.workspacecheck`，起点release/0.1.6，debug 0.1.6/code8、min26/target34、ARM64，14,916,433字节，SHA256 `c386a91a682c1797f32a47235ff9301d9e51732087b4c3770f088db09a622100`。不是本次新release。
- 复用 `Pixel_7_API_34_arm64` / `emulator-5572`，测试前无node/proot/apt/dpkg/真实任务。仅同包同证书覆盖，不卸载/清数据/wipe/恢复快照。debug阶段安装本轮debug，随后发布阶段覆盖为0.1.7/code9 release与配套测试APK；临时MANAGE AppOp在仪器结束后由宿主恢复为原 `default`，没有遗留运行任务。前轮文档未提交改动全部保留。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `MainActivity.kt` | 手机授权/返回刷新，运行中导入导出与SAF单文件导出、失败目标清理及进度接线 |
| `storage/WorkspaceFileManager.kt`、`TextFileReader.kt` | 读写权限分离，无固定目录数量限制，手机只读卷解析，多格式识别与分页/编码读取，单文件副本/变化检测 |
| `storage/PhoneStorageAccess.kt`、`WorkspaceOpenFileProvider.kt`、manifest与`workspace_open_paths.xml` | 系统授权、共享卷发现，独立非导出只读provider仅暴露所选文件缓存；不共享整个私有目录 |
| `storage/ProjectArchive.kt`、`WorkspaceStorage.kt`、`DirectoryUsage.kt` | 运行中传输、分块取消/真实字节进度与变化检测、移除默认条目数上限；保留字节预算/磁盘余量/统计耗时约束 |
| `ui/WorkspaceFilesPage.kt`、`WorkspaceFileViewer.kt` | 运行中浏览与传输入口、后台筛选/排序、全页文本/图片/媒体查看器、权限提示与导出入口 |
| `TextFileReaderTest.kt`、文件管理/展示既有测试及新增LiveProjectTransfer/PhoneStorageAccess/WorkspaceVideoPresentation/FileTransferPresentation测试 | 编码分页、运行中读写边界、5001/5051/6000条目、并发导出、取消、实际播放器、权限与系统选择器回归 |
| `app/src/androidTest/assets/workspace-synthetic-colors.mp4` | 自生成3秒H.264 Baseline红绿蓝测试视频，无第三方素材；3056字节，SHA256 a57bbcbe20e0a9bf80b6b1912ffdc075978d51742d96a10a53ff328172431735，随源码包分发，不依赖.build生成器 |
| `app/build.gradle.kts`、`scripts/package-source.py`、发布/取包/更新/上手/上传说明 | 用户确认0.1.7/code9后的默认版本、源码命名与交付流程 |
| 本记录、AGENT-STATE、WORKSPACE-STORAGE、SECURITY、TEST-REPORT、CURRENT.json | 说明实现、权限、真实验证与未验边界 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| 阅读交接/上手/README/安全/存储/测试；impeccable context及产品/Android指导 | 完成；沿用现有Material3，无新UI/媒体依赖 | 不恢复已暂停API Key工作 |
| Gradle `-PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:assembleModernDebug :app:assembleModernDebugAndroidTest :app:testModernDebugUnitTest :app:lintModernDebug` | 最终产品构建2分45秒、86任务成功；100 JVM，0失败/错误/跳过；lint0错误/75警告/1Hint | 初次32秒编译失败为provider可空返回，修正后通过；75警告含所有文件访问的ScopedStorage提示，不宣称清零 |
| 测试夹具收尾后 `:app:assembleModernDebugAndroidTest :app:lintModernDebug` | 1分2秒、63任务成功 | 仅测试改动，产品APK SHA保持上述值 |
| `apkanalyzer manifest application-id/print`、apksigner verify、同包 `adb -s emulator-5572 install -r` app/test | 实际包、instrumentation target、版本/debug/min/target、签名通过；实际APK的MANAGE、READ maxSdk29、legacy属性核验通过 | 不是生产签名，不操作真机；权限测试必须区分app与test manifest |
| 宿主临时 `appops set ... MANAGE_EXTERNAL_STORAGE allow`，目标35项仪器 | 29.905秒：34通过、1项manifest夹具失败；其他后端功能均通过 | 后续夹具改按安装包当前API权限检查，maxSdk/legacy由宿主核实际APK，不修改产品权限绕过 |
| 文件页7方法、视频类、SAF类，目标9项仪器 | 45.710秒：7通过、2项断言失败，后续复查通过 | 包含真实DocumentsUI导出/导入、音频、6000列表、文本与手机权限UI；合成RUNNING状态，不启动真实DSH任务 |
| 仅复查manifest方法、运行中菜单方法、视频类 | `OK (3 tests)`，6.945秒，无失败/跳过/中断 | 修正文本子节点不继承disabled语义、视频固定250ms假设，改实际控件+点击阻断及最长8秒实际时钟观测；未改产品、未证明首帧延迟根因 |
| `git diff --check`、`python3 scripts/check-publication.py --git` | 通过；415公共候选、401index条目、0发现（收尾后再次扫描） | 未暂存/提交/推送；公开扫描有既定范围 |
| 交接刷新/无参检查、源码准备包与SOURCE外部SHA | 前半轮全部通过；0.1.7冻结文档后按同一流程刷新，并对真实index再扫 | 不改历史release快照，不将源码ZIP自身散列写回归档 |

- 按最终方法集合去重，本次 **44个不同目标设备用例最终通过**（35后端+9UI），不是首轮全绿；100 JVM为本次新跑。前轮97 JVM/57 release设备方法不混算。
- 首批36项设备尝试：33项完成中24过、9失败（8项为新增导出路径/派生断言，已修正；另1项旧 `FilesystemSemanticsTest#atomicPublicationAndTemporaryDirectoriesSurviveCleanupAndRestart` 触发完整环境下载而中断，未修/未重试、不计最终通过）。随后权限夹具内部恢复AppOp导致仪器进程中断，余下不计通过。将授权/恢复移到宿主后再跑上述目标；未把ADB返回0当成功。
- 途中并行开发导致一次交接印章刷新后立刻过期，后续文件冻结后重新刷新；不是产品测试结果。
- 未测：真实DSH/模型写入任务、真机/MIUI、低API、SD/USB实际卷、更多媒体编码、长期压力、完整Ubuntu/Node/DSH升级；本次故意并发改写/删除由合成文件回调验证。文本搜索针对当前段，媒体/文档格式依设备解码器/已安装应用。编辑仍限UTF8/256KiB；查看无此总大小限制。

## 0.1.7发布追加范围与实测

- 用户在文件功能验证收尾时明确追加：push到新分支并打包0.1.7，由用户合并和发布。已确认版本，不重复询问；workspacecheck沿用开发证书，code9高于公开Feed/0.1.6更新清单、当前设备和本地全部release的最高8。
- `git fetch --no-tags origin main`确认PR #6已由用户合至main/1024eeb；原release/0.1.6与该main完整树相同，直接`git switch -c feature/live-file-manager-017 origin/main`保留全部本地改动，无stash/强推/重复合功能。
- 默认版本和源码包名同步0.1.7/9；发布/取包/应用更新/上手/上传说明更新。提交前按规则迁移必要事实后清理1204/1323/1458，当前只保留本记录、TEMPLATE和CURRENT。原016六附件及SHA复核匹配，未改写。
- 新跑49项宿主（发布18、工作流12、公开19）全过；本次release独立Gradle命令为 `-PtestBuildType=release -PdevelopmentReleaseSigning=true -PtanukiVersionName=0.1.7 -PtanukiVersionCode=9 -I scripts/workspace-verification.init.gradle :app:assembleModernRelease :app:assembleModernReleaseAndroidTest :app:testModernReleaseUnitTest :app:lintModernRelease`，3分2秒、95任务成功；100项release JVM，0失败/错误/跳过，release lint0错误/75警告/1Hint。
- 候选已独立核包名/版本/SDK/测试target/开发证书及non-debuggable；固定副本置于构建准备目录。APK10,424,304字节、SHA256 `aaf92f12bdd407bfa7a9b27be6634d6308a1cc594d43e0accfc63cb0f5ab34ca`，证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`，ARM64、min26/target34。
- 覆盖前debug旧code8运行`ApplicationUpdateNativeTest#privateSyntheticFileSurvivesCoverInstall`，OK(1 test)、0.007秒，种入已知合成标记。随后同包同签名install-r候选与release测试APK，宿主临时授权共享存储，新跑52项release目标（44文件目标+ApplicationUpdateNative3+ApkInstallAccess5，带verifyUpgradeMarker=true），OK(52 tests)、66.258秒，0失败/跳过/中断；读回旧标记并清理，临时权限在仪器结束后恢复default。没有把debug44算作release结果。
- 交接/源码/真实暂存公开检查通过后，统一一次提交push新分支；仅push成功才以脚本verified模式封装上述已测字节，6附件输出至artifacts/releases/0.1.7。不直推main，不代合PR、创建tag或上传Release。提交前冻结公共记录，完成情况以远端分支等于本地HEAD、最终6附件和外部SHA为准；不为回填提交自身制造额外push。

## 未完成与暂停项

- 文件功能及独立0.1.7 release候选已完成目标验证，无阻塞。公共记录在一次提交前冻结；后续推送/verified封装的完成证据为远端feature分支等于本地HEAD及0.1.7实际6附件与SHA清单，不能仅凭本记录推断成功。
- 用户暂停：API Key保存。旧真实账号/模型、真机、完整环境升级等未验范围保留。
- 已fetch确认前轮PR #6由用户合至main/1024eeb；审批设置本次未改，当前新功能PR由用户自行合并，见RELEASE规则历史。

## 下一步

1. 已确认0.1.7/code9，完成实际index/归档检查后统一提交push feature/live-file-manager-017，并verified封装上述固定release APK；用户自行合并PR及发布Pre-release。
2. 真机/SD/低API或真实DSH验收需对应数据/设备许可；继续复用既有模拟器，不创建新AVD或清数据。

## 约束与风险

- 不读取真实Key、Cookie、token、会话/备份；公共证据仅合成文件/视频，不对真实任务安装或instrumentation。
- 字节预算、64MiB设备余量、单次导入512MiB和统计耗时约束保留；取消固定文件数量限制不等于承诺无限设备内存/磁盘。
- 保留官方DSH与审批，不改权限模式、TLS、哈希或WebView进程级整图设置；共享存储权限仅用于原生文件管理，不自动授权AI读手机。
- 子Agent各改独立实现/测试；根Agent串行构建、安装、仪器与汇总，子Agent未覆盖状态/印章。

## 产物与校验

- 本次新增0.1.7/code9 release候选与配套测试输出，目标交付artifacts/releases/0.1.7六附件；0.1.6及更早历史发布保持原样。
- 源码准备区 `artifacts/sources/Tanuki-0.1.7-source.zip` 随收尾文档刷新，外部 `artifacts/SOURCE-SHA256SUMS` 核验；推送后以prepare-app-release.py verified模式生成完整6附件与RELEASE外部清单，不重编已测字节。
- 不安装到真机、不卸载/清数据、不创建tag/Release；只按新授权提交推送功能新分支，本次提交前清理1204/1323/1458：016收尾/旧验证归TEST-REPORT、规则历史/官方链接归RELEASE、旧全禁策略归WORKSPACE-STORAGE，无独有未完成事项；保留本记录/TEMPLATE/CURRENT，已修引用。前轮有用文档与未验边界全部迁移保留。
- 结束前运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261105-1503-live-file-manager.md`、无参检查、`python3 scripts/package-source.py`、在artifacts运行 `shasum -a 256 -c SOURCE-SHA256SUMS`。

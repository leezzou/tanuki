# 交接：视频播放器与DSH手机文件访问

- Agent/会话标识：主Agent / video-shared-storage
- 时间：2026-11-05 16:25 CST开始
- 状态：实现与目标debug验证完成，按用户追加授权提交推送新分支；不发版
- 前一记录：0.1.7旧交接1503已完成并在本次提交前清理，必要事实归TEST-REPORT及专题，无独有未完成项

## 用户目标与范围

- 用户反馈视频点开没有封面、不能全屏/快进/慢放，以及DSH看不到手机文件；本次补视频封面与完整播放控制、打通授权共享存储的客体访问。
- 用户先答可读写，随后明确更正为“默认给读权限，写权限需要专门给”。最终以更正为准：默认只读的原生手机文件RPC与独立原生写授权，每次写入及提交检查授权/撤权epoch，不把系统MANAGE权限或PRoot绑定当作写许可；不直接挂载真实手机卷。验证只用合成文件。
- 测试完成后用户追加“测完就快push到一个分支，我来合并”，授权提交推送新分支 `fix/video-phone-file-permissions`；本次不发新版本、不覆盖既有0.1.7发布附件，API Key保存继续暂停。

## 当前状态

- 接手工作树干净，已测起点为已推送0.1.7/code9功能分支782ad0d；追加push时fetch确认用户已合PR #7到main/ab0d762，完整树相同，以此新建fix/video-phone-file-permissions并保留改动。上轮排查文档已按用户要求撤销，不恢复。
- 旧VideoView仅播放入口和系统MediaController，缺封面/显式倍速/外壳全屏联动；Android共享存储权限只用于原生文件页，Launcher无共享存储通道。直接挂卷无法可靠只读，方案改用独立受控客户端/Skill，不修改官方npm。
- 当前目标包com.tanuki.modern.workspacecheck，测试复用Pixel_7_API_34_arm64/emulator-5572；本次已核身份后覆盖安装debug/app-test，无卸载或清数据。模拟器Ubuntu/Node均不存在，不选依赖真实客体的用例。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| 本记录、AGENT-STATE.md | 新任务范围、授权与验证事实 |
| WorkspaceVideoPlayer、VideoPlaybackPolicy、Viewer/Page、MainActivity/TanukiUi | 封面IO提帧、全屏外壳联动与同播放器控制 |
| PhoneFileAccess、PhoneFileService、PhoneFileIntegration、SharedStoragePaths及手机文件client/Skill、Launcher/RuntimeManager | 默认只读RPC，独立原生本次写权限、撤销epoch；只绑定私有工具目录，不直连手机卷 |
| Manifest/MainActivity/PhoneStorageAccess | Android<=29写入单独申请WRITE，>=30系统MANAGE与App本次写许可分开 |
| 新/改JVM、手机文件策略/HTTP/UI及视频设备回归；scripts/test-phone-client.mjs | 目标回归，不操作真实用户数据 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| 阅读状态、1503交接、上手、README、存储/安全/测试与播放器/Launcher | 完成；接手工作树干净 | 前轮100 JVM/52 release不作本轮结果 |
| Gradle compileModernDebugKotlin/testModernDebugUnitTest | 1分17秒通过；后续还在补传输/权限回归 | 非最终设备验证 |
| 完整debug/app-test/JVM/lint构建 | 首轮测试编译因ACTION_SET_PROGRESS常量引用错误失败，修正后2分9秒成功；补齐最后回归/错误反馈后再次增量验证 | 非release/真机/模型验收 |
| node scripts/test-phone-client.mjs | 13项合成HTTP客户端回归通过；另node --check/diff通过 | 未连接真实DSH或手机数据 |
| 完整debug第四轮构建 | 2分58秒、86任务；104 JVM零失败/错误/跳过，lint0错误/79警告/1Hint | 此轮后修正已纳入最终build7 |
| 首轮目标仪器分组 | 手机权限/HTTP11项通过8.101秒；UI关联34项33通过/1失败133.03秒；未授权单项通过0.315秒 | 唯一失败为暂停后native speed返回0，已改独立保存用户所选速率、播放时重设，后续已验证；不把首轮称全绿 |
| 收尾静态权限核对与回归补充 | 找到旧认证请求延迟body可跨重启借用新授权，已补执行锁内及逐操作连接检查、真实Socket回归；旧系统WRITE回调绑定runId，onResume观察系统写权失效即撤本次许可 | 已由最终目标回归覆盖；旧系统仅静态与通用runId逻辑测试，不称低API实测 |
| 最终产品构建与身份核验 | build7 1分53秒、86任务成功，104 JVM零失败/错误/跳过，lint0错误/79警告/1Hint；app/test实际包名、target、开发证书及debug/SDK核对后install-r | APK 14,813,669字节，SHA256 6d2a91ff4ae1e075446c07836a61790fe982b77d220fcc4ff5cf54417fe5bf3e；非新release |
| 同一产品APK目标设备47项 | 首组44过/3败，137.269秒；失败3项原样单例复查2过/1败，26.135秒；无权限组单项0.143秒通过 | 两项旧UI无修改即通过，未定位首败根因；视频失败稳定发生在无障碍进度动作返回false，补测试等待可操作节点及诊断，保留真实原生seek断言，最终三项已通过 |
| 仅视频测试调整后的app-test/lint及复查 | 重建1分10秒通过；再次核验test target后只覆盖测试APK，产品SHA未变；视频整类OK(3 tests)，12.688秒 | 等待实际可操作的局部Slider节点、选择不同目标，仅提交一次动作并核对原生进度；之前false细因不能确定为标签层/时序/等值之一 |
| 最终方法集合与宿主、lint | 48个不同设备方法最终全通过，无跳过；客户端13项1.041秒通过，JVM104零失败/错误/跳过，lint0错误/79警告/1Hint | 两项旧UI首败原因未定位；不是首组全绿、不是release/实际DSH任务验收 |
| 源码候选公开检查 | 文档收尾前Git扫描427 public/412 index，0发现；diff check通过 | 收尾冻结后继续执行交接/归档/外部SHA检查；原始日志和合成截图留忽略目录，不公开 |

本轮实际构建命令（多次串行，末次产品构建为build7；test-only收尾不重编产品）：

```bash
source scripts/env.sh
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest :app:lintModernDebug
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebugAndroidTest :app:lintModernDebug
node scripts/test-phone-client.mjs
adb -s emulator-5572 shell am instrument -w -r -e class <TEST-REPORT列明的47个目标方法> \
  com.tanuki.modern.workspacecheck.test/androidx.test.runner.AndroidJUnitRunner
```

无系统权限单项在宿主恢复MANAGE AppOp default后独立执行；未在仪器内撤销系统授权导致自杀。UI失败单例及最后视频整类均使用同一产品APK，最后只重装测试APK。封面像素/播放器/速度/进度/全屏及权限拒绝均为实际断言；合成RUNNING/DSH HTML不是模型调用。新真实客体用例未运行，未计作跳过或通过。

## 未完成与暂停项

- 无当前实现待办。待验真实Ubuntu/Node/DSH/模型任务：本机环境缺失，不自动下载，也不以已有测试源码代替执行。
- 真机/MIUI、低API权限弹窗、SD/USB实物、更多媒体编码及长期压力未验；两项旧UI首次失败原因未定位。
- API Key保存仍由用户暂停，本轮未恢复调查或修改。

## 下一步

1. 完成实际暂存/归档检查后统一一次提交推送fix/video-phone-file-permissions，核对远端等于本地HEAD；用户自行合并。不创建tag/Release或覆盖已交付版本。
2. 若后续验证真实客体，先确认既有测试环境已安装且无真实任务，再执行PhoneFileGuestClientTest；不得触发自动安装、清数据或恢复快照。
3. 部署到已有运行环境时，在任务结束后手动重启DSH一次加载新Skill；之后写许可由原生界面即时控制，不需重启。

## 约束与风险

- 子Agent分工独立文件，根Agent汇总状态/印章，不并行Gradle。
- 不读取真实Key/Cookie/token/会话，不清数据/卸载/wipe/恢复快照，不自动中断DSH真实任务。
- 不把PRoot挂载宣称只读或恶意代码沙箱；不开放Android仍禁止的其他App私有数据。

## 产物与校验

- 本次只安装专用模拟器debug/app-test，未触碰真机、未发布新版本；本轮按追加授权提交push新分支。已测产品APK为14,813,669字节/SHA256 `6d2a91ff4ae1e075446c07836a61790fe982b77d220fcc4ff5cf54417fe5bf3e`；最后test APK SHA256 `5177a2e16ca0c5d818eaaaf27d6e650e233b35497933648dc01f333a87d72a9a`。同workspacecheck开发证书，debuggable=true、0.1.7/code9、min26/target34，不能称正式release。
- 既有 `artifacts/releases/0.1.7/` 六文件逐字节SHA核对保持；旧016及更早目录未改。新源码准备区按默认脚本更新，名称仍为Tanuki-0.1.7-source.zip但含本地未发布修改，不能替换已交付源码快照；ARTIFACTS/生成README已明确边界。
- 提交前清理过期1503：任务已完成且由PR #7合入，旧实现/首败/升级/发布验证已归TEST-REPORT，权限边界归WORKSPACE-STORAGE/SECURITY、发布步骤归RELEASE；补迁移合成素材大小/SHA和并发印章失效事实后删除，无独有未完成项。修复全部引用，明确暂存删除。本轮记录、TEMPLATE和CURRENT保留，没有恢复1609调查记录。
- 交接与专题记录公开命令、结果与未验边界，私有诊断/合成截图仍在忽略目录；无真实Key/Cookie/启动token/会话或备份进入源码。不把同UID能力通道或PRoot称恶意代码沙箱。
- 本记录在统一提交前冻结；推送完成以远端分支等于本地HEAD及最终回执为准，不为回填提交自身再造第二次push。文档冻结后运行以下收尾守卫及源码/外部SHA检查；源码散列只放外部清单，避免归档自引用：

```bash
python3 scripts/check-handoff.py --record docs/handoffs/20261105-1625-video-shared-storage.md
python3 scripts/check-handoff.py
python3 scripts/check-publication.py --git
python3 scripts/package-source.py
python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip
(cd artifacts && shasum -a 256 -c SOURCE-SHA256SUMS)
```

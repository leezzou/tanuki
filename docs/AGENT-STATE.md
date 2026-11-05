# Tanuki 当前状态

当前交接：[视频播放器与DSH手机文件访问](handoffs/20261105-1625-video-shared-storage.md)。本轮实现和目标debug回归完成，用户追加要求测完即push新分支，由用户合并；本轮按此推送 `fix/video-phone-file-permissions`，**不发新版本**。既有0.1.7六个发布附件保持原样；API Key保存继续用户暂停。

上一轮0.1.7交付的验证已归[TEST-REPORT](TEST-REPORT.md)，已推送基线为 `feature/live-file-manager-017` / `782ad0d1c39acad3bd5679ee8049403d4bab5671`。用户已将其合为PR #7 / origin/main ab0d762，完整树等于782ad0d；本轮从该main建立新分支。用户自行合PR/发布Pre-release，Agent不代合main、创建tag或上传Release。用户上传错误附件后的本地调查改动已在新任务开始前恢复，不恢复旧调查记录。

## 本轮已实现与已验证

- 视频后台提取封面、显式播放/暂停、拖动进度、前后10秒、0.5/0.75/1/1.25/1.5/2倍速、全屏与返回键联动；保持同一VideoView/MediaPlayer，后台暂停、关闭释放。倍速/编码能力取决于设备，保留其他应用打开入口。
- 用户最终要求默认读取、写入专门给。DSH通过受管 `tanuki-phone` Skill/原生RPC访问系统已授权的共享存储，逻辑路径为 `/phone/primary/...` 等；不是可直接cat的Linux挂载。只绑定私有工具目录，不直接挂真实手机卷，不更改官方DSH/npm/模型/审批/沙箱设置。
- 写入默认关闭；「文件 → 手机存储 → 授权DSH写入」原生确认后仅当前运行期允许，可随时撤销。停止/重启/观察到系统写权撤销均恢复只读；分块与提交逐次检查许可、代次及连接。旧认证请求和旧系统授权回调不能借用新运行期许可。Android8–10单独申请WRITE，普通读取只申请READ；Android11+仍需系统MANAGE加本次许可。行为边界见[项目存储](WORKSPACE-STORAGE.md)/[安全](SECURITY.md)。
- 新跑最终modern debug/app-test/JVM/lint：**104 JVM、13客户端宿主项通过，48个不同模拟器目标方法最终通过**；lint0错误/79警告/1Hint。首轮测试编译、暂停速度0、两项旧UI首败及视频无障碍动作等待问题和复查过程均在[测试现状](TEST-REPORT.md)保留，不宣称首组全绿。视频最后仅改测试，产品APK字节保持。
- 已测本地debug为 `com.tanuki.modern.workspacecheck`、0.1.7/code9、ARM64、min26/target34、debuggable=true，14,813,669字节，SHA256 `6d2a91ff4ae1e075446c07836a61790fe982b77d220fcc4ff5cf54417fe5bf3e`，沿用开发证书。不是本轮发布候选，不能拿它覆盖已交付release附件。

## 环境、未验与暂停

- 复用Pixel_7_API_34_arm64/emulator-5572，ARM64/API34/WebView159；最终保留上述debug与配套测试APK。宿主MANAGE AppOp恢复default，合成文件由夹具清理；没有卸载、清数据、wipe、恢复旧快照或触碰真机/旧.modern。
- 本机Ubuntu/Node不存在，真实Launcher→Ubuntu/Node文件客户端用例仅新增/编译，**未运行**；不将合成RUNNING/HTML页面当实际DSH或模型任务。部署到已有运行环境后，需在任务结束时手动重启DSH一次加载新Skill，后续写授权不需重启。
- **待验证：** 真实Ubuntu/DSH/模型任务、真机/MIUI、低API权限弹窗、SD/USB实物、更多媒体编码及长期压力。旧系统权限回调为静态核对和通用runId回归；两项旧UI首次失败未定位原因。**用户暂停：** API Key保存。无当前产品修复阻塞。

## 既有发布与后续

- 0.1.7/code9已交付release：开发证书、non-debuggable、ARM64、min26/target34，10,424,304字节，SHA256 `aaf92f12bdd407bfa7a9b27be6634d6308a1cc594d43e0accfc63cb0f5ab34ca`。上一轮100 JVM/49宿主/52 release设备全过仅为历史范围，本轮未重跑release。
- 本轮刷新交接、专题与源码准备区；`artifacts/sources/Tanuki-0.1.7-source.zip`包含当前未发布修改，已交付源码快照仍在 `artifacts/releases/0.1.7/`。取包边界见[ARTIFACTS](ARTIFACTS.md)，源码ZIP不是新release。
- 本轮在全部验证/文档与公开检查后统一提交并推送 `fix/video-phone-file-permissions`；公共记录在提交前冻结，推送成功以远端分支等于本地HEAD为准，不为回填提交自身制造第二次push。已迁移1503独有素材/失败/旧发布事实至TEST-REPORT及专题后删除该过期交接，无未完成事项；保留本轮记录/TEMPLATE/CURRENT。实际暂存须含此删除，按[上传检查](PUBLICATION.md)执行。
- 下一步由用户合并新分支；若要求发布，先确认新版本、递增code与推送范围，不能沿用上一轮授权覆盖0.1.7。需真实客体验证时先确认已存在环境和无真实任务，再运行TEST-REPORT列明的PhoneFileGuestClientTest，不自动安装组件。

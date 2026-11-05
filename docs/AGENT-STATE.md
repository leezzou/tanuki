# Tanuki 当前状态

最新交接：[0.1.7运行中文件管理与手机存储](handoffs/20261105-1503-live-file-manager.md)。文件功能与独立release候选验证已完成，记录冻结于一次提交前；交付分支为 `feature/live-file-manager-017`，用户自行合PR和发布Pre-release，Agent不直推main、代合、创建tag或上传Release。

## 当前产品与已验证状态

- 版本已由用户确认 **0.1.7/code9**，沿用 `com.tanuki.modern.workspacecheck` 开发证书、ARM64、non-debuggable release、min26/target34；code9高于公开/设备/本地最高8。0.1.6及更早发布附件保持原样。
- 已实现：DSH运行中浏览/预览/导入导出，手机共享存储授权、多卷入口、只读打开/单文件导出；分页多编码文本、当前段搜索/字号/换行/复制；图片/原生音视频与系统应用打开；取消目录固定数量上限，后台筛选/排序。修改已有文件仍需停止DSH；导入新UUID项目不覆盖；导出检测逐文件变化，不承诺全工作区原子快照。
- 新跑release构建、**100 JVM、49宿主、52项release设备方法全通过**，lint0错误/75警告/1Hint。此前debug44项最终通过、初次路径/夹具失败、权限恢复中断与旧完整环境下载失败均在[测试现状](TEST-REPORT.md)保留，不能混算。
- 已测release APK10,424,304字节，SHA256 `aaf92f12bdd407bfa7a9b27be6634d6308a1cc594d43e0accfc63cb0f5ab34ca`；证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。升级覆盖前合成私有标记已读回验证；不是完整Ubuntu/真实账号数据升级验收。
- 使用入口：文件 → 位置下拉 → 手机存储 → 授权访问手机文件。Android11+所有文件访问、旧版读取权限；其他App私有数据和系统受限目录仍不可访问，不自动将共享存储挂给DSH或浏览器AI。格式/字节限制见[项目存储](WORKSPACE-STORAGE.md)，原安全/浏览器规则保持。

## 环境、未验与暂停

- 本机复用Pixel_7_API_34_arm64/emulator-5572，ARM64/API34/WebView159，52项后MANAGE AppOp已由宿主恢复原default；最终保留本轮release0.1.7/9及测试APK。没有卸载/清数据/wipe/恢复快照，不碰真机或已卸载旧.modern。
- **用户暂停：** API Key保存。**未验：** 真机/MIUI、低API、SD/USB实物、更多媒体编码、真实账号/模型/DSH任务、完整运行环境升级与长期压力。当前无外部阻塞，不以合成RUNNING测试冒充实际模型任务。

## Git、交接与交付

- 用户合入0.1.6 PR #6后的最新origin/main为1024eeb，完整树等于原release/0.1.6；本轮从该main建立新功能分支，保留已有改动。旧016已推送b3e5d468并封装核验，其97/49/57与首败/复跑历史证据完整保留TEST-REPORT。
- 本次提交前逐项迁移并清理1204（旧发布验证与完成收尾归TEST-REPORT）、1323（规则完整参数/官方证据归RELEASE）、1458（全禁旧策略被替代，归WORKSPACE-STORAGE）；仅保留当前1503/TEMPLATE/CURRENT，不依赖Git历史或.build接手。原1632/1009/1134/1118证据仍在TEST-REPORT。
- 完成全部版本/测试/专题/交接、公开候选包和实际index检查后统一一次提交push **feature/live-file-manager-017**，再用prepare-app-release.py的verified参数封装上述同一已测字节。公共记录在提交前冻结；推送与封装是否完成以远端该分支等于本地HEAD、`artifacts/releases/0.1.7/`实际六附件和RELEASE-SHA256SUMS为准，不仅凭此记录推断成功，不为回填提交自身再造第二次push。
- 完整取包入口见[ARTIFACTS](ARTIFACTS.md)，流程见[RELEASE](RELEASE.md)/[PUBLICATION](PUBLICATION.md)；用户在网页合PR后发布v0.1.7开发签名Pre-release，上传全部6附件。审批规则历史与官方链接已归发布专题，不把过去参数当当前设置。

# 应用内更新

**当前候选：0.1.13/code18，workspacecheck，基线 `main=9f5a2fe3c1b16e019144d1ff26f94c5babf12bcf`。** 包名 `com.tanuki.modern.workspacecheck`，ARM64、non-debuggable、minSdk26/targetSdk34，沿用开发证书 `371dc301...`。六附件位于 `artifacts/releases/0.1.13/`，实际大小与 SHA-256 以该目录 `app-update.json`、`RELEASE-SHA256SUMS` 为准。未合并、未创建 tag/Release、未上传。

0.1.13 包含基线已合并的原生聊天历史分页/停靠条、设计令牌接入、首次启动引导、文件导入与复制移动、统一失败出口、局域网发现与远程页、横屏阅读/复制反馈、QA-10 会话同步/QA-07 阅读状态、文本完整字符分页、全局安装进度浮层与文件空态（#62/#67/#68/#70/#71/#73/#74/#75/#76/#79/#80/#82/#83）；#84 为动效盘点与方案文档，不宣称方案已实现。本轮仅更新发布版本与必要文档，不改产品逻辑。

**本轮未验证：** JVM 全套、lint、Android 仪器执行、设备安装/升级、模拟器/真机 UI、真实 PRoot/DSH/sshd/中继、弱网、真实模型/账号；按用户要求不等待 QA，先出候选包。历史测试记录不作为本版已测依据。API Key 保存与真机/真实模型验收继续暂停。

入口：**设置 → 应用更新 → 检查更新**。这里更新 Tanuki APK，不是「组件维护与诊断」中的 DSH/Ubuntu/Node 更新。

## 更新页信息层级

主体说明为“只会安装同一来源的更高版本，下载后会校验完整性”，包名、签名、哈希与数据保留说明默认收进“了解详情”。检查更新使用主按钮，当前版本旁通过状态点区分有新版本、已是最新、尚未检查、检查中与失败；后台周期和上次时间为次要说明。既有错误分类、缓存清理/空间设置/重试入口，以及下载、校验和系统安装规则保留。

页面与其他主页面共用 Scaffold 导航/系统栏避让；大字体、深色和末项滚动仍需独立设备 QA，Host 编译不能替代。

## 来源与检查

- 固定读取 `https://github.com/leezzou/tanuki` 的公开 Release 信息，**不调用 GitHub REST API，不需要 GitHub token**。避免匿名 API 额度导致「请求受限」。
- `workspacecheck` / debug 渠道读取公开 `releases.atom` 中最近最多 10 个 tag，再取同一 tag 的 `app-update.json`，包括公开 Pre-release。正式渠道只读取 `releases/latest/download/app-update.json`，从 GitHub 的稳定版跳转取得明确 tag，不跟随预发布。
- 只允许本仓库的合法 tag/附件名、HTTPS 与 GitHub 官方附件 CDN；限制重定向、单份元数据 1 MiB、最多 8 个包条目。未发布清单（404）、没有更高兼容版本、网络/发布页失败分别反馈，不能把「没有 Release」当成「已是最新版」。
- 清单只声明预期包名、版本、SDK、附件名、大小、SHA-256 和证书指纹；应用用固定仓库/tag 构造附件 URL，不信任任意镜像 URL。公开清单不是独立发布者签名，最终还须核对 APK 的实际身份和 Android 安装签名。

实现：[`ApplicationUpdateManager.kt`](../app/src/main/kotlin/com/tanuki/update/ApplicationUpdateManager.kt)、[`AppReleaseManifest.kt`](../app/src/main/kotlin/com/tanuki/update/AppReleaseManifest.kt)、[`AppUpdatePolicy.kt`](../app/src/main/kotlin/com/tanuki/update/AppUpdatePolicy.kt)。

## 后台周期检查（APK 与 DSH 独立）

应用用 WorkManager 注册两个唯一周期任务，均要求联网；它们是系统尽力调度，省电模式或 OEM 后台限制可能推迟，不承诺精确到分钟，也不替代用户主动检查。

- **Tanuki APK**：`tanuki-apk-update-check`，每 **30 分钟**。只读取稳定 Release 的 `https://github.com/leezzou/tanuki/releases/latest/download/app-update.json`，用当前安装的 `BuildConfig.VERSION_CODE` 与清单中同包名条目比较；同 `versionName`、更高 code 也视为更新。恢复快照时按当前安装身份重算可用性，保留远端结果与通知去重。发现更高版本只记录并提示，不下载、不安装。debug/预发布渠道的候选发现仍走“设置 → 应用更新 → 检查更新”的 Atom 流程。
- **DSH 引擎**：`dsh-engine-update-check`，每 **24 小时**。先读 `https://registry.npmmirror.com/@deepseek-ai%2Fdsh`，网络失败或元数据不完整时回退 `https://registry.npmjs.org/@deepseek-ai%2Fdsh`；连接/读取超时各 12 秒。只比较 `dist-tags.latest` 与已安装 `installed.json` 的 `dsh` 版本，不触发下载、安装、快照或 `UpdateManager` 事务。

两份状态独立持久化，不能互相覆盖：

- `files/state/apk-update-check.json`：`lastCheckedAt`、`latestVersion/Code`、`available`、`lastError/At`、`notifiedVersion`。
- `files/state/dsh-update-check.json`：`lastCheckedAt`、`latestVersion`、`available`、`source`、`lastError/At`、`notifiedVersion`。

前台入口：APK 当前版本旁显示真实状态点和短文案，DSH 版本卡片保留“有更新：`<version>`”徽标；手动“检查更新”仍可用，只在检查完成事件同步独立后台快照而不重复联网；开始时间早于后台结果的旧结果不会覆盖较新快照。后台发现新版本且通知权限允许时，分别通过 `updates` 通知渠道提示一次；同一版本由持久化 `notifiedVersion` 去重，不重复骚扰，不自动下载/安装。Android 13+ 未授予 `POST_NOTIFICATIONS` 时静默跳过，手动检查入口会顺带申请权限。DSH 安装/切换/卸载后只按当前安装身份重算 `installedVersion/available`，保留已知 latest 与通知去重，不重新联网。检查失败只记录时间和简短原因，不弹错误、不影响应用使用；最小查询间隔 60 秒避免重复请求。

实现：[`BackgroundUpdateChecks.kt`](../app/src/main/kotlin/com/tanuki/update/BackgroundUpdateChecks.kt)、[`BackgroundUpdateRepositories.kt`](../app/src/main/kotlin/com/tanuki/update/BackgroundUpdateRepositories.kt)、[`UpdateCheckWorkers.kt`](../app/src/main/kotlin/com/tanuki/update/UpdateCheckWorkers.kt)、[`UpdateNotifier.kt`](../app/src/main/kotlin/com/tanuki/update/UpdateNotifier.kt)。

## 下载与安装

1. 检查当前安装的实际包名/证书，仅接受同包名、同签名证书、适配当前 Android 且 `versionCode` 更高的候选。不跨 `com.tanuki.modern.workspacecheck` / `com.tanuki.modern` 迁移。
2. 用户点击「下载更新」才下载，使用应用专属 CDN 范围、实际大小/字节进度、耗时、取消和 SHA-256；单 APK 最多 512 MiB。未知总量/检查/校验阶段是不定进度，不编造百分比。
3. 文件只放 `cacheDir/application-updates`，验证目录/文件归属与链接，下载后及安装前重新检查真实 APK 包名、版本、最低 SDK、签名证书和字节哈希。主线程不做网络、磁盘哈希或 APK 解析。
4. 安装前要求 DSH 已停止、没有组件安装/更新、真实子进程和文件任务；如果环境忙碌，保留已下载文件，提示先停止。检查/下载通过前台任务显示状态，不自动中断真实工作。
5. 更新与浏览器下载分别使用独立的非导出FileProvider子类，保持原authority和目录范围，避免两个authority共用同一组件身份。更新provider仅共享已经复验的APK。Intent同时带data、ClipData和只读标记；仅向实际匹配APK的系统安装处理器补充单文件只读授权，并使用解析到的系统组件，兼容MIUI安装器在Activity启动前的预读。不授予普通APK关联应用，不申请全量应用可见性。需要用户确认安装；未获「安装未知应用」许可时先由用户在系统设置授权，再明确重试，不偷偷自动安装。系统还会校验 APK 的真实签名与安装兼容性。
6. 校验/网络失败或取消不清除现有 App 和环境，不卸载、不 `pm clear`，也不自动回滚用户数据。

补充读取授权最多10分钟，下一次检查/下载/安装准备或启动安装器失败时收回；只保存本应用更新URI与到期时刻，进程重建后继续按原期限回收，不自动重放安装。安装器可能先把读取工作交给后台组件，不能依赖Activity立即返回就撤销授权。实现见 `update/ApkInstallAccess.kt`，FileProvider仍为 `exported=false`，不扩大可共享目录。

旧版本发起安装时的授权逻辑无法由待安装APK倒过来修改。如旧版本已遇到MIUI拒绝读取，可将新APK保存到系统下载目录，使用系统文件管理器手动覆盖一次；保持同包同证书，不卸载或清数据。后续由修复版发起更新使用新的授权链。MIUI真机仍需复验，AOSP模拟器结果不冒充其通过。

实现：[`Downloader.kt`](../app/src/main/kotlin/com/tanuki/download/Downloader.kt)、[`AppUpdateFiles.kt`](../app/src/main/kotlin/com/tanuki/update/AppUpdateFiles.kt)、[`MainActivity.kt`](../app/src/main/kotlin/com/tanuki/MainActivity.kt)、[`update_file_paths.xml`](../app/src/main/res/xml/update_file_paths.xml)。

同包同证书覆盖安装保留私有 Ubuntu/Node/DSH、项目与配置；仅更新 APK 不触发重装组件。安装失败时不要先卸载旧 App，详见 [组件边界](COMPONENTS.md#升级-tanuki-apk-与组件重装) 和 [安全](SECURITY.md)。

## 发布者必须提供什么

未来用户说「发布」时，Agent **先问版本号**（已经明确则不重复问），确认递增的 `versionCode` 和渠道后再打包。历史记录：用户确认过 `v0.1.7`（code9）、`v0.1.8`（code10）、`v0.1.9`（code11）、`v0.1.11`（code13）；本次确认 `v0.1.13`（code18）。封装前须核对公开、已安装及归档上限，确保 code 严格递增。沿用原 workspacecheck 开发证书；目标为 `com.tanuki.modern.workspacecheck`、ARM64、non-debuggable、minSdk26/targetSdk34。


下面命令以本次 0.1.13/code18 候选为例；未来发版替换为用户确认的版本和递增 code：

```bash
source scripts/env.sh
python3 scripts/prepare-app-release.py --version 0.1.13 --version-code 18 --channel workspacecheck \
  --verified-apk <已完成测试的候选.apk> --verified-apk-sha256 <候选实际SHA256>
```

脚本验证实际 APK 包名、版本、SDK、证书和文件字节，生成 `artifacts/releases/0.1.13/`：

- `Tanuki-0.1.13-workspacecheck-arm64-release.apk`：开发证书、non-debuggable 的测试发布包；GitHub 应标 Pre-release。
- **`app-update.json`**：脚本生成的更新清单，必须与 APK 上传到 tag `v0.1.13` 的同一个 Release，不能只上传 APK。
- `RELEASE-SHA256SUMS`、完整 `Tanuki-0.1.13-source.zip`、原生对应源码与校验文件。

清单结构为 `schemaVersion:1` / `packages` 数组，每个包包含 `packageName`、`versionName`、整数 `versionCode/minSdk/bytes`、`assetName`、`sha256`、`certificateSha256`。不要把文档示例或其他包/版本的元数据当成实际附件。更换/重打 APK 后必须重跑生成和外部散列检查。

正式渠道使用 `--channel production` 与仓库外的正式密钥，包名不同且不能覆盖当前测试包。完整步骤见 [发布说明](RELEASE.md)。

## 验证范围

既有回归覆盖公开Feed、清单、来源/大小/哈希、APK身份、缓存文件与签名边界；早期ARM64 API34模拟器曾实际进入更新页并点击检查，得到「暂无可用于应用内更新的Release」，而非API限流。这是历史结果，不代表当前公开 Release 状态或 0.1.9 已通过验证；0.1.9 没有随版本记录完整设备测试。后续候选的实际命令和结果以[测试现状](TEST-REPORT.md)为准。网络始终不可达时仍会失败，不承诺GitHub全球可用。

真实未来 Release 的发现→下载→系统授权→安装完整链仍需在用户上传本版清单及更高版本 APK 后验收；合成私有文件跨覆盖保留、模拟器安装不等于真实账号/真机/全套运行环境升级。最新结果见 [测试现状](TEST-REPORT.md)。

0.1.13 基于 `main=9f5a2fe3` 准备，未合并/未发布；旧版附件保持原样。

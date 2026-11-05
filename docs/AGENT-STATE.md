# Tanuki 当前状态

先读本页、[当前交接](handoffs/20261105-1029-control-return-sync.md)、[上手指南](AGENT-QUICKSTART.md)及任务专题。安全边界见[SECURITY](SECURITY.md)，验证与历史发布集中在[TEST-REPORT](TEST-REPORT.md)。

## 仓库与当前交付

- 仅在根工程开发；origin为主仓库`https://github.com/leezzou/tanuki`（Git用SSH），不再使用fork。原功能/备份分支、stash及旧worktree保留，不自动清理。
- 当前`feat/navigation-browser-control`，按用户要求基于修复分支4a7bc31，包含cbe9e9c悬浮/可选底部避让及全屏占位修复。2026-11-05 fetch复核主库main仍4509972、不含两笔修复；本分支保留两笔，不退回旧main、不强推。
- **松手修正已验证，用户现已授权本地提交**：基于已推主库的d1a6989，按用户纠正，镜片按住时可停在中央，仅松手才吸附最近有效页签，取消回原页；拖动不切权。按钮/权限、登录保护、请求/下载及独立小窗不变。
- 2026-11-05本轮双debug/AndroidTest、97 JVM、11项API36专项通过（162.2秒），lint0/71/1；新增按住中央像素断言在旧版LTR先失败。完整范围见TEST-REPORT，未重跑前轮全部25项。
- 2026-11-04双debug/AndroidTest、97 JVM、25项不同设备专项通过；lint0错误/71警告/1提示。独立按钮误把拖动当点击先复现后修复；只读截图用例补可视帧同步后3次通过，不重复计数。具体失败、范围和方法见测试现状，前轮数字不冒充本轮。

## 环境与数据边界

- Tanuki_UI_API36/emulator-5554，ARM64/API36/WebView133；仅独立`com.tanuki.modern.uipolish.workspacecheck`及合成页面。实际包/版本/签名/target核对，不清数据、不覆盖正常安装、不运行真实环境安装或模型任务。
- AI合法连接可读普通当前页，操作需授权；读取不切页，OAuth页不给AI读写/列出。临时接管30秒归还，显式收回持续有效；页面/后台不自行改授权。
- 保留官方DSH、TLS/哈希/审批；PRoot不是恶意代码沙箱。**API Key保存暂停**，不读/导出真实凭据。

## 产物与下一步

- 当前修正后的独立debug APK在`artifacts/builds/navigation-release-snap`；旧d1a6989包仍在`artifacts/builds/navigation-control`，0.1.5/code7，可覆盖此前独立测试包，不覆盖常用包；当前源码候选为`artifacts/sources/Tanuki-0.1.5-source.zip`。校验见各SHA清单，不混用旧全屏/悬浮包作本次功能。
- 正常Studio共享输出已恢复，普通包名及instrumentation target核对通过，未安装；已测独立APK固定副本另存。
- 用户2026-11-05授权一次功能提交并普通推送至主库`feat/navigation-browser-control`，交付前核对远端SHA与本地HEAD一致；不改main、不建tag/Release或迁移旧fork PR。上次提交收尾的宿主12/19项不计本轮，远端d1a6989已核对。用户随后授权先本地提交松手修正，再排查临时接管30秒交回AI时中央按钮状态未同步的问题；新问题待复现/修复，不自动推送。
- 待验：真机/低API、完整宽屏Activity、真实账号/模型、长期后台/大量复杂标签、完整运行环境升级及安装授权期限进程死亡恢复。本轮不是正式产品验收；上游API34发布证据见测试现状，不混计。

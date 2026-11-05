# Tanuki 当前状态

先读本页、[当前交接](handoffs/20261105-1118-main-merge.md)、[上手指南](AGENT-QUICKSTART.md)及任务专题。安全边界见[SECURITY](SECURITY.md)，历史验证集中在[TEST-REPORT](TEST-REPORT.md)。

## 分支与当前任务

- 仅根工程开发；origin为主仓库https://github.com/leezzou/tanuki（SSH访问）。当前feat/navigation-browser-control，保留悬浮/全屏修复基线4a7bc31；本轮fetch确认主库main已到8b5776a（PR #4），其完整源码树与4a7bc31相同。本轮正常合入8b5776a解决squash历史冲突，产品、测试、构建脚本、依赖/许可均与452aab1逐字节相同；仅收尾文档变化，不改写已发布历史、不强推。
- 中央按钮d1a6989、松手才吸附4ad6661、过期提示修正452aab1均已推主库功能分支；本轮通过普通合并提交接入main。旧分支/stash/worktree保留，不自动合main或发布。
- **已修、已验证并已推功能分支**：自动交回提示曾在再次收回权限后残留，与人工状态矛盾；现在仅在当前可操作状态有效时保留，不清除下载等其他通知，不改变临时30秒/显式撤权持久策略。
- **已验证/未复现须区分**：真实30秒中控制器授权、按钮语义和机器人像素会同步，浏览器页及切至工作台原生玻璃页均通过；未复现用户所述首次自动交回后图标仍为人，不能用已修的旧提示矛盾替代该原始场景结论。
- 前一实现轮双debug/AndroidTest、97 JVM、11个不同API36设备专项最终通过；lint0错误/71警告/1提示。旧小窗测试入口适配后通过；重复计时用例不累计。前轮松手11项/中央25项另见测试现状，不冒充重跑全套。

## 环境与边界

- Tanuki_UI_API36/emulator-5554，ARM64/API36/WebView133，仅独立com.tanuki.modern.uipolish.workspacecheck及合成页。安装前核对包/版本/签名/target及无运行任务，不清数据、卸载或覆盖正常安装。
- AI合法连接可读普通当前页，操作需授权；读取不切页，OAuth页不给AI读写/列出。只有临时接管空闲30秒返回，中央按钮显式收回仍持续有效，后台/切页不自行改权。
- 官方DSH、TLS/哈希/审批、进程级整页绘制不改；PRoot不是恶意代码沙箱。**API Key保存暂停**，不读真实凭据/会话。

## 产物与下一步

- 当前独立debug候选在artifacts/builds/navigation-control-sync（含松手修正及提示一致性）；navigation-release-snap为4ad6661对应旧测试包，navigation-control为d1a6989旧包，均0.1.5/code7。新包可覆盖独立测试包，不覆盖常用安装。
- 已测APK字节固定，最后扩展原生页测试使用固定副本；普通Studio共享输出恢复/核对但未安装。源码候选为artifacts/sources/Tanuki-0.1.5-source.zip，附外部SHA。
- 等用户体验；若首次自动交回仍出现人图标，需确认进入人工的入口、所在页面/是否私有页、前后台与设备版本；只需导航/提示局部证据，不收集账号内容。本轮合并的宿主工作流12项、公开边界19项通过，产品/测试树不变故未重跑Android/JVM；合并提交普通推当前分支后复核远端SHA与main祖先关系，不改远端main或发布。
- 真机/低API/完整宽屏、真实账号/模型、长期后台/复杂标签、完整运行环境升级等仍未验收；不自动建PR、tag或Release。

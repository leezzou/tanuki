# Tanuki 当前状态

当前交接：[0.1.11 发布准备与交接集成](handoffs/20261110-1636-release-0.1.11.md)（android_agent-49，基线 `main=1f7222d0e4ac`，#40 已合并；0.1.11/code13 workspacecheck 候选已核验，PR #44 待合并，未推送、未发布）。上一次集成：2026-11-10，`main=1f7222d0e4ac`。

## 最近 5 条交接

| 日期 | 记录 | 状态 | 已验证 | 未验证 / 暂停 |
|---|---|---|---|---|
| 2026-11-10 | [0.1.11 发布准备与交接集成](handoffs/20261110-1636-release-0.1.11.md) | 候选已生成 | 794 tests/90 文件、lint 0 errors/102 warnings；APK 0.1.11/code13 实核，SHA `192f73f2...31a976d`；六项检查与六附件通过 | 未 push/tag/上传；设备 QA 未跑 |
| 2026-11-10 | [交接机制改造：普通 PR 不再改共享状态](handoffs/20261110-1333-handoff-pr-mode.md) | 已合并（PR #42） | 三模式、CI PR 模式、11 项自测；Host/Android CI 全绿 | 无设备项；显式 `--require-latest-record` 未进 main |
| 2026-11-10 | [原生界面可交互切片](handoffs/20261110-1233-native-ui-chat.md) | 已合并（PR #40，`1f7222d`） | 794 tests / 90 文件；lint 0 errors / 101 warnings；发送/实时流/审批接线与定向回归通过 | 真实 PRoot DSH、输入法/附件、通知、远程/安全模式、设备/仪器 |
| 2026-11-10 | [原生非聊天主链路](handoffs/20261110-1226-native-parity-non-chat.md) | 已合并（PR #38，merge `bc802cf`，head `cf0fb76`） | 626 tests / 74 文件；lint 0 errors / 97 warnings；assemble、Host+Android CI 通过 | 真实 PRoot DSH、设备 UI/仪器；临时 transport 替换仍待办 |
| 2026-11-10 | [原生输入栏与附件组件](handoffs/20261110-1142-input-attachments.md) | 已合并（PR #43，`b075725`） | 41 项目标回归；完整 JVM 684 tests / 78 文件（合并前）；lint 通过 | 真实输入/附件/设备未验 |

更早的 DSH 版本卸载、文件查看器、复制稳定性、远程中继、审批工具、温控、PDF/DOCX/XLSX、安全模式、聊天通知、冷启动等记录保留在 `docs/handoffs/`，可按主题检索。

## 全局未验证与暂停项

- **外部依赖已收口**：PR #40 `DshTransport` 已合并（`1f7222d`）；临时 `DshHttpAdminTransport` 仍在，替换为 `DshAdminTransportAdapter` 属后续清理，不再阻塞发布；归档列表恢复依赖 workspace/follow WebSocket。
- **设备 QA（未授权/未执行）**：真实 PRoot DSH 的 `/connect`、聊天发送/停止/实时流、审批/工具、输入附件、会话管理；远程 SSH/中继；SAF 导入/导出、文件查看器手势、PDF/DOCX/XLSX 大文件；深色/大字体/低 API/真机。
- **用户暂停**：API Key 保存、真实模型/账号验收、真机验收；未获合并/发布授权时不得合并或发布。
- **版本事实**：0.1.9/code11 是当前已发布 Pre-release，未随版本记录完整设备验收；0.1.10/code12 为历史本地候选，未发布；0.1.11/code13 为当前候选（APK SHA-256 `192f73f2...31a976d`、11,890,210 字节），未 push/tag/上传。

## 长期入口

- [交接机制与迁移步骤](HANDOFFS.md)
- [上手指南](AGENT-QUICKSTART.md)
- [测试现状](TEST-REPORT.md)
- [发布说明](RELEASE.md) / [取包说明](ARTIFACTS.md) / [上传检查](PUBLICATION.md)

# Tanuki 当前状态

当前交接：[0.1.12 发布准备](handoffs/20261111-0325-release-0.1.12.md)（android_agent-60，最终基线 `main=f338bdb`，含 #48/#51/#57/#58/#59；0.1.12/code14 workspacecheck 候选已核验，发布 PR #61 待合并，未合并/未发布）。上一次集成：2026-11-11，`main=f338bdb`。

## 最近 5 条交接

| 日期 | 记录 | 状态 | 已验证 | 未验证 / 暂停 |
|---|---|---|---|---|
| 2026-11-11 | [0.1.12 发布准备](handoffs/20261111-0325-release-0.1.12.md) | 候选已生成，PR #61 待合并 | 1044 tests/107 文件；lint 0 errors/101 warnings/1 hint；APK 0.1.12/code14 SHA `27580e6d...949a02`、12,295,334 字节；六附件通过 | 设备/模拟器/真机 PRoot/真实 sshd/弱网未验证；未合并/未发布 |
| 2026-11-10 | [文件页信息层级重构](handoffs/20261110-1936-files-layout.md) | 已合并（PR #58） | 944 tests/103 文件；lint 0 errors/104 warnings；文件权限/传输回归 | 设备 UI、深色/大字体、滚动手势、TalkBack 未验证 |
| 2026-11-10 | [PR #47 第二轮审批/提问修复](handoffs/20261110-1935-pr47-followup.md) | 已合并（PR #54） | 审批抽屉/轻提示、计划顺序、工具合并、提问卡与草稿；目标 JVM 回归通过 | 真实 PRoot 审批/提问、设备 UI、回答 RPC 协议确认未执行 |
| 2026-11-10 | [文本编码识别与兜底显示](handoffs/20261110-1824-text-encoding-fallback.md) | 已合并（PR #53） | 802 tests/90 文件；lint 0 errors/102 warnings；编码回归 28 项通过 | 设备打开真实 GBK/GB18030/UTF-16 文件未验证 |
| 2026-11-10 | [原生聊天渲染质量](handoffs/20261110-1802-native-chat-render.md) | 已合并（PR #51） | 981 tests/104 文件；lint 0 errors/103 warnings/1 hint；定向 56 tests 通过 | 设备流式/Markdown/图片附件/长历史未验证 |

更早的原生输入栏、会话管理、SSH 六步向导、审批工具、设计令牌、文件查看器、复制稳定性、远程中继、DSH 版本卸载等记录保留在 `docs/handoffs/`，可按主题检索。

## 全局未验证与暂停项

- **设备 QA（未授权/未执行）**：设备/模拟器安装与升级覆盖、真实 PRoot DSH 的 `/connect`、聊天发送/停止/实时流、审批/工具、输入附件、会话管理；真机 PRoot、真实 sshd/远程中继、弱网/断线；SAF 导入/导出、文件查看器手势、PDF/DOCX/XLSX 大文件；深色/大字体/低 API/真机。
- **用户暂停**：API Key 保存、真实模型/账号验收、真机验收；未获合并/发布授权时不得合并或发布。
- **版本事实**：0.1.11/code13 是当前已发布 Pre-release（APK SHA-256 `192f73f2...31a976d`、11,890,210 字节）；0.1.12/code14 为当前 workspacecheck 候选（APK SHA-256 `27580e6d...949a02`、12,295,334 字节），基于最终 `main=f338bdb`，PR #61 待合并，未创建 tag/Release/上传附件。

## 长期入口

- [交接机制与迁移步骤](HANDOFFS.md)
- [上手指南](AGENT-QUICKSTART.md)
- [测试现状](TEST-REPORT.md)
- [发布说明](RELEASE.md) / [取包说明](ARTIFACTS.md) / [上传检查](PUBLICATION.md)

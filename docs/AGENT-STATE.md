# Tanuki 当前状态

当前交接：[0.1.14最终rebuild](handoffs/20261111-2313-rebuild-0.1.14.md)（android_agent-98，分支ao/release/0.1.14，最终main=`16c07591b7ba170a77fdc0720b201a77e5477db3`）。0.1.14/code19/workspacecheck普通release入口已重新构建并生成六附件，位于artifacts/releases/0.1.14。APK **13,073,258 字节**，SHA-256 `57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff`；证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。 本轮统一一次新commit/push更新OPEN的PR #105；实际最新head以provider回执为准。未合并/tag/上传。

0.1.14 已合入最终 main 的 #104 常驻 sheet 宿主完整断言，覆盖单一宿主、visible互斥、直属层级和失败详情可空模型；传输与失败详情的产品代码不变。已包含此前 #98/#103 及主题/文案、聊天/分页、动效 A/B/C/D/E/F 等实现。本轮测试类与最终main按字节相等，不保留上一轮发布分支的最小替代断言。 完整 JVM **1408 tests / 144 XML / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 117 warnings / 2 hints**。 本轮无过滤完整复验与独立APK/六附件核验通过；设备/真实链路未验。前轮0910cc1/efc1d9ae的1407项与旧APK事实已归档，不作为本轮结果。

## 最近5条交接

| 日期 | 记录 | 状态 | 已验证 | 未验证/暂停 |
|---|---|---|---|---|
| 2026-11-11 | [0.1.14发布候选](handoffs/20261111-2313-rebuild-0.1.14.md) | 16c07591b候选已生成 | 1408项/144 XML全过；lint0错/117警告/2提示；APK/六附件核验 | 设备/真实链路、最终PR审查/CI |
| 2026-11-11 | [视觉P2](handoffs/20261111-2200-visual-p2-copy.md) | 已合并#103 | 原基线224项定向及lint；本次集成全套另计 | 新包设备复验、VQ-01不在范围 |
| 2026-11-11 | [视觉QA](handoffs/20261111-2108-visual-qa-0.1.14.md) | 已合并#101；独立原始证据 | 原37358849的debug包与限定手工检查 | 1P1/4P2原记录保留，新包未复验 |
| 2026-11-11 | [C批浮层](handoffs/20261111-1900-motion-batch-c.md) | 已合并#98 | 原基线119项定向、compile/lint；集成轮只compile | 系统sheet两项仪器未执行；遗留P2 |
| 2026-11-11 | [八旅程QA](handoffs/20261111-1216-device-qa.md) | 已合并#78；保留原设备证据 | 原8ba7b4b3的合成旅程/跨页缺陷 | 修复后的QA-04/07/09/10/12需新包复验 |

## 全局未验证与暂停项

- **设备待验**：安装/覆盖升级、Android仪器、首启/权限/进度失败恢复、SAF、阅读分页/配置恢复、会话同步、深浅色/大字号/窄屏/TalkBack/RTL/系统栏/输入法与真实Compose帧行为。保留两份独立QA原SHA/APK，不把旧结果当本版通过。
- **能力与遗留**：VQ-01提问真实入口/回答RPC仍不在本轮范围；视觉P2已实现、尚未新包复验；C批记录中的键盘焦点/操作回调及文件页外层宿主两项P2仍保留，不冒称本发布修复。真实目录缺失时工作区兜底，缺真实用量不编造数据。
- **真实链路未验**：真实PRoot/DSH、模型发送/停止/流式、审批/提问/附件、sshd/中继/弱网、真机/低API。
- **用户暂停**：API Key保存、真实模型/账号、真机验收；本次不访问设备、不自行触发独立review、不合并、不打tag、不创建或上传Release。最后合并/tag/上传由编排者处理。
- **后续**：当前完整PR SHA的审查/CI由编排安排；按已核验六附件发布，若更改产品字节应重新验证构建。发布目录源码ZIP与校验清单按最终文档更新，不重新签发已实核APK。

## 交接清理与长期入口

前轮75份、本轮2份中间交接均先迁事实再删除；72份记录保留在[历史事实](DEVELOPMENT-HISTORY.md)，5份审计/规格移入专题；当前handoffs目录7文件，设备证据和未完成事项保留。来源/历史数字按原基线标注。

- [交接机制](HANDOFFS.md) / [上手指南](AGENT-QUICKSTART.md) / [测试现状](TEST-REPORT.md)
- [界面规格](NATIVE-UI-BEHAVIOR-SPEC.md) / [UX审计](UX-AUDIT.md) / [第一波计划](UX-WAVE1-PLAN.md) / [动效审计](MOTION-AUDIT.md) / [文案审计](COPY-AUDIT.md)
- [发布](RELEASE.md) / [取包](ARTIFACTS.md) / [应用内更新](APP-UPDATES.md) / [上传检查](PUBLICATION.md)

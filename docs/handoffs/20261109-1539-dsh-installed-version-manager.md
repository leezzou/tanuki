# 交接：20261109-1539-dsh-installed-version-manager

- Agent/会话标识：android_agent-25（DSH Worker）
- 时间：2026-11-09 15:39 CST
- 状态：部分完成（实现与 JVM 验证完成，待 PR CI、独立 Review 与设备验收）
- 前一记录：[视频全屏允许旋转](20261106-1735-video-fullscreen-full-sensor.md)（PR #17，历史记录保留）

## 用户目标与范围

- 用户本次实际要求：完善 DSH 版本管理：查看已安装版本、占用大小、当前/备用状态，卸载非当前版本，在已安装版本之间切换；基线 `main=5c680b99075b87953a74cf22cfaee89608d058c1`。
- 本次包含：已安装 DSH 清单（严格结构/链接校验、实际占用、安装时间、当前/备用、校验状态）、`switchDsh(version)`、版本管理界面的卸载保护与降级说明、MainActivity 回调、JVM 回归。
- 本次不包含：DSH 下载加速、30 分钟/每日更新检查、旧版数据快照自动恢复、合并/发布/tag、独立 Review 触发、真机或模拟器验证。

## 当前状态

- 已实现：`DshVersions` 纯逻辑清单与切换策略；`RuntimeComponents.installedDshDetails()` 与 `switchDsh()`；`RuntimeService` 的 `DSH_SWITCH`；独立 `DshVersionManagerDialog`；TanukiUi 入口与 MainActivity 回调。
- 已验证：`:app:compileModernDebugKotlin` 与 `:app:testModernDebugUnitTest` 均 BUILD SUCCESSFUL；148 JVM 测试 0 失败/0 错误/0 跳过，其中 `DshVersionsTest` 6 项、`DshSwitchTest` 10 项。
- 待验证：PR 的 Host/Android CI、独立 Codex High Review、真实设备上的对话框布局/切换/卸载、`DataSnapshots.create` 的真实数据量耗时与失败路径。
- 当前分支：`ao/dsh-version-manager/root`（独立 worktree，基线 `5c680b9`）；未运行真实 DSH 任务。
- 关键事实：`DshVersions` 只把程序目录、`.installed`、依赖入口与中间目录都非符号链接的版本视为已安装；`verified` 还要求依赖锁哈希与 `.installed` 一致且存在 `.web-verified`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/DshVersions.kt` | 新增纯 Kotlin 清单/校验/切换策略与元数据事务；优先按 `unix:blocks` 统计实际占用并对硬链接去重；不依赖 Android API。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeComponents.kt` | 清单统一走 `DshVersions`；新增 `installedDshDetails()` 与 `switchDsh()`；切换前检查基础环境与目标 Node 兼容性；复用同一版本正则。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeService.kt` | 新增 `DSH_SWITCH` 前台服务动作。 |
| `app/src/main/kotlin/com/tanuki/ui/DshVersionManagerDialog.kt` | 新增独立“已安装 DSH 版本”对话框：总占用、当前/备用、校验、切换/卸载确认与降级说明。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 仅新增入口按钮、对话框调用与 3 个回调字段。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 仅接线清单读取、切换与卸载回调，并把 `DSH_SWITCH` 纳入前台通知条件。 |
| `app/src/test/kotlin/com/tanuki/DshVersionsTest.kt` | 清单校验、占用/时间、符号链接、校验状态与版本管理卸载保护回归。 |
| `app/src/test/kotlin/com/tanuki/DshSwitchTest.kt` | 当前/未安装/运行中/降级拒绝、成功元数据更新、写入失败回滚、快照/兼容性失败不动元数据。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` | 通过（BUILD SUCCESSFUL，2m45s） | 本 worktree，JDK17；仅告警既有弃用项。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest` | 通过（BUILD SUCCESSFUL） | 148 tests / 0 failures / 0 errors / 0 skipped；`DshVersionsTest` 6/6、`DshSwitchTest` 10/10。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:lintModernDebug` | 通过（BUILD SUCCESSFUL，3m07s） | 仅有既有弃用/可读性告警；lint 报告写入工作材料，不提交。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1539-dsh-installed-version-manager.md` | 通过 | 刷新 `docs/handoffs/CURRENT.json` 指纹。 |
| `python3 scripts/check-handoff.py` | 通过 | 当前记录与源码指纹一致。 |

- 哪些是前一轮结果，哪些是本次新验证：148 JVM 与编译均为本次新跑；历史 132 项、126 项不再冒充本轮结果。
- 未测试、超时、失败和暂未复现的情况：未跑 assemble/仪器/真机；未在真实 userdata 上执行切换快照；未验证 PR CI。

## 未完成与暂停项

- 未完成：PR 创建后的 Host/Android CI、独立 Codex High Review、设备 UI 与真实切换验收。
- 外部阻塞：无（等待 PR 流水线与审查安排）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

## 下一步

1. 在 `/tmp` 独立 clone 提交并开 PR；PR 描述写明基线、改动、测试与未验证项。
2. PR CI 若失败，按实际日志修复并重新推送；不自行合并、不触发 Review。
3. 设备验收覆盖：对话框列出多版本与总占用、当前版本卸载禁用、非当前卸载后刷新、较新版本切换成功且元数据更新、较旧版本按钮禁用并显示“需恢复匹配的数据快照”、运行中禁用操作；另验证 Node 降级后目标版本不兼容时切换被拒绝且指针/快照未变。

## 约束与风险

- 数据/设备/权限边界：切换只创建私有数据快照并改写 `installed.json`；不删除旧版本目录、不恢复旧数据、不操作设备。
- 关键决策及理由：清单与切换策略抽成纯 Kotlin 以便 JVM 回归；占用优先按 `unix:blocks` 分配块统计并回退逻辑长度；`verified` 采用依赖锁哈希 + `.web-verified` 双重依据，缺少标记时仍列出但显示“未校验”；降级继续沿用现有数据兼容规则，不绕过。
- 失败尝试与避免重复踩坑的结论：首个符号链接测试把真实目标放在 `engines` 内，导致目标本身成为合法安装；已改为放在 `engines` 外。占用优先统计实际分配块，`unix:blocks` 不可用时回退逻辑长度，界面文案使用“占用”。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除历史记录；本次仅新增记录与状态条目，避免覆盖其他并发 Worker 的结论。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

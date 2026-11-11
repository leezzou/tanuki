# 交接：进度宿主重构后的布局断言与 CI 修复

- Agent/会话标识：android_agent-97
- 时间：2026-11-11，UTC+08:00
- 状态：测试修复已交付 PR #102；补齐交接记录并验证 CI 门禁中
- 前一记录：[动效 F 批](20261111-1900-motion-batch-f.md)

## 用户目标与范围

修复 main 的 OperationProgressLayoutTest 仍断言旧语法 `else summary?.let` 导致 verify 失败的问题。首次要求只改测试文件，定向与完整 JVM 通过后推送 PR，不改产品、不合并。PR #102 的 Host 检查随后因缺少交接记录失败；用户明确要求修复该 CI 并再次推送，本次仅追加必要交接记录，不改产品、测试断言或 CI 门禁。

## 当前状态

- 基线远端 main 与 provider API 实核为 `13f1b235207abed24e6a662bc67e71fff369c930`。
- 分支 `ao/progress-test/layout-contract`；原测试修复提交 `2f0d8667eee74879d1140d426ed244f91c232718`；[PR #102](https://github.com/leezzou/tanuki/pull/102)。CI 跟进前已通过 AO 认领并核对抓取的 PR SHA 与本地 HEAD 一致，工作树干净。
- 已实现：以当前 WorkspaceFilesPage/TanukiUi 的真实结构替代过期语法要求。已验证：定向与完整 ModernDebug JVM；待验证：补交接后的新 head CI。
- 未生成、安装或分发 APK；目标验证为 Host JVM，不表示 Android 设备验收。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/test/kotlin/com/tanuki/OperationProgressLayoutTest.kt` | 断言外层统一避让导航；文件列表、内部查看器、外部文档路径各一个宿主；runtime overlay 优先；查看器传入实测操作栏高度；退出摘要缓存、入口交互和详情抽屉互斥。 |
| `docs/handoffs/20261111-2200-progress-layout-test.md` | 按 CI 反馈补本次唯一交接记录，保留实际测试证据与范围。 |

## 验证记录

首次修复在 JDK17、SDK36、独立 Gradle/Android 缓存环境下，先执行 `source scripts/ao-worktree-env.sh`，再依次执行：

```bash
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline \
  :app:testModernDebugUnitTest --tests com.tanuki.OperationProgressLayoutTest
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline \
  :app:testModernDebugUnitTest
```

- 定向 BUILD SUCCESSFUL，退出 0，2m58s；JUnit XML 为 6 tests / 0 failures / 0 errors / 0 skipped。
- 完整 JVM BUILD SUCCESSFUL，退出 0，35s；测试任务实际执行，XML 汇总 1380 tests / 140 结果文件 / 0 failures / 0 errors / 0 skipped。
- 首次 `git diff --check` 与 `python3 scripts/check-publication.py --git` 通过，公开检查 0 findings。
- 首次单文件范围执行 `python3 scripts/check-handoff.py --pr --changed-file app/src/test/kotlin/com/tanuki/OperationProgressLayoutTest.kt` 退出 1，原因是缺本次交接记录。随后 [PR Host CI](https://github.com/leezzou/tanuki/actions/runs/43033831917/job/130567387492) 在同一条件失败；本次按用户 CI 修复要求补齐记录。
- 本次文档跟进：`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-2200-progress-layout-test.md` 在显式传入本 PR 测试与交接两个 `--changed-file` 后通过；不指定 `--record`、仅传同一两文件清单也通过。`python3 scripts/check-publication.py --git` 为 0 findings，`git diff --check` 通过。上述 JVM 是原测试提交的实际结果，本次不改可执行代码，不冒充重跑。
- 未执行 lint、Android 仪器、设备操作、真实 PRoot/DSH/模型；跳过原生运行时构建的 Host 检查不作为设备包证据。

## 未完成与暂停项

- 补交接后的当前 head provider CI、独立审查待核对；未启动 AO review，需用户或编排者同意。
- API Key 保存、真实模型/账号、真机验收继续暂停。
- 无产品修复或其他任务包含在本次 CI 跟进中。

## 下一步

1. 验证本次记录与实际 PR 两文件变更集，提交并推送至现有 PR #102，不创建替代 PR。
2. 核对新 head 的 Host 与 Android CI；若失败继续定位。未经明确授权不合并或发布。

## 约束与风险

源码文本契约只能保护宿主接线与条件，不能替代 Compose 动画帧序列、设备布局或触控验收。首次单文件限制与强制交接门禁冲突已如实报告；此次用户明确要求修复缺记录的 CI，追加最小必要文档，不放宽门禁。Git 写操作因 linked worktree 元数据位于工作区外而被拒后，使用已获授权的单次升级重试；未改共享 Git 配置或强推历史。author/committer 均使用 leezzou。

## 产物与校验

交付为 PR 源码与本记录，无 APK、源码 ZIP、Release 或设备数据变动，不覆盖安装。临时测试日志与 PR 描述不提交。普通 PR 不刷新 AGENT-STATE/CURRENT 印章。

提交前检查相关旧记录：动效 F 批记录仍有独有验证、设备未验证事项和实现决策，动效审计仍供其他批次使用，均保留；本次没有确认可删除且属于本任务范围的过期记录，不开展跨任务清理。

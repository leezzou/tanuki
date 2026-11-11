# 交接：文本多字节分页边界

- Agent/会话标识：android_agent-76
- 时间：2026-11-11 14:56 CST 开始
- 状态：实现与 Host 验证完成；以本会话分支 PR 交付，当前 head CI、独立审查与设备 QA 由 provider/AO 后续状态确认
- 前一记录：[文本编码识别与兜底显示](20261110-1824-text-encoding-fallback.md)

## 用户目标与范围

- 修复 QA-12：GBK `.cpp` 的字符首字节在 65535，Android 第一页却返回 `nextOffset=65536`，第二页中文乱码、两页拼接不等于原文。任务提供的设备记录未包含于本次基线；已只读核对独立 QA 工作区中的同名记录，未复制其截图或临时文件。
- 包含：64 KiB 分页与 4 MiB 上限的完整字符切点、严格解码收尾、游标前进约束、GBK 双字节/GB18030 四字节/UTF-8 三与四字节回归、Android 回归源码、对应文档与 PR。
- 不包含：设备安装与执行（独立 QA 负责）、其他查看器或编码识别策略重构、合并、发布、tag/Release。API Key 保存调查、真实账号与真机验收继续暂停。

## 当前状态

- 基线：先核对远端并 fetch，`origin/main=6b0b0b3fe4e327fb62fd30ddcbd979f2737ab446`。从本会话 root 建同级分支 `ao/paging-boundary/page-char-boundary`，未集成其他并发任务。
- 已实现：非 EOF 页从页尾向前最多退 3 字节，逐个严格解码并完成 flush，取最长合法前缀；文本解码与 `nextOffset` 使用同一前缀。未消费字节由下一页重新读取，继续沿用第一页编码。
- 已实现：切点相当于本页完整输入时完成解码收尾；已知非空来源读不到字节时抛中文读取失败，不能伪造游标跳过数据；空文件停止，下一页单字符可推进，上限内放不下完整字符时停止空尾页。
- 已验证：最终源码完整 JVM 1243 tests / 124 文件 / 0 failures / 0 errors / 0 skipped，其中 `TextFileReaderTest` 27/27；lint 0 errors / 109 warnings / 1 hint；工作区 debug app 与测试 APK 构建通过（14m31s）。
- 待验证：PR 当前 head CI、独立审查、Android 平台解码器与真实查看器翻页；PR URL、完整 head 与后续结果以 provider/AO report 为准。验证包 `com.tanuki.modern.workspacecheck`，本会话未安装。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt` | 显式严格解码试探页尾，补完整输入的 flush，不依赖解码器消费位置代表字符边界；非空来源读取失败时防止游标循环或跳字节。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 四组字符的全部内部切点，下一页单字符与中文后缀，文件及已知/未知大小顺序流，逐字节还原与无替代字；读不到字节的来源及上限空尾页保护。 |
| `app/src/androidTest/kotlin/com/tanuki/TextFilePagingBoundaryTest.kt` | 四项 Android 回归，覆盖 9 个内部切点及两类后缀，共 18 个合成文件场景；只写测试缓存。 |
| `docs/WORKSPACE-STORAGE.md` | 说明读取窗口、完整字符游标、flush、空页与非法输入边界。 |
| `docs/TEST-REPORT.md` | 记录最终实际验证结果与设备未验证项。 |
| `docs/handoffs/20261111-1456-text-page-boundary.md` | 本记录；未修改共享状态或印章。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git ls-remote origin refs/heads/main`、`git fetch --no-write-fetch-head origin main` | 通过，远端为任务给定完整 SHA | 普通 fetch 写工作区外 FETCH_HEAD 被拒；无 FETCH_HEAD 写入的 fetch 完成，未切到其他工作区。 |
| `source scripts/ao-worktree-env.sh; ./gradlew -PtestBuildType=debug :app:testModernDebugUnitTest --tests 'com.tanuki.TextFileReaderTest'` | 第一轮 25 tests / 0 failures / 0 errors / 0 skipped | 四组新增用例执行；此时尚未补 flush 和额外边界保护。首次离线运行缺 `jsch:2.28.8`，联网解析后通过。 |
| `./gradlew -PtestBuildType=debug :app:testModernDebugUnitTest :app:lintModernDebug` | 第一轮 BUILD SUCCESSFUL，9m42s；1241 tests / 124 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 109 warnings / 1 hint | 宿主 JDK17/macOS ARM64；不替代最终源码结果。 |
| `./gradlew --console=plain -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest :app:lintModernDebug :app:assembleModernDebug :app:assembleModernDebugAndroidTest` | 最终 BUILD SUCCESSFUL，14m31s；1243 tests / 124 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 109 warnings / 1 hint | app/test APK 构建成功，四项 Android 专项回归编译通过、未执行；未跳过原生构建任务。此前一次候选验证主动中断以补齐 flush，不算通过。 |
| `aapt2 dump badging`、`aapt2 dump xmltree --file AndroidManifest.xml`、SHA-256 | 包名、runner 与 target 核对通过 | app 为 `com.tanuki.modern.workspacecheck` / 0.1.12 / code14 / target34，17,330,545 字节，SHA `0d2149b82e8c4bda595544ca4c7b716f644537383c5a9574b147bcf2de86b8dd`，含 4 个 ARM64 原生库；测试包为 `.workspacecheck.test`，runner `AndroidJUnitRunner`，target `.workspacecheck`，SHA `b4ec84fbf96448e61390c7c8209bd4af9b231236dd72e329bd68c01b515377cc`。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1456-text-page-boundary.md`、`python3 scripts/check-publication.py --git` | 交接检查通过；公开边界 794 public files / 792 index entries / 0 findings | 提交暂存后再次检查实际索引；普通 PR 不刷新共享印章。 |

- 全部执行结果均来自本会话；其他 PR 和历史设备验证没有折算为本次通过。
- 四组断言：GBK 2 字节（1 切点）、GB18030 4 字节（3 切点）、UTF-8 3 字节（2 切点）、UTF-8 4 字节（3 切点）；每个切点分别覆盖空后缀和中文后缀。JVM 还比较可随机访问文件及已知/未知大小顺序流结果，共 54 个跨页场景。
- 未测试：Android 仪器测试未执行；真实查看器下一段/上一段、行号、SAF 提供方、设备 UI、真机、低 API 均未验证。新增 Android 源码存在或编译不等于设备执行。

## 未完成与暂停项

- 未完成：PR 当前 head 的独立 Codex High 审查、独立 Android QA；CI 需按本次实际推送的 SHA 在 provider 核对。本会话交付修复 PR，不将这些待验项算作已完成。
- 外部阻塞：无实现阻塞；设备与审查需要编排方按独立职责安排。
- 用户暂停：API Key 保存、真实账号与真机验收；本次明确不合并、不发布。

## 下一步

1. 在 provider 核对本会话分支 PR 的最终完整 head 与 CI；有本次引入的失败时继续修复并重新验证。
2. 独立 QA 在 PR 最终完整 SHA 上构建并运行 `com.tanuki.TextFilePagingBoundaryTest`，随后按 QA-12 的合成 GBK `.cpp` 复测真实查看器下一段/上一段、第二页中文与行号，并核对两页逐字节还原。
3. 编排方安排当前 head 的独立 Codex High 审查；CI 或 review 发现本次问题时继续修复，不自行合并。

## 约束与风险

- 只读写合成临时文件，不读取真实用户文件，不操作设备，不安装、不清数据、不恢复快照。
- 根因证据：设备报告的 `input.position()` 相关游标已越过不完整字符，而宿主 JVM 留住残字节；不能推断所有平台解码器的内部缓存/替代路径相同。因此严格试探使用完整 decode + flush 入口，不能只给 `endOfInput=true` 而忽略收尾。
- 不完整尾字节最多 3 字节，最多 4 次有界严格解码，内存仍受 64 KiB 页窗口约束。页内本就非法的文本仍走原有替代显示；无损拼接保证以选定编码下合法文本为前提。
- 旧 JVM 用例在旧代码上也能通过，不能宣称 Host 复现了 Android 缺陷；本次增加 Android 专项测试供独立 QA 验证平台实际实现。

## 产物与校验

- 构建产物只作为本地 debug 编译验证，不作为正式签名、发布或已安装证据；不生成正式发布附件。
- 源码候选检查已通过：将公开清单复制到系统临时目录后执行 `python3 scripts/package-source.py`，核对 `artifacts/SOURCE-SHA256SUMS` 与实际归档 SHA-256，再运行 `python3 scripts/check-publication.py --archive <临时源码归档>`，794 文件、0 findings。最终文档写定后重新生成并检查，不沿用旧 ZIP；候选只作检查，不作为用户发布包。
- 提交前交接清理：复核了前一编码记录，仍含 Big5/Shift_JIS/UTF-16、SAF 与设备未完成项，且是共享状态引用的历史依据，因此保留；本次无已确认过期且属于本范围的记录可删除，不做跨 PR 清理。
- 普通 PR 不修改 `AGENT-STATE.md` / `CURRENT.json`；共享状态与印章由集成维护方维护。

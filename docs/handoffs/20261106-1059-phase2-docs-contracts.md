# 交接：20261106-1059-phase2-docs-contracts

- Agent/会话标识：android_agent-4（DSH Worker 1，文档门禁与接口契约）
- 时间：2026-11-06 10:59 CST
- 状态：部分完成（文档/门禁改动已完成并验证；提交、推送、PR 以最终回执为准）
- 前一记录：[PR #8 浏览器输入线程](20261105-1509-browser-input-thread.md)（保留，未删除）

## 用户目标与范围

- 用户本次实际要求：恢复 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/handoffs/CURRENT.json`；新建本轮唯一交接；修复 `check-publication.py --git` 的全部 20 个断链；新建 `docs/INTERFACES.md` 固定控制面/Browser RPC/Phone RPC/进程挂载契约；只改 docs，不改产品代码。
- 本次包含：docs 下的状态、测试报告、专题文档当前状态段落、历史 handoff 的失效链接、接口契约、交接印章。
- 本次不包含：`app/`、`scripts/`、`runtime/`、`artifacts/releases/0.1.8/` 的任何改动；不重打/替换 0.1.8 附件；不构建 APK；不运行 JVM/设备/lint；不创建 tag/Release；不改 main。
- 用户暂停/边界：API Key 保存继续暂停；真实模型/账号未验收；CI 由 W3 建立；不启用 `danger-full-access`。

## 当前状态

- 已实现：恢复 `AGENT-STATE.md` 与 `TEST-REPORT.md`（历史测试全部标注“历史记录，未在本轮复跑”）；新增 `INTERFACES.md`；修复 20 个断链；同步 `AGENT-QUICKSTART`、`APP-UPDATES`、`ARTIFACTS`、`PUBLICATION`、`RELEASE`、`RUNTIME-COMPATIBILITY`、`BROWSER-WORKSPACE` 的过期版本/分支/交接描述。
- 已验证：文档/门禁命令（见下）；0.1.8 产物身份、SHA-256、证书、公开附件可访问性；模拟器 serial/API/ABI/WebView/已安装版本，均为只读。
- 待验证：0.1.8 没有随版本记录设备测试，本轮也未补跑；真实模型/账号、真机/MIUI、低 API、长期后台、CI 均未在本分支验证。
- 当前包名/分支：`com.tanuki.modern.workspacecheck`（0.1.8/code10 已发布）；本轮分支 `chore/phase2-docs-contracts`，基线 `origin/main/c196bbf4d211604bc2076de450a8ed2d52ddde99`。
- 关键事实与定位：接口方法表见 `docs/INTERFACES.md`；Browser 17 个方法见 `app/src/main/kotlin/com/tanuki/browser/AgentBrowserController.kt` 与 `app/src/main/assets/browser/tanuki-browser.mjs`；Phone 11 个方法见 `app/src/main/kotlin/com/tanuki/storage/PhoneFileService.kt`、`PhoneFileAccess.kt` 与 `app/src/main/assets/phone/tanuki-phone.mjs`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `docs/AGENT-STATE.md` | 恢复当前状态；记录 0.1.8 已发布、无设备测试报告、本轮 docs 验证与暂停项 |
| `docs/TEST-REPORT.md` | 恢复测试现状；新增 0.1.8 只读复核与本轮门禁结果；旧测试逐节标注“历史”，不冒充 0.1.8 通过 |
| `docs/INTERFACES.md` | 新增控制面、Browser/Phone RPC、错误码、进程/挂载与变更流程契约；复核后将 Phone 未知方法错误码精确为服务端 `invalid_method`（`PhoneFileAccess` 的 `unknown_method` 仅兜底） |
| `docs/AGENT-QUICKSTART.md` | 更新当前分支/0.1.8 状态、历史测试措辞（54 项标为历史、未在本轮复跑）、接口契约入口、下一步与源码包路径 |
| `docs/APP-UPDATES.md` | 更新已发布版本、0.1.8 清单/路径、验证范围与分支描述 |
| `docs/ARTIFACTS.md` | 当前发布改为 0.1.8/code10；补目录、校验命令与源码快照路径；0.1.7 标为历史；统一“不回写已发布附件”的源码打包措辞 |
| `docs/PUBLICATION.md` | 更新 0.1.8 状态、版本/源码包示例、发布附件不回写边界与分支描述 |
| `docs/RELEASE.md` | 更新 0.1.8 版本/命令/输出目录/tag 示例；保留审批规则历史并标明 0.1.8 已由用户发布 |
| `docs/RUNTIME-COMPATIBILITY.md` | 修正 SDK 行（compileSdk36、modern target34、compat target28）；补充 0.1.8 无设备测试说明 |
| `docs/BROWSER-WORKSPACE.md` | 标明 0.1.8 含输入线程修复、无设备测试报告；历史键盘截图结果改为“历史” |
| `docs/handoffs/20261105-1509-browser-input-thread.md` | 仅修复前一记录的失效链接，指向 `TEST-REPORT` 历史记录与 `AGENT-STATE`；不改写已发生事实 |
| `docs/handoffs/20261106-1059-phase2-docs-contracts.md` | 本记录；按模板补齐目标、证据、未测与下一步 |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --record` 生成，不手写 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `pwd`、`git status --short --branch`、`git rev-parse HEAD` | 工作树干净；分支 `chore/phase2-docs-contracts`；HEAD `c196bbf4d211604bc2076de450a8ed2d52ddde99` | 基线正确，未重置、未改 main |
| `git fetch origin` | 失败：沙箱禁止写主仓库 `.git/worktrees/android_agent-4/FETCH_HEAD`（Operation not permitted） | 未绕过沙箱；`origin/main` 已是 `c196bbf`，无需 fetch |
| `shasum -a 256 artifacts/releases/0.1.8/*.apk *.zip *.tar.gz` | APK `eb695a12…b146e`、源码 ZIP `10ef8ca5…e4c62`、原生源码 `9b71f9e7…28f8a`；与 `RELEASE-SHA256SUMS` 一致 | 只读，未改附件 |
| `aapt dump badging` + `apksigner verify --print-certs` | 包名 `com.tanuki.modern.workspacecheck`、0.1.8/code10、min26/target34、arm64-v8a；证书 SHA-256 `371dc301…cd84f` | 只读；与 `app-update.json` 一致 |
| `curl -L` 公开 Release 的 `app-update.json` 与 APK | 均 HTTP 200；APK 10,479,850 字节 | 只读网络检查，不代表设备安装/升级验收 |
| `adb -s emulator-5572` 只读查询 | API34、arm64-v8a、WebView 159.0.8133.0、已安装 0.1.7/code9 | 未安装、未清数据、未运行仪器；`files/runtime`/`files/engines` 为空仅来自任务基线，未提权读取 |
| `python3 scripts/check-publication.py --git`（修复前） | 20 findings（`AGENT-STATE`/`TEST-REPORT` 缺失 19 处 + 旧 handoff 链接 1 处） | 已全部定位并修复 |
| `python3 scripts/check-publication.py --git`（修复后） | 0 findings，425 个公开文件/索引条目 | 文档链接/公开边界检查，不是产品测试 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1059-phase2-docs-contracts.md` | 通过，刷新 `CURRENT.json`（schema 1、记录 SHA-256、源码指纹、文件数） | 由脚本生成，未手写 |
| `python3 scripts/check-handoff.py` | 退出 0（`Handoff current`） | 同上 |
| `git diff --stat` | 只包含 `docs/` | 无产品代码、构建产物或 0.1.8 附件改动 |

- 哪些是前一轮结果：`TEST-REPORT` 中 0.1.7 及更早的 104 JVM、48/52/54 设备方法、PR #8 的 22 项等全部是历史记录，本轮未复跑。
- 未测试、超时、失败和暂未复现：未构建 APK；未运行 Gradle/JVM/lint/Android 仪器；未验证真实模型/账号、真机/MIUI、低 API、长期后台、CI；`git fetch origin` 因沙箱失败，未重试或绕过。

## 未完成与暂停项

- 未完成：提交、推送与 PR 创建（以最终回执为准）；合并后需在 main 复跑 `check-handoff.py`、`check-publication.py --git`、`verify-workspace.sh`。
- 外部阻塞/待接手：CI 由 W3 建立；0.1.8 没有设备测试报告，若需要证据应另开验证任务。
- 用户暂停：API Key 保存；真实模型/账号验收。
- 本轮未重打源码 ZIP：只改 docs，且不授权改写已发布 0.1.8 附件；下一次交付前由发布流程重新打包。

## 下一步

1. 按明确 docs 路径提交，推送 `chore/phase2-docs-contracts`，创建指向 `main` 的 PR；确认 PR 只含 docs 变化。
2. 合并后在 main 运行 `python3 scripts/check-handoff.py`、`python3 scripts/check-publication.py --git`、`bash scripts/verify-workspace.sh`，确认门禁恢复。
3. 0.1.8 的设备/JVM 测试如需要，另开授权任务实际构建与运行；不要把本记录或历史数字写成 0.1.8 已通过。
4. CI 建立后由 W3 接手；API Key 与真实模型/账号验收仍等待用户恢复。

## 约束与风险

- 数据/设备/权限边界：不读真实 API Key/Cookie/token/会话；只用只读命令检查公开产物和模拟器；不卸载、不 `pm clear`、不 wipe、不恢复快照、不杀其他进程。
- 关键决策及理由：0.1.8 已有发布附件，本轮只补文档门禁与契约，不回写附件、不重打 APK；历史测试保留但逐节标注“历史”，避免把旧结果当 0.1.8 证据。
- 失败尝试与避免重复踩坑：`git fetch` 受主仓库 `.git` 写权限限制，不能在当前沙箱内成功；不要用 `danger-full-access` 绕过，远端基线已正确。`check-handoff.py` 在印章存在前会失败，这是预期；必须先写记录/状态并用 `--record` 刷新。
- 接口契约以源码为准：Browser 17 个方法、Phone 11 个方法；Phone 没有 `clientId`，用运行代次与 `expectedRevision`；不能把 `/phone/...` 当真实挂载。

## 产物与校验

- 新/旧 APK、源码 ZIP：**未更新**。0.1.8 发布目录六附件保持原样；本轮不生成新 APK 或源码 ZIP。
- 覆盖安装/签名范围/未发布事项：未安装、未签名、未发布；无 tag/Release 变更。
- 交接清理：未删除任何旧记录。保留 `20261105-1509-browser-input-thread.md`（PR #8 验证依据）与 `20261105-1625-video-shared-storage.md`（0.1.7 视频/手机文件依据）；仅修复前者的失效链接。保留 `TEMPLATE.md`、`CURRENT.json` 与本记录。
- 文档变化后仍需刷新交接印章；本次已用脚本生成 `CURRENT.json`。源码 ZIP 待下一次交付/发布流程重打，不在本 docs PR 内改写已发布附件。

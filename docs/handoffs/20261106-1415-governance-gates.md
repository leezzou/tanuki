# 交接：20261106-1415-governance-gates

- Agent/会话标识：android_agent-7（DSH 规范维护 Worker，DeepSeek V4.1 Flash）；PR #14 rebase 与复验：android_agent-13；仅测试确定性修复：android_agent-14
- 时间：2026-11-06 14:15 CST；rebase 复验于 2026-11-06 完成（最终 head 以 [PR #14](https://github.com/leezzou/tanuki/pull/14) 为准）
- 状态：实现、离线验证、文档同步与交接清理完成；PR #14 已 rebase 到最新 main，待独立 Codex High Review 与合并授权
- 前一记录：[20261106-1357-android-qa-readiness-audit.md](20261106-1357-android-qa-readiness-audit.md)（W8，随 PR #13 合并）

## 用户目标与范围

- 用户本次实际要求：落实长期研发治理，不对产品功能开发；对齐现有规范，最小修改 AGENTS，新增缺失的 `ORCHESTRATOR-RULES`、`QA-GATES`、`LESSONS`、`code-standards`；实现只读 `scripts/check-development-gates.py` 与离线回归并接入 Host CI；更新当前状态与交接；一个治理 PR，独立 Review 后才合并。
- 本次包含：`AGENTS.md` 入口/角色/合并授权/两轮根因规则；四个专题文档；合并前门禁脚本与其 26 项离线回归；Host CI 步骤；`AGENT-STATE`、`AGENT-QUICKSTART`、`CI`、`PUBLICATION`、`ARTIFACTS`、`RELEASE`、`APP-UPDATES`、`TEST-REPORT`、`AO-WORKTREE-BUILD` 的过期 main/分支表述；W8 只读 QA 报告保留；过期 handoff 清理与印章刷新；PR #14 rebase 到 `origin/main=14d2884` 并复跑交接/门禁/公开检查/源码包。
- 本次不包含：产品 Kotlin/原生代码、协议方法集、`INTERFACES` 契约；AO 配置；0.1.8 附件、版本号、tag/Release；设备执行；合并或发布。PR #15 的设备门禁测试补充不在本 PR。
- 用户暂停/保留：API Key 保存；真实模型/账号验收；真机验收；未获合并/发布授权不得自行合并、直推 main 或发版。

## 当前状态

- 已实现：上述治理文档、门禁脚本、离线回归、Host CI 接入和状态/交接同步；PR #14 已在独立 `/tmp` clone 中 rebase 到 `14d2884`。
- 已验证：门禁离线回归 24/24；真实 `gh`/`ao` 只读 CLI 格式核对（已合并 PR #12 被保守拒绝）；PR #14 rebase 后交接检查、公开候选检查、源码包刷新结果见“验证记录”。
- 待验证：PR #14 的独立 Codex High Review（由 Orchestrator 触发）；最终 head 的 Host/Android CI；真实设备上的设备门禁；0.1.8 的 Ubuntu/Node/DSH 安装与真实模型任务。Android 设备/产品功能不在本 PR 范围。
- 当前分支/基线：`chore/autonomous-development-governance`，基线 `origin/main=14d2884d690f0264b14c5ada8391ad14f42f12f2`。PR #13 已合并（`b2a018d`）；PR #14 打开待审；PR #15（设备门禁测试补充）已合并（`14d2884`）。Wave0 的 PR #10/#11/#12 均已合并。
- 发布边界：0.1.8/code10 已由用户发布，`v0.1.8` tag 仍为 `c196bbf`；本轮不改附件、不新建正式版本。0.1.8 已有 android_agent-10 的首轮模拟器冒烟（0.1.8/code10、冷启动 1616ms、无 FATAL/ANR、WebView 正常），但 Ubuntu/Node/DSH 安装与真实模型未验证，不能写成已验收。
- 关键事实与定位：门禁 `scripts/check-development-gates.py`；离线回归 `scripts/test-development-gates.py`；CI 步骤 `.github/workflows/verify.yml` 的 Host job；编排规则 `docs/ORCHESTRATOR-RULES.md`；QA 证据格式 `docs/QA-GATES.md`；经验证据 `docs/LESSONS.md`；W8 报告 `docs/handoffs/20261106-1357-android-qa-readiness-audit.md`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `AGENTS.md` | 只加入口、五角色/并发、单一状态维护、共享接口先行、集成后复验、两轮根因、独立 Review 绑 SHA、合并授权与门禁入口；细节留在专题 |
| `docs/ORCHESTRATOR-RULES.md` | 新增编排职责、并发上限、共享接口、独立审查、CI/QA 分工、状态印章串行、合并/发布授权、门禁用法与安全边界 |
| `docs/QA-GATES.md` | 新增 QA 适用路径、独立 QA 最少证据、不能算通过的情况、证据分层、门禁用法与记录位置 |
| `docs/LESSONS.md` | 新增带证据的经验：交接/印章并发、AO 配置角色字段、CI APK 非设备物、三类证据不可混算、历史状态≠远端 SHA、AO review 顶层 status、重复 check、旧 SHA 审查、Python 版本差异 |
| `AGENTS.md`、`docs/ORCHESTRATOR-RULES.md`、`docs/LESSONS.md`（复验修正） | 按 `ao project get` 实测配置删除“保存的 `orchestrator.agent=codex`”过期断言，改为 `deepseek-harness`/DeepSeek V4.1 Flash；Reviewer 仍为 Codex/GPT High |
| `docs/code-standards.md` | 新增 Kotlin/Android、Python 3.9、Shell、文档、提交/PR 和审查清单规范 |
| `scripts/check-development-gates.py` | 新增只读合并前门禁：PR 身份/最新 head/全部 check/独立 review/QA 适用与证据结构；失败、空 checks、旧 SHA、自评、未知范围均拒绝 |
| `scripts/test-development-gates.py` | 新增 26 项离线回归，用假 `gh`/`ao` 覆盖旧 SHA、缺 review/CI、QA 漏项、失败/跳过、身份不符、豁免和有效 QA；补 app 内 `SKILL.md` 必须 QA、根目录 Markdown 豁免 |
| `.github/workflows/verify.yml` | 在既有 Host job 增加 `test-development-gates.py`；Android job 和 CI 边界不变 |
| `docs/CI.md` | 记录新回归、合并前门禁、独立 review 与 provider 审批边界 |
| `docs/AGENT-STATE.md` | 以 PR #13 合并后的状态为基础，更新 main/CI/Wave0/W4/W5/0.1.8 冒烟边界；删除过期“待复审/未合并”描述 |
| `docs/AGENT-QUICKSTART.md`、`docs/PUBLICATION.md`、`docs/ARTIFACTS.md`、`docs/RELEASE.md`、`docs/APP-UPDATES.md`、`docs/RUNTIME-COMPATIBILITY.md` | 修正 main/tag/分支/当前任务表述；保留 0.1.8 附件与历史测试边界 |
| `docs/TEST-REPORT.md` | 追加本轮治理门禁、W8 只读核查摘要与 0.1.8 首轮设备冒烟边界；历史数字继续标为历史 |
| `docs/AO-WORKTREE-BUILD.md` | 修正对已清理 W2 交接的引用，事实指向 AGENT-STATE/PR #11 |
| `docs/handoffs/20261106-1357-android-qa-readiness-audit.md` | 保留 PR #13 合并的 W8 报告；仅修正其“前一记录”链接，避免指向已清理的 W3 记录 |
| `docs/handoffs/20261106-1059-phase2-docs-contracts.md`、`20261106-1207-ci-rpc-contract-tests.md`、`20261106-1211-phase2-worktree-build.md` | 迁移必要事实后删除（见“产物与校验”） |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --record` 生成，不手写 |

## 仅测试确定性修复（android_agent-14）

- 原因：`scripts/test-agent-workflow.py` 的 `test_verify_workspace_device_runs_are_serialized_per_serial` 直接断言 `first.stdout.readline()` 首行包含 `DEVICE_LOCK_ACQUIRED`；`device-lock.py` 先启动子进程再 flush 锁行，子进程输出（fixture 的 `Synthetic handoff guard`）可能先进入同一管道，导致偶发失败。
- 改动：仅该用例改为循环读取 stdout，最多等待 30 秒直到出现锁标记；未出现仍断言失败。保留第二任务退出码 3 与“设备任务被拒绝”断言，不放宽设备门禁，不改产品、协议、`device-preflight.py`/`device-lock.py` 逻辑或 0.1.8 附件。
- 验证：目标用例连续 10 次通过；完整 `test-agent-workflow.py` 19 项通过，见“验证记录”。本修复不启动设备、不跑 Gradle/Android 构建。

## Review 修复：app 内 Markdown 不得豁免 QA（android_agent-14）

- Review 意见（P2）：`classify_paths` 在所有路径判断前用 `path.endswith(".md")` 直接豁免，导致 `app/src/main/assets/browser/SKILL.md`、`app/src/main/assets/phone/SKILL.md` 这类随 App 发布到 Android 内 DSH 的产品资源只改 Markdown 时得到 `qa=not_required`，绕过 `app/**` 必须独立 QA 的规则。
- 修复：必需 QA 路径（`QA_REQUIRED_FILES`/`QA_REQUIRED_PREFIXES`）优先判断；Markdown 后缀豁免仅限根目录（路径不含 `/`），与 [QA-GATES](../QA-GATES.md) 的 `docs/**`、根目录 `*.md` 表述一致。`app/**/*.md` 现进入 QA 必需范围。
- 回归：新增 `test_product_skill_markdown_requires_qa`（两个真实资源路径均拒绝）与 `test_root_markdown_remains_exempt`；门禁套件 26 项通过。未改 QA 证据结构、CI 配置或产品代码。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/test-development-gates.py` | 24 tests OK；覆盖旧 SHA、缺 review、空/排队/失败/跳过 checks、QA 漏项/错 SHA/同 session/未知 session/失败步骤/无 Android 步骤、跨仓库 PR、head 变化、纯文档豁免 | 宿主 macOS Python 3.9.6；假 CLI，无网络/设备/真实 PR |
| `python3 scripts/check-development-gates.py --pr 12 --session android_agent-6` | exit 1，`PR 不是打开状态` | 真实 `gh`/`ao` 只读；证明已合并 PR 被拒绝，不证明 open PR 通过 |
| `gh pr view 12 --json …`、`gh pr checks 12 --json …`、`gh pr diff 12 --name-only` | 字段/退出码与脚本解析一致；同一 head 返回 push+PR 两组 check | 只读公开数据；未修改 PR |
| `gh api repos/leezzou/tanuki/commits/main`、`gh run view 42163963031` | main=`a9aa507…`；CI success（Host+Android） | 公开证据；Android job 不重建原生 `.so` |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1357-android-qa-readiness-audit.md`（W7 原记录） | `Handoff refreshed: …` | 历史记录，已被本记录取代 |
| `git clone https://github.com/leezzou/tanuki.git /tmp/tanuki-pr14-*`；`git checkout -B pr14 9a49dfa`；`git rebase origin/main` | 独立 clone `.git` 可写；先 rebase 到 `b2a018d`（冲突含 1357 记录），#15 合并后再次 rebase 到 `14d2884`（冲突仅 `AGENT-STATE`/`CURRENT.json`，1357 按 main 保留） | 未写共享 worktree 的 `.git`；未把 main merge 进分支 |
| `ao project get android_agent --json` | `config.orchestrator.agent`/`config.worker.agent` 均为 `deepseek-harness`，模型 `opencode-go/deepseek-v4.1-flash`；`config.reviewers=[codex/gpt-6.1-sol/high]` | 只读 AO 配置；据此删除“orchestrator.agent=codex”过期断言 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1415-governance-gates.md` | `Handoff refreshed: docs/handoffs/20261106-1415-governance-gates.md (445 public files)` | 印章由脚本刷新；N 以实际输出为准 |
| `python3 scripts/check-handoff.py` | `Handoff current: docs/handoffs/20261106-1415-governance-gates.md (445 public files)`，exit 0 | 不证明自然语言真实 |
| `python3 scripts/test-development-gates.py` | 24 tests OK | 宿主 Python 3.9；离线假 CLI |
| `python3 scripts/check-publication.py --git` | `446 public files, 446 index entries, 0 findings`，exit 0 | 文档链接/公开边界检查，不是产品测试 |
| `python3 scripts/package-source.py` | `source_files=446`、`native_sources=absent`；生成 `artifacts/sources/Tanuki-0.1.7-source.zip` 与 `artifacts/SOURCE-SHA256SUMS` | 工作材料，不提交/不发布；外部 SHA 只在本轮报告给出 |
| `git push --force-with-lease origin HEAD:chore/autonomous-development-governance`；`gh pr view 14` | PR #14 head 更新，触发 Host/Android CI | 仅更新 PR 分支，不合并、不触发 Review/发布 |
| `python3 scripts/test-agent-workflow.py WorkflowTests.test_verify_workspace_device_runs_are_serialized_per_serial` ×10（android_agent-14） | 连续 10/10 通过，单次 6.512–11.453s，均为 `OK` | 宿主 mock；不启动设备/Gradle |
| `python3 scripts/test-agent-workflow.py`（android_agent-14） | `Ran 19 tests in 74.688s`，`OK`，exit 0 | 宿主 mock；设备/Android 未执行 |
| `python3 scripts/test-development-gates.py`（android_agent-14 review 修复） | `Ran 26 tests in 16.588s`，`OK`；新增 app 内两个 `SKILL.md` 拒绝与根目录 Markdown 豁免回归 | 宿主 Python 3.9；离线假 CLI，无网络/设备 |

- 哪些是前一轮结果：Wave0 的 24 项门禁回归、W2 构建、W3 CI/契约测试、0.1.7/0.1.8 历史设备数字均为前轮记录；本表区分 W7 原轮次、PR #14 rebase 后复验与 android_agent-14 的仅测试修复。W8 的 AVD/ADB 核查发生在 W8/#13/#15 分支，本 PR 未重跑；`test-agent-workflow.py` 19 项已由 android_agent-14 在本 PR 重跑（74.688s）。
- 未测试、失败和暂未复现：未运行 Gradle/Android 构建、lint、设备/仪器；无真实 open PR 的完整门禁通过样例；W8 的 `device-preflight.py` 未在真实设备执行；0.1.8 的 Ubuntu/Node/DSH 安装与真实模型未验证。

## 未完成与暂停项

- 未完成：本 PR 的独立 Codex High Review（P2 的 app 内 Markdown 豁免已修复，待复验）；推送后当前 head 的 Host/Android CI 确认；合并与发布未授权。
- 未完成（其他会话）：PR #15 补充的设备门禁测试已随 `14d2884` 合并；W8 的设备门禁虽随 PR #13 合并，但尚未在真实设备执行。
- 设备边界：0.1.8 只有 android_agent-10 的安装/冷启动/基础 UI/WebView 冒烟；Ubuntu/Node/基础工具/DSH 安装、真实模型任务、底部导航点击、真机/MIUI/低 API 未验证。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。

## 下一步

1. 等 PR #14 当前 head 的 Host/Android CI；由 Orchestrator 触发独立 Codex High Review，本会话不自行触发、不自评。
2. Review 意见逐条修复并重审；通过且用户明确授权后才合并，不代合、不直推 main。
3. 合并后在 main 复跑 `check-handoff.py`、`check-publication.py --git`、`test-development-gates.py` 与适用的 `verify-workspace.sh`。
4. PR #15 已合并；真实设备执行前确认无真实数据、无 apt/dpkg/安装/真实任务，不把 host mock 当设备验收。
5. 0.1.8 的 Ubuntu/Node/DSH/真实模型验证另开授权任务；API Key 保存与真实模型/账号验收继续等待用户恢复。

## 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不安装、不卸载、不 `pm clear`、不 wipe、不恢复快照、不启动/停止 App 或模拟器；不使用 `danger-full-access`。
- 关键决策及理由：门禁只读、保守拒绝，空/排队/失败/跳过 checks、旧 SHA、自评、未知范围都不放行；QA 证据只校验结构，不执行命令、不打印命令/日志；纯文档与本门禁自身豁免设备 QA，但仍等 Host CI。
- Git 隔离：共享 worktree 的 `.git` 写操作曾被沙箱拒绝；本轮改用独立 `/tmp` clone 完成 rebase/提交，未绕过沙箱、未改写 main 历史；PR 分支按 `--force-with-lease` 更新。
- 编排配置：当前 `ao project get android_agent` 的 orchestrator/worker 均为 `deepseek-harness`，模型 `opencode-go/deepseek-v4.1-flash`；早期记录中的 `orchestrator.agent=codex` 差异已不存在，不得再写成当前事实。Reviewer 仍是 Codex/GPT High，只做独立 Review。
- W8 归档：W8 报告随 PR #13 合并保留；本 PR 只修正其过期链接，不把 W8 的 host mock 或 AVD 只读结果当成设备验收。
- 门禁局限：脚本能防旧 SHA、空/跳过 check、自评和证据漏项，但不能证明自然语言、截图、设备结果真实；调度者仍需抽查 serial、APK hash、provider 记录和实际输出。
- 失败尝试与避免重复踩坑：本地 `main` 引用曾为 `c196bbf`（旧）；判断基线使用 `origin/main`/provider 实际 SHA。W8 记录原链接指向已清理的 W3，已改为 AGENT-STATE/PR #12。

## 产物与校验

- 新/旧 APK、源码 ZIP：**未更新 0.1.8 附件**；`python3 scripts/package-source.py` 只刷新 `artifacts/sources/` 的默认工作包与 `SOURCE-SHA256SUMS`，该目录被忽略，不提交、不发布。不创建 tag/Release，不改版本号。
- 覆盖安装/签名范围/未发布事项：本 PR 未安装、未签名、未发布；无 tag/Release。0.1.8 的安装/冒烟证据属于 android_agent-10 的独立 QA 会话，不是本 PR 的产物。
- 交接清理：删除已完成的 W1 `20261106-1059-phase2-docs-contracts.md`、W2 `20261106-1211-phase2-worktree-build.md`、W3 `20261106-1207-ci-rpc-contract-tests.md`。迁移位置：W1 事实见 AGENT-STATE/`INTERFACES`/PR #10；W2 见 `AO-WORKTREE-BUILD`/AGENT-STATE/PR #11；W3 见 AGENT-STATE、`CI`、`INTERFACES`/PR #12。保留 `20261105-1509-browser-input-thread.md`、`20261105-1625-video-shared-storage.md`（仍被 TEST-REPORT 引用）、W8 报告、本记录、`TEMPLATE.md`、`CURRENT.json`。
- 文档/脚本变化后已刷新交接印章；源码 ZIP 仅用于检查，不进入公开 PR。

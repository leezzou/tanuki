# 研发经验与规则沉淀

本文件记录 Tanuki 研发过程中可复核的经验。原则：经验必须带真实证据；普通经验先作为“观察”记录，重复出现或影响面扩大后再升级为“规则”；安全、数据、发布或验收边界问题可以立即升级。禁止编造事故、把计划写成已发生事实，或用自然语言总结替代可定位的 PR/CI/源码/文档证据。

证据优先级：公开 PR/commit/CI 记录 > 仓库内专题文档与源码 > 本机只读命令输出（需写明命令与时间）。涉及凭据、用户数据、设备私有目录的内容不进入本文件。

## 状态与升级

- **观察**：已有一次真实证据，先在任务交接或本文件记录，不立即增加硬门禁。
- **规则**：同一问题重复出现，或涉及安全/数据/发布/验收边界；必须有明确证据和可执行动作。
- **门禁**：能自动检查的规则尽量落到脚本或 CI；不能自动判断的写清人工核查点，不伪装成全自动。
- 规则失效或被替代时更新证据和动作，不静默删除历史；已发生的失败不能被改写成“从未发生”。

## L-001 交接/状态印章并发冲突

- 类型：规则（已升级）
- 证据：Wave0 的 PR #12（<https://github.com/leezzou/tanuki/pull/12>）在 W2 合并后需要同步 `docs/AGENT-STATE.md`、`docs/handoffs/CURRENT.json` 并 rebase 解决冲突；当时交接记录明确写到“解决 AGENT-STATE/CURRENT 冲突并重建印章”。同类冲突也出现在 W1/W2/W3 并行交付后的状态汇总。
- 结论：`AGENT-STATE.md`、`CURRENT.json`、交接记录和发布清单只能由一个维护方串行写入。并行 Worker 把要写入的事实、验证命令和未完成项交回编排者，由维护方统一汇总后再刷新印章。
- 门禁/动作：每轮指定唯一状态维护方；提交前重新运行 `check-handoff.py --record` 和无参检查；不把多个 Worker 的本地印章直接合并。

## L-002 AO 项目配置的角色字段不能只看顶层 agent

- 类型：规则（已升级）
- 证据：2026-11-06 在本项目执行 `ao project get android_agent --json`，顶层 `agent` 显示 `claude-code`，而 `config.worker.agent` 与 `config.orchestrator.agent` 均为 `deepseek-harness`（模型 `opencode-go/deepseek-v4.1-flash`）、`config.reviewers=[codex/gpt-6.1-sol/high]`。只看顶层字段会误判实际分工；保存配置也会随编排调整变化。
- 结论：核对角色和 reviewer 时读取 `worker`、`orchestrator`、`reviewers` 的完整配置，不以顶层 `agent` 或显示名代替。配置修改仍由用户/编排者授权，Worker 不自行改 AO 配置。
- 门禁/动作：合并前门禁用 `ao project get` 校验 reviewer 配置；人工复核时同时看 worker/orchestrator 字段。

## L-003 CI 跳过原生构建时，其 APK 不是设备验收物

- 类型：规则（安全/验收边界，立即升级）
- 证据：`.github/workflows/verify.yml` 的 Android job 使用 `-x buildNativeRuntime`；[CI](CI.md) 明确说明 CI 不重建原生 `.so`，其 APK 只用于编译/JVM/lint 门禁。main 的 CI run 42163963031 成功也不能改变这一点。
- 结论：CI 编译出的 APK 不能安装后当作产品验收或发布包；涉及原生运行时、安装、升级和设备行为的结论必须由独立 Android QA 在明确设备上重新构建/安装/执行。
- 门禁/动作：[QA-GATES](QA-GATES.md) 明确禁止把 CI APK 当设备交付物；PR 正文和测试报告必须区分 CI 产物与设备验证。

## L-004 Host、Android、模型证据不能互相折算

- 类型：规则（安全/验收边界，立即升级）
- 证据：[TEST-REPORT](TEST-REPORT.md) 记录 0.1.8 没有随版本设备测试；0.1.7/PR #8 的 JVM 和设备数字均为历史记录。PR #12 的审查记录也出现“Host 全绿、Android 仍在运行”的分层状态，不能用 Host 结果替代 Android。
- 结论：Host 脚本/编译/JVM/lint、Android 设备行为、真实模型/账号任务是三层证据，分别记录、分别引用。缺少一层就写“未验证”，不能把其他层的通过写成全链路通过。
- 门禁/动作：`check-development-gates.py` 只在有任何 Android/构建改动时要求 QA 证据；QA 证据至少一条 Android 内实际执行并通过的步骤；跳过和未配置模型保留为 `unverified`。

## L-005 历史状态文字不等于当前远端 SHA

- 类型：规则（立即升级）
- 证据：2026-11-06 本轮开始时，`AGENT-STATE.md` 仍写 `main/9e3e316`、PR #12“待复审”；实际 `gh api repos/leezzou/tanuki/commits/main` 为 `a9aa507`，PR #12 已合并，main CI run 42163963031 成功。本地 `main` 引用仍停在 `c196bbf`（v0.1.8 tag），也不是最新。
- 结论：baseline 以远端实际 SHA/PR 状态为准；本地 `main`、旧交接、PR 描述和聊天记录都可能过期。同步后再创建分支或判断“最新”。
- 门禁/动作：每轮开始执行远端查询（`git rev-parse origin/main` 或 provider API）并记录 SHA；状态文档更新时同时写远端 SHA 和查询日期。

## L-006 AO review 的顶层 status 不能当审批结论

- 类型：规则（立即升级）
- 证据：对已合并的 PR #12 执行 `ao review ls android_agent-6 --json`，顶层 `status` 为 `ineligible`，但 `latestRun.status=complete`、`latestRun.verdict=approved`、`latestRun.targetSha=5168e8b...`。只看顶层 status 会误判为未通过或状态不明。
- 结论：审批必须读取 `latestRun` 的 `targetSha`、`status`、`verdict`、`harness` 和 provider review 绑定；顶层 status 可能因 PR 已合并/关闭而变成 `ineligible`。
- 门禁/动作：`check-development-gates.py` 只接受绑定当前 head 的 `latestRun`，要求 `complete + approved + 项目配置的独立 reviewer + githubReviewId`；旧 SHA 的 approved 一律不算。

## L-007 同一个 head 可能有重复的 check run

- 类型：观察
- 证据：对 PR #12 执行 `gh pr checks 12 --json name,state,bucket,link` 返回 4 条记录，来自 push 与 PR 两次 workflow run，均绑定同一 head 且均成功。若只按名称去重或只看一条，可能漏掉另一条失败/排队。
- 结论：合并前逐条检查当前 head 的全部 check，任一非 `pass` 都拒绝；不能假设只有一个 Host/Android job。
- 门禁/动作：脚本要求 checks 非空且每一项 `bucket == pass`；不按名称抽样。

## L-008 审查必须绑定具体 SHA，不能沿用旧批准

- 类型：规则（立即升级）
- 证据：PR #12 的 AO review 记录分别有 `435dfaa...` 与 `5168e8b...` 两个 approved；PR #11 的历史 review 记录也包含多个 SHA 的 approved/changes_requested。head 变化后旧结论不代表新提交。
- 结论：每次推送后重新审查当前 head；合并前核对 review 的 targetSha 等于 PR head，不能引用“之前批准过”。
- 门禁/动作：脚本同时读取 PR head 和 review targetSha，并要求相等；head 在查询期间变化时直接拒绝并提示重跑。

## L-009 本地 Python 与 CI Python 版本不同

- 类型：观察
- 证据：本机 `python3 --version` 为 3.9.6，Host CI 使用 Python 3.12；脚本若使用 3.10+ 语法会在本机或迁移环境失败。
- 结论：新增 Python 脚本保持 3.9 兼容（必要时用 `from __future__ import annotations` 和 `typing.Optional`，避免只有 3.10 才支持的语法）；离线回归在双方版本都能运行。
- 门禁/动作：Host CI 运行新脚本的离线测试；迁移/新机器仍按仓库要求使用 Python 3。

## 复盘与引用要求

- 每条经验至少给出一个可定位证据：PR/commit/CI 链接、仓库文件路径或带日期的只读命令。不能只写“上次出过问题”。
- 失败尝试要保留原因和避免重复踩坑的结论，不能抹掉；已修复的问题写清修复证据，不把“已计划”写成“已完成”。
- 新增规则若改变合并/发布/设备边界，需要独立审查；普通观察随治理 PR 提交，重复出现后再升级。

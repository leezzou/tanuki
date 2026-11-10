# 交接与共享状态维护

本文件说明 Tanuki 的交接记录、共享状态（[AGENT-STATE](AGENT-STATE.md) 与 `docs/handoffs/CURRENT.json` 印章）由谁、在什么时候刷新，以及怎样避免再次出现“每个 PR 都改共享文件、main 每合并一个 PR 其余 PR 全部冲突”的死循环。

## 1. 问题与结论

旧机制要求每个 PR 同时修改：

- `docs/handoffs/<本次记录>.md`
- `docs/AGENT-STATE.md` 的“当前交接”
- `docs/handoffs/CURRENT.json` 的源码指纹与记录哈希

`CURRENT.json` 是对整棵公开源码树计算的指纹，任何一个 PR 合并都会让其他 PR 的印章失效；`AGENT-STATE.md` 又是所有 PR 都会改写的共享文件。结果：main 每合并一个 PR，其余 PR 立刻 `CONFLICTING`，逐个 rebase 后 head 变化又让评审作废。

新机制把“写记录”和“刷新共享状态”拆开：

- **普通 PR 只新增自己的 `docs/handoffs/<日期>-<主题>.md`**，不修改 `AGENT-STATE.md`、`CURRENT.json`。
- **共享状态只在集成阶段由一个维护方刷新**：批量合并后、发布前或需要收口时。
- CI 默认使用**普通 PR 模式**，只校验本 PR 的记录自身完整，并强制“公开文件有改动就必须附带交接记录”。
- **发布流程使用严格模式**，要求印章、记录哈希、公开源码指纹和最新记录全部一致。

## 2. 角色与时间点

| 角色 | 何时 | 写什么 | 检查命令 |
|---|---|---|---|
| 普通 Worker / PR 作者 | 开始任务、结束或中断前 | 只新增/补完自己的 `docs/handoffs/YYYYMMDD-HHMM-topic.md` | `python3 scripts/check-handoff.py --pr --record docs/handoffs/<记录>.md` |
| 集成维护方（同一时间只有一人） | 一批 PR 合并后、准备发布前、状态明显滞后时 | 刷新 `AGENT-STATE.md` 指针与最近 N 条；确认最新记录完整；重建印章 | `python3 scripts/check-handoff.py --integrate --record docs/handoffs/<最新记录>.md`，再 `--release` |
| 发布维护方 | 发布打包前 | 只读核对严格模式，不改记录正文 | `python3 scripts/check-handoff.py --release` |
| 只读 Agent | 任意 | 不落盘，输出同模板完整文本交主 Agent | 由主 Agent/集成方执行 |

“同一时间只有一名集成维护方”是硬约束。并行 Worker 只回报告，不写共享状态，也不刷新 `CURRENT.json`。

## 3. 三种检查模式

| 模式 | 参数 | 校验范围 | 是否写文件 |
|---|---|---|---|
| 普通 PR（默认） | `--pr` 或不带参数 | 指定/最新交接记录的结构与完整性；可选变更集守卫 | 否 |
| 集成 | `--integrate [--record PATH]` | 记录已被 AGENT-STATE 链接且是最新记录；带 `--record` 时重建印章 | 带 `--record` 时写 `CURRENT.json` |
| 发布严格 | `--release` | 印章 schema、记录哈希、公开源码指纹、AGENT-STATE 链接、最新记录全部一致 | 否（拒绝 `--record`） |

常用命令：

```bash
# 普通 PR：只校验自己的记录；共享状态落后不会失败
python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md

# CI / 本地带变更清单：公开文件有改动却没有交接记录会失败
python3 scripts/check-handoff.py --pr --changed-from /tmp/changed-files.txt

# 集成：先手动把 AGENT-STATE 指针/最近 N 条改好，再刷新印章
python3 scripts/check-handoff.py --integrate --record docs/handoffs/<最新记录>.md

# 发布前：只读严格核对
python3 scripts/check-handoff.py --release
```

`--changed-file PATH` 可重复传入单个路径；`--changed-from FILE` 每行一个路径，`-` 表示标准输入。CI 用 `git diff --name-only --diff-filter=AMR <base>...HEAD` 生成该清单。

普通 PR 模式的记录检查包括：

- 路径必须是 `docs/handoffs/YYYYMMDD-HHMM-topic.md`，拒绝 `TEMPLATE.md`、路径穿越、绝对路径与符号链接；
- 八个必需章节齐全且非空：用户目标与范围、当前状态、本次改动、验证记录、未完成与暂停项、下一步、约束与风险、产物与校验；
- 不得残留模板占位符；
- 必须显式写明未测试/未验证/未执行/未跑/跳过/待验证之一，不能用“全部通过”掩盖未测范围。

变更集守卫把公开文件分为两类：

- **交接记录与共享状态**（`docs/handoffs/*.md`、`AGENT-STATE.md`、`CURRENT.json`）：允许单独出现，不要求新记录；
- **其余公开文件**（`app/src`、`scripts`、`docs`、`.github` 等，按 `scripts/package-source.py` 白名单）：只要有改动，本次变更集就必须同时新增/修改一条交接记录，否则失败。`app/src/main/jniLibs` 等生成物不在公开清单内，不触发。

## 4. 普通 PR 流程

1. 从最新 `origin/main` 建独立分支，开始任务时记录实际基线 SHA。
2. 从 [交接模板](handoffs/TEMPLATE.md) 复制出唯一记录 `docs/handoffs/YYYYMMDD-HHMM-topic.md`，先写目标/范围/开始状态。
3. 结束、移交或中断前补完：改动文件与目的、实际验证命令及结果、未测试范围、失败与原因、关键决策、未完成/暂停项、下一步、产物边界。
4. 运行普通 PR 检查；共享状态不链接本记录、`CURRENT.json` 仍是旧的，都不影响通过：
   ```bash
   python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md
   ```
5. 只提交本记录、代码/文档改动与必要的过期记录清理。**不要改 `AGENT-STATE.md`，不要改 `CURRENT.json`。**
6. 交付 PR。CI 用 PR 模式校验；源码有改动时，PR 必须包含本次记录。

## 5. 集成阶段流程

触发时机：一批 PR 已合并到 main、准备发布、或 `AGENT-STATE` 明显落后。由唯一的集成维护方在最新 main 上操作，建议使用独立分支和单独 PR（`docs: integrate handoffs`），避免和功能改动混在一起。

1. 同步并确认基线：`git fetch origin && git switch -c ao/<namespace>/integrate-handoffs origin/main`。
2. 选择最新记录（按文件名日期时间排序）：
   ```bash
   ls docs/handoffs/*.md | sort | tail -n 5
   ```
3. 按第 7 节格式更新 `AGENT-STATE.md`：当前交接指针、最近 N 条（建议 N=5）、全局未验证/暂停项、长期入口。旧内容只保留仍有效的事实，其余迁到对应交接记录或 [TEST-REPORT](TEST-REPORT.md)。
4. 确认最新记录完整，并且其“验证记录 / 未完成与暂停项 / 下一步”覆盖这批合并的实际结果。
5. 刷新印章：
   ```bash
   python3 scripts/check-handoff.py --integrate --record docs/handoffs/<最新记录>.md
   ```
   集成模式要求该记录已被 `AGENT-STATE.md` 链接且是当前最新记录。若源码变了但记录正文没变，脚本会拒绝“只重盖章”。
6. 严格复核：
   ```bash
   python3 scripts/check-handoff.py --release
   ```
7. 跑公开与打包检查：
   ```bash
   python3 scripts/check-publication.py --git
   python3 scripts/package-source.py
   ```
8. 一个提交推送并开集成 PR；合并后共享状态回到最新。

发生共享状态冲突时：**不要手工合并两个 `CURRENT.json`**。取 main 的版本，重新执行第 3–5 步生成新印章；`AGENT-STATE.md` 冲突则取 main，再按当前 main 的最新记录重写指针和最近 N 条。

## 6. 发布流程

1. 集成方先完成第 5 节，确保 `--release` 通过。
2. 发布打包脚本 `scripts/prepare-app-release.py` 会调用 `check-handoff.py --release`；失败即停止，不生成发布附件。
3. 手动可再跑一次 `python3 scripts/check-handoff.py --release` 并记录输出。
4. CI 的 `workflow_dispatch` 可选择 `handoff_mode=release` 做同一严格检查；日常 push/PR 仍是 PR 模式。

发布严格模式不会自动修任何东西：记录哈希不匹配说明记录在盖章后被改过；源码指纹不匹配说明盖章后公开文件又变了；“最新记录”不匹配说明还有更新的交接没有集成。按错误提示回到第 5 节。

## 7. 共享状态的目标格式：指针 + 最近 N 条

`AGENT-STATE.md` 不再堆积每个功能的完整实现细节。目标结构：

```markdown
# Tanuki 当前状态

当前交接：`handoffs/YYYYMMDD-HHMM-topic.md`（<主题>；<状态>；基线 `main=<SHA>`；分支/PR；是否合并/发布）。
上一次集成：<YYYY-MM-DD>，基线 `main=<SHA>`。

## 最近 5 条交接

| 日期 | 记录 | 状态 | 已验证 | 未验证 / 暂停 |
|---|---|---|---|---|
| 2026-11-10 | `handoffs/20261110-1333-topic.md` | 已完成 / 待审 / 受阻 | 实际跑过的命令与结果 | 设备/真机/发布等缺口 |

## 全局未验证与暂停项

- 用户暂停：...
- 外部阻塞：...
- 仍待独立 QA：...

## 长期入口

- [测试现状](TEST-REPORT.md)
- [上手指南](AGENT-QUICKSTART.md)
- [交接机制](HANDOFFS.md)
```

规则：

- “最近 N 条”建议保留 5 条，最少 3 条；按记录文件名日期排序，不按 mtime。
- 每条必须能看出：状态、已验证什么、未验证/暂停什么；细节链接到记录，不复制整段实现。
- 当前交接必须是最新记录；这是集成模式的强制项。
- 更早的记录仍保留在 `docs/handoffs/`，可按主题检索，不要求全部列进状态。
- 全局未验证/暂停项不能因为精简而消失；没有则明确写“无”。

## 8. 迁移步骤：把现有 AGENT-STATE 精简为指针 + 最近 N 条

本机制改造 PR 只建立规则和工具，不在同一 PR 里重写全部历史状态；首次迁移由合并后的集成维护方按下列步骤执行。

1. **冻结写入**：确认没有其他 Worker 正在改 `AGENT-STATE.md` / `CURRENT.json`；并行 PR 继续只写自己的记录。
2. **收集现状**：`ls docs/handoffs/*.md | sort | tail -n 10`，确定最近 N 条（建议 5 条）与当前最新记录。
3. **逐条核对事实**：对 `AGENT-STATE.md` 中准备删除的每个功能段落，确认其独有事实已存在于对应交接记录、专题文档或 [TEST-REPORT](TEST-REPORT.md)；缺失的先迁移，不能直接删。
4. **重写状态文件**：按第 7 节结构写“当前交接 + 最近 N 条 + 全局未验证/暂停 + 长期入口”。把“已实现/已验证/待验证”的细节留在记录里，状态只保留结论和链接。
5. **修复引用**：`grep -rn "AGENT-STATE" docs README.md AGENTS.md`，确保没有文档依赖被删除的锚点或段落；断链由 `check-publication.py` 兜底。
6. **保留历史**：不删除仍有未完成事项的交接记录；已过期记录的清理按 [PUBLICATION](PUBLICATION.md) 执行，并在当前记录写清清理范围与结论保留位置。
7. **刷新与验证**：
   ```bash
   python3 scripts/check-handoff.py --integrate --record docs/handoffs/<最新记录>.md
   python3 scripts/check-handoff.py --release
   python3 scripts/check-handoff.py --pr --changed-file docs/AGENT-STATE.md
   python3 scripts/check-publication.py --git
   python3 scripts/package-source.py
   ```
8. **单独提交**：把状态重写、记录更新和印章放在一个 `docs:` 集成 PR 中；合并后其余 PR 不需要再改共享文件。

迁移检查清单：

- [ ] 当前交接链接指向最新记录，且该记录存在、章节完整。
- [ ] 最近 N 条能查到状态、验证、未验证/暂停。
- [ ] 被删除段落的事实有明确落点（记录/专题/TEST-REPORT）。
- [ ] 全局暂停项（如 API Key 保存、真机 QA）仍在。
- [ ] `--release`、`check-publication.py --git`、`package-source.py` 通过。
- [ ] 没有把 `CURRENT.json` 当成手写文件合并。

## 9. 冲突避免规则

- 普通 PR 不写 `AGENT-STATE.md`、`CURRENT.json`；这是本机制的核心。
- 同一时间只有一个集成维护方；集成在最新 main 上串行执行。
- 不手工合并印章；冲突时取 main 后重新 `--integrate`。
- 不为刷新印章而 rebase 功能 PR；PR 模式不再要求印章最新。
- 批量合并后集中集成一次，而不是每合并一个 PR 刷新一次。
- 交接记录文件名全局唯一（日期+时间+主题）；并行任务不共用文件。
- 清理过期记录属于集成/收尾动作，必须写进当前记录并在同一个变更集里完成。

## 10. 仍能查到什么

精简共享状态不会降低可追溯性：

- **当前状态**：`AGENT-STATE.md` 顶部指针 + 最新交接记录。
- **最近记录**：状态文件的“最近 N 条”表格。
- **验证证据**：每条记录的“验证记录”章节；跨版本摘要见 [TEST-REPORT](TEST-REPORT.md)。
- **未完成/暂停**：每条记录的“未完成与暂停项” + 状态文件的“全局未验证与暂停项”。
- **历史主题**：`docs/handoffs/` 全量记录（文件名即日期+主题）。

常用检索：

```bash
# 找某主题的历史交接
grep -rl "远程主机" docs/handoffs
# 看最近 8 条记录
ls docs/handoffs/*.md | sort | tail -n 8
# 找所有未完成项
grep -rn "未完成\|暂停\|待验证" docs/handoffs docs/AGENT-STATE.md
```

## 11. 脚本与自测

- `scripts/check-handoff.py`：三种模式的唯一入口；schema 2 印章包含记录哈希、公开源码指纹、证据指纹和文件数。
- `scripts/test-check-handoff.py`：8 组离线回归，覆盖 PR 容忍落后共享状态、记录缺失/空章节/占位符、路径与符号链接、变更集守卫、集成链接与最新记录、发布篡改检测、目录移动可迁移。
- `scripts/test-agent-workflow.py`：保留端到端交接/构建门禁回归。
- `.github/workflows/verify.yml`：Host job 先跑两个自测，再按 PR 模式校验本次变更；`workflow_dispatch` 可选严格发布模式。

证据指纹（`evidenceFingerprint`）排除 `AGENT-STATE.md`，用于区分“只刷新共享状态”和“源码变了但记录没更新”：前者允许集成，后者拒绝重盖章。发布严格模式仍会校验包含 `AGENT-STATE.md` 的完整公开指纹，所以盖章后再改状态也必须重新集成。

常见失败与处理：

| 错误 | 含义 | 处理 |
|---|---|---|
| `Public files changed, but ... no ... handoff record` | 改了公开文件却没写本次记录 | 新增/更新自己的记录 |
| `Missing/Empty handoff sections` | 记录不完整 | 按模板补章节 |
| `still contains template placeholders` | 模板占位符未替换 | 填写真实内容 |
| `must state unverified/untested scope` | 没写未测范围 | 在验证记录写明未测试/待验证项 |
| `CURRENT.json points to ..., but the latest record is ...` | 共享状态落后 | 集成最新记录 |
| `Stale handoff stamp` | 盖章后公开文件又变了 | 更新记录后重新集成 |
| `Public source changed but the handoff text did not` | 只重盖章没更新记录 | 补记录正文再集成 |

## 12. 相关文件

- [AGENTS.md](../AGENTS.md)：接手与交接总规则。
- [交接模板](handoffs/TEMPLATE.md)：记录章节与占位符。
- [AGENT-STATE](AGENT-STATE.md)：当前状态指针与最近记录。
- [CI](CI.md)：Host/Android 门禁与两种交接模式。
- [上传检查](PUBLICATION.md)：提交前清理、脱敏与源码包。
- [编排规则](ORCHESTRATOR-RULES.md)：单一维护方与并发边界。

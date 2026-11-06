# 研发编排规则

本文件是 Agent Orchestrator（编排者）的专题规则。通用接手、交接和安全边界仍以 [AGENTS.md](../AGENTS.md) 为准；Android 验收标准见 [QA-GATES](QA-GATES.md)，经验沉淀见 [LESSONS](LESSONS.md)，代码与脚本约定见 [code-standards](code-standards.md)。本文件不复制这些文档的细节，只固定编排职责和决策顺序。

## 1. 角色与并发

本轮确认的五类角色：

| 角色 | 承担者 | 职责 |
|---|---|---|
| Orchestrator | DSH / DeepSeek V4.1 Flash | 架构取舍、任务拆分、Worker 调度、独立审查安排、CI/QA/合并推进和复盘 |
| Worker | DSH / DeepSeek V4.1 Flash | 实现、测试、文档与交付；只完成分配任务 |
| Reviewer | Codex / GPT High | 只做独立 Review，不参与开发或编排；审查绑定最新 SHA |
| Android QA | DSH | 独立设备验证、证据采集；不与实现自证 |
| 规范维护 | DSH | 规范、交接、状态、门禁脚本与经验沉淀 |

配置核对（2026-11-06）：实际运行中的编排会话 `android_agent-9` 为 `deepseek-harness`（DSH/DeepSeek V4.1 Flash）；`ao project get android_agent --json` 的 `config.orchestrator.agent` 与 `config.worker.agent` 均为 `deepseek-harness`（模型 `opencode-go/deepseek-v4.1-flash`），`config.reviewers` 为 `codex`/`gpt-6.1-sol`/`high`。运行会话与保存配置可能变化，决策前先以 `ao session get` 和 `ao project get` 核对实际值并报告；本文件不改 AO 配置，也不把保存值当成当前运行值。

- 编排者负责架构取舍、任务拆分、Worker 调度、独立审查安排、CI/QA/合并的推进和复盘；不把实现、测试或设备核查交给同一个执行者自证。
- 常规实现、测试、QA 和规范维护使用 DSH Worker。按任务独立性启动 1 至 3 个 Worker，默认最多 3 个；同一文件或同一核心接口不要交给多个 Worker 并发修改。
- 每个 Worker 使用独立 worktree 和独立分支，经 PR 交付。分支名保持在本会话命名空间内，便于 AO 归属和追踪。
- 需要并行时，优先拆分互不重叠的目录、测试或只读调查；共享接口、状态文件、交接印章和发布脚本由单一维护方串行处理。
- Worker 只完成分配的任务，不做无关重构。只读/审查任务不得写文件或修改他人 PR；实现任务需要继续或修改既有 PR 时，先按 AO 规则声明归属。

## 2. 共享接口与核心文件

- 跨端协议、RPC 方法集、启动/就绪约定、错误码和进程/挂载边界先固定在 [INTERFACES](INTERFACES.md)，再分别实现；不得在多个 Worker 中各自发明同义协议。
- 核心文件（协议实现、`AGENT-STATE`、`CURRENT.json`、发布清单、CI 工作流）同一时间只有一个写入方。其他 Worker 把结论和证据交回编排者汇总。
- 依赖其他分支或外部交付时，先集成到最新基线，再刷新验证；不能把集成前的局部通过当成集成后的结果。
- 同一问题连续两轮没有实质进展时，停止盲试，先分析根因、缩小复现范围或更换方案；第三轮仍无进展则向用户报告阻塞和可选决策。

## 3. 单任务流程

1. 读取最新 `AGENTS.md`、`AGENT-STATE.md` 和本任务相关专题；不要依赖聊天记忆或旧 worktree。
2. 用远端事实确认基线：`git rev-parse origin/main` 或 provider API 的实际 SHA，不能只看本地 `main`、旧交接文字或 PR 描述。基线不是最新时先同步。
3. 明确范围、不包含项、用户暂停项、所需授权和验证层级；复杂任务先写短计划再改文件。
4. 共享接口先定；实现时补回归，分别记录构建、JVM、lint、Android 仪器与设备结果。
5. 提交前按 [PUBLICATION](PUBLICATION.md) 清理过期交接、刷新印章、检查公开候选；提交信息使用 conventional commit，保持单一主题。
6. 推送分支并创建独立 PR，写清摘要、实际执行的测试、未验证范围和风险；有 provider issue 时在正文链接。
7. 请求独立审查，审查对象必须是当前 PR head 的完整 SHA；审查后若有新提交，旧审查不能沿用。
8. 等当前 head 的 CI 和适用 QA 完成；合并前运行 [门禁脚本](#8-合并前门禁)，再决定是否合并。
9. 合并、发布或 tag 需要用户或明确授权的规则；没有授权时停在“可合并候选”，由用户决定。

## 4. 独立审查

- 最新 head 必须经过独立审查。本项目使用 AO 原生 reviewer（配置为 Codex High）；Worker 自评、实现者本地检查或“看起来没问题”都不替代独立审查。
- 审查必须以 PR 的 `targetSha` 绑定当前 head。审查后又推送新提交的，必须重新审查；不能把旧 SHA 的 approved 当成新 head 通过。
- AO 的 approved 只是 AO 内部结论，不等于 GitHub 的 required review 或独立账号 approval，也不解除分支保护。需要 provider review 时按平台要求另行完成。
- 审查意见逐条处理并推送修复；平台支持时把已修复的 thread 标记 resolved。无法处理或不同意时说明理由并请用户决策，不静默忽略。
- 审查者只做审查时，不 claim/attach 目标 PR、不改代码、不运行项目测试或构建；只提交被要求的 provider review。

## 5. CI 与 QA 的分工

- CI 负责可重复的宿主检查、编译、JVM 单测和 lint；它不能替代设备、真实运行时、真实模型或人工验收。
- CI 跳过原生运行时构建时，其 APK 只用于编译/测试门禁，不是可安装交付物，也不能作为设备验收对象。
- 改动 `app/`、`runtime/`、构建配置或安装/执行脚本时，按 [QA-GATES](QA-GATES.md) 安排独立 Android QA；纯文档和治理门禁自身可合理豁免。
- Host、Android 设备和模型三类证据分别记录、分别引用，不能互相折算。缺少一类就明确写“未验证”，不能把其他类的通过写成全链路通过。
- 设备由独立 QA 独占。实现 Worker 不在自己的会话里运行设备测试来替代 QA；设备证据必须绑定 SHA、独立 QA session、AVD/serial/API/ABI、APK 身份与哈希、实际命令/结果和 Android 内执行出处。

## 6. 状态、交接与印章

- 本轮只有一名 `AGENT-STATE.md` / `CURRENT.json` 维护方。其他 Worker 把要写入的事实、验证命令和未完成项作为报告交回，由维护方串行汇总，避免并发覆盖或印章冲突。
- 每个任务创建唯一 `docs/handoffs/YYYYMMDD-HHMM-topic.md`；完成后把仍有效的事实迁入状态、专题文档或 [TEST-REPORT](TEST-REPORT.md)，再删除过期记录并修复引用。
- 状态文字必须区分“已实现 / 已验证 / 待验证 / 用户暂停 / 外部阻塞”。历史测试只能标为历史，不能写成当前轮已复跑。
- 本地 `main`、旧交接、PR 描述都可能过期；当前 baseline 以远端实际 SHA 和 provider 查询为准。
- 每次提交或推送前按 [PUBLICATION](PUBLICATION.md) 的顺序刷新印章、重打源码包并核对外部 SHA；印章只证明公开源文件指纹一致，不证明自然语言内容真实。

## 7. 合并与发布授权

- 没有明确的合并授权，不代合、不直推默认分支、不改分支保护、不改仓库权限。
- 合并前至少满足：PR 身份正确、目标分支正确、head 未变化、全部适用 check 通过、独立审查批准同一 SHA、适用 QA 证据齐全且身份/设备/APK 绑定正确。
- 尊重 provider 的 required review、必需检查和分支保护；AO 结论不能绕过它们，也不能用管理员权限强行合并。
- 发布、tag、Release 和正式附件属于单独授权；日常 debug 构建、合并候选和源码包检查都不等于发布。回滚、删除标签或改写共享历史需要用户明确要求。
- 安全清理只清理生成缓存、临时目录和已确认无用的过期记录；不删除未提交的用户数据、未推送分支、设备安装或历史交付附件。

## 8. 合并前门禁

只读辅助脚本：

```bash
python3 scripts/check-development-gates.py \
  --pr <PR 编号或当前仓库 PR URL> \
  --session <实现该 PR 的 AO worker session id> \
  [--qa-evidence <独立 QA 证据 JSON>]
```

脚本通过 `gh` 读取当前 PR 身份、最新 head、目标分支和当前 head 的全部 check；通过 `ao review ls` 读取绑定当前 head 的 AO 原生 review 的 `latestRun.targetSha`、`status`、`verdict` 和 harness；通过 `ao project get` 核对项目配置的独立 reviewer。以下情况一律退出 1（拒绝）：

- PR 不属于当前仓库、不是打开状态、是草稿、目标分支不是 `main`，或 head 在查询期间变化。
- 没有任何 check，或任一 check 的 bucket 不是 `pass`（排队、失败、跳过、取消都不算通过）。
- 没有绑定当前 head 的 AO review、review 未完成、verdict 不是 approved、reviewer 不是项目配置的独立 reviewer、reviewer 与实现 worker 相同，或没有已提交的 provider review id。
- 改动涉及 Android、构建或安装执行脚本，却没有 QA 证据；QA 证据的 PR/SHA/session/设备/APK/步骤字段不完整，或出现失败步骤。
- 查询命令失败、返回无法解析的 JSON，或项目配置缺失。

通过时输出当前 PR、head、check 数、review harness/target 和 QA 状态；存在跳过的 QA 项或未配置的真实模型链路时，输出 `qa=passed_with_unverified` 并逐项列出，不能显示为全部绿色。

该脚本只校验公开字段和证据结构，不能证明自然语言、截图或设备结果真实；调度者仍需抽查原始记录、设备 serial 与 provider 事实。脚本不执行证据中的命令字符串，不读取密钥，不打印完整日志。

## 9. 安全与边界

- 不读取或输出真实 API Key、Cookie、启动 token、用户会话、内部备份或原始敏感日志；公共证据只用合成数据。
- 不执行未审查的外部命令字符串；不自动切换 DSH 的 `danger-full-access`，不关闭审批、TLS 或哈希校验。
- 不 `pm clear`、不卸载用户 App、不 wipe AVD、不恢复旧快照、不终止其他应用或所有 Node 进程；破坏性动作先取得明确许可。
- 设备测试只用明确选定、数据可测且无真实任务的环境；模拟器结果不等于真机验收。

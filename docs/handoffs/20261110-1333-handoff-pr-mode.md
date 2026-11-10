# 交接：交接机制改造（普通 PR 不再改共享状态）

- Agent/会话标识：android_agent-49（AO Worker）
- 时间：2026-11-10 13:33 CST
- 状态：已完成（PR 待独立审查；不合并、不发布）
- 前一记录：[文件查看器格式支持扩展](../handoffs/20261110-1142-file-viewer-formats.md)

## 用户目标与范围

- 用户本次实际要求：Tanuki 交接文档机制导致每个 PR 都改 `docs/AGENT-STATE.md` 的“当前记录”和 `docs/handoffs/CURRENT.json` 印章，main 每合并一个 PR 其余 PR 立刻冲突，rebase 又让评审作废。要求基于 `origin/main=1cb7ad95` 建独立分支做机制性改造（不改产品代码）：
  1. 普通 PR 只新增自己的 `docs/handoffs/<日期>-<主题>.md`，不再修改共享状态；`AGENT-STATE` 与 `CURRENT.json` 由集成阶段单一维护方统一刷新。
  2. `scripts/check-handoff.py` 普通 PR 模式允许共享状态落后，只校验本记录完整（目标/范围/改动/验证/未验证/下一步）；新增显式严格模式才强制最新。
  3. `.github/workflows/verify.yml` 使用 PR 模式；发布流程保留严格模式。
  4. 新增 `docs/HANDOFFS.md`：谁在何时刷新共享状态、如何避免冲突、如何把 `AGENT-STATE` 迁移为指针 + 最近 N 条。
  5. 不降低交接质量，仍能查到当前状态、最近记录、验证与未完成项；检查脚本要有自测。
- 本次包含：检查脚本三模式与变更集守卫、离线自测、CI、发布/构建入口调用、交接与治理文档、本轮记录与共享状态指针/印章。
- 本次不包含：Android 产品代码、APK 构建、设备/仪器测试、发布打包、合并、tag/Release；`AGENT-STATE` 的“指针 + 最近 N 条”实际精简迁移（由合并后的集成维护方执行，步骤已写入 `docs/HANDOFFS.md`）。
- 用户改变或暂停的要求（需保留）：API Key 保存、真实模型/账号、真机验收仍暂停；不合并、不发布。

## 当前状态

- 已实现：`scripts/check-handoff.py` 支持 `--pr`（默认）、`--integrate`、`--release` 三种模式；PR 模式可接收变更清单，强制“公开文件有改动必须附带交接记录”；集成模式要求记录已链接且为最新并刷新 schema 2 印章；发布模式只读严格校验。新增 `scripts/test-check-handoff.py` 8 组离线回归。CI Host job 改为 PR 模式并支持 `workflow_dispatch` 选择 release 严格模式。`prepare-app-release.py` 改为 `--release`，`verify-workspace.sh` 明确 `--pr`。新增 `docs/HANDOFFS.md` 并同步 `AGENTS.md`、模板、QUICKSTART、PUBLICATION、CI、ORCHESTRATOR-RULES、LESSONS、code-standards、QA-GATES、RELEASE。
- 已验证：`test-check-handoff.py` 8/8、`test-agent-workflow.py` 19/19、`test-publication.py` 19/19、`test-app-release.py` 18/18、PR/集成/发布三种模式、`check-publication.py --git` 0 findings、`package-source.py` 成功、脚本语法与 CI YAML 解析通过；具体命令与结果见“验证记录”。
- 待验证：PR 的独立 Codex High 审查、GitHub PR CI、合并后的首次集成迁移。
- 当前分支：`ao/handoff-conflicts/pr-mode`，基线 `origin/main=1cb7ad95229c539231a64c103b581a477709e344`；未合并、未发布、未创建 tag/Release。
- 关键事实与定位：模式说明与迁移步骤见 [交接机制](../../docs/HANDOFFS.md)；脚本入口 `scripts/check-handoff.py`；自测 `scripts/test-check-handoff.py`；CI `.github/workflows/verify.yml`；发布入口 `scripts/prepare-app-release.py`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/check-handoff.py` | 重写为 PR/集成/发布三模式；schema 2 印章增加证据指纹；PR 模式支持 `--changed-from`/`--changed-file` 守卫；拒绝模板占位符与未声明未测范围。 |
| `scripts/test-check-handoff.py` | 新增离线自测：容忍落后共享状态、记录完整性、路径/符号链接、变更集守卫、集成最新记录、发布篡改检测、目录移动可迁移。 |
| `scripts/test-agent-workflow.py` | 原有交接用例迁移到新语义（PR 校验、`--integrate` 刷新、`--release` 严格）。 |
| `.github/workflows/verify.yml` | Host job 先跑交接自测；交接步骤改为 PR 模式 + 变更清单，步骤名统一为 `Check handoff records`；新增 `workflow_dispatch` 的 `handoff_mode=release` 严格路径。 |
| `scripts/verify-workspace.sh` | 构建/设备门禁明确使用 `--pr`，普通 PR 不被共享状态阻塞。 |
| `scripts/prepare-app-release.py` | 发布打包前使用 `--release` 严格核对。 |
| `docs/HANDOFFS.md` | 新增：角色/时间点、三模式、普通 PR 与集成流程、冲突避免、指针 + 最近 N 条目标格式、迁移步骤、质量检索与失败处理。 |
| `AGENTS.md` | 交接总规则拆分为普通 PR/集成/发布三层；更新命令、清理与接手入口。 |
| `docs/handoffs/TEMPLATE.md` | 明确普通 PR 不改共享状态；结束命令改为 `--pr`。 |
| `docs/AGENT-QUICKSTART.md` | 接手入口、宿主自测与交付步骤改用 PR 模式。 |
| `docs/PUBLICATION.md` | 提交前清理/检查区分普通 PR 与集成方，新增 `--release`。 |
| `docs/CI.md` | 说明 CI PR 模式、变更清单守卫和手动严格模式。 |
| `docs/ORCHESTRATOR-RULES.md` | 明确普通 Worker 不写共享状态、集成阶段单一维护方流程。 |
| `docs/LESSONS.md` | L-001 更新为“普通 PR 不写共享状态 + 集成统一刷新”的结论与动作。 |
| `docs/code-standards.md` | 文档/脚本提交检查改为 PR 模式与集成模式分工。 |
| `docs/QA-GATES.md` | QA 记录只写自己的交接，`AGENT-STATE` 由集成方汇总。 |
| `docs/RELEASE.md` | 说明发布前使用严格模式。 |
| `docs/TEST-REPORT.md` | 增加本轮非设备验证摘要。 |
| `docs/handoffs/20261110-1333-handoff-pr-mode.md` | 本记录。 |
| `docs/NATIVE-PARITY.md` | 维护规则与验收步骤改用 `--pr --record`，共享状态由集成方刷新。 |
| `docs/AO-WORKTREE-BUILD.md` | 更新 worktree 门禁说明：`verify-workspace.sh` 先跑 `--pr`，不再要求每个 worktree 自带最新印章。 |
| `docs/AGENT-STATE.md` | 仅更新当前交接指针、机制说明与合并后集成命令；未做最近 N 条精简（迁移步骤见 HANDOFFS）。 |
| `docs/handoffs/CURRENT.json` | 由 `--integrate` 生成 schema 2 印章，不手写。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/test-check-handoff.py` | 通过，8 tests / 0 failures | 临时目录合成 fixture；覆盖三模式、篡改、变更集、目录移动 |
| `python3 scripts/test-agent-workflow.py` | 通过，19 tests / 0 failures（54.318s） | 含设备锁/模拟 ADB 离线回归，不访问真实设备 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1333-handoff-pr-mode.md` | 通过（共享状态可落后） | 本记录结构、占位符、未测声明 |
| `python3 scripts/check-handoff.py --integrate --record docs/handoffs/20261110-1333-handoff-pr-mode.md` | 通过，刷新 schema 2 印章 | 要求 AGENT-STATE 已链接且本记录最新 |
| `python3 scripts/check-handoff.py --release` | 通过（记录哈希、公开指纹、证据指纹、最新记录一致） | 只读严格模式 |
| `python3 scripts/check-publication.py --git` | 通过，0 findings | 检查公开候选、链接与身份/凭据模式；提交前最终复跑 |
| `python3 scripts/package-source.py` | 通过，生成源码包与 `artifacts/SOURCE-SHA256SUMS` | 工作材料；最终文档改动后会再生成，SHA 以最后输出为准 |
| `ruby -ryaml -e 'YAML.load_file(".github/workflows/verify.yml")'` | `YAML_OK` | 只证明 YAML 可解析；GitHub Actions 表达式以 PR CI 为准 |
| `bash -n scripts/verify-workspace.sh`；Python 内存语法检查 | 通过 | 未执行真实 Gradle/设备 |
| `python3 scripts/test-publication.py`、`test-app-release.py` | 19/19、18/18 通过 | Host CI 同套离线回归；发布入口断言 `--release` |

- 哪些是前一轮结果，哪些是本次新验证：以上均为本次新跑；0.1.10 的 535 JVM、设备 QA、发布包等是历史结果，本轮未复跑。
- 未测试、超时、失败和暂未复现的情况：未跑 Android 构建/JVM/lint/仪器/设备；未在 GitHub CI 上验证新 workflow；未执行 AGENT-STATE 实际精简；PR 尚未创建、未合并、未发布。

## 未完成与暂停项

- 未完成：创建 PR 并等待当前 head 的独立 Codex High 审查与 GitHub CI；合并后由集成维护方按 `docs/HANDOFFS.md` 第 8 节执行 `AGENT-STATE` 指针 + 最近 N 条迁移，并重跑 `--integrate` / `--release`。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；未获合并/发布授权。
- 不在本任务范围：产品代码与 Android 测试、APK/发布附件。

## 下一步

1. 推送分支 `ao/handoff-conflicts/pr-mode`，创建面向 `main` 的 PR；PR 描述包含三模式命令、验证结果、迁移步骤与风险，不合并、不发布。
2. 独立审查关注：PR 模式是否仍能发现“源码改动未写交接”、变更清单基线是否可靠、`evidenceFingerprint` 是否允许不更新记录而刷新共享状态、发布严格模式是否覆盖旧 schema 迁移。
3. 合并后首个集成：按 `docs/HANDOFFS.md` 第 8 节精简 `AGENT-STATE`，再运行 `--integrate --record <最新记录>`、`--release`、`check-publication.py --git`、`package-source.py`。
4. 若 GitHub CI 因 `fetch-depth: 0` 或基线 SHA 不可用失败，回退为“仅校验最新记录结构”并在 PR 中记录原因，不放宽变更集守卫。

## 约束与风险

- 数据/设备/权限边界：只改脚本、CI 与文档，未读写真实 Key/Cookie/token，未启动设备、未构建 APK、未发布。
- 关键决策及理由：
  - 默认模式设为 PR 模式，避免普通 PR 被共享状态阻塞；严格性放在显式 `--integrate` / `--release`。
  - 用 CI 传入的变更清单补回“源码变化必须有交接”的质量守卫，弥补 PR 模式不再比较全局指纹的缺口。
  - 印章 schema 升到 2，新增 `evidenceFingerprint`（排除 `AGENT-STATE.md`），允许集成方只刷新共享状态而不被迫改写记录正文；发布仍校验包含状态的完整指纹。
  - 集成模式要求目标记录是文件名最新记录，确保 `AGENT-STATE`/`CURRENT.json` 不会指向旧交接。
- 失败尝试与避免重复踩坑的结论：`py_compile` 在沙箱外写缓存失败，改用内存 `compile()`；自测 stub 最初未复刻 `jniLibs` 排除规则，补齐后通过。不要手工合并 `CURRENT.json`；冲突时取 main 后重新 `--integrate`。

## 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：不生成 APK；已重新生成 `artifacts/sources/Tanuki-0.1.7-source.zip` 与 `artifacts/SOURCE-SHA256SUMS`（工作材料，不提交）。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名发布、未创建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史交接；F1、PDF、远程主机等记录仍有未完成/待审事项，保留。本记录与 `TEMPLATE.md`、`CURRENT.json` 保留。
- 普通 PR 不刷新共享状态/印章；本轮因是机制改造并需验证 `--release`，由本会话执行一次 `--integrate` 生成 schema 2 印章。后续普通 PR 只跑 `--pr`。
- 文档或脚本变化后重新生成源码 ZIP；不把源码 ZIP 自身哈希写回归档内形成循环。

# 交接：20261106-1207-ci-rpc-contract-tests

- Agent/会话标识：android_agent-6（DSH Worker 3，CI 与协议测试）
- 时间：2026-11-06 12:07 CST（rebase 到 main `9e3e316` 后更新）
- 状态：已完成（实现与本地/干净树验证完成；最终 head 的 GitHub Actions 结果见 PR #12）
- 前一记录：[AO Worktree 构建环境](20261106-1211-phase2-worktree-build.md)（W2，已合并）；更早为[文档门禁恢复与接口契约](20261106-1059-phase2-docs-contracts.md)（W1，已合并）

## 用户目标与范围

- 用户本次实际要求：建立 GitHub Actions 门禁；新增 Browser/Phone RPC 契约检查；为 Browser 客户端补 mock server 回归并扩展 Phone 客户端测试；锁定两端方法集与 `/rpc`、Bearer、token、请求/响应上限、错误码格式；不修改产品协议。
- 本次包含：`.github/workflows/verify.yml`、`scripts/check-rpc-contract.py`、`scripts/test-browser-client.mjs`、`scripts/test-phone-client.mjs` 测试增强、`docs/CI.md`、`scripts/package-source.py` 的 `.github` 公开源码白名单；W3 交接记录与印章。
- 本次不包含：`app/` 产品代码与既有协议改动；W1/W2 文档内容改写（只同步 main 已合并版本）；APK/源码 ZIP 重打；tag/Release；main 推送或合并；设备/模拟器测试。
- 用户暂停/边界：API Key 保存、真实模型/账号验收继续暂停；不启用 `danger-full-access`，本地 `.git` 不可写时经编排器授权使用 GitHub API 发布。

## 当前状态

- 已实现：CI 的 host 与 Android 两个 job；Browser 17 方法、Phone 11 方法的冻结契约检查；Browser 7 个、Phone 18 个 mock server/JVM 无关回归；`.github` 进入公开源码包；`docs/CI.md` 说明 CI 边界。
- 已验证：本地 Node/Python 回归、契约检查、YAML 解析、干净树 Host 模拟；首轮 GitHub Actions 的 Android job success，Host 仅因 W1 未合并在 `check-handoff` 失败；同步 main `5b01ddf` 后曾得到 Host/Android 双绿；本次按冲突要求 rebase 到 `main/9e3e316`，最终 head 的 Actions 以 PR #12 为准。
- 待验证：rebase 后最终 head 的 Host/Android job；PR 复审与合并由用户/编排器执行，W3 不自行合并；0.1.8 产品设备/JVM 测试不在本轮范围。
- 当前分支/基线：`ci/phase2-ci-protocol-tests`，PR #12；已同步 `main` 的 `9e3e3161a74bf95a5b91089facdeddcadb6d4dda`（W2 PR #11 合并提交），并保留 W1 `5b01ddf` 的文档门禁内容。
- 关键事实与定位：CI 入口 `.github/workflows/verify.yml`；契约冻结常量 `scripts/check-rpc-contract.py`；Browser 客户端 `app/src/main/assets/browser/tanuki-browser.mjs`；Browser 原生 `app/src/main/kotlin/com/tanuki/browser/AgentBrowserController.kt`；Phone 客户端 `app/src/main/assets/phone/tanuki-phone.mjs`；Phone 原生 `app/src/main/kotlin/com/tanuki/storage/PhoneFileService.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `.github/workflows/verify.yml` | 新增 push + PR(main) 门禁；host 运行协议/客户端/发布脚本，Android 运行 assemble、AndroidTest、JVM 单测与 lint；缓存 Gradle/SDK；无 secrets/设备/发布/`continue-on-error` |
| `scripts/check-rpc-contract.py` | 冻结 Browser 17、Phone 11 方法；校验 `/rpc`、Bearer、43 字符 token、65536/300000 请求上限、3MiB/1MiB 响应上限与错误码格式；漂移非零退出 |
| `scripts/test-browser-client.mjs` | 新增回环 mock server 测试：成功、`ok:false`、超时、超大响应、无效 JSON、非 200、方法集/大小限制、token 不泄露 |
| `scripts/test-phone-client.mjs` | 仅测试增强：补非 200、无效 JSON、缺 `ok`、超大响应、超时、方法集/大小限制、错误码格式 |
| `docs/CI.md` | 说明 CI job、Node 22/Python 3.12、交接门禁顺序与原生 `.so` 边界；明确 CI 不重建原生 `.so`，原生构建由 W2 的本地引导和人工验证覆盖 |
| `scripts/ao-worktree-env.sh`、`scripts/test-ao-worktree-env.sh`、`docs/AO-WORKTREE-BUILD.md`、`docs/handoffs/20261106-1211-phase2-worktree-build.md` | 从 main `9e3e316` 同步 W2 已合并文件，rebase 时保留其内容，未改写 |
| `scripts/package-source.py` | 仅将 `.github` 加入 `TREES`（`scripts` 后、`docs` 前），使 workflow 进入公开源码包并消除白名单告警；不改其他打包语义 |
| `docs/handoffs/20261106-1207-ci-rpc-contract-tests.md` | 本记录 |
| `docs/AGENT-STATE.md` | 当前交接指向本记录；合并 W1/W2 事实，补充 CI/协议测试状态与下一步 |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --record` 生成，不手写 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `node scripts/test-browser-client.mjs` | 7/7 pass | macOS Node 26；覆盖 mock server，不依赖浏览器/设备 |
| `node scripts/test-phone-client.mjs` | 18/18 pass | 同上；新增 5 个测试 |
| `python3 scripts/check-rpc-contract.py` | OK（Browser 17/17、Phone 11/11） | 临时副本注入 `eval` 已验证非零退出 |
| `python3 scripts/test-agent-workflow.py` | 12 tests OK | 本地 |
| `python3 scripts/test-app-release.py` | 18 tests OK | 本地 |
| `python3 scripts/test-publication.py` | 19 tests OK | 本地；`.github` 入白名单后仍通过 |
| `ruby -rpsych -e 'Psych.parse_file(...)'` | YAML 解析 OK | 仅语法/结构校验，不替代 GitHub Actions |
| 首轮 GitHub Actions（PR run 42158796608 / push run 42158790509） | Android job success；Host failure 仅在最后 `check-handoff`（`No handoff stamp`） | W1 未合并前的预期依赖；其前 Node/Python/契约步骤均 success |
| 第二轮 GitHub Actions（PR run 42161362291 / push run 42161355499，head 435dfaa） | Host success（10–12s）、Android success（3–4m） | 同步 main 5b01ddf 并刷新印章后的双绿；本次 rebase 到 9e3e316 后重新触发 |
| 同步 W2 变更 + rebase 到 `main/9e3e316` | 从 GitHub API 取 `docs/AGENT-STATE.md`、`AO-WORKTREE-BUILD.md`、W2 记录、`CURRENT.json`、`scripts/ao-worktree-env.sh`、`test-ao-worktree-env.sh`；解决 AGENT-STATE/CURRENT 冲突并重建印章 | 未改写 W2 文件内容；未改 main |
| 干净树 Host 模拟（临时目录，main 9e3e316 + W2 文件 + 本 PR 文件 + 印章） | `check-handoff`、Node/Python/契约全部通过；`check-publication` 0 findings | 用于确认干净 checkout 不依赖本机 `.git` 或未提交文件 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1207-ci-rpc-contract-tests.md` | 通过，刷新 `CURRENT.json` | 由脚本生成，未手写 |
| `python3 scripts/check-handoff.py` | `Handoff current` | 同上 |
| `python3 scripts/check-publication.py --git` | 0 findings | 在同步 W1 docs、加入本记录并刷新印章后执行 |

- 哪些是前一轮结果：W1 的文档/门禁验证、0.1.8 产物核对与模拟器只读检查均为历史记录；本表只列 W3 本轮实际执行。
- 未测试、超时、失败和暂未复现：未在本地运行 Gradle/Android 构建（仅由 Actions Android job 验证）；未跑设备/模拟器；未重打 APK/源码 ZIP；rebase 后最终 head CI 结果以 PR #12 的 Actions 为准。

## 未完成与暂停项

- 未完成：PR #12 的最终复审与合并（用户/编排器负责；W3 不自行合并）。
- 外部依赖：rebase 后最终 head 的 Host/Android job 需要在 GitHub Actions 干净检出中确认；本记录已用临时干净树模拟通过。
- 用户暂停：API Key 保存、真实模型/账号验收；0.1.8 设备/JVM 证据另开授权任务，不能以本记录代替。

## 下一步

1. 等待 rebase 后最终 head 的 GitHub Actions：Host 的 `check-handoff` 应通过，Android 构建/JVM/lint 应保持通过。
2. 通过后请求 Codex Review；按 review 意见继续修复，不跳过检查、不自行合并。
3. 合并后由维护者在 main 复跑 `check-handoff.py`、`check-publication.py --git` 与 `verify-workspace.sh`。

## 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不跑设备、不安装/清数据；不使用 secrets；不启用 `danger-full-access`。
- 关键决策及理由：`-x buildNativeRuntime` 避免 CI 引入 NDK/macOS 依赖；仓库当前未跟踪 `app/src/main/jniLibs`，CI APK 不含原生运行时，不能替代设备/发布验证。`.github` 加入公开源码 `TREES` 是解决 `check-publication` 白名单告警的最小改动，不改变打包语义。
- 失败尝试与避免重复踩坑：本地共享 `.git` 在 `workspace-write` 下不可写（`index.lock: Operation not permitted`）；经编排器明确授权后使用 GitHub Git Data API 发布。本次按用户要求 rebase 到最新 main，仅 force-update 本 PR 分支，未 force/推送 main。Browser CLI 从符号链接路径调用会静默退出（主入口未 realpath），测试使用真实路径规避，产品代码未改。
- 交接门禁：新增 `scripts/`、`docs/` 与 `.github` 公共文件会改变源码指纹；本记录已用 `--record` 刷新，后续任何公共文件变化都必须重新刷新，不能跳过或自动重盖。

## 产物与校验

- 新/旧 APK、源码 ZIP：**未更新**；本轮只改 CI/测试/文档/公开源码白名单，不重打 0.1.8 附件。
- 覆盖安装/签名范围/未发布事项：未安装、未签名、未发布；无 tag/Release。
- 交接清理：未删除任何旧记录；保留 W1 记录、PR #8 记录、`TEMPLATE.md`、`CURRENT.json` 与本记录。
- 文档/脚本变化后已刷新交接印章；源码 ZIP 待下一次发布流程重打，本次不写回归档或发布附件。

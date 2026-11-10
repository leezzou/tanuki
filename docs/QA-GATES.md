# QA 门禁与证据

本文件规定什么时候需要独立 QA、设备证据至少包含什么，以及哪些结果不能当成通过。编排流程见 [ORCHESTRATOR-RULES](ORCHESTRATOR-RULES.md)，历史经验见 [LESSONS](LESSONS.md)。本文件不改变 [INTERFACES](INTERFACES.md) 的产品契约，也不替代 [TEST-REPORT](TEST-REPORT.md) 的历史记录。

## 1. 适用范围

门禁按 PR 实际改动路径判断是否需要独立 Android QA。判定实现见 `scripts/check-development-gates.py`，修改分类时必须同时改脚本和本节。

| 改动范围 | 是否需要独立 Android QA | 说明 |
|---|---|---|
| `docs/**`、根目录 `*.md`、`LICENSE`、`THIRD_PARTY_NOTICES`、`.gitignore` | 否 | 纯文档/许可/忽略规则；仍需适用 Host 检查 |
| `scripts/check-development-gates.py`、`scripts/test-development-gates.py` | 否 | 本门禁自身及其离线回归 |
| `.github/**` | 否 | CI 配置；Host/Android CI 结果本身另行核对 |
| `app/**`（含 `app/build.gradle.kts`） | 是 | 产品代码、资源、Android 测试或 App 构建 |
| `runtime/**`、`gradle/**`、根 `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、`gradlew` | 是 | 原生运行时、构建配置或 Wrapper |
| `scripts/build-debug.sh`、`install-and-launch.sh`、`verify-workspace.sh`、`prepare-build-tools.sh`、`env.sh`、`ao-worktree-env.sh`、`workspace-verification.init.gradle`、`device-ui.py`、`prepare-app-release.py`、`collect-diagnostics.sh`、`fetch-test-webview.py`、`sign-release.sh` | 是 | 构建、安装、设备执行、诊断或发布产物脚本 |
| 其他未列出的路径 | 需确认 | 保守处理：没有 QA 证据时拒绝；确认是纯 Host 改动时，在 PR 中明确范围并由审查者核对 |

纯文档或仅本门禁的 PR 不要求为本 PR 额外做产品设备验收，但仍必须等当前 head 的真实 Host CI；涉及 Android job 时也应等它完成。不能因为“只改文档”就跳过 Host 检查。

## 2. 独立 QA 的基本要求

- QA 必须由独立 QA session/执行者完成，不能由实现该 PR 的 worker 自证；证据里的 `qaSession` 不能等于 `--session`，且必须是当前 AO 已知 session。
- 设备由独立 QA 独占。实现 worker 的本地模拟器结果、Host 编译结果或单元测试不能填成 QA 证据。
- 证据绑定 PR 和 head SHA；PR 更新后必须重新执行受影响的最小验证，旧 SHA 的证据不能沿用。
- 至少一条步骤在 Android 内实际执行并 `passed`；只有 Host 命令、只有“测试源码存在”或只有构建成功都不够。
- 失败步骤直接拒绝；跳过步骤必须写明原因并保留为“未验证”，不能用跳过凑成全绿。
- 不把真实 API Key、Cookie、token、用户数据或原始敏感日志写入证据。证据只保存必要的公开/合成信息和结果摘要。

## 3. 设备证据最少字段

```json
{
  "schema": 1,
  "pr": "https://github.com/leezzou/tanuki/pull/13",
  "sha": "<PR head 的 40 位小写 SHA>",
  "qaSession": "android_agent-8",
  "device": {
    "avd": "Pixel_7_API_34_arm64",
    "serial": "emulator-5572",
    "api": 34,
    "abi": "arm64-v8a"
  },
  "apk": {
    "package": "com.tanuki.modern.workspacecheck",
    "sha256": "<APK 实际 SHA-256，64 位小写十六进制>"
  },
  "steps": [
    {
      "name": "目标设备用例",
      "command": "实际执行的命令（只用于记录，门禁不会执行）",
      "result": "passed",
      "source": "android"
    },
    {
      "name": "真实模型任务",
      "command": "实际执行的命令",
      "result": "skipped",
      "source": "android",
      "reason": "用户暂停 API Key 保存，未配置真实模型"
    }
  ],
  "model": {
    "configured": false
  }
}
```

字段约束：

- `schema` 固定为 `1`；`pr` 必须等于当前 PR URL；`sha` 必须等于当前 head。
- `qaSession` 必须独立且存在；`device` 的 `avd`、`serial`、`abi` 非空，`api` 为正整数。
- `apk.package` 非空，`apk.sha256` 为 64 位小写十六进制；包名和哈希必须来自实际 APK，不能从文件名推断。
- `steps` 非空、数量有上限；`result` 只能是 `passed`/`failed`/`skipped`，`source` 只能是 `android`/`host`；`skipped` 必须有 `reason`。
- `model.configured` 不是 `true` 或没有 `model` 时，门禁把真实模型/账号链路列为未验证。
- 门禁会限制证据文件大小并拒绝符号链接；它只校验结构，不执行 `command`，也不证明字段真实。

## 4. 不能算通过的情况

以下任一情况都不能把链路标绿：

- 只有 Host 构建/JVM/lint，没有设备执行。
- CI 因 `-x buildNativeRuntime` 没有打包原生运行时，其 APK 不是可运行交付物，不能拿来安装验收或当发布包。
- 只看到测试源码、测试计划、截图或“应该能过”，没有实际命令和结果。
- 设备上的测试被跳过、超时、中断、崩溃，或只执行了不相关的合成用例。
- 证据绑定旧 SHA、绑定其他 PR、由实现 session 自证，或 serial/AVD/API/ABI 缺失。
- APK 身份或哈希不匹配，或安装的包名与目标包不一致。
- 真实模型/账号未配置却声称模型任务通过；宿主模拟的 DSH/模型结果不能算 Android 内真实模型结果。
- 使用未授权设备、真实用户数据或未确认的安装环境。

## 5. 证据分层

| 层级 | 能证明 | 不能证明 |
|---|---|---|
| Host | 脚本、编译、JVM 单测、lint、契约检查在宿主可重复执行 | 设备运行、原生运行时、真实安装/升级、真实模型 |
| Android 设备 | 指定 APK 在指定 AVD/serial 上的实际行为 | 真机/MIUI、低 API、长期后台、所有账号场景 |
| 真实模型/账号 | 在已授权配置下的端到端任务 | 未执行的其他模型、未授权账号或生产环境 |

每一层都要单独记录。历史测试只能作为历史证据，不能写成当前轮已复跑；0.1.8 目前没有随版本记录设备测试，这一事实继续保留。

## 6. 运行门禁

```bash
# 纯文档/治理门禁 PR：仍会核对 PR 身份、最新 head、CI 和独立 review
python3 scripts/check-development-gates.py --pr <PR> --session <实现 worker>

# 涉及 Android/构建/未知范围：补独立 QA 证据
python3 scripts/check-development-gates.py --pr <PR> --session <实现 worker> \
  --qa-evidence <qa-evidence.json>
```

退出码 `0` 表示结构上满足合并前条件；`1` 表示拒绝。`qa=passed_with_unverified` 表示证据有效但仍有跳过或未配置项，必须继续在 PR、交接或 [TEST-REPORT](TEST-REPORT.md) 中保留这些未验项。

门禁不能证明自然语言、截图或设备结果真实；它只能防止旧 SHA、空/跳过 check、自评和非独立 reviewer 混入。调度者仍需抽查设备 serial、APK 哈希、provider 记录和实际命令输出。

## 7. 记录位置

- 设备/JVM/lint 的验证摘要写入 [TEST-REPORT](TEST-REPORT.md)，注明环境、命令、结果和限制；不要只写“已测试”。
- 本任务的执行范围和未完成项写入 `docs/handoffs/<本轮记录>.md` 并区分已验证/待验证；`AGENT-STATE` 由集成维护方在集成阶段汇总，普通 QA 任务不改共享状态。
- PR 正文写清实际执行的测试、未执行范围和风险；不要用历史数字替代当前 head 的结果。
- 设备原始日志留在 `.build` 或本机临时目录，不提交、不打包、不直接粘贴到公开证据。

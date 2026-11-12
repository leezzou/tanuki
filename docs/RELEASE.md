# 签名、打包与 GitHub Release

## 当前发布策略

用户要求 main 每次合并自动发一个新版本，直接程序化上传，不需要 Agent 执行上传。patch 按整数递增：`0.1.9 → 0.1.10`、`0.1.99 → 0.1.100 → 0.1.101`，不会自动跳到 0.2，也不再逐次询问版本。版本依据实际已发布 Release、已有 tag 与持久队列保留身份；同一合并失败重试仍使用同一版本/code。

2026-11-12 只读核对：公开 [v0.1.14](https://github.com/leezzou/tanuki/releases/tag/v0.1.14) 已发布为 Pre-release，tag 指向 `3c56167b94efeff47a20bd609b389708ba080ce5`；`app-update.json` 为 0.1.14/code19/workspacecheck，六附件齐全。APK 13,073,258 字节，SHA-256 `57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff`。这是本次初始化的已知基线，未来启用前仍须重新查证。下一次保留版本从实际公开/已保留上限分配，当前应为 0.1.15，code 至少20。

**合并前独立审查、当前 head 的 CI 和适用 QA 仍是前置条件。** 自动上传不等于允许跳过合并门禁，也不授权发布器代合、修改保护或直推 main。开发签名包一律 Pre-release；正式渠道不在自动发布范围。当前实现尚需独立审查、适用构建验证及集成方显式启用，不会因合入脚本就自行注册服务。

## 本机执行器与持久边界

入口为 [`merge-release.py`](../scripts/merge-release.py)，只用 Python 标准库、Git、现有 gh、JDK/Android SDK/NDK。没有 LLM、上传 Agent、GitHub 签名 secret 或自托管 runner 的依赖。不读取 gh 凭据或密钥内容；gh 自行使用已有认证。服务配置只保存工具、已有证书路径与公开身份，不能放 token/password。

专用稳定服务目录（示例 `$SERVICE_HOME`）位于源码/AO 工作树之外，权限700，包含：

- `bin/`：安装时复制的已审监视器与共享锁代码；不从即将归档的 worker 路径启动。
- `config.json`、`state.json`、`control.json`：环境路径、持久 FIFO/扫描游标/版本保留、暂停状态。
- `checkout/`：专用独立 clone，逐次切到精确 main 合并 SHA；`.build` 与下载/native 缓存留在此处。
- `gradle/`：独立且可复用的 Gradle 缓存，不与前台工作树共享可变输出。
- `bundles/<merge-sha>/`：固定的六附件；`candidates/` 保留固定 APK 重试摘要。
- `service.stdout.log`、`service.stderr.log`：受控阶段与简短失败原因，不原样转发外部工具日志或环境。

发布目录会保留供重试/复现；执行器不自动删除历史包、清理设备或清空缓存。运维在服务停止后按已发布事实管理容量与日志轮换；不能清掉尚未发布候选或依赖的状态。状态丢失/损坏、main 历史改写、tag冲突都应停机核查，不重新 init 来跳过失败。

## 准备、初始化与启停

以下是集成维护方在实现通过审查、CI 和适用验证后的操作。示例变量是本机路径占位，不能照搬别人的环境；不需要导出私钥或密码。

```bash
# SERVICE_HOME 必须是专用稳定目录；BUILD_SLOT 必须由所有重构建共同采用。
python3 scripts/merge-release-service.py prepare --home "$SERVICE_HOME" \
  --jdk "$TANUKI_JDK" --sdk "$ANDROID_SDK_ROOT" \
  --keystore "$EXISTING_DEBUG_KEYSTORE" --build-slot "$BUILD_SLOT" \
  --gate-receipts "$GATE_RECEIPTS" --installed-code-floor 19

# 只创建配置/代码快照/plist，不注册、不启动。明确已发布基线，避免重发全部历史。
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" init --baseline-tag v0.1.14
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" plan
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" status

# 可重复单次执行，与常驻服务使用同一 writer 锁、队列与候选；这一步会真实发布。
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" once

# 自动监视网页、CLI 等途径合入 main 的后续 PR；显式启用才注册 launchd。
python3 scripts/merge-release-service.py enable --home "$SERVICE_HOME" --confirm-shared-slot
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" pause
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" resume
python3 scripts/merge-release-service.py stop --home "$SERVICE_HOME"
python3 scripts/merge-release-service.py start --home "$SERVICE_HOME" --confirm-shared-slot
```

`plan` 是 dry-run：只读 provider/状态，不构建、不保留版本、不 fetch、不上传。`status` 显示持久队列及错误；退出0为完成或安全暂停，退出1为拒绝/失败，参数错误退出2。watch 遇错保留队首，间隔重试；不会处理失败提交之后的发布。轮询默认30秒，主机离线/休眠时延迟，恢复后补齐全部 first-parent 边界。

pause 写独立控制文件，监视器持 writer 锁时也能暂停；正在运行的构建/单个上传允许完成，下一阶段检查停止状态。SIGTERM/SIGINT 也在阶段边界停止，不为停止而杀其他任务。launchd 使用较长退出宽限，后台 I/O 优先级；停止不会撤回已公开 Release。恢复通过远端实际状态继续，不能假定上次报错就没有完成上传/发布。

服务 prepare 拒绝覆盖已有目录；升级须先停止服务、保留状态/候选，再审核并替换代码快照和配置。服务不自行从 main 升级执行器代码。

## 合并门禁回执与共享印章

合并流程使用 [`record-release-gate.py`](../scripts/record-release-gate.py) 包装原门禁：

```bash
python3 scripts/record-release-gate.py --pr <PR> --session <实现会话> \
  --directory "$GATE_RECEIPTS" --qa-evidence <适用时的独立QA证据.json>
```

它实际运行 `check-development-gates.py`，只有当前 head 的独立审查、CI、适用 QA 成功且 PR 查询前后身份不变才写 `<PR-head-sha>.json`。纯文档等门禁豁免范围省略 QA 参数。回执不是新的审批替代，也不会触发合并/发布；原合并流程仍负责 provider 分支保护与必需独立账号审批。所有合并入口包括网页合并都必须先完成这一门禁；缺回执时合并仍会被发现并入队，但发布停在该项，补齐真实依据后自动恢复。不要手写 `passed`，不要派上传 Agent。

发布器运行时无需 AO：它用 GitHub 核对 main SHA 对应唯一 merged PR、真实 PR head、head/main 两组 CI，再读取预先持久化的回执。回执目录与服务状态同为受信任本机运维数据，不能由其他用户/不可信代码写入；它不是签名证明，不能防同 UID 恶意修改。

自动逐次发布采用**精确 SHA/tree + 本次合并变更的 PR 交接检查 + 合并前门禁回执**。普通 PR 不写共享 `AGENT-STATE`/`CURRENT`，自动发布也不补共享印章、不提交版本修改；因此不要求每个中间合并 SHA 通过批量集成印章的 `--release`。共享状态仍由唯一集成维护方按批次维护。手工发布继续使用 `check-handoff.py --release`；自动封装的 `--source-commit/--merge-base` 例外仅适用于上述队列，不能当作略过审查/QA 的快捷参数。

## 队列、身份与上传恢复

初始化 tag 必须已公开发布且位于 main first-parent 链。扫描将其后每个提交按顺序入持久 FIFO；merge commit 和 squash merge 都能对应单个 PR。多提交 rebase 或直接 push 无法证明为唯一 PR 合并边界时明确阻塞，不猜测、不跳过。扫描游标与发布状态分开保存，连续多次合并及重启不会漏中间项。

一台机器同一服务目录只能有一个 writer（flock、锁文件不删除）。所有实例必须使用同一服务目录，不能在第二个目录初始化第二个同仓库发布者；多主机服务不在本实现范围。版本/code 在构建之前持久保留。code 高于实际公开清单、安装上限配置和先前队列保留；安装上限由维护方复核并配置，发布器不访问设备。外部人工发布导致保留身份落后时停机处理，不无限增加同一候选 code。

专用 checkout 逐次检出完整 SHA 并检查 HEAD/干净状态/源码 tree，绝不从较新 main 构建较早版本。不自动改产品版本文件、commit、push 或创建 release PR，因此没有自动版本提交触发循环。Gradle 参数传入 versionName/code，每次版本不同都生成对应新 APK；只对完全匹配的本候选复用已固定字节。Native/下载/Gradle 缓存由构建任务按输入复用，不用旧 APK 假装新版本。

上传前本地严格检查六个名称、大小、更新身份及两份 SHA 清单：

1. `Tanuki-<version>-workspacecheck-arm64-release.apk`
2. `app-update.json`
3. `Tanuki-<version>-source.zip`
4. `Tanuki-native-corresponding-sources.tar.gz`
5. `native-source-SHA256SUMS`
6. `RELEASE-SHA256SUMS`

创建的 tag 精确指向该合并 SHA；Release 先 draft/Pre-release。已有 tag 指向不同 SHA、远端额外附件、同名不同大小/字节都拒绝。只上传缺失附件，不 `--clobber`；已存在附件实际下载比较散列，齐全后再次核对再 publish，最后重读远端公开状态和六附件。若 publish 已成功但响应丢失，重启识别公开事实并完成队列记录，不重复发布。既有公开 Release 缺附件/有冲突也不会盲目修补或覆盖。

发布说明引用合并 SHA/tree、PR、证书及回执验证范围；明确 Host、Android、真实模型/真机各自边界，不能把构建成功当作设备验收。

## 跨工作树构建互斥

[`build-slot.py`](../scripts/build-slot.py) 以同一绝对目录的 flock 串行重任务。启用前集成维护方必须让所有重构建采用：

```bash
python3 scripts/build-slot.py --directory "$BUILD_SLOT" --priority development -- \
  bash -c 'source scripts/ao-worktree-env.sh; exec ./gradlew <本次实际任务>'
```

自动发布只在 checkout/构建/封装阶段持低优先级锁；已有前台等待者时让其先运行，不中断正在构建的任务。网络轮询/上传不占构建位。仅给发布服务加锁不能保护绕过 wrapper 的前台构建；`--confirm-shared-slot` 要求集成维护方确认实际采用，不能以口头排程代替长期互斥。

## 签名与手工封装

workspacecheck 为 `com.tanuki.modern.workspacecheck`、ARM64、minSdk26/targetSdk34、non-debuggable 的 native-inclusive release；开发证书 SHA-256 固定 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。[`development-signing.init.gradle`](../scripts/development-signing.init.gradle) 只通过 `TANUKI_WORKSPACECHECK_KEYSTORE` 引用仓库外已有 debug.keystore，保留 AGP debug 默认字段，不读取/导出密码、不生成新证书。实际签名由 APK 检查确认，不能相信路径或文件名。

手工明确版本与适用门禁完成后可用原入口，不自动上传：

```bash
source scripts/env.sh
python3 scripts/prepare-app-release.py --version <明确版本> --version-code <递增整数> --channel workspacecheck
# 固定已验证 APK，成对指定完整实际 SHA-256；仍核对真实身份/证书/SDK/原生库。
python3 scripts/prepare-app-release.py --version <明确版本> --version-code <递增整数> --channel workspacecheck \
  --verified-apk <本候选.apk> --verified-apk-sha256 <完整实际SHA256>
```

`production` 使用仓库外正式密钥、环境密码和 `scripts/sign-release.sh`；包名 `com.tanuki.modern`，不与 workspacecheck 混用，不进入本自动发布器。正式包名、证书/迁移和发布另按明确授权执行，不卸载旧 App 绕过签名限制。

PRoot/talloc 等 GPL/LGPL 对应源码、补丁、构建脚本与许可必须和 APK 同时提供。`package-native-sources.sh` 与 `package-source.py` 是已有打包入口，公开文件/归档检查遵守 [上传检查](PUBLICATION.md)，取包分类见 [ARTIFACTS](ARTIFACTS.md)，客户端发现与安装边界见 [APP-UPDATES](APP-UPDATES.md)。历史构建、失败、设备未验与旧版本事实继续保留在 [TEST-REPORT](TEST-REPORT.md) 与各自交接，不把旧结果写成本轮已测。

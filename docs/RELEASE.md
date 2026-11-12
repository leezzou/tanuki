# 签名、打包与 GitHub Release

## 当前发布策略

用户要求 main 每次合并自动发一个新版本，直接程序化上传，不需要 Agent 执行上传。patch 按整数递增：`0.1.9 → 0.1.10`、`0.1.99 → 0.1.100 → 0.1.101`，不会自动跳到 0.2，也不再逐次询问版本。版本依据实际已发布 Release、已有 tag 与持久队列保留身份；同一合并失败重试仍使用同一版本/code。

2026-11-12 只读核对：公开 [v0.1.14](https://github.com/leezzou/tanuki/releases/tag/v0.1.14) 已发布为 Pre-release，tag 指向 `3c56167b94efeff47a20bd609b389708ba080ce5`；`app-update.json` 为 0.1.14/code19/workspacecheck，六附件齐全。APK 13,073,258 字节，SHA-256 `57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff`。这是本次初始化的已知基线，未来启用前仍须重新查证。下一次保留版本从实际公开/已保留上限分配，当前应为 0.1.15，code 至少20。

**合并前独立审查、当前 head 的 CI 和适用 QA 仍是前置条件。** 自动上传不等于允许跳过合并门禁，也不授权发布器代合、修改保护或直推 main。开发签名包一律 Pre-release；正式渠道不在自动发布范围。当前实现尚需独立审查、适用构建验证及集成方显式启用，不会因合入脚本就自行注册服务。初始化仍从已发布基线开始逐合并处理；目标 SHA 必须包含此发布协议，因此集成顺序需先合入本发布工具 PR，再合后续功能 PR。已核 main 尚为上述基线；若此前已合入不含协议的提交，执行器会在构建前明确停止，需集成方安排兼容方案，不能把较新工具混入旧树或挪游标跳过。

## 本机执行器与持久边界

入口为 [`merge-release.py`](../scripts/merge-release.py)，只用 Python 标准库、Git、现有 gh、JDK/Android SDK/NDK。没有 LLM、上传 Agent、GitHub 签名 secret 或自托管 runner 的依赖。不读取 gh 凭据或密钥内容；gh 自行使用已有认证。服务配置只保存工具、已有证书路径与公开身份，不能放 token/password。

专用稳定服务目录（示例 `$SERVICE_HOME`）位于源码/AO 工作树之外，权限700，包含：

- `bin/`：安装时复制的已审监视器与共享锁代码；不从即将归档的 worker 路径启动。
- `config.json`、`state.json`、`control.json`：环境路径、持久 FIFO/扫描游标/版本保留、暂停状态。
- `checkout/`：专用独立 clone，逐次切到精确 main 合并 SHA；`.build` 与下载/native 缓存留在此处。
- `gradle/`：独立且可复用的 Gradle 缓存，不与前台工作树共享可变输出。
- `bundles/<merge-sha>/`：固定的六附件；`candidates/` 保留固定 APK 重试摘要，`receipts/<merge-sha>.json` 保留候选专属冻结门禁回执，其散列写入队列。
- `floor/value.json`：独立持久的安装 code 下限、公开事实依据与更新历史；`floor/writer.lock` 串行 floor 更新及远端公开阶段。
- `service.stdout.log`、`service.stderr.log`：受控阶段与简短失败原因，不原样转发外部工具日志或环境。

发布目录会保留供重试/复现；执行器不自动删除历史包、清理设备或清空缓存。运维在服务停止后按已发布事实管理容量与日志轮换；不能清掉尚未发布候选或依赖的状态。状态丢失/损坏、main 历史改写、tag冲突都应停机核查，不重新 init 来跳过失败。

## 准备、初始化与启停

以下是集成维护方在实现通过审查、CI 和适用验证后的操作。建议稳定布局（由编排核对后统一采用）为 `$HOME/Library/Application Support/TanukiRelease`、共享构建锁 `$HOME/Library/Application Support/TanukiBuildSlot`、合并前回执 `$HOME/Library/Application Support/TanukiReleaseGates`。这些路径不依赖 AO 工作树；启用前仍由集成维护方统一所有重任务采用同一锁。既有开发证书路径为 `$HOME/.android/debug.keystore`，仅由构建工具引用，不读取/输出密钥或密码；其他 JDK/SDK 路径须来自稳定本机环境。

```bash
export SERVICE_HOME="$HOME/Library/Application Support/TanukiRelease"
export BUILD_SLOT="$HOME/Library/Application Support/TanukiBuildSlot"
export GATE_RECEIPTS="$HOME/Library/Application Support/TanukiReleaseGates"
export EXISTING_DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
# BUILD_SLOT 必须由所有重构建共同采用，不因配置了发布服务就假定已迁移。
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
python3 "$SERVICE_HOME/bin/merge-release-service.py" enable --home "$SERVICE_HOME" --confirm-shared-slot
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" pause
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" resume
python3 "$SERVICE_HOME/bin/merge-release-service.py" stop --home "$SERVICE_HOME"
python3 "$SERVICE_HOME/bin/merge-release-service.py" start --home "$SERVICE_HOME" --confirm-shared-slot
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

发布器运行时无需 AO：它用 GitHub 核对 main SHA 对应唯一 merged PR、真实 PR head、head/main 两组 CI，再验证预先持久化的回执；首次处理后复制到候选专属位置并固定散列。重试校验冻结副本，不依赖原 AO 会话或可变源回执继续在线；冻结副本缺失/字节改变明确拒绝。初次缺回执时 status/error 明确给出 PR、head 与 `record-release-gate.py` 待执行原因。回执目录与服务状态同为受信任本机运维数据，不能由其他用户/不可信代码写入；它不是签名证明，不能防同 UID 恶意修改。

自动逐次发布采用**精确 SHA/tree + 本次合并变更的 PR 交接检查 + 合并前门禁回执**。普通 PR 不写共享 `AGENT-STATE`/`CURRENT`，自动发布也不补共享印章、不提交版本修改；因此不要求每个中间合并 SHA 通过批量集成印章的 `--release`。共享状态仍由唯一集成维护方按批次维护。手工发布继续使用 `check-handoff.py --release`；自动封装的 `--source-commit/--merge-base` 例外仅适用于上述队列，不能当作略过审查/QA 的快捷参数。

## 队列、身份与上传恢复

初始化 tag 必须已公开发布且位于 main first-parent 链。扫描以 first-parent 列表为底，向 provider 逐项核对引入提交的唯一 merged PR 和最终 `merge_commit_sha`。merge commit/squash 形成单项；多提交 rebase 只有整个连续区间都属于同一 PR、最终边界已在扫描范围时才归并为一项，使用该区间之前的基线与最终 SHA 构建。一个 PR 只保留一次版本，不按 rebase 内部提交逐个发布。直接 push、区间混入别的 PR、边界未完整取得等无法证明的情况明确阻塞；已经确认的前序区间仍可按序完成，扫描游标不越过未知提交。游标与发布状态分开保存，连续合并及重启不漏中间项。

一台机器同一服务目录只能有一个 writer（flock、锁文件不删除）。所有实例必须使用同一服务目录，不能在第二个目录初始化第二个同仓库发布者；多主机服务不在本实现范围。版本/code 在构建之前持久保留。code 高于实际公开清单、安装 floor 和先前队列保留。19只是已核初始事实，不是永久常量：初始化读取安装配置，运行中每次保留与发布前重新核对持久 floor。设备事实由独占 QA/集成维护方核对，发布器不访问 ADB 或安装。外部发布或更高安装 floor 越过已保留身份时明确停机处理，不无限增加同一候选 code。

运行中通过下面命令更新事实，不直接改运行中 config/state。floor 只能升不能降，记录时间、公开依据和历史；构建期间可更新，发布前重读。远端上传/公开阶段持 floor 锁，并发更新立即拒绝、需阶段完成后重试，不能静默接受后仍发布更低 code。

```bash
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" set-floor \
  --version-code <已核安装下限> --reason '<简短公开事实，不含原始日志或凭据>'
# QA 安装的恰是已固定候选时，可明确绑定同一 SHA 与实际 APK 散列。
python3 "$SERVICE_HOME/bin/merge-release.py" --home "$SERVICE_HOME" set-floor \
  --version-code <该候选code> --reason '<同候选QA安装事实>' \
  --candidate-sha <合并完整SHA> --apk-sha256 <固定候选实际SHA256>
```

相等 floor 只在 code、候选 SHA、实际 APK 哈希与已保留 inventory 全部匹配时允许继续发布该同一候选，不因此再占新号。不能把别的 QA 包或未知 hash 说成同候选。status/plan 显示 effective floor、历史和下一次未保留 code。

专用 checkout 逐次检出完整 SHA 并检查 HEAD/干净状态/源码 tree，绝不从较新 main 构建较早版本。不自动改产品版本文件、commit、push 或创建 release PR，因此没有自动版本提交触发循环。Gradle 参数传入 versionName/code，每次版本不同都生成对应新 APK；只对完全匹配的本候选复用已固定字节。Native/下载/Gradle 缓存按可核输入复用，不用旧 APK 假装新版本。

原生缓存由 `native-cache-identity.py` 记录真实内容身份：runtime/补丁/锁、C源码与构建/Gradle配置、固定 NDK 完整文件内容和权限、PRoot实际源码及当前 talloc 源码/归档，以及三份原生输出散列；不靠 mtime 判断。构建后再次核对源码/工具链，APK内三份库字节必须等于对应构建输出，才固定缓存回执。首次取齐上游依赖后刷新身份，避免第二次发布因初始空缓存无谓重编。NDK核验显示实际条目/字节/耗时，未做真实工具链耗时评估。

输入/输出匹配时允许专用构建树增量；不匹配时 `--force-native` 通过 init script 仅强制 `buildNativeRuntime`，不用 `--rerun-tasks` 重做无关任务。固定候选重试核对来源/code/版本/证书/哈希及 native 输入身份；工具链改变时拒绝重签同一候选，不与强制 native 混用。新源码/锁/ABI/工具链变动不能悄悄复用旧库；未知协议或上游版本不匹配需明确处理，不能绕过校验。

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

## 无 Git 源码包的证明与重建

自动源码 ZIP 内额外生成 `RELEASE-SOURCE.json`，不写入 Git 工作树、不刷新 CURRENT/AGENT-STATE。它声明为生成的来源证明，包含原始提交对象、Git tree 对象、first-parent 合并范围、本次改动与交接索引、逐公开文件 SHA-256，以及版本/code/证书/原 APK 散列。生成时要求干净精确 checkout，公开文件内容必须属于声明的 tree；包内 native 对应源码附件单独纳入文件散列。发布器还把包内身份与实际 APK/更新清单/合并基线交叉核对。

解压后 [`release-source.py`](../scripts/release-source.py) 不调用 Git 或 AO 即可重算 commit/tree/blob 散列、验证全部公开文件和本次交接变更，拒绝未记录源码、额外文件、被改写的文件清单或伪造的 tree/交接索引。共享 CURRENT/AGENT-STATE 可以是对应合并原树中的较早状态，不能因此补章或篡改原源码。生成证明不表示设备/真实模型 QA，不是独立发布者签名；仍须通过 Release 外部校验清单、来源及 APK 实际签名确认分发身份。

在解压的 `Tanuki-<version>/` 中：

```bash
python3 scripts/release-source.py verify --version <版本> --version-code <code>
python3 scripts/check-handoff.py --pr
python3 scripts/check-handoff.py --release
python3 scripts/release-source.py rebuild --version <版本> --version-code <code> --plan
# 实际构建前准备标准 JDK/SDK/NDK，引用已有开发证书文件；不需要前任 .build 缓存。
export TANUKI_WORKSPACECHECK_KEYSTORE="$HOME/.android/debug.keystore"
python3 scripts/release-source.py rebuild --version <版本> --version-code <code>
```

`rebuild` 先验证包身份，拒绝不同 version/code，恢复包内原生归档的上游源码及最小公开 Git 元数据到当前解压目录的 `.build/sources`，再通过原 Gradle 参数构建 native-inclusive release。恢复只接受已验证的源码路径与目录内相对链接，不覆盖已有不同字节或链接；遇冲突请重新解压到全新目录，不依赖前任机器缓存。没有证书路径时停止，不生成替代 key。构建工具/公开依赖首次下载按上手指南准备，通常需要网络；不承诺全部 Gradle 依赖随源码离线分发。既有 `verify-workspace.sh` 在无 Git 包内也先核对生成证明，再按原默认规则构建/测试，不操作设备。

同输入源码 ZIP 字节可复现（固定归档时间/权限，元数据不含 host 路径）；不承诺重建 APK 字节必然等于原 APK。新 APK 仍须重新实核身份/证书/散列，不能取代原固定候选。

`check-handoff --release` 仅在无 `.git` 且有有效生成证明的源码包中采用此来源验证；普通 Git 工作树仍检查共享印章。既有手工封装入口显式使用 `--release --shared-stamp`，强制原严格印章，production 不能借自动来源证明或 merge 参数跳过它。

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

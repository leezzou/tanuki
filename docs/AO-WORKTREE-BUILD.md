# AO Worktree 构建环境

本文说明如何在 AO 独立 worktree 中构建 Tanuki：用 `scripts/ao-worktree-env.sh` 把 JDK、Android SDK、Gradle 发行版与缓存的**可写状态**收敛到当前 worktree，而不依赖或修改全局 `~/.gradle`、`~/.android`、JDK、SDK 和 shell 配置。

## 1. 为什么需要它

AO 的每个 worker 在独立 worktree 中运行，DSH 文件沙箱为 `workspace-write`：只能写当前 worktree。直接执行 `./gradlew` 时：

- Gradle Wrapper 默认写 `~/.gradle/wrapper/dists/gradle-8.13-bin/<hash>/gradle-8.13-bin.zip.lck`，被沙箱拒绝（`Operation not permitted`）；
- JDK 17 在主仓库 `.build/jdk/`，Android SDK 在 `~/Library/Android/sdk`，两者都不在 worktree 内，但只需只读使用；
- worktree 中没有 `local.properties`，AGP 找不到 SDK；
- `assemble`/`lint` 还需要写 `~/.android/debug.keystore` 与指标目录，同样被拒绝。

因此需要 per-worktree 的 `GRADLE_USER_HOME`（以及 `ANDROID_USER_HOME`），并把已有缓存只读复制进来，避免重复下载。

## 2. 用法

```bash
cd /path/to/ao/worktree

# 每个新 shell 都要 source；脚本只影响当前 shell
source scripts/ao-worktree-env.sh

./gradlew --version
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest
```

需要 APK 时再执行：

```bash
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:assembleModernDebug
```

注意：

- **必须用 `source`**。`bash scripts/ao-worktree-env.sh` 只会校验并准备目录，导出的变量不会留在当前 shell。
- 脚本可重复执行；缓存齐备时通常 0–1 秒返回。
- 设备/模拟器任务不要 source 本脚本，改用 `scripts/env.sh`。本脚本把 `ANDROID_USER_HOME` 指向 worktree，会看不到 `~/.android/avd` 下的既有 AVD（见 §7）。
- 不在同一目录并行运行不同 flavor/init script 的 Gradle 构建。

## 3. 脚本做了什么

| 项目 | 解析顺序 / 默认值 | 写入位置 |
| --- | --- | --- |
| 项目根 | 脚本目录的上一级，解析为绝对路径 | 不写 |
| JDK 17 | `TANUKI_JDK` → worktree `.build/jdk/*/Contents/Home` → 主仓库 `.build/jdk/*/Contents/Home`；校验 `JAVA_VERSION="17` | 不写（只读使用） |
| Android SDK | `TANUKI_SDK` → 现有 `ANDROID_HOME`/`ANDROID_SDK_ROOT` → `~/Library/Android/sdk`；校验 `platforms/android-36`、`build-tools/35.0.0`、`ndk/27.2.12479018` | 不写（只读使用） |
| `GRADLE_USER_HOME` | `<worktree>/.build/gradle-user-home` | worktree `.build` |
| Gradle 8.13 发行版 | 从 `~/.gradle/wrapper/dists/gradle-8.13-bin/*` 复制 | 新 `GRADLE_USER_HOME/wrapper/dists/...` |
| 依赖缓存 | 从 `~/.gradle/caches/{modules-2,jars-9,8.13/generated-gradle-jars}` 复制 | 新 `GRADLE_USER_HOME/caches/...` |
| 原生构建源码 | 从主仓库 `.build/{sources,downloads,native}` 复制缺失项 | `<worktree>/.build/...` |
| `local.properties` | 缺失时写入 `sdk.dir=<SDK>`；确认被 `.gitignore` 忽略 | worktree 根目录（不提交） |
| `ANDROID_USER_HOME` | `<worktree>/.build/android-user-home`（debug keystore、指标） | worktree `.build` |

复制优先使用 macOS APFS 的 `cp -c`（写时复制），失败时退回普通 `cp -R`；回退前会清理 clone 失败留下的部分临时目录，避免把源目录嵌套复制进缓存布局。符号链接采用方案 C：顶层链接先解析目标再复制实体，目录复制使用 `cp -cRL` / `cp -RL` 完全 dereference，worktree 内不保留任何链接文本；复制前拒绝绝对链接、逃出源码树的链接和先 `..` 出源根再返回的链接，循环链接由 `cp` 拒绝。复制后再断言无残留链接。这样写副本不会修改源，Gradle 也不会写回共享缓存。脚本只读取主仓库与 `~/.gradle`，不会修改它们。

`ANDROID_USER_HOME` 只影响 AGP 的 keystore/指标目录；`~/.android` 的 analytics 告警仍可能出现（见 §7），但不影响构建结果。

### 可调环境变量

| 变量 | 作用 |
| --- | --- |
| `TANUKI_JDK` | 显式指定 JDK 17 home |
| `TANUKI_SDK` | 显式指定 Android SDK |
| `TANUKI_MAIN_REPO` | 显式指定主仓库（默认由 `git rev-parse --git-common-dir` 推导） |
| `TANUKI_SKIP_GRADLE_CACHE_COPY=1` | 跳过依赖缓存复制，由 Gradle 联网下载 |
| `TANUKI_COPY_GRADLE_VERSION_CACHE=1` | 额外复制 `caches/8.13`（含 `transforms`，首次更慢，约 +550 MB 逻辑占用） |
| `TANUKI_ANDROID_USER_HOME` | 覆盖 worktree 内 `ANDROID_USER_HOME` 路径（必须仍在 worktree 内） |
| `TANUKI_SKIP_ANDROID_USER_HOME=1` | 不重定向 `ANDROID_USER_HOME`；`assemble`/`lint` 会因写 `~/.android` 失败 |

## 4. 为什么必须 per-worktree `GRADLE_USER_HOME`

Gradle 在 `GRADLE_USER_HOME` 下创建发行版锁、依赖缓存锁、transform 缓存和 daemon registry，这些都需要**写权限**。把它指向 `~/.gradle` 会有三个问题：

1. 当前沙箱直接拒绝写 `~/.gradle/...zip.lck`，构建无法启动；
2. 即使有权限，多个 worker/主仓库共享同一缓存，并发构建会互相争锁，`transforms`、`jars-9` 等缓存也可能互相污染；
3. 缓存无法随 worktree 生命周期清理，磁盘会持续增长。

把 `GRADLE_USER_HOME` 放进 worktree 的 `.build/` 后，每个 worktree 自包含、可独立清理；`~/.gradle` 只作为只读缓存源。**不要**用符号链接把 `modules-2` 或整个 `caches` 指回 `~/.gradle`：Gradle 仍会写锁文件与元数据，等于绕过隔离。

## 5. 缓存、磁盘与时间成本

### 首次准备的逻辑数据量（实测）

| 内容 | 逻辑大小 |
| --- | --- |
| Gradle 8.13 发行版 | 约 145 MB |
| `caches/modules-2`（依赖） | 约 784 MB |
| `caches/jars-9` | 约 7.7 MB |
| `caches/8.13/generated-gradle-jars` | 约 176 MB |
| 原生源码/下载/中间产物 | 约 9 MB |
| **合计** | **约 1.1 GB** |

APFS 克隆时这些文件与源缓存共享数据块，实际新增磁盘通常远小于 1.1 GB；首次构建还会在 per-worktree Gradle home 生成 `transforms` 等文件，完整构建后实测约 **1.6 GB 逻辑占用**。若文件系统不支持 clonefile（退回普通复制），会真实占用约 1.1 GB 以上。

### 时间（本机实测）

| 阶段 | 耗时 |
| --- | --- |
| 首次 `source`（复制发行版 + 依赖缓存 + 原生源码） | 约 23 s |
| 再次 `source`（缓存已存在） | 0–1 s |
| `./gradlew --version` | 首次约 2.8 s，缓存热后约 1 s |
| 首次 `:app:compileModernDebugKotlin`（含原生运行时重建） | 约 119 s |
| 首次 `:app:testModernDebugUnitTest` | 约 50 s |
| `:app:assembleModernDebug`（缓存热、keystore 干净） | 约 15 s |

正常路径不需要联网：Gradle 发行版、依赖缓存、PRoot/talloc 源码都从本机复用。若主仓库没有对应缓存，Gradle Wrapper/依赖会下载；原生构建会按 `runtime/build-native-runtime.sh` 重新 `git fetch` PRoot、`curl` talloc（该脚本自带 SHA-256 校验）。

## 6. 实测记录（2026-11-06）

环境：macOS 27.0.1 arm64；Temurin JDK 17.0.21+9；Gradle 8.13；AGP 8.13.2；Kotlin 2.3.10；SDK platform 36、build-tools 35.0.0、NDK 27.2.12479018；基线 `origin/main` = `c196bbf4d211604bc2076de450a8ed2d52ddde99`（分支 `chore/phase2-worktree-build`）。

| 命令 | 结果 | 备注 |
| --- | --- | --- |
| `source scripts/ao-worktree-env.sh` | 成功，23 s / 再次 0 s | 复用发行版与缓存；未写 `~/.gradle` |
| `./gradlew --version` | 成功 | Gradle 8.13，Launcher JVM 17.0.21 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | **BUILD SUCCESSFUL**，119 s | 含 `:app:buildNativeRuntime`；19 个任务执行 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest` | **BUILD SUCCESSFUL**，50 s | 21 个测试套件、104 个测试、0 失败/0 错误/0 跳过 |
| `./gradlew ... :app:assembleModernDebug` | **BUILD SUCCESSFUL**，15 s | 清空 `.build/android-user-home` 后重新生成 `debug.keystore`，APK 14,293,015 字节 |

未运行 `scripts/verify-workspace.sh` 的完整门禁；其开头的 `python3 scripts/check-handoff.py` 当前失败（W1 正在修复）：

```text
Handoff check failed: No handoff stamp: write a record/state, then use --record docs/handoffs/<session>.md
```

这属于已知的交接文档门禁失败，不能据此宣称 `verify-workspace.sh` 通过。本 worktree 只新增脚本、回归测试与本文档，未改产品源码。

构建产物（`app/build/`、`.build/`、`app/src/main/jniLibs/*.so`、`local.properties`）均在 `.gitignore` 覆盖范围内，不提交。

### 回归测试

```bash
bash scripts/test-ao-worktree-env.sh
```

只加载复制辅助函数，不执行环境准备。覆盖：clonefile 失败留下部分临时目录后回退普通复制不嵌套、文件复制内容完整、顶层外部链接实体化、目录内绝对/逃逸链接拒绝、先逃出源根再返回的链接拒绝、循环链接拒绝、内部安全链接 dereference 为普通文件且源/目标目录名不同时写副本不修改源、回退也失败时清理目标与临时目录、正常复制路径。测试在临时目录中运行并自动清理。

## 7. 失败排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Operation not permitted`，路径为 `~/.gradle/wrapper/dists/...zip.lck` | 未 source 脚本，或 `GRADLE_USER_HOME` 未生效 | `source scripts/ao-worktree-env.sh`；确认 `echo "$GRADLE_USER_HOME"` 指向 worktree `.build` |
| `未找到 JDK 17` | 主仓库没有 `.build/jdk`，或 `TANUKI_JDK` 指向非 17 | 在主仓库执行 `scripts/prepare-build-tools.sh`（需网络），或 `export TANUKI_JDK=/path/to/jdk-17` |
| `Android SDK 缺少组件: ...` | SDK 未安装指定 platform/build-tools/NDK | 用 SDK 自带 `sdkmanager` 安装；脚本不自动安装 |
| `Unable to create debug keystore in ~/.android` | `ANDROID_USER_HOME` 未重定向（或设置了 `TANUKI_SKIP_ANDROID_USER_HOME=1`） | 重新 source；确认 `echo "$ANDROID_USER_HOME"` 在 worktree 内 |
| `Unable to initialize metrics, ensure ~/.android is writable ... (Operation not permitted)` | AGP/工具尝试写全局 analytics 设置；沙箱拒绝 | 已知无害告警，构建继续；不需要放宽沙箱 |
| 缓存复制失败并报 `Operation not permitted` | 目标不在当前 worktree，或沙箱策略更严 | 记录完整路径并上报；不要改指向 `~/.gradle`，不要启用 `danger-full-access` |
| 原生构建重新联网 | `.build/sources` / `.build/downloads` 未复用 | 确认主仓库存在 `.build/sources/termux-proot/.git` 与 `.build/sources/talloc-2.4.3` |
| `git fetch` / `git add` 报 `Operation not permitted`（主仓库 `.git/worktrees/<id>/...`） | 本沙箱只允许写 worktree 工作区，worktree 的 Git 元数据在主仓库 `.git` 下 | 属沙箱限制，不是构建问题；按 AO 约定上报，交由有权限的会话完成提交/推送 |
| `local.properties` 未被忽略 | `.gitignore` 被改动 | 恢复 `.gitignore` 中的 `local.properties`，脚本会拒绝继续 |

## 8. 给 CI（W3）的复用建议

- 显式设置 `GRADLE_USER_HOME` 到 job 临时目录，不要依赖宿主 `~/.gradle`；本脚本的路径布局可直接照搬。
- 缓存键建议包含 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionSha256Sum` 与 Gradle 版本；缓存内容至少包含 `wrapper/dists`、`caches/modules-2`、`caches/jars-9`，可选 `caches/<版本>/generated-gradle-jars`。
- 原生构建缓存按 `PRoot commit`（`runtime/build-native-runtime.sh` 中 `b27d81a...`）与 talloc 版本做键；或把 `.build/sources`、`.build/downloads` 作为只读缓存挂载，避免每次 `git fetch`/`curl`。
- `ANDROID_USER_HOME` 指向 job 临时目录，让 debug keystore 每个 job 独立生成；不要复用开发者 `~/.android`。
- 命令顺序：`./gradlew --version` → `:app:compileModernDebugKotlin` → `:app:testModernDebugUnitTest`；需要 APK 时追加 `:app:assembleModernDebug`。失败即失败，不跳过。
- `scripts/verify-workspace.sh` 会先跑 `check-handoff.py`，当前该门禁失败（W1 修复后再纳入 CI 必过项）。
- 同一 job/目录不要并行跑不同 flavor 或不同 init script 的构建。
- 不要上传 `.build`、`local.properties`、`app/src/main/jniLibs/*.so` 作为源码产物。

## 9. 边界

- 不关闭/不放宽 DSH 安全沙箱，不使用 `danger-full-access`。
- 不修改全局 shell、Java、Android SDK、NDK、`~/.gradle`、`~/.android`。
- 不修改产品代码；`app/src/main/jniLibs/*.so` 是原生构建产物，保持忽略。
- 发布签名、正式 keystore、`versionCode/versionName` 不在本文范围，仍按 `docs/RELEASE.md` 执行。
- 设备/模拟器与真机测试使用 `scripts/env.sh` 与 `docs/AGENT-QUICKSTART.md` 的流程；本脚本面向构建与 JVM 测试。

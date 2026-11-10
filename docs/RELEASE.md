# 签名、打包与 GitHub Release

当前已发布 **0.1.9/code11** 为 Pre-release：`v0.1.9` tag 为 `5c680b9`，APK SHA-256 `210b6ec2119e8dd6ee08ec3d2be07889f1fa36720c6e15dd24d74a1d25a49dc8`、10,506,550 字节、证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；0.1.9 没有随版本记录完整设备验收。**0.1.10/code12** 已在本地准备候选（基线 `main=9e7edbb9`，APK SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`、`11,485,978` 字节），本版执行 push main、创建 tag `v0.1.10` 与 Pre-release；状态以[交接](AGENT-STATE.md)为准。

**用户说要发布时，先询问版本号；已经明确的版本不重复问。** 历史记录：用户确认过 **v0.1.7**（code9）、**v0.1.8**（code10）、**v0.1.9**（code11）；当前候选 **v0.1.10**（code12），沿用原有 workspacecheck 开发证书。后续封装前须重新核对公开、已安装及归档版本上限，确保 code 严格递增；不能只依据本地默认值判断。

0.1.10 候选基于 `main=9e7edbb9`，包含 0.1.9 之后的 PR #21 设备温控、#22 DSH 通知、#23 XLSX、#24 PDF、#25 失败分类、#26 原生界面切换、#27 冷启动、#28 DOCX、#29 远程主机 等已合并改动（最终以 rebase 后的 main 为准）。候选 release 须独立执行适用测试，不能以 debug 或 0.1.8/0.1.9 历史结果代替；实际命令、结果及未验范围见[测试现状](TEST-REPORT.md)。0.1.9 已按该流程由用户合并并发布。

源码已推送不等于 Release 已发布；本地打包、Git 提交/推送、创建 tag、上传附件和更改可见性分别遵守当前授权。

固定收尾顺序：修复与候选APK验证 → 完成所有适用测试 → 版本、测试报告、专题与交接文档一并收尾 → 清理过期记录、检查源码候选包和实际暂存 → 一次提交推送 → 生成/刷新最终6个附件。最终打包按已验证APK的实际SHA核对，纯文档收尾不重复推送或重跑相同产品测试；如APK字节改变则不能沿用候选测试。最终回执与外部校验清单记录交付结果，不为了回填提交自身再制造后续提交。

## 已确认版本的最终封装

先完成候选release构建和全部适用测试，收尾版本/测试/专题文档与交接，清理过期记录并通过公开检查。统一一次commit/push成功后，再使用下面的固定字节模式刷新最终取包目录：

```bash
source scripts/env.sh
python3 scripts/prepare-app-release.py --version 0.1.10 --version-code 12 --channel workspacecheck \
  --verified-apk <已完成测试的候选.apk> --verified-apk-sha256 <候选实际SHA256>
```

两个verified参数必须成对，哈希必须完整匹配。脚本先复制到私有临时文件固定字节，仍检查实际包名、版本、SDK和签名，封装副本再次核hash；只跳过重新编译。此方式避免推送改变AGP提交号元数据后生成另一份未测APK，不能用来宣称未经测试的包已通过。普通构建入口仍保留。

脚本先以发布严格模式校验当前交接（`check-handoff.py --release`，要求印章、记录哈希、公开源码指纹与最新记录一致）；verified模式直接核对已测APK的字节和真实包名/版本/SDK/证书，普通模式才重新构建。输出 **`artifacts/releases/0.1.10/`**（只从此目录取本版发布附件；分类与历史归档见[取包说明](ARTIFACTS.md)）：

- `Tanuki-0.1.10-workspacecheck-arm64-release.apk`
- `app-update.json`：应用内更新所需清单；不是手写的可信证书来源。
- `Tanuki-0.1.10-source.zip`：完整公开源码，另含必要原生对应源码附件。
- `Tanuki-native-corresponding-sources.tar.gz`、`native-source-SHA256SUMS`
- `RELEASE-SHA256SUMS`：以上附件的实际 SHA-256。

该渠道是 **开发证书签名、non-debuggable 的 release 构建**，并非正式签名。目标为 `com.tanuki.modern.workspacecheck`、ARM64、minSdk26/targetSdk34；保持原开发证书才能覆盖之前同包测试版。生成过程不读取或上传用户数据，不访问设备，不创建 tag/Release。

```bash
(cd artifacts/releases/0.1.10 && shasum -a 256 -c RELEASE-SHA256SUMS)
```

文档/源码变化后也须重打源码 ZIP，并更新发布目录内副本及外部散列，不能沿用打包前的旧 ZIP；已发布的 0.1.9 附件不回写，下一次交付前重新打包。`python3 scripts/package-source.py --version 0.1.10` 仅生成源码包，不重新签发 APK；复制后重新核对发布清单。重新打 APK 则应重新执行准备脚本。

## 用户网页发布

1. 按 [上传检查](PUBLICATION.md) 归档必要结论、清理已过期交接、修复引用、检查实际暂存与公开归档，再将对应源码提交推送到目标仓库。tag 应指向与 APK 对应的提交。
2. GitHub 仓库 → **Releases → Draft a new release**，选择与本版已测源码一致的提交（合入PR后核对源码树），创建未使用的 **`v0.1.10`** tag，标题可为 **Tanuki v0.1.10**。不要只改 tag 而保留 APK 内旧版本。
3. 将上述发布目录的 **全部 6 个附件** 上传到同一个 Release，尤其不能漏掉 `app-update.json`。GitHub 自动生成的 Source code 压缩包不能替代带原生对应源码的完整附件。
4. 本版为 workspacecheck 开发签名测试包，勾选 **Set as a pre-release**；核对版本说明、仓库可见性和附件，再由用户点击 **Publish release**。

应用内检查使用公开 Feed/Release 附件，不调用 GitHub API 或要求 token。测试渠道可以发现公开预发布；正式渠道只跟随 latest 稳定发布。详情及安装边界见 [应用内更新](APP-UPDATES.md)。

## 正式签名渠道

正式发布前先决定长期包名、单调递增版本和证书/迁移策略；使用用户持有并在仓库外备份的独立密钥。不要把开发证书宣传成正式证书，不以卸载旧 App 绕过签名限制。

生成密钥应由用户在可信本机交互完成，例如：

```bash
# 示例，不由 Agent 自动生成或提交；密码不进入命令行或 Git。
keytool -genkeypair -storetype PKCS12 -keystore /用户指定的仓库外目录/Tanuki.p12 \
  -alias tanuki -keyalg RSA -keysize 3072 -validity 10000
```

设置 `TANUKI_KEYSTORE`、`TANUKI_STORE_PASSWORD` 与可选的 `TANUKI_SIGNING_ALIAS` / `TANUKI_KEY_PASSWORD` 后：

```bash
source scripts/env.sh
python3 scripts/prepare-app-release.py --version 用户已确认的版本 --version-code 已确认的递增整数 --channel production
```

[`scripts/sign-release.sh`](../scripts/sign-release.sh) 校验仓库外密钥路径（含相对路径/符号链接），从 `TANUKI_VERSION_NAME` / `TANUKI_VERSION_CODE` 接收明确版本，构建 unsigned、签名、verify、计算 SHA；密码只通过命名环境变量交给 apksigner，不写进源码/Gradle/日志。脚本不上传。

标准 production 包名是 **`com.tanuki.modern`**，不同于当前 `.workspacecheck`，两者不能共享私有 Ubuntu/Node/DSH 或自动迁移数据。同包但不同签名也不能覆盖；完整账号/会话便携迁移仍有 [限制](UPDATE-RECOVERY.md)。本轮文档任务未使用正式密钥、未迁移签名、未构建或发布正式渠道。

## 本地 debug 验证

```bash
source scripts/env.sh
./gradlew -PtestBuildType=debug -PdevelopmentReleaseSigning=true \
  -PtanukiVersionName=0.1.10 -PtanukiVersionCode=12 \
  -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleModernRelease
```

输出路径由构建类型共用：`app/build/outputs/apk/modern/debug/app-modern-debug.apk`、`app/build/outputs/apk/modern/release/app-modern-release.apk`。必须实际核验包名/证书，不凭文件名安装；不传开发签名属性时 release 默认 unsigned。

## 许可与发布说明

PRoot/talloc 等采用 GPL/LGPL，APK 发布必须同时提供精确对应源码、补丁、构建脚本和许可。[`scripts/package-native-sources.sh`](../scripts/package-native-sources.sh) 仅收集必要公共源码，不收集用户 Git 配置、reflog、签名密钥、SDK/JDK、账号或设备数据。

版本说明应列明应用版本/ARM64/SDK、开发或正式签名、证书公开指纹、APK SHA-256；Ubuntu/Node/npm/pnpm/DSH/PRoot 实际版本与来源许可；空间与首次下载、模型 API 数据边界；模拟器/真机/合成登录/真实模型的分别验证范围；WebView123+与 Android Linux 沙箱/后台限制；完整源码和构建方式。不要把局部测试等同正式产品验收。

本轮发布准备分支为 `ao/xlsx-preview/release-0.1.10`（`/tmp` 独立 clone），只改版本号、发布文档与交接，执行 push main、tag v0.1.10 与 Pre-release 上传；不合并 PR；0.1.9 及更早交付附件保持原样。最终由用户确认合并与 Pre-release 上传；Agent 不直推 main 或修改保护规则。


## 审批规则历史与用户合并

2026-11-05 13:23的历史公开查证：[main ruleset 26522583](https://github.com/leezzou/tanuki/rules/26522583)要求1人批准，开启dismiss_stale_reviews_on_push；Code Owners、最新推送审批和review_thread条件未开启，required_reviewers为空。作者不能批准自己的PR；此前答疑建议用户将Required approvals调为0，保留Require a pull request before merging后保存。Agent没有修改保护或代合PR，不把历史参数当当前值。依据见[GitHub审批说明](https://docs.github.com/en/pull-requests/how-tos/review-pull-requests/approving-a-pull-request-with-required-reviews)与[规则编辑说明](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/managing-rulesets-for-a-repository)。历史记录：用户已合入0.1.6的PR #6至main/1024eeb，并已合并0.1.7、发布0.1.8、发布0.1.9；0.1.8 发布点为 `c196bbf`，0.1.9 发布点为 `5c680b9`。后续分支仍由用户自行创建并合并PR、发布Pre-release。

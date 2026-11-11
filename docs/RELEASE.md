# 签名、打包与 GitHub Release

## 发布顺序

**打包上传一气呵成，然后再收尾。**

固定顺序：

1. 确认版本号 → 改 `versionName` / `versionCode`。
2. 构建 → 实核包名、版本、minSdk、证书、大小和 SHA-256。
3. 合并 release PR → 打 tag → 创建 Release → 上传六附件。
4. 全部完成后再做收尾：文档一致性、设备 QA、遗留项。

**明确禁止：** 等 QA 结果再上传、等文档写完再上传、等 CI 全绿再上传、把打包上传拆成多次确认。已获得的打包上传授权连续执行；实际合并、tag、Release 和上传范围以当次明确授权为准，未验证项如实保留。

**当前候选：0.1.14/code19，workspacecheck，最终基线 `main=efc1d9ae4e5d62e6492807d965697a1b863a7511`。** 实核包名 `com.tanuki.modern.workspacecheck`、ARM64、non-debuggable、minSdk26/targetSdk34。六附件已生成并核验于 `artifacts/releases/0.1.14/`。APK **13,093,605 字节**，SHA-256 `87bc0f8b9701eda85afdc384fdafd4953bce8ce334ee1b1fd1d40e42939c1f8f`；证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。 以 `release: 0.1.14` PR交付；未合并、未打tag、未创建或上传Release。

0.1.14 基于最终 main，已包含视觉 P2 #103、弹层/抽屉动效 C 批 #98，以及原生聊天/分页/停靠条、首启、文件传输、失败恢复、局域网发现、阅读恢复、主题/文案和动效 A/B/D/E/F。本发布分支只改版本、必要文档及一条过时的传输抽屉源码契约断言；产品逻辑与最终 main 相同。

**最终集成验证：** 完整 JVM **1407 tests / 144 XML / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 117 warnings / 2 hints**。 最终集成首轮仅过时源码断言失败，定向6项及全量复跑后通过；中间失败保留于TEST-REPORT。APK实际身份/签名/字节/散列和六附件清单均已核验；未执行设备、Android仪器或真实链路。API Key、真实模型/账号、真机验收继续暂停。

**用户说要发布时，先询问版本号；已经明确的版本不重复问。** 历史记录：用户确认过 **v0.1.7**（code9）、**v0.1.8**（code10）、**v0.1.9**（code11）、**v0.1.11**（code13）；本次确认 **v0.1.14**（code19），沿用原有 workspacecheck 开发证书。后续封装前须重新核对公开、已安装及归档版本上限，确保 code 严格递增；不能只依据本地默认值判断。


源码已推送不等于 Release 已发布；本地打包、Git 提交/推送、创建 tag、上传附件和更改可见性分别遵守当前授权。

发布按开头「发布顺序」连续完成，文档一致性、设备 QA 和遗留项在六附件上传后收尾。封装按已实核 APK 的实际 SHA-256 固定字节；如 APK 字节改变，重新核验，不能沿用旧结果。最终回执与外部校验清单记录交付结果，不为了回填提交自身再制造后续提交。

本次任务按明确的两阶段授权执行：已收到 `rebuild`，完成fetch/merge最终origin/main、版本核对、旧目录检查、普通构建、APK实核及六附件校验；文档/源码副本收尾后统一一次commit/push，创建标题 `release: 0.1.14` 的PR。不合并、不打 tag、不创建或上传 Release。

```bash
source scripts/ao-worktree-env.sh
python3 scripts/prepare-app-release.py --version 0.1.14 --version-code 19 --channel workspacecheck
```

workspacecheck 的 worktree 环境可能生成新的 debug.keystore，不能假设 `developmentReleaseSigning=true` 自动等于既有证书。必须实核完整 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；不符时以仓库外既有开发证书重新签名已构建载荷，验证后使用下述固定字节封装模式。不要把密钥或密码加入源码或附件。本次已完成该修正，最终 APK 散列以上文为准。

## 已确认版本的最终封装

确认版本并完成构建与 APK 实核后，使用下面的固定字节模式封装六附件，随后按已授权范围连续完成 release PR 合并、tag、Release 和上传；不以设备 QA、文档收尾或 CI 全绿作为上传等待项：

```bash
source scripts/env.sh
python3 scripts/prepare-app-release.py --version 0.1.14 --version-code 19 --channel workspacecheck \
  --verified-apk <已完成测试的候选.apk> --verified-apk-sha256 <候选实际SHA256>
```

两个verified参数必须成对，哈希必须完整匹配。脚本先复制到私有临时文件固定字节，仍检查实际包名、版本、SDK和签名，封装副本再次核hash；只跳过重新编译。此方式避免推送改变AGP提交号元数据后生成另一份未测APK，不能用来宣称未经测试的包已通过。普通构建入口仍保留。

脚本先以发布严格模式校验当前交接（`check-handoff.py --release`，要求印章、记录哈希、公开源码指纹与最新记录一致）；verified模式直接核对已测APK的字节和真实包名/版本/SDK/证书，普通模式才重新构建。输出 **`artifacts/releases/0.1.14/`**（只从此目录取本版发布附件；分类与历史归档见[取包说明](ARTIFACTS.md)）：

- `Tanuki-0.1.14-workspacecheck-arm64-release.apk`
- `app-update.json`：应用内更新所需清单；不是手写的可信证书来源。
- `Tanuki-0.1.14-source.zip`：完整公开源码，另含必要原生对应源码附件。
- `Tanuki-native-corresponding-sources.tar.gz`、`native-source-SHA256SUMS`
- `RELEASE-SHA256SUMS`：以上附件的实际 SHA-256。

该渠道是 **开发证书签名、non-debuggable 的 release 构建**，并非正式签名。目标为 `com.tanuki.modern.workspacecheck`、ARM64、minSdk26/targetSdk34；保持原开发证书才能覆盖之前同包测试版。生成过程不读取或上传用户数据，不访问设备，不创建 tag/Release。本版 APK 大小/SHA-256 以发布目录的实际校验清单为准，证书须实核前缀 `371dc301`。

```bash
(cd artifacts/releases/0.1.14 && shasum -a 256 -c RELEASE-SHA256SUMS)
```

文档/源码变化后也须重打源码 ZIP，并更新发布目录内副本及外部散列，不能沿用打包前的旧 ZIP；已发布的 0.1.11 及更早附件不回写，下一次交付前重新打包。`python3 scripts/package-source.py --version 0.1.14` 仅生成源码包，不重新签发 APK；复制后重新核对发布清单。重新打 APK 则应重新执行准备脚本。

## 用户网页发布

1. 完成版本修改、构建、APK 实核与六附件准备，将对应源码提交推送并合并 release PR；tag 应指向与 APK 对应的提交。文档一致性、设备 QA 与遗留项在上传完成后收尾；公开文件与附件的数据边界仍按 [上传检查](PUBLICATION.md) 核对。
2. GitHub 仓库 → **Releases → Draft a new release**，选择与本版已测源码一致的提交（合入PR后核对源码树），创建未使用的 **`v0.1.14`** tag，标题可为 **Tanuki v0.1.14**。不要只改 tag 而保留 APK 内旧版本。
3. 将上述发布目录的 **全部 6 个附件** 上传到同一个 Release，尤其不能漏掉 `app-update.json`。GitHub 自动生成的 Source code 压缩包不能替代带原生对应源码的完整附件。
4. 本版为 workspacecheck 开发签名测试包，勾选 **Set as a pre-release**；核对版本说明、仓库可见性和附件，再由用户点击 **Publish release**。

应用内检查使用公开 Feed/Release 附件，不调用 GitHub API 或要求 token。测试渠道可以发现公开预发布；正式渠道只跟随 latest 稳定发布。详情及安装边界见[应用内更新](APP-UPDATES.md)。

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
  -PtanukiVersionName=0.1.14 -PtanukiVersionCode=19 \
  -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleModernRelease
```

输出路径由构建类型共用：`app/build/outputs/apk/modern/debug/app-modern-debug.apk`、`app/build/outputs/apk/modern/release/app-modern-release.apk`。必须实际核验包名/证书，不凭文件名安装；不传开发签名属性时 release 默认 unsigned。

## 许可与发布说明

PRoot/talloc 等采用 GPL/LGPL，APK 发布必须同时提供精确对应源码、补丁、构建脚本和许可。[`scripts/package-native-sources.sh`](../scripts/package-native-sources.sh) 仅收集必要公共源码，不收集用户 Git 配置、reflog、签名密钥、SDK/JDK、账号或设备数据。

版本说明应列明应用版本/ARM64/SDK、开发或正式签名、证书公开指纹、APK SHA-256；Ubuntu/Node/npm/pnpm/DSH/PRoot 实际版本与来源许可；空间与首次下载、模型 API 数据边界；模拟器/真机/合成登录/真实模型的分别验证范围；WebView123+ 与 Android Linux 沙箱/后台限制；完整源码和构建方式。不要把局部测试等同正式产品验收。

0.1.14 基于 `main=efc1d9ae` 准备，未合并/未发布；旧版附件保持原样。


## 审批规则历史与用户合并

2026-11-05 13:23的历史公开查证：[main ruleset 26522583](https://github.com/leezzou/tanuki/rules/26522583)要求1人批准，开启dismiss_stale_reviews_on_push；Code Owners、最新推送审批和review_thread条件未开启，required_reviewers为空。作者不能批准自己的PR；此前答疑建议用户将Required approvals调为0，保留Require a pull request before merging后保存。Agent没有修改保护或代合PR，不把历史参数当当前值。依据见[GitHub审批说明](https://docs.github.com/en/pull-requests/how-tos/pull-requests/approving-a-pull-request-with-required-reviews)与[规则编辑说明](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/managing-rulesets-for-a-repository)。历史记录：用户已合入0.1.6的PR #6至main/1024eeb，并已合并0.1.7、发布0.1.8、发布0.1.9、发布0.1.11；0.1.8 发布点为 `c196bbf`，0.1.9 发布点为 `5c680b9`。后续分支仍由用户自行创建并合并PR、发布Pre-release。

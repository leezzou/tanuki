# Tanuki 构建产物：从哪里取包

**当前 workspacecheck 候选为 v0.1.12/code14（基于最终 `main=f338bdb`，含 #48/#51/#57/#58/#59）**：包名 `com.tanuki.modern.workspacecheck`、ARM64、non-debuggable、minSdk26/targetSdk34，六附件在 `artifacts/releases/0.1.12/`；APK SHA-256 `27580e6d4c2234466b026cd46c65a3c01794a327c717acc1218cb19ca6949a02`、12,295,334 字节、证书 `371dc301...`，最终以该目录的 `RELEASE-SHA256SUMS`、`app-update.json` 和交接记录为准。发布 PR 待合并；**未创建 tag/Release、未上传附件。** 已发布版本仍为 v0.1.11/code13（Pre-release，tag `v0.1.11`）：APK SHA-256 `192f73f2e07782c63a9b8defc5294153987fd03285181a5c12960531231a976d`、11,890,210 字节、证书 `371dc301...`。上一 Pre-release 0.1.9/code11（tag `5c680b9`）APK SHA-256 `210b6ec2119e8dd6ee08ec3d2be07889f1fa36720c6e15dd24d74a1d25a49dc8`、10,506,550 字节；0.1.8/code10 及更早目录保持历史原样。

- **0.1.12/code14 workspacecheck 候选（发布 PR 待合并）**：`artifacts/releases/0.1.12/Tanuki-0.1.12-workspacecheck-arm64-release.apk`，SHA-256 `27580e6d4c2234466b026cd46c65a3c01794a327c717acc1218cb19ca6949a02`、12,295,334 字节；同目录含 `app-update.json`、完整源码 ZIP、原生对应源码 TAR 与校验。
- **0.1.11/code13 已发布（Pre-release，tag `v0.1.11`）**：`artifacts/releases/0.1.11/Tanuki-0.1.11-workspacecheck-arm64-release.apk`，SHA-256 `192f73f2e07782c63a9b8defc5294153987fd03285181a5c12960531231a976d`、11,890,210 字节、证书 `371dc301...`；同目录含 `app-update.json`、完整源码 ZIP、原生对应源码 TAR 与校验。
- **0.1.10/code12 历史本地候选（未发布）**：`artifacts/releases/0.1.10/Tanuki-0.1.10-workspacecheck-arm64-release.apk`，SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`、`11,485,978` 字节；仅作历史核对，不是当前发布附件。
- GitHub 发布：`v0.1.11` **Pre-release** 已发布，含 `artifacts/releases/0.1.11/` 全部 **6 个附件**（`app-update.json`、完整源码、原生对应源码及散列）；0.1.12 尚未上传，未覆盖 0.1.11/更早附件。
- 当前是同一开发证书的 `com.tanuki.modern.workspacecheck` 包，ARM64、non-debuggable、minSdk26/targetSdk34；不是正式签名，也没有由 Agent 自动发布。后续发布须重新确认版本并核对 code 严格递增。
- 根目录不再放 APK/ZIP。`archive/` 中的旧 0.1.0 和已撤回、未发布的旧候选 0.1.2/code3 **不要拿来安装或发布**；0.1.9 取 `releases/0.1.9/`，0.1.11 取 `releases/0.1.11/`，0.1.12 候选取 `releases/0.1.12/`，不能只按版本名称猜测当前附件。

0.1.12 候选基于最终 `main=f338bdb`，包含 0.1.11 之后合并的原生聊天渲染、输入胶囊、审批抽屉与提问卡、会话管理设置、文件页瘦身、SSH 六步向导、文本编码识别、设计令牌；完整 JVM **1044 tests / 107 结果文件**、lint **0 errors / 101 warnings / 1 hint**。0.1.11 已发布，包含其之前合并的原生聊天/输入栏/审批接线、交接机制、文件查看器、复制稳定性、远程中继、原生会话管理、原生审批/工具、输入附件、DSH 版本卸载等；#40 `DshTransport` 已随 0.1.11 包含（合并点 `1f7222d0`）；临时 `DshHttpAdminTransport` 适配器切换留作后续清理。0.1.10 为历史本地候选，不是已发布版本。行为和权限说明见[项目存储说明](WORKSPACE-STORAGE.md)/[浏览器工作区](BROWSER-WORKSPACE.md)。源码准备区文件名随 `scripts/package-source.py --version` 变化，不代表已发布；0.1.9 已交付源码快照取 `releases/0.1.9/Tanuki-0.1.9-source.zip`，0.1.11 已发布源码在 `releases/0.1.11/`，0.1.12 候选源码在 `releases/0.1.12/`。发布目录是否齐全以实际六附件及`RELEASE-SHA256SUMS`为准，验证范围见[测试现状](TEST-REPORT.md)。2026-11-04的PR #2组合包仍在 `artifacts/builds/workspacecheck/pr2-upload-browser/Tanuki-pr2-upload-browser-debug.apk`，是历史0.1.3/5开发debug包，不是本次release。

## 目录约定

以下树以工程根目录为起点；打开本文件的产物副本时，当前文件夹就是 `artifacts/`。

```text
artifacts/
├── README.md                  # 本说明的生成副本
├── SOURCE-SHA256SUMS           # 最近一次源码打包的校验入口
├── releases/
│   ├── 0.1.1/                 # 已发布历史附件，保持原样
│   ├── 0.1.3/                 # 既有发布附件，原样保留
│   ├── 0.1.4/                 # 既有发布附件，原样保留
│   ├── 0.1.5/                 # 已交付历史附件（code7），保持原样
│   ├── 0.1.6/                 # 已交付历史附件（code8），保持原样
│   ├── 0.1.7/                 # 已交付历史附件（code9），保持原样
│   ├── 0.1.8/                 # 历史发布（code10），保持原样
│   ├── 0.1.9/                 # 已发布 Pre-release（code11，6 个附件）
│   ├── 0.1.10/                # 历史本地候选（code12，未发布）
│   ├── 0.1.11/                # 已发布 Pre-release（code13，6 个附件）
│   └── 0.1.12/                # workspacecheck 候选（code14，6 个附件，未发布）
├── sources/
│   ├── Tanuki-<版本>-source.zip # 最近一次源码打包副本，按实际版本命名
│   └── native/                # 原生对应源码和同目录校验清单
├── builds/
│   ├── production/<版本>/     # 正式签名脚本的中间 APK/签名报告，非完整发布包
│   └── workspacecheck/pr2-upload-browser/ # 历史开发debug验证包及SHA256SUMS
└── archive/
    ├── 0.1.0-dev/             # 旧开发 APK、旧源码和原校验文件
    ├── 0.1.1-before-layout/   # 本次整理前的源码/校验记录；不是完整发布包
    ├── 0.1.2-withdrawn/       # 已被用户撤回的候选版本，原目录保留
    ├── 0.1.2-code4-before-uri-fix/ # 修复前中间包，只归档、不发布
    └── ARCHIVE-SHA256SUMS      # 归档后可直接校验的相对路径清单
```

`builds/production/` 只在实际正式签名时生成；没有正式密钥时不要创建假包。日常 debug/AndroidTest 构建仍在 `app/build/outputs/apk/`，不冒充发布附件。`sources/` 与 `builds/` 是准备区，不上传整个目录。

## 校验与维护

在工程根目录执行：

```bash
(cd artifacts && shasum -a 256 -c SOURCE-SHA256SUMS)
(cd artifacts/sources/native && shasum -a 256 -c native-source-SHA256SUMS)
(cd artifacts/releases/0.1.12 && shasum -a 256 -c RELEASE-SHA256SUMS && shasum -a 256 -c native-source-SHA256SUMS)
(cd artifacts/releases/0.1.11 && shasum -a 256 -c RELEASE-SHA256SUMS)
(cd artifacts/archive && shasum -a 256 -c ARCHIVE-SHA256SUMS)
```

历史清单原文保留，里面可能是迁移前的路径或旧打包缺陷；不把它们当当前清单执行。归档完整性使用 `ARCHIVE-SHA256SUMS`，当前发布使用对应版本目录的 `RELEASE-SHA256SUMS`。

- `scripts/package-source.py` 写入 `sources/`，并刷新根目录的 `SOURCE-SHA256SUMS` 与本说明副本。
- `scripts/package-native-sources.sh` 写入 `sources/native/`，校验路径相对此目录。
- `scripts/sign-release.sh` 写入 `builds/production/<版本>/`；`prepare-app-release.py` 核对实际 APK 后，从分类目录收集完整发布附件至 `releases/<版本>/`。
- 发布前先确认版本；同步本说明与当前状态，不在根目录复制“最新 APK”别名，不改旧 APK 文件名假装新版，不根据目录名代替实际包名/签名检查。
- 只改文档/脚本时不重编 APK；下一次交付前重打源码并刷新散列，已发布的 0.1.11 及更早附件不回写。
- `artifacts/` 全部是被Git忽略的生成产物；本说明的源码为 `docs/ARTIFACTS.md`。既有历史包和校验保持原样，不因本次版本入口更新而删除或改写。

0.1.11 已由 PR #44 squash 合并且发布（main=`4f2a3717d6d7`，tag `v0.1.11`，Pre-release）。0.1.12 候选基于最终 `main=f338bdb` 生成，发布 PR 待合并；未创建 tag/Release、未上传附件；0.1.9 及更早交付附件保持原样。

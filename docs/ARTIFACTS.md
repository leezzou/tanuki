# Tanuki 构建产物：从哪里取包

**已交付发布包为 v0.1.8/code10。** `v0.1.8` tag 为 `c196bbf`，远端 `main` 为 `14d2884`（PR #13/#15 已合并）；release 六附件保持原样，0.1.8 没有随版本记录设备测试。本轮为研发治理任务，分支 `chore/autonomous-development-governance`，不发布新版本。

- 安装 APK：`artifacts/releases/0.1.8/Tanuki-0.1.8-workspacecheck-arm64-release.apk`
- GitHub 发布：由用户在网页创建 `v0.1.8` 的 **Pre-release**，只上传 `artifacts/releases/0.1.8/` 内全部 **6 个附件**，包含 `app-update.json`、完整源码、原生对应源码及散列。
- 当前是同一开发证书的 `com.tanuki.modern.workspacecheck` 包，ARM64、non-debuggable、minSdk26/targetSdk34；不是正式签名，也没有由Agent自动发布。后续发布须重新确认版本并核对code严格递增。
- 根目录不再放APK/ZIP。`archive/`中的旧0.1.0和已撤回、未发布的旧候选0.1.2/code3 **不要拿来安装或发布**；当前新包取 `releases/0.1.8/`，不能只按版本名称猜测当前附件。0.1.7/code9及更早历史发布目录、旧包与校验保留原样。

0.1.8 包含运行中文件访问、手机共享存储、多格式查看与浏览器输入线程修复，行为和权限说明见[项目存储说明](WORKSPACE-STORAGE.md)/[浏览器工作区](BROWSER-WORKSPACE.md)。源码准备区文件名随 `scripts/package-source.py --version` 变化，不代表已发布；已交付源码快照取 `releases/0.1.8/Tanuki-0.1.8-source.zip`。发布目录是否齐全以实际六附件及`RELEASE-SHA256SUMS`为准，验证范围见[测试现状](TEST-REPORT.md)。2026-11-04的PR #2组合包仍在 `artifacts/builds/workspacecheck/pr2-upload-browser/Tanuki-pr2-upload-browser-debug.apk`，是历史0.1.3/5开发debug包，不是本次release。

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
│   └── 0.1.8/                 # 当前已发布附件（6 个文件，code10）
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
(cd artifacts/releases/0.1.8 && shasum -a 256 -c RELEASE-SHA256SUMS && shasum -a 256 -c native-source-SHA256SUMS)
(cd artifacts/archive && shasum -a 256 -c ARCHIVE-SHA256SUMS)
```

历史清单原文保留，里面可能是迁移前的路径或旧打包缺陷；不把它们当当前清单执行。归档完整性使用 `ARCHIVE-SHA256SUMS`，当前发布使用对应版本目录的 `RELEASE-SHA256SUMS`。

- `scripts/package-source.py` 写入 `sources/`，并刷新根目录的 `SOURCE-SHA256SUMS` 与本说明副本。
- `scripts/package-native-sources.sh` 写入 `sources/native/`，校验路径相对此目录。
- `scripts/sign-release.sh` 写入 `builds/production/<版本>/`；`prepare-app-release.py` 核对实际 APK 后，从分类目录收集完整发布附件至 `releases/<版本>/`。
- 发布前先确认版本；同步本说明与当前状态，不在根目录复制“最新 APK”别名，不改旧 APK 文件名假装新版，不根据目录名代替实际包名/签名检查。
- 只改文档/脚本时不重编 APK；下一次交付前重打源码并刷新散列，已发布的 0.1.8 及更早附件不回写。
- `artifacts/` 全部是被Git忽略的生成产物；本说明的源码为 `docs/ARTIFACTS.md`。既有历史包和校验保持原样，不因本次版本入口更新而删除或改写。

本轮工作分支为 `chore/autonomous-development-governance`，只改治理文档、门禁脚本/回归和 CI Host 步骤，经 PR 交付；0.1.8 及其更早交付附件保持原样。main 通过 PR 合入，由用户自行创建并合并 PR、创建 tag 及发布 Pre-release；Agent 不直推 main 或修改保护规则。

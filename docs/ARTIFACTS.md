# Tanuki 构建产物：从哪里取包

**本次发布目标为 v0.1.7（候选versionCode 9）。** 候选须完成本轮release验证、文档与交接、过期记录清理和公开检查，统一一次commit/push成功后，再按已测APK的SHA使用verified模式生成最终六附件；不能用此前debug结果代替。

- 安装 APK：`artifacts/releases/0.1.7/Tanuki-0.1.7-workspacecheck-arm64-release.apk`
- GitHub 发布：由用户在网页创建 `v0.1.7` 的 **Pre-release**，只上传 `artifacts/releases/0.1.7/` 内全部 **6 个附件**，包含 `app-update.json`、完整源码、原生对应源码及散列。
- 当前是同一开发证书的 `com.tanuki.modern.workspacecheck` 包，ARM64、non-debuggable、minSdk26/targetSdk34；不是正式签名，也没有由Agent自动发布。封装前须核对code9严格高于公开、已安装及归档版本上限。
- 根目录不再放APK/ZIP。`archive/`中的旧0.1.0和已撤回、未发布的旧候选0.1.2/code3 **不要拿来安装或发布**；本次新包取 `releases/0.1.7/`，不能只按版本名称猜测当前附件。0.1.6/code8及更早历史发布目录、旧包与校验保留原样。

本版包含运行中文件访问、手机共享存储与多格式查看改进，行为和权限说明见[项目存储说明](WORKSPACE-STORAGE.md)。当前源码准备区对应本次工作树；最终发布目录须在release验证和新分支推送成功后生成，是否齐全以实际六附件及`RELEASE-SHA256SUMS`为准，验证范围见[测试现状](TEST-REPORT.md)。2026-11-04的PR #2组合包仍在 `artifacts/builds/workspacecheck/pr2-upload-browser/Tanuki-pr2-upload-browser-debug.apk`，是历史0.1.3/5开发debug包，不是本次release。

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
│   └── 0.1.7/                 # 本次最终封装目标（6 个文件，code9）
├── sources/
│   ├── Tanuki-0.1.7-source.zip # 当前工作树源码的打包副本
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
(cd artifacts/releases/0.1.7 && shasum -a 256 -c RELEASE-SHA256SUMS && shasum -a 256 -c native-source-SHA256SUMS)
(cd artifacts/archive && shasum -a 256 -c ARCHIVE-SHA256SUMS)
```

历史清单原文保留，里面可能是迁移前的路径或旧打包缺陷；不把它们当当前清单执行。归档完整性使用 `ARCHIVE-SHA256SUMS`，当前发布使用对应版本目录的 `RELEASE-SHA256SUMS`。

- `scripts/package-source.py` 写入 `sources/`，并刷新根目录的 `SOURCE-SHA256SUMS` 与本说明副本。
- `scripts/package-native-sources.sh` 写入 `sources/native/`，校验路径相对此目录。
- `scripts/sign-release.sh` 写入 `builds/production/<版本>/`；`prepare-app-release.py` 核对实际 APK 后，从分类目录收集完整发布附件至 `releases/<版本>/`。
- 发布前先确认版本；同步本说明与当前状态，不在根目录复制“最新 APK”别名，不改旧 APK 文件名假装新版，不根据目录名代替实际包名/签名检查。
- 只改文档/脚本时不重编 APK，但必须重打源码、刷新待交付版本目录的源码副本与散列。已发布的历史附件不要静默改写。
- `artifacts/` 全部是被Git忽略的生成产物；本说明的源码为 `docs/ARTIFACTS.md`。既有历史包和校验保持原样，不因本次版本入口更新而删除或改写。

本次工作分支为 `feature/live-file-manager-017`。完成适用release测试、文档与交接、过期记录清理、公开源码候选及实际index检查后，统一一次commit/push该新分支；推送成功后按verified模式封装同一已测APK。main通过PR合入，由用户自行创建并合并PR、创建tag及发布Pre-release；Agent不直推main或修改保护规则。0.1.6及更早已交付附件保持原样。

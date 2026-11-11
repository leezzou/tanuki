# Tanuki 构建产物：从哪里取包

**当前候选：0.1.14/code19，workspacecheck，最终基线 `main=16c07591b7ba170a77fdc0720b201a77e5477db3`。** 实核 `com.tanuki.modern.workspacecheck`、ARM64、non-debuggable、minSdk26/targetSdk34；普通准备入口重新生成 `artifacts/releases/0.1.14/` 六附件。APK **13,073,258 字节**，SHA-256 `57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff`；证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。 本轮更新既有 `release: 0.1.14` PR #105，未合并、未打tag、未创建或上传Release。

0.1.14 已合入最终 main 的 #104 常驻 sheet 宿主完整断言，覆盖单一宿主、visible互斥、直属层级和失败详情可空模型；传输与失败详情的产品代码不变。已包含此前 #98/#103 及主题/文案、聊天/分页、动效 A/B/C/D/E/F 等实现。本轮测试类与最终main按字节相等，不保留上一轮发布分支的最小替代断言。

**最终集成验证：** 完整 JVM **1408 tests / 144 XML / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 117 warnings / 2 hints**。 本轮在合并工作树无过滤执行JVM/lint，BUILD SUCCESSFUL（59s）。普通release构建成功（15s，增量；package任务实际执行），既有开发证书由私有缓存init显式引用；APK身份/签名与六附件清单已独立核验。设备、Android仪器、真实链路未执行，API Key/真实模型/账号/真机仍暂停。

- **0.1.14/code19 workspacecheck 候选**：`artifacts/releases/0.1.14/Tanuki-0.1.14-workspacecheck-arm64-release.apk`；13,073,258字节，SHA-256 `57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff`；同目录六附件已核验，设备QA未执行。
- **0.1.11/code13 已发布（Pre-release，tag `v0.1.11`）**：`artifacts/releases/0.1.11/Tanuki-0.1.11-workspacecheck-arm64-release.apk`，SHA-256 `192f73f2e07782c63a9b8defc5294153987fd03285181a5c12960531231a976d`、11,890,210 字节、证书 `371dc301...`；同目录含 `app-update.json`、完整源码 ZIP、原生对应源码 TAR 与校验。
- **0.1.10/code12 历史本地候选（未发布）**：`artifacts/releases/0.1.10/Tanuki-0.1.10-workspacecheck-arm64-release.apk`，SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`、`11,485,978` 字节；仅作历史核对，不是当前发布附件。
- GitHub 发布：`v0.1.11` **Pre-release** 已发布，含 `artifacts/releases/0.1.11/` 全部 **6 个附件**（`app-update.json`、完整源码、原生对应源码及散列）；0.1.14 尚未上传，未覆盖 0.1.11/更早附件。
- 当前是同一开发证书的 `com.tanuki.modern.workspacecheck` 包，ARM64、non-debuggable、minSdk26/targetSdk34；不是正式签名，也没有由 Agent 自动发布。后续发布须重新确认版本并核对 code 严格递增。
- 根目录不再放 APK/ZIP。`archive/` 中的旧 0.1.0 和已撤回、未发布的旧候选 0.1.2/code3 **不要拿来安装或发布**；0.1.9 取 `releases/0.1.9/`，0.1.11 取 `releases/0.1.11/`，0.1.14 候选取 `releases/0.1.14/`，不能只按版本名称猜测当前附件。


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
│   └── 0.1.14/                # workspacecheck 候选（code19，六附件已核验，未发布）
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
(cd artifacts/releases/0.1.14 && shasum -a 256 -c RELEASE-SHA256SUMS && shasum -a 256 -c native-source-SHA256SUMS)
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

0.1.14 基于 `main=16c07591b` 准备，未合并/未发布；旧版附件保持原样。

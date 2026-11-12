# Tanuki 构建产物：从哪里取包

**启用前历史已核发布基线：0.1.14/code19，workspacecheck；tag `v0.1.14` 指向 `3c56167b94efeff47a20bd609b389708ba080ce5`。** 以下候选构建的原源码基线为 `16c07591b7ba170a77fdc0720b201a77e5477db3`。 实核 `com.tanuki.modern.workspacecheck`、ARM64、non-debuggable、minSdk26/targetSdk34；普通准备入口重新生成 `artifacts/releases/0.1.14/` 六附件。APK **13,073,258 字节**，SHA-256 `57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff`；证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。 PR #105 已合并，六附件已发布为 Pre-release。后续[自动发布队列](RELEASE.md)将候选持久保存在服务目录 `bundles/<merge-sha>/`，代码/状态不依赖临时工作树；启用前仍须完成审查与适用验证。

**实际当前取包依据：** GitHub Release、该 Release 对应的 `app-update.json` 与自动队列 `status`，不要把本页历史批次当最新版。以下测试数字只属于 0.1.14 历史验证，生成的 `artifacts/README.md` 同样保留此限定；每次自动发布不反向提交版本文档来触发额外发布。

0.1.14 已合入当时 main 的 #104 常驻 sheet 宿主完整断言，覆盖单一宿主、visible互斥、直属层级和失败详情可空模型；传输与失败详情的产品代码不变。已包含此前 #98/#103 及主题/文案、聊天/分页、动效 A/B/C/D/E/F 等实现。本轮测试类与最终main按字节相等，不保留上一轮发布分支的最小替代断言。

**0.1.14 历史集成验证（不代表后续每版）：** 完整 JVM **1408 tests / 144 XML / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 117 warnings / 2 hints**。 本轮在合并工作树无过滤执行JVM/lint，BUILD SUCCESSFUL（59s）。普通release构建成功（15s，增量；package任务实际执行），既有开发证书由私有缓存init显式引用；APK身份/签名与六附件清单已独立核验。设备、Android仪器、真实链路未执行，API Key/真实模型/账号/真机仍暂停。

- **0.1.14/code19 workspacecheck 候选**：`artifacts/releases/0.1.14/Tanuki-0.1.14-workspacecheck-arm64-release.apk`；13,073,258字节，SHA-256 `57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff`；同目录六附件已核验，设备QA未执行。
- **0.1.11/code13 已发布（Pre-release，tag `v0.1.11`）**：`artifacts/releases/0.1.11/Tanuki-0.1.11-workspacecheck-arm64-release.apk`，SHA-256 `192f73f2e07782c63a9b8defc5294153987fd03285181a5c12960531231a976d`、11,890,210 字节、证书 `371dc301...`；同目录含 `app-update.json`、完整源码 ZIP、原生对应源码 TAR 与校验。
- **0.1.10/code12 历史本地候选（未发布）**：`artifacts/releases/0.1.10/Tanuki-0.1.10-workspacecheck-arm64-release.apk`，SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`、`11,485,978` 字节；仅作历史核对，不是当前发布附件。
- GitHub 发布：`v0.1.11` **Pre-release** 已发布，含 `artifacts/releases/0.1.11/` 全部 **6 个附件**（`app-update.json`、完整源码、原生对应源码及散列）；0.1.14 已上传全部六附件，未覆盖 0.1.11/更早附件。
- 当前是同一开发证书的 `com.tanuki.modern.workspacecheck` 包，ARM64、non-debuggable、minSdk26/targetSdk34；不是正式签名。后续发布由持久队列自动递增整数版本/code，通过 gh 上传，无需 Agent 执行上传。
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
│   └── 0.1.14/                # workspacecheck Pre-release（code19，六附件已发布）
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
- 自动发布以队列保留的整数版本/code 为准；手工封装必须明确身份。不在根目录复制“最新 APK”别名，不改旧 APK 文件名假装新版，不根据目录名代替实际包名/签名检查。
- 维护既有已发布版本的文档不回写旧附件；main 每次新合并都按新版本/code 生成对应 APK，并重打本次完整源码和清单。缓存可复用，旧 APK 不能冒充新版本。
- `artifacts/` 全部是被Git忽略的生成产物；本说明的源码为 `docs/ARTIFACTS.md`。既有历史包和校验保持原样，不因本次版本入口更新而删除或改写。

自动发布仍是六附件；完整源码 ZIP 内另含生成的 RELEASE-SOURCE.json（不是第七附件），绑定原提交/tree、公开文件散列、本次交接和版本/code/证书/原 APK 散列。解压后通过 release-source.py 验证/计划或执行同身份重建，无需原 .git 或前任缓存；证明不等于设备 QA。具体命令与依赖边界见[发布说明](RELEASE.md)。

0.1.14 已发布身份见公开 tag/清单；本页构建和测试为历史候选记录。旧版附件保持原样，不按目录最大版本猜测有效发布。

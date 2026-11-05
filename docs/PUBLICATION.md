# Git 上传与脱敏

当前视频/手机文件独立写授权已完成debug目标验证；用户追加授权推送新分支 `fix/video-phone-file-permissions`，由用户自行合并。未要求新版本，下述0.1.7/code9为上一轮已完成交付，不覆盖其发布目录；当前状态以[交接](AGENT-STATE.md)为准。

## 只上传源码

保留源码、Gradle Wrapper、固定构建脚本/补丁、许可和必要文档；本轮新增/修改的第一方注释使用中文，第三方版权/许可证保留原文。`.gitignore` 排除构建/原生ELF/安装包/归档、缓存/依赖、本机IDE和SDK配置、密钥/签名/账号配置、用户项目/会话/快照、原始日志/抓包/数据库。`docs/evidence/` 不上传，公开测试结论写 [TEST-REPORT](TEST-REPORT.md) 和当前交接。

忽略不等于脱敏：**已跟踪文件仍会提交，历史也不会消失**。不要自动 `git add .`、提交或推送；先检查实际候选和暂存内容。

## 每次提交/推送前清理过期交接

每次 Git 提交或推送前，先检查 `docs/handoffs/`，清理已经完成或被后续实现替代、且必要信息已归档的记录，再做下方上传检查；不能只依靠 `.gitignore`、Git 历史或仅清理工作树。

1. 逐项核对记录的独有结论、验证、失败与未完成事项；将仍有效的信息归入当前状态、专题文档或 [TEST-REPORT](TEST-REPORT.md)，保留实际日期和未测边界。
2. 删除已确认过期的记录，修复文档引用，在当前交接列出清理范围与保留依据。当前记录、`TEMPLATE.md`、`CURRENT.json`、仍需使用的验证依据和未完成记录保留；不按日期一刀切。
3. 更新状态/本次记录，运行带 `--record` 的交接刷新和无参数检查；重打源码包、核对外部 SHA，并执行本地链接和公开归档检查。
4. 只有获准提交时才按明确路径暂存，包括已跟踪旧记录的删除；复查实际暂存不残留过期记录，再运行 `check-publication.py --git`、`git diff --cached --check`。推送已有提交前，也需复查这一清理要求；不静默改写已经发布的历史。

此步骤是每次提交/推送的必做人工流程；现有检查脚本验证指纹/公开边界，不会自动判断自然语言记录是否过期或自动删除。

## 本地检查

```bash
python3 scripts/test-agent-workflow.py
python3 scripts/test-publication.py
python3 scripts/check-publication.py --git
python3 scripts/check-handoff.py

git status --short
git diff --cached --stat
# 在本机人工复查 git diff --cached，勿把潜在秘密贴到聊天/公开日志。
```

`check-publication.py --git` 分别检查索引blob和工作树候选，已暂存内容即使在工作树被清理也不会漏扫；不打开被忽略的凭据，已跟踪文件若违反忽略/公开边界只报告、不代删。检查本机身份路径、常见API/云/平台凭据格式、非示例邮箱和文档链接；归档检查包含ZIP/APK/TAR成员、TAR身份元数据与嵌套累计预算。输出文件位置也先脱敏，**不输出匹配值**。必要第三方许可/固定上游源码署名保留，第一方文件不作整包豁免。

交付附件独立放在被忽略的artifacts；0.1.6及更早历史包与校验保持原样。本机配置、生成缓存、原始日志/截图、JDK、AVD数据/控制文件和其他任务的暂存不进入公开源码，不因整理本轮源码而删除历史交付、wipe设备或杀进程。

这不是全能Secret Scanner：不能识别所有姓名、任意插件输出、自定义凭据格式、所有图片像素或Git历史。历史截图/原始诊断已从docs清理；新增图片/附件必须人工检查像素、EXIF和内容。示例只能使用合成值/保留域名。

## 身份与历史

提交前自行设置允许公开的昵称和GitHub隐私邮箱，例如：

```bash
git config --local user.name "Public contributor"
git config --local user.email "<GitHub提供的隐私邮箱>"
```

本地初始化/修改作者设置不能改写已提交历史。若文件已被跟踪，确认无泄露后再有选择地从索引移除；若秘密曾公开，先吊销/轮换，再另行授权清理历史。检查脚本不会重写历史、修改作者配置或上传文件。

## 源码归档与二进制

用户本次确认0.1.7，候选versionCode为9；发布前须重新核对公开/设备/归档版本上限，确保严格递增。沿用同workspacecheck开发证书、non-debuggable、ARM64、minSdk26/targetSdk34。本次文件管理功能与权限边界见[项目存储说明](WORKSPACE-STORAGE.md)，候选release须独立验证；debug或0.1.6历史测试不能替代本轮结果，实际范围见[测试现状](TEST-REPORT.md)。

```bash
python3 scripts/check-publication.py
python3 scripts/package-source.py --version 0.1.7
python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip
(cd artifacts && shasum -a 256 -c SOURCE-SHA256SUMS)
```

Git检出不依赖本机构建附件。源码ZIP按明确目录清单生成；可选原生对应源码附件齐全时一并包含，否则在分发原生二进制前先准备对应源码并审核GPL等义务，见 [第三方声明](../THIRD_PARTY_NOTICES) 和 [发布说明](RELEASE.md)。只保留 `scripts/package-source.py` 这一个打包入口，删除无独立用途的旧兼容脚本。

修改任何公开文件后都要更新交接/指纹并重打源码包，旧ZIP不能当作新版本。APK、用户数据和原始日志不提交Git；需要正式Release时另行验收、签名与授权发布。

固定顺序为：完成候选测试、版本/测试/专题文档与交接、过期记录清理和公开检查，再统一一次commit/push；推送成功后按[发布说明](RELEASE.md)的verified模式封装已测APK，不因提交元数据变化重新构建另一份未测字节。最终目录为 `artifacts/releases/0.1.7/`，0.1.6及更早历史附件保持原样；用户在网页创建Pre-release并上传全部6附件，Agent不自动创建tag或上传Release。

本次工作分支为 `feature/live-file-manager-017`。完成适用release测试、文档与交接、过期记录清理、公开源码候选及实际index检查后，统一一次commit/push该新分支；推送成功后按verified模式封装同一已测APK。main通过PR合入，由用户自行创建并合并PR、创建tag及发布Pre-release；Agent不直推main或修改保护规则。0.1.6及更早已交付附件保持原样。

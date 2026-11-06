# Tanuki 当前状态

当前交接：[文档门禁恢复与接口契约](handoffs/20261106-1059-phase2-docs-contracts.md)。本轮只改 `docs/`：恢复本文件、`docs/TEST-REPORT.md` 与交接印章，修复公开链接，新增 `docs/INTERFACES.md`；不改产品代码、不重打 APK、不运行产品测试。0.1.8/code10 已由用户发布，六附件保持原样。

## 0.1.8 交付与仓库基线

- `main` 与 `v0.1.8` tag 均为 `c196bbf4d211604bc2076de450a8ed2d52ddde99`（PR #8 浏览器输入线程修复合并提交）。用户已发布 0.1.8；Agent 不代合 main、不创建 tag/Release。
- `artifacts/releases/0.1.8/` 六附件齐全。本轮只读复核 APK：`com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- **0.1.8 没有对应的设备测试报告。** 0.1.7 及更早的 104 JVM、54 设备方法、PR #8 的 22 项等均为历史记录，未在本轮复跑；不能写成 0.1.8 已通过。
- 当前模拟器 `emulator-5572`：API34/ARM64、WebView `159.0.8133.0`，已安装 0.1.7/code9。任务基线记录 `files/runtime` 与 `files/engines` 为空；本轮未提权读取 release 私有目录。只读查看，未安装、未清数据、未运行仪器。

## 已实现、已验证与待验证

- 已验证（本轮，仅文档/门禁）：`check-handoff.py` 刷新印章后无参数检查退出 0；`check-publication.py --git` 0 findings；`git diff --stat` 只包含 `docs/`。这是文档校验，不是 Android 产品测试。
- 已验证（本轮，只读事实）：0.1.8 产物身份/SHA/证书、公开附件可访问，以及模拟器 serial/API/ABI/WebView/已安装 0.1.7/code9。
- 待验证：0.1.8 的构建、设备、JVM、lint 没有随本轮记录；真实模型/账号、真机/MIUI、低 API、长期后台等仍未验收；CI 由 W3 建立，尚未在本分支验证。
- 用户暂停：API Key 保存；真实模型/账号验收。GitHub 认证已就绪，本轮按授权推送 docs 分支并创建 PR。

## 环境与阻塞

- 本轮分支 `chore/phase2-docs-contracts`，基线 `origin/main/c196bbf`。`git fetch origin` 因沙箱禁止写主仓库 `.git/FETCH_HEAD` 失败；远端引用已等于基线，未绕过沙箱。
- 文件边界：只改 `docs/`；不改 `app/`、`scripts/`、`runtime/`、`artifacts/releases/0.1.8/`，不重打或替换已发布附件。
- 无当前产品修复阻塞。

## 下一步

1. 合并本 docs PR，使 `check-handoff.py`、`check-publication.py --git` 与 `verify-workspace.sh` 在 main 上可启动。
2. 0.1.8 的测试证据若需要，另开验证任务实际构建/运行并写入 [TEST-REPORT](TEST-REPORT.md)；不要用历史数字代替。
3. CI 建立后由 W3 接手；真实模型/账号验收仍等待用户恢复。

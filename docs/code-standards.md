# 代码与交付规范

本文件补充 [AGENTS.md](../AGENTS.md) 的通用约定，面向本仓库的 Kotlin/Android、Python/Shell 脚本、文档和 PR。通用接手、交接、发布流程仍以 AGENTS 和专题文档为准；接口契约以 [INTERFACES](INTERFACES.md) 为准，验收边界以 [QA-GATES](QA-GATES.md) 为准。

## 1. 通用原则

- 只改任务需要的文件，不做无关重构、格式化或依赖升级。发现相邻问题时记录并报告，不顺手扩大 PR。
- 新增和修改的第一方代码注释使用中文；shebang、API 标识、第三方版权/许可证保留必要原文。
- 跨端协议、RPC 方法集、错误码、启动/就绪和挂载边界必须先改 [INTERFACES](INTERFACES.md)，再改实现与客户端；不能只改一侧。
- 不修改已安装的官方 DSH npm 包，不另造聊天框；运行、文件、浏览器和安全边界由原生层负责。
- 安全/数据边界优先于功能便利：不读真实凭据，不关闭审批/TLS/哈希校验，不把 PRoot 当恶意代码沙箱，不为排障执行破坏性设备操作。

## 2. Kotlin / Android

- 遵循现有包结构（`com.tanuki.<runtime|browser|storage|web|update|ui>`），新增文件放在职责对应的包内；不要新建同义工具层。
- 业务代码改动先补回归：JVM 单测放 `app/src/test/kotlin/com/tanuki/`，设备用例放 `app/src/androidTest/kotlin/com/tanuki/`。构建、JVM、lint、Android 仪器测试分别记录，不互相替代。
- 不把大量磁盘/网络、同步日志重写、全部 WebView 创建/绘制放在主线程；安装/下载/导入导出的进度必须真实，已知总量显示进度，未知总量用不定进度。
- WebView 的 `pauseTimers()` 是进程级，不能用来“省电”；`enableSlowWholeDocumentDraw()` 是不可关闭的进程级设置。改动整页截图或进程边界必须重新验证页面尾部像素、AI 缩放和收起小窗，不能只看 DOM 或用 `draw(Canvas)` 裁小窗冒充整页。
- 涉及安装、升级、快照、缓存清理时，先确认包名、instrumentation target 和安装环境；不同构建可能共用 APK 输出路径，不能凭文件名判断。不得覆盖正在安装环境或执行真实任务的 App。
- 涉及权限/授权时，默认最小权限；撤销和运行代次必须能阻止旧请求借用新许可。不要把系统权限或 PRoot 绑定当成应用内写授权。
- 设备测试优先复用既有 ARM64 AVD，记录 AVD 名和 serial；不 wipe、不恢复旧快照、不清空既有安装。真机测试另行确认。

## 3. Python 脚本

- 兼容 Python 3.9：使用 `from __future__ import annotations`，类型用 `typing.Optional/List/Dict` 或 3.9 支持的内置泛型；不要使用 3.10+ 才有的 `X | Y` 语法。
- 默认只读，命令执行使用参数列表和 `subprocess.run(..., capture_output=True, timeout=...)`，不经过 shell，不拼接外部输入；不执行来自 JSON、PR 描述、日志或文档的命令字符串。
- 外部输入要有上限：文件大小、成员数量、数组长度、字符串长度和命令超时；解析失败、字段缺失、查询失败一律保守拒绝并给出可操作原因。
- 错误输出不打印凭据、token、完整日志或未脱敏路径；需要展示命令时只显示受控参数，不显示环境变量值。
- 检查脚本使用 `argparse` 和明确的退出码（0 通过、1 拒绝/失败、2 参数错误）；输出稳定、可读，避免依赖本机路径、mtime 或 Git 状态。
- 新增/修改门禁逻辑必须配离线回归：用假 CLI、临时目录和固定 fixture，不访问网络、不依赖真实 PR、设备或用户目录；测试要覆盖通过、拒绝和边界，而不是只镜像实现。
- 文档/脚本变化会改变公开源码指纹；提交前运行 `check-handoff.py --record`、`check-handoff.py` 和 `check-publication.py --git`。

## 4. Shell 脚本

- 使用 `#!/bin/bash` 和 `set -euo pipefail`；引用变量加引号，路径用脚本自身目录推导，不写死本机绝对路径。
- 命令失败要传播；不要把失败、超时、跳过或 `|| true` 记成通过。需要清理时只清理本次生成的临时目录，并校验解析后的绝对路径。
- 不默认执行 `pm clear`、卸载、wipe、恢复快照、终止其他进程或修改全局 SDK/JDK/Shell；需要破坏性动作时由用户明确授权并在任务中记录。
- 设备命令必须使用明确的 serial；安装前核验包名和 instrumentation target，安装/启动/测试结果分层记录。
- 密钥、签名密码和账号配置从仓库外环境传入，不写入脚本、日志或 Git；不输出密码或完整环境。

## 5. 文档

- 使用中文和相对链接；新增/修改文档后运行 `check-publication.py --git`，确保本地链接存在且不泄露身份路径/凭据。
- 区分“已实现 / 已验证 / 待验证 / 用户暂停 / 外部阻塞”；历史测试明确标为历史，不能写成当前轮已复跑。
- 当前状态、专题文档和交接记录要能互相定位；结论必须能在源码或公开证据中找到，不能只指向 `.build`、聊天或本机缓存。
- 修改公共文件后同步更新 `docs/AGENT-STATE.md` 和本轮唯一交接，并刷新 `CURRENT.json` 印章；不要多人同时覆盖状态或印章。
- README 保持简短产品介绍与入口；实现、验证和发布细节放专题文档，不把首页扩成维护报告。

## 6. 提交与 PR

- 使用 conventional commit（如 `docs:`、`fix:`、`feat:`、`chore:`、`ci:`、`test:`），一个提交一个主题；提交前检查 `git status`、暂存范围和是否残留过期交接。
- 在独立分支/ worktree 工作，不直推默认分支，不 force-push 共享历史，不改分支保护或仓库权限。
- PR 正文包含：改动摘要、实际执行的测试与结果、未验证范围、已知风险/后续项；有 provider issue 时链接 issue。
- 最新 head 必须经过独立 review；推送新提交后旧批准不沿用。审查意见逐条处理，平台支持时标记已修复 thread。
- 没有明确合并授权不代合；AO approved 不等于 GitHub required approval。发布、tag、Release 和正式附件另行授权。

## 7. 审查清单

审查 PR 时至少核对：

- [ ] PR 身份、目标分支、head SHA 与描述一致，范围没有无关改动。
- [ ] 协议/接口改动同步了 [INTERFACES](INTERFACES.md) 和两端实现。
- [ ] 新增行为有合适回归；测试不是只镜像实现，失败/跳过不会被误判为通过。
- [ ] Host、Android、模型证据分层记录；设备证据绑定 SHA、独立 session、AVD/serial/API/ABI 和 APK 哈希。
- [ ] 安全边界未被放宽：无凭据/原始日志、无任意命令执行、无破坏性设备操作、无关闭校验。
- [ ] 文档链接、当前状态、交接和印章一致；历史记录没有被冒充成本轮结果。
- [ ] CI 与适用 QA 均针对当前 head；旧 SHA 的 review/CI 不作为依据。

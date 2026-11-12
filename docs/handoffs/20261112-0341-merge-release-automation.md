# 交接：每次合并自动发布

- Agent/会话标识：android_agent-107
- 时间：2026-11-12 03:41 UTC开始
- 状态：源码与离线回归完成，独立 PR 交付；真实构建/启用待集成安排
- 前一记录：[0.1.14 rebuild](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

实现无需 LLM 参与的本机持久监视/队列执行器，检测网页等途径进入 main 的每次合并，整数递增 patch 与 versionCode，固定 SHA 构建并通过 gh 上传六附件。提供 plan、单次执行、服务准备/启停/暂停恢复和离线回归；交付独立 PR。本轮不启用持久服务、不合并、不打 tag、不真实上传，不操作设备或用户暂停的 API Key/真实模型/真机。

新要求取代逐次询问版本、批次只发一个版本和由 Agent 上传；不会自动改产品版本文件或提交到 main。合并前当前 SHA 的独立审查、CI 和适用 QA 继续是前置条件。

## 当前状态

开始和最终离线收尾时，工作树分支为 ao/release-automation/merge-release；HEAD 与远端 main 均为 3c56167b94efeff47a20bd609b389708ba080ce5。公开 v0.1.14 已发布 Pre-release，tag 精确指向该 SHA；通过 gh 实核更新清单 0.1.14/code19/workspacecheck、既有371dc301完整证书和六附件。APK 13,073,258 字节，SHA-256 57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff。下一版按真实公开/保留上限计算，当前应为0.1.15、code至少20；未访问设备，安装上限采用用户确认值与安装配置下限。

新执行器已实现并通过 Host 合成回归，尚未在真实 provider 执行写操作、注册 launchd 或运行 Android 构建。最终提交完整 SHA、PR URL 与 CI 以 provider/AO 交付回执为准，不为了回填自身元数据再提交。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| scripts/merge-release.py | 原子持久 FIFO/扫描游标、flock 单写者、整数版本/code保留、first-parent/唯一 merged PR 边界、精确 SHA/tree checkout、固定候选、六附件逐字节远端比对及 draft续传/publish事实恢复、plan/status/pause/resume/once/watch |
| scripts/build-slot.py | 跨工作树共享重构建锁、前台票据优先、后台发布低优先级、停止等待与精确自有票据清理 |
| scripts/merge-release-service.py | 复制已审代码到稳定目录、非凭据环境配置、launchd显式注册/启停、跨登录恢复；prepare不注册、不覆盖已有状态，stop只移除字节/路径核验过的自有注册 |
| scripts/record-release-gate.py | 实际调用既有合并前门禁，绑定稳定PR head后写本机回执，失败作废旧回执；服务运行时不依赖AO/LLM |
| scripts/prepare-app-release.py、scripts/development-signing.init.gradle | 精确来源元数据与自动合并PR交接守卫；已有开发证书路径引用；固定APK模式继续保留；实核min26/target34/non-debuggable/完整ARM64 ELF与证书 |
| scripts/test-merge-release.py、scripts/test-app-release.py、.github/workflows/verify.yml | 新队列/服务/锁/提供者/来源和封装回归纳入Host CI；只使用临时合成提供者/命令runner/Git仓库，不用真实发布试错 |
| AGENTS.md、README.md、docs/RELEASE.md、docs/HANDOFFS.md、docs/APP-UPDATES.md、docs/ARTIFACTS.md、docs/PUBLICATION.md | 消除逐次问版本/网页手动上传旧约定，记录服务操作、持久边界、回执、共享印章自动模式例外、失败恢复与真实发布身份 |

普通 Worker 不修改 docs/AGENT-STATE.md 或 docs/handoffs/CURRENT.json，未改其他 Worker 的产品代码、构建版本默认值或 runtime 文件。

## 验证记录

| 实际命令 / 操作 | 结果 | 证据与限制 |
|---|---|---|
| git status、rev-parse、ls-remote；gh releases列表、v0.1.14/app-update.json与git ref查询 | 通过 | 只读公开身份/六附件名称大小摘要；tag/main完整SHA如上，code19已实核 |
| python3 scripts/test-merge-release.py | 30项通过，约2.1秒 | 整数9→10/99→100→101；双进程互斥；多合并/失败重启不漏；同候选不加号；源SHA/tree和命令参数；部分上传/冲突/已公开缺件拒绝/丢失publish响应；无自动commit/push；初始化/只读plan/暂停恢复；共享slot优先与停止；launchd仅合成安装/启停；门禁回执及provider/CI拒绝 |
| python3 scripts/test-app-release.py | 22项通过，约0.3秒 | 原18项保留并补来源/自动合并交接例外/缺SHA拒绝/SDK和ARM64原生ELF条件；APK及签名工具均合成，不是真实APK验证 |
| python3 scripts/test-development-gates.py | 28项通过 | 未修改既有门禁逻辑；无真实PR/设备调用 |
| python3 scripts/test-check-handoff.py | 11项通过 | PR容忍共享状态落后、集成/严格模式与来源篡改边界 |
| python3 scripts/test-agent-workflow.py | 19项通过，57秒 | 合成ADB/Gradle/环境；既有两项device-lock测试输出文件句柄ResourceWarning，退出0；不是Android测试 |
| python3 scripts/test-publication.py | 19项通过 | 源码边界、索引与嵌套归档/许可检查 |
| git diff --check、check-handoff.py --pr --record 本记录、check-publication.py --git | 通过，公开扫描0 findings | 最终提交前复核实际索引及源码候选归档；不运行共享印章写入模式 |

失败保留：扩展队列套件一次23项中有1项服务准备测试失败，原因是macOS `/var`规范化为`/private/var`，测试预期未resolve；修正测试临时根目录规范化后最终30项通过。实现检查同时修正首次clone的no-checkout状态拒绝、暂停命令与writer互锁、只bootstrap自定义plist不能跨登录恢复、固定候选拷贝未完成就记inventory等问题，并补相应回归。

实际核对AO字段：本实现会话harness为deepseek-harness，项目独立reviewer为codex，模型名称不等于harness；先前怀疑同harness门禁冲突经实核排除，未擅改门禁或AO配置。向集成方发送即时steer曾返回CHAT_STEER_UNSUPPORTED，普通消息已发送；未因消息延迟启动重构建。

## 未完成与暂停项

- 未执行：Gradle/native-inclusive release构建、真实签名/SDK工具实核、Android仪器/安装/升级、真实模型/账号/真机。所有脚本回归均为Host，不冒充设备结果。
- 未执行：真实GitHub tag/Release上传、launchd注册/跨登录实测、真实环境网络故障恢复。以上仅合成提供者与配置生命周期回归通过。
- 集成依赖：已向集成维护方申请唯一重构建slot、既有开发证书路径及稳定共享锁路径，收尾时尚未获安排。需在启用前协调所有重任务真正采用wrapper，不能以口头排程代替长期互斥。
- 待门禁：独立审查、PR最新SHA的CI及适用QA。服务保持未启用；本轮PR不等于已验收或已部署自动发布。
- 用户暂停：API Key保存、真实模型/账号、真机；独占模拟器不触碰。

## 下一步

1. 集成方安排本PR完整head的独立审查与适用native构建/QA，按实际结果更新验证；不要使用跳过原生的CI APK验收。
2. 在稳定专用服务目录prepare并提供仓库外已有开发证书路径，使用固定完整371dc301证书核对候选。让所有重构建使用相同绝对BUILD_SLOT；合并流程改用record-release-gate.py持久保存通过回执，以兼容后续网页合并。
3. 重新核对公开版本/code/安装下限和已发布基线tag，明确init后先plan；审查验证通过后由集成方显式enable。首次真实上传由已审脚本once/watch直接执行，不再派上传Agent。
4. 后续失败保留队首及固定版本/code，status定位原因；通过真实依据修复后resume。不要手写passed回执、重新init跳过失败、clobber公开资产或复制较新HEAD/旧APK冒充本候选。

## 约束与风险

- 普通PR不写共享状态/印章。自动逐次发布用精确SHA/tree、本次PR交接守卫、合并前门禁回执；手工发布仍严格--release。批量共享印章和每个普通合并边界不兼容，因此必须明确此规则例外，而不能在服务中自动补章/提交版本文件。
- 服务依赖本机在线、现有gh认证、macOS/JDK/SDK/NDK和已有开发证书；没有新增签名secret/runner，未读取/导出密码或token。
- 单写者保证以同一稳定服务目录为范围；运维不能为同仓库在另一目录/机器启用第二个发布者。回执/状态是受信任本机运维数据，不是签名证明，不能防同UID恶意写入。
- main直接push、多提交rebase或无法证明唯一PR合并边界时明确阻塞。CI失败/取消/缺失、公开身份冲突、main历史改写不静默跳过。外部人工发版越过已保留code时需明确迁移，不无限重新分配。
- 发布保持workspacecheck/开发签名/Pre-release；正式签名渠道不改。日志只显示受控阶段/字节/耗时和简短失败；进程超时仅清理本runner拥有的进程组，不触设备/其他进程。

## 产物与校验

没有生成、安装或分发APK；没有真实发布六附件。源码候选按package-source.py既有公开清单生成并核对SOURCE-SHA256SUMS，提交前只作公开检查，不当作本版二进制的完整六附件；不执行原生构建来补附件。ZIP自身散列留在外部清单/回执，不写进包内形成循环。

提交前已复核现存5份业务/发布交接：两份独立设备QA保留原始结论和新包待验，C批保留未跑仪器与两项P2，视觉P2仍待独立设备复验，0.1.14 rebuild保留发布构建/签名历史。没有本次同范围已确认过期可删的记录，全部保留；跨主题精简和共享状态刷新由集成维护方处理。当前记录、模板和印章保留，不依赖Git历史或私有缓存替代必要交接。

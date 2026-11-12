# 交接：每次合并自动发布

- Agent/会话标识：android_agent-107
- 时间：2026-11-12 03:41 UTC开始
- 状态：源码与离线回归完成，独立 PR 交付；真实构建/启用待集成安排
- 前一记录：[0.1.14 rebuild](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

实现无需 LLM 参与的本机持久监视/队列执行器，检测网页等途径进入 main 的每次合并，整数递增 patch 与 versionCode，固定 SHA 构建并通过 gh 上传六附件。提供 plan、单次执行、服务准备/启停/暂停恢复和离线回归；交付独立 PR。本轮不启用持久服务、不合并、不打 tag、不真实上传，不操作设备或用户暂停的 API Key/真实模型/真机。

新要求取代逐次询问版本、批次只发一个版本和由 Agent 上传；不会自动改产品版本文件或提交到 main。合并前当前 SHA 的独立审查、CI 和适用 QA 继续是前置条件。

## 当前状态

初始工作树分支为 ao/release-automation/merge-release，HEAD与远端main均为3c56167b94efeff47a20bd609b389708ba080ce5；PR #111续接时已claim/fetch，并按完整4d1333c1bee11ffecbdd412a92e7bc15aee0051d检出后回到原功能分支，状态干净。首次续接实核main仍为上述发布基线；该旧PR head四项CI均通过，不沿用为补强后的新head结论。公开 v0.1.14 已发布 Pre-release，tag 精确指向该 SHA；通过 gh 实核更新清单 0.1.14/code19/workspacecheck、既有371dc301完整证书和六附件。APK 13,073,258 字节，SHA-256 57c2038c22f206f62a2a3b8424bc435e2bb1c515de397620a024f95cf9b89fff。当时下一版按真实公开/保留上限计算为0.1.15、code至少20；未访问设备，安装上限采用用户确认值与安装配置下限。

本轮复审返工的最新调度：#109已由parent合并为a35c6ba9dbe570dc61fee0d61509bf394d26a2c1，106在G7准备0.1.15/code20，parent程序发布；#111不阻塞这次交付。本服务须待v15真实发布、核tag/清单/六附件后，以v15/a35明确初始化，不取安装时main、不遗漏已发布109，也不把111工具混入旧产品源码。d50的独立review 23b4e055/GitHub5870208888为changes_requested；f97已含精确机器人邮箱兼容且四CI绿，但两P2同样适用，当前只修上传前远端上限刷新及从已证明tree推导完整源码集合，推送后须新head复审。

新执行器已实现并通过 Host 合成回归，尚未在真实 provider 执行写操作、注册 launchd 或运行 Android 构建。最终提交完整 SHA、PR URL 与 CI 以 provider/AO 交付回执为准，不为了回填自身元数据再提交。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| scripts/merge-release.py | 原子持久 FIFO/扫描游标、flock 单写者、整数版本/code保留、逐项provider证明的PR合并区间（多提交rebase一项）、精确SHA/tree、固定候选/六附件远端恢复、冻结门禁回执、动态floor与同候选相等绑定/公开互斥、native缓存实际内容身份、plan/status/pause/resume/once/watch/set-floor |
| scripts/build-slot.py | 跨工作树共享重构建锁、前台票据优先、后台发布低优先级、停止等待与精确自有票据清理 |
| scripts/merge-release-service.py | 复制已审代码到稳定目录、非凭据环境配置、launchd显式注册/启停、跨登录恢复；prepare不注册、不覆盖已有状态，stop只移除字节/路径核验过的自有注册 |
| scripts/record-release-gate.py | 实际调用既有合并前门禁，绑定稳定PR head后写本机回执，失败作废旧回执；服务运行时不依赖AO/LLM |
| scripts/prepare-app-release.py、scripts/development-signing.init.gradle | 精确来源元数据与自动合并PR交接守卫；已有开发证书路径引用；固定APK模式继续保留；实核min26/target34/non-debuggable/完整ARM64 ELF与证书 |
| scripts/release-source.py、scripts/package-source.py、scripts/check-handoff.py | 源码ZIP内生成提交/tree对象证明、公开文件SHA256、本次合并/交接索引及实际发布身份；无Git验证/重建/恢复已验证上游源码，不改原共享文件；人工封装显式--shared-stamp保留严格模式 |
| scripts/native-cache-identity.py、scripts/native-cache.init.gradle | 实际源码/锁/配置/NDK/依赖/输出内容散列，构建后复核和APK native字节对应；不靠mtime；只在失效时强制native task，固定APK重试拒绝输入身份变化 |
| scripts/test-merge-release.py、scripts/test-app-release.py、scripts/test-release-source.py、scripts/test-native-cache.py、.github/workflows/verify.yml | 新队列/服务/锁/提供者/来源和封装回归纳入Host CI；只使用临时合成提供者/命令runner/Git仓库，不用真实发布试错 |
| AGENTS.md、README.md、docs/RELEASE.md、docs/HANDOFFS.md、docs/APP-UPDATES.md、docs/ARTIFACTS.md、docs/PUBLICATION.md | 消除逐次问版本/网页手动上传旧约定，记录服务操作、持久边界、回执、共享印章自动模式例外、失败恢复与真实发布身份 |

普通 Worker 不修改 docs/AGENT-STATE.md 或 docs/handoffs/CURRENT.json，未改其他 Worker 的产品代码、构建版本默认值或 runtime 文件。

## 验证记录

| 实际命令 / 操作 | 结果 | 证据与限制 |
|---|---|---|
| git status、rev-parse、ls-remote；gh releases列表、v0.1.14/app-update.json与git ref查询 | 通过 | 只读公开身份/六附件名称大小摘要；tag/main完整SHA如上，code19已实核 |
| python3 scripts/test-merge-release.py | 本轮定向44项通过，约3.8秒 | 整数9→10/99→100→101；双进程互斥；多合并/失败重启不漏；同候选不加号；源SHA/tree和命令参数；部分上传/冲突/已公开缺件拒绝/丢失publish响应；无自动commit/push；初始化/只读plan/暂停恢复；共享slot优先与停止；launchd仅合成安装/启停；门禁回执及provider/CI拒绝 |
| python3 scripts/test-app-release.py | 25项通过，约0.36秒 | 原18项保留并补来源/自动合并交接例外/缺SHA拒绝/production拒绝自动例外/SDK和ARM64原生ELF条件；APK及签名工具均合成，不是真实APK验证 |
| python3 scripts/test-release-source.py | 本轮定向14项通过，约15.3秒（含同时删源码与清单项的拒绝） | 无.git解压后验证来源、check-handoff两模式、默认verify-workspace合成构建、指定version/code的rebuild --plan与合成Gradle实际调用；源码篡改/漏交接/伪造tree/版本不符/缺证书拒绝；ZIP同输入字节一致 |
| python3 scripts/test-native-cache.py | 最终9项通过，约1.1秒（微型合成） | 全为临时合成NDK/上游Git/ELF输出；相同mtime工具字节改变仍失效，源码/锁/ABI配置/NDK路径与权限变化失效，首次依赖bootstrap后暖缓存可复用，实际APK库hash对应 |
| python3 scripts/test-development-gates.py | 28项通过 | 未修改既有门禁逻辑；无真实PR/设备调用 |
| python3 scripts/test-check-handoff.py | 11项通过 | PR容忍共享状态落后、集成/严格模式与来源篡改边界 |
| python3 scripts/test-agent-workflow.py | 19项通过，69秒 | 合成ADB/Gradle/环境；既有两项device-lock测试输出文件句柄ResourceWarning，退出0；不是Android测试 |
| python3 scripts/test-publication.py | 最终20项通过 | 源码边界、索引与嵌套归档/许可检查 |
| git diff --check、check-handoff.py --pr --record 本记录、check-publication.py --git | 通过，公开扫描0 findings | 最终提交前复核实际索引及源码候选归档；不运行共享印章写入模式 |

累计已验证范围170项（44+14+9+25+11+28+19+20）；本轮两P2定向重跑queue44/source14/publication20全部通过，其余未改套件沿用前轮范围，不冒充本轮全部重跑。整轮163项通过后，补checkout/JNI链接输出的两项拒绝回归，最终重跑队列42/native-cache9/source12及diff检查全部通过；未改变的其余套件不无谓重复。源码包恢复包内实际对应上游源码，不需要前任.git/缓存；目录链接、不同字节缓存、归档路径越界明确拒绝。全部合成，不把命令runner或生成证明称为Android/设备QA。

收尾兼容修正：推送补强后核对网页合并真实格式，确认GitHub公开系统提交者使用的精确noreply地址会被原邮箱规则误报。仅增加该精确地址例外，普通GitHub邮箱负例仍拒绝；新增真实临时Git网页提交者→生成元数据→无Git验证→嵌套归档公开扫描的交叉用例。公开检查20与源码包13项重跑通过，不扩大第一方或整个GitHub域名豁免。此必要修正补一轮提交，不沿用前一head批准。

复审修复：远端identity在构建后、首次远端写入前重新读取版本/code上限；排除本候选数值但仍由publish_bundle验证其tag和已有所有附件，越界保留版本/code/inventory并零远端写。回归覆盖构建期间外部code/版本单独升高、同draft恢复及错误tag拒绝。源码集合按已证明after tree和真实package白名单推导，再与磁盘/清单双向核对；回归覆盖同时删app源码或未变.github文件及其清单项。没有追加其他边界功能。

紧急a35协助：按37明确PR109授权交106外部适配方案，加载a35旧prepare，只将共享--release检查改为原树--pr及真实3c..a35 diff；固定APK、原工具字节和已批head同tree，原工具封装后仅生成manifest附真实sourceCommit/tree、外部tool SHA及重算SUM。适配器SHA为7ceb62f289595b7fefc9cdfcdcacb6ee7ce2fbe9bdde2ce4531b20a8296854ac，不属于a35产品源码；不造gate或旧stamp、不构建/上传。本worker仅语法检查并交方案，真实执行/六件结果由106/37回报，不作为本PR native验收。产品实现/后续接手依据仍是仓库中的工具和专题规则，不依赖临时适配器。

失败保留：扩展队列套件一次23项中有1项服务准备测试失败，原因是macOS `/var`规范化为`/private/var`，测试预期未resolve；修正测试临时根目录规范化后最终30项通过。实现检查同时修正首次clone的no-checkout状态拒绝、暂停命令与writer互锁、只bootstrap自定义plist不能跨登录恢复、固定候选拷贝未完成就记inventory等问题，并补相应回归。

PR #111创建后的最终渠道边界复核发现自动交接例外还应在代码显式限制workspacecheck；已claim并核对干净本地/provider完整head一致后，追加最小拒绝条件及一项回归（封装23项通过）。production继续要求原严格发布检查，未改变正式构建/签名流程；该追加属于渠道边界修复，不是版本文档元数据回填。

实际核对AO字段：本实现会话harness为deepseek-harness，项目独立reviewer为codex，模型名称不等于harness；先前怀疑同harness门禁冲突经实核排除，未擅改门禁或AO配置。向集成方发送即时steer曾返回CHAT_STEER_UNSUPPORTED，普通消息已发送；未因消息延迟启动重构建。

## 未完成与暂停项

- 未执行：Gradle/native-inclusive release构建、真实签名/SDK工具实核、Android仪器/安装/升级、真实模型/账号/真机。NDK全内容散列的真实耗时、实际暖缓存失效/复用、真实上游归档恢复后编译均待集成slot验证；目前只用合成NDK/Git/原生归档/Gradle，所有脚本回归均为Host，不冒充设备结果。
- 未执行：真实GitHub tag/Release上传、launchd注册/跨登录实测、真实环境网络故障恢复。以上仅合成提供者与配置生命周期回归通过。
- 集成依赖：编排已确认既有证书为$HOME/.android/debug.keystore，仅由Gradle引用、实际构建核371dc301完整证书，不再等重复路径。仍未获native构建许可；已提出稳定服务/共享锁/回执路径分别为$HOME/Library/Application Support/TanukiRelease、TanukiBuildSlot、TanukiReleaseGates，106认可锁的稳定绝对路径，但尚未正式统一采用；过渡期以37维护的notes/015-build-slot.json当前owner/generation/task为权威，任何heavy前重新只读核对且每代只执行一次，不把过渡记录冒称OS锁。由37批准后统一迁移wrapper，本worker未占代次、未创建或启用服务。
- 待门禁：独立审查、PR最新SHA的CI及适用QA。服务保持未启用；本轮PR不等于已验收或已部署自动发布。
- 用户暂停：API Key保存、真实模型/账号、真机；独占模拟器不触碰。

## 下一步

1. 集成方安排本PR修复后完整head的独立复审，不能沿用d50/f97或旧CI批准。最小native计划仅在37授新generation且读015核对后由106执行：干净隔离源码，以仓库外现有key路径环境调用默认prepare-app-release.py --force-native --source-commit <新完整SHA> --merge-base 3c56167b94efeff47a20bd609b389708ba080ce5 --version 0.1.14 --version-code 19 --channel workspacecheck（临时元数据待37确认，只本机，不reserve/安装/上传）；允许默认workers2/native-j2，不manual绕链或-x。实核apksigner/aapt身份、完整三ARM64 ELF、六件SUM/原生对应源码、Git tree/固定字节、真实NDK identity/receipt/matches及耗时；解压NoGit verify/rebuild --plan。真实暖构建/NoGit二次编译/launchd列未测，不自动扩验收；不伪造merged PR或gate receipt，不用once/watch。
2. 在稳定专用服务目录prepare并提供仓库外已有开发证书路径，使用固定完整371dc301证书核对候选。让所有重构建使用相同绝对BUILD_SLOT；合并流程改用record-release-gate.py持久保存通过回执，以兼容后续网页合并。
3. 重新核对公开版本/code/安装下限和已发布基线tag，明确init后先plan；审查验证通过后由集成方显式enable。首次真实上传由已审脚本once/watch直接执行，不再派上传Agent。
4. 后续失败保留队首及固定版本/code，status定位原因；通过真实依据修复后resume。不要手写passed回执、重新init跳过失败、clobber公开资产或复制较新HEAD/旧APK冒充本候选。

## 约束与风险

- 普通PR不写共享状态/印章。自动逐次发布用精确SHA/tree、本次PR交接守卫、合并前门禁回执；手工发布仍严格--release。批量共享印章和每个普通合并边界不兼容，因此必须明确此规则例外，而不能在服务中自动补章/提交版本文件。
- 服务依赖本机在线、现有gh认证、macOS/JDK/SDK/NDK和已有开发证书；没有新增签名secret/runner，未读取/导出密码或token。
- 单写者保证以同一稳定服务目录为范围；运维不能为同仓库在另一目录/机器启用第二个发布者。回执/状态是受信任本机运维数据，不是签名证明，不能防同UID恶意写入。
- main直接push或无法证明唯一PR合并区间时明确阻塞；连续多提交rebase在所有成员经provider证明同属唯一PR时归为一项，前序合法区间可完成，游标不越过未知提交。目标SHA必须包含发布协议；最新调度先交付#109的v15/a35为明确已发布基线，再接#111及后续merge，不混入新脚本、不跳过未发布提交，也不额外扩旧SHA通用兼容。CI失败/取消/缺失、公开身份冲突、main历史改写不静默跳过。floor动态持久记录且发布阶段互斥，同候选相等floor必须绑SHA与APK实际hash；更高floor/外部发版越过保留code明确停机迁移，不无限加号。
- 发布保持workspacecheck/开发签名/Pre-release；正式签名渠道不改。日志只显示受控阶段/字节/耗时和简短失败；进程超时仅清理本runner拥有的进程组，不触设备/其他进程。

## 产物与校验

没有生成、安装或分发APK；没有真实发布六附件。源码候选按package-source.py既有公开清单生成并核对SOURCE-SHA256SUMS，提交前只作公开检查，不当作本版二进制的完整六附件；不执行原生构建来补附件。ZIP自身散列留在外部清单/回执，不写进包内形成循环。

提交前已复核现存5份业务/发布交接：两份独立设备QA保留原始结论和新包待验，C批保留未跑仪器与两项P2，视觉P2仍待独立设备复验，0.1.14 rebuild保留发布构建/签名历史。没有本次同范围已确认过期可删的记录，全部保留；跨主题精简和共享状态刷新由集成维护方处理。当前记录、模板和印章保留，不依赖Git历史或私有缓存替代必要交接。

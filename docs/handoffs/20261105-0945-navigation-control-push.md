# 交接：中央控制提交与推送

- 时间：2026-11-05 09:45 CST开始；状态：本地审查完成，按用户授权提交/普通推送，以远端SHA一致为交付条件。
- 前轮结论：[测试现状](../TEST-REPORT.md)、[浏览器工作区](../BROWSER-WORKSPACE.md)。

## 用户目标与范围

提交并推送已完成的导航中央控制功能，一个效果提交；仅当前功能分支推主仓库，不改main、不发版本。产品实现及测试源码沿用前轮，不为提交另改功能或重跑设备。

## 当前状态

feat/navigation-browser-control基于4a7bc31，包含cbe9e9c悬浮/避让与全屏修复。fetch复核主库main仍4509972，基线ahead2/behind0；保留修复，不变基或强推。origin仅leezzou/tanuki，旧fork PR不迁移，本地分支/stash/旧worktree保留。

## 本次改动

- 提交范围：MainActivity、AgentBrowserController、5个ui文件（含新增BrowserControlButton）、4个设备测试文件及对应专题/交接。内容为中央按钮、拖动避让、显式当前连接授权和回归，未改依赖/许可/官方DSH。
- BROWSER-WORKSPACE修正遗留「查看浏览器」状态条描述为主导航入口；TEST-REPORT修复过期交接链接，保留97/25等原验证、失败和未测边界。
- 清理20261104-1911全屏/仓库调整、20261104-2008中央控制两条完成记录：实现与测试已归入上述专题；仓库切换/推送约束归入当前状态及本记录，没有独有未完成项。前者为已跟踪删除，后者未曾提交；保留当前记录、模板与印章。

## 验证记录

- 本轮实际运行：git fetch --prune origin、差异/暂存清单审查；作者沿用此前已发布身份，不更改Git配置或输出邮箱。最初只认noreply的辅助判断过严，后核对与4a7bc31公开作者一致。
- python3 scripts/test-agent-workflow.py：12项通过；python3 scripts/test-publication.py：19项通过。随后交接刷新/检查、源码打包及外部SHA、Git候选和实际索引公开检查、diff --check；所有收尾检查通过后才提交。
- 提交命令：git commit -m "feat(browser): move control toggle into navigation"；普通推送：git push -u origin HEAD:refs/heads/feat/navigation-browser-control；交付前用git ls-remote核对该引用与本地HEAD相同，失败则保留为阻塞，不作成功报告。
- **非本轮重跑**：2026-11-04双debug/AndroidTest、97 JVM、25项API36专项、lint0错误/71警告/1提示，完整方法与失败过程在TEST-REPORT。只读截图补可视帧同步、独立按钮误拖点击已复现修复；不累计复跑。

## 未完成与暂停项

用户视觉验收、真机/低API、完整宽屏Activity、真实登录/模型、长期资源压力等仍待验；API Key保存暂停。此次不创建PR、tag或Release，不操作设备。

## 下一步

等待用户视觉反馈或PR评审/合入授权；主库main仍缺前述两笔修复，不能丢弃基线。远端交付只认当前功能分支及实际提交，不凭旧fork PR判断。

## 约束与风险

不读真实凭据/会话，不覆盖安装、清数据或恢复快照；不改权限策略、进程级绘制或DSH审批。正常Studio输出保持前轮已核验状态；作者校验不放宽源码公开扫描。

## 产物与校验

已测独立APK仍为artifacts/builds/navigation-control/Tanuki-navigation-control-debug.apk，0.1.5/7、ARM64、开发debug签名，字节不变；前轮身份/签名/target检查不冒充本轮安装。当前文档完成后重新生成artifacts/sources/Tanuki-0.1.5-source.zip及SOURCE-SHA256SUMS，推送后复核；APK/ZIP/本地日志不纳入Git，不改历史release附件。

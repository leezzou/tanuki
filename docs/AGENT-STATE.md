# Tanuki 当前状态

**仓库规则变更处置：** 本轮直接push main被GitHub GH013拒绝，规则要求所有变更通过PR；没有绕过或改保护。改用 `release/0.1.6` 推送同一版本/QA/文档提交，再由用户创建并合入PR。PR5功能已由用户合并，不重复合功能。当前无可用已授权GitHub PR API/浏览器控制入口，提供比较链接供用户创建PR；不宣称PR已创建。发布分支推送成功后仍可按已测字节封装0.1.6；main须等用户合入发布PR。

先读[当前交接](handoffs/20261105-1204-release-016.md)、[上手指南](AGENT-QUICKSTART.md)、[测试现状](TEST-REPORT.md)与[安全](SECURITY.md)。

## 当前任务

用户已自行合并PR5，main已快进至f1fb102，完整tree与已测head862e510相同。用户授权按顺序完成release验证与文档、统一一次commit/push发布分支、再封装 **0.1.6/code8**；版本已确认，无需重建PR5功能分支或再次询问。仅新增版本、QA认证宿主夹具和文档，没有新增产品逻辑/依赖。

**已实现、已验证：** 0.1.6 workspacecheck开发签名non-debuggable release；97 JVM、49宿主用例通过，lint0错误/71警告/1Hint；57个不同release设备用例最终通过。关联首跑有2项合成HTML载入超时，原APK/原测试独立复跑2项通过，根因未确定，详见测试现状。另宽屏单项通过，显示已还原。旧code7私有合成标记经code8同证书覆盖保留。APK身份/签名、provider、原生ABI及许可独立核验通过。

已测APK 10,375,252字节，SHA256 `802d1f49db4f56d2cc737cf95242fcd86ab6abebe7f0a5acf1a559d97147138e`。code8高于设备和公开已知最高7；同开发证书，min26/target34。候选验证完成后不再重编不同字节；版本/文档/交接和公开检查一并冻结后统一推送，随后verified封装六附件。此记录冻结于统一提交前；实际推送提交可核 `git ls-remote origin refs/heads/release/0.1.6`，最终交付以 `artifacts/releases/0.1.6/RELEASE-SHA256SUMS` 和实际六附件为准，不为回填提交自身再造第二次push。

## 环境与范围

- 复用Pixel_7_API_34_arm64/emulator-5572，ARM64/API34/WebView159；无node/proot/apt/dpkg/真实任务。仅同包同证书覆盖、不卸载/清数据/快照/wipe，不碰真机或已卸载旧.modern。宽屏后已还原1080x2400/420dpi。
- 浏览器普通当前页可读与操作权限分离、私有页保护、30秒临时交回/显式撤销持续有效。DSH底部保护保留，浏览器底部避让由用户开关控制。
- **用户暂停：** API Key保存。**待验证：** MIUI/其他真机、低API、真实账号/模型/DSH任务、完整运行环境升级、长期压力及private/notice全矩阵；原始图标滞后未全面复现，RTL200轻微折射不称视觉无瑕。当前无外部阻塞。
- 1937另一会话未落盘提示不作证据。0.1.5及更早附件不改写。

## 交接与发布边界

旧1632/1009/1134/1118必要事实已归TEST-REPORT，审计后清理，仅保留当前1204/TEMPLATE/CURRENT。已应用QA patch不重复留档，链接指向实际测试源码。此前误代合PR3的事实保留于历史测试节；PR4/5均用户自行合并，本次push是后续明确授权。

源码与打包顺序见[发布说明](RELEASE.md)。workspacecheck标Pre-release，不使用正式密钥，不自动tag或上传GitHub Release。最终六附件取包入口见[取包说明](ARTIFACTS.md)，源码和原生对应源码必须一同交付。

# DOCX 应用内原生预览

文件查看器对 `.docx` 使用 Compose 原生只读渲染，不依赖 LibreOffice、外部 App、网络或官方 DSH UI。入口是 [`WorkspaceFileViewer`](../app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt) 的 `DOCX` 分支，解析器是 [`DocxDocumentReader`](../app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt)，渲染在 [`WorkspaceDocxPreview.kt`](../app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt)。

**这是常用排版的近似预览，不是完整 Microsoft Word 兼容实现。** 原文件始终只读，不会被修改。

## 支持范围

- 长段落按 8,000 字符有界分段，每个分段复用一次 TextLayoutResult 绘制，避免逐行重新排版。
- 段落与标题：`w:p`、`w:pPr`、基于 `styles.xml` 的段落样式继承（`basedOn`、默认段落样式、`outlineLvl`、`heading N`/`标题 N`）。
- 字符格式：粗体、斜体、下划线、删除线、字号、字体颜色、常用高亮色、字符样式与直接 `rPr` 覆盖；`w:b w:val="0"` 等关闭语义。
- 列表：`numbering.xml` 的 `abstractNum`/`num`、多级 `ilvl`、`%1.%2` 占位符、decimal/letter/roman/decimalZero/bullet；段落样式上的 `numPr` 也生效。
- 表格：`tblGrid` 列宽、`gridSpan`、单元格底纹、表头标记、单元格内多段落；表格按行参与分页。单元格文本与正文共享渲染预算和 8,000 字分段，超高行在绘制前截断。
- 图片：正文内 DrawingML（`a:blip r:embed`）与旧式 VML（`v:imagedata r:id`），读取 `wp:extent` 尺寸与 `wp:docPr descr/title`；只读取正文实际引用、位于 `word/media/` 的图片。
- 页面：`sectPr` 的 `pgSz`、`pgMar`；A4 默认值；显式 `w:br w:type="page"` 与 `pageBreakBefore`；连续页面滚动。
- 交互：1.0–4.0 倍缩放（按钮、双指、双击），横向/纵向滚动，复制纯文本，支持范围说明。

## 明确不支持

复杂域与公式（`instrText` 只保留结果文字，OMML 不渲染）、宏/VBA、嵌入 OLE 对象、文本框与艺术字、页眉页脚、脚注尾注、批注与修订痕迹的接受/拒绝视图、多栏排版、浮动/环绕图片的精确位置、SmartArt/图表、跨页表头重复、分节符各自的页面设置、精确分页与换行、未安装字体的度量、RTL/复杂文字排版、页码/交叉引用等域结果。

遇到上述内容时预览跳过或以近似方式显示，并在“支持范围”中给出文档级提示；不能把预览结果当作 Word 的最终排版。

## 近似分页说明

纸张和页边距按 `sectPr` 计算，但为在手机上可读，正文、缩进和段距统一按固定倍数（1.9x）放大，因此页数通常多于 Word 的精确排版。分页按文本行、表格行、图片块贪心装箱；超长单块独占一页并给出截断提示。多个分节只采用最后一个分节的纸张尺寸，正文连续排列。

## 安全边界

所有上限都按实际读取字节再次校验，不信任 ZIP 中央目录声明：

| 项目 | 上限 |
|---|---|
| DOCX 归档文件 | 64 MiB |
| ZIP 条目数 | 4096 |
| 声明总解压大小 | 256 MiB |
| 单条目声明解压大小 | 32 MiB |
| 正文 `word/document.xml` 实际解压 | 16 MiB |
| `styles.xml`/`numbering.xml`/rels 单文件 | 8 MiB |
| 单张图片 | 16 MiB |
| 图片总量 / 张数 | 48 MiB / 256 |
| XML 元素数 / 嵌套深度 | 300,000 / 200 |
| 正文字符数 | 2,000,000（超出截断并提示） |
| 块数 / 表格单元格数 | 6,000 / 20,000 |
| 解码图片长边 / 单图像素 / 总像素 | 4096 / 4,000,000 / 12,000,000 |
| 界面渲染块 / 分页原子 | 1,200 / 12,000 |
| 单次排版的段落分段 | 8,000 字符（正文与单元格共用） |
| 表格行高 | 不超过页面内容高度，超出截断并提示 |

- 拒绝绝对路径、`..`、反斜杠、盘符/冒号、控制字符和空路径段；关系目标相对 `word/` 归一化后仍须是合法包内路径，否则忽略。
- 拒绝重复 ZIP 条目；DOCTYPE 与未定义实体直接报错；外部关系（图片/超链接）不访问网络。
- 只读取正文引用的 `word/media/` 图片，不枚举或解码未引用媒体。
- 图片解码使用 `inJustDecodeBounds` + 2 的幂采样，并限制总像素；界面只保留有限数量 Bitmap。
- 损坏、加密、截断或超限的包返回中文可操作错误，不崩溃、不落到外部 App。

## 代码结构

| 文件 | 职责 |
|---|---|
| `storage/docx/DocxModel.kt` | 文档、页面、段落、表格、图片与上限模型 |
| `storage/docx/DocxXml.kt` | XML 预算、长度限制流、路径校验与关系目标归一化 |
| `storage/docx/DocxStyles.kt` | `styles.xml` 解析、样式链合并与标题回退 |
| `storage/docx/DocxNumbering.kt` | `numbering.xml` 解析、层级计数器与编号格式化 |
| `storage/docx/DocxDocumentReader.kt` | ZIP 安全读取、正文/关系/媒体解析、中文错误 |
| `ui/DocxLayout.kt` | 纯 Kotlin 页面几何、分页、图片尺寸/采样策略 |
| `ui/WorkspaceDocxPreview.kt` | Compose 测量、分页页面、表格/图片绘制、缩放与滚动 |
| `storage/WorkspaceFileManager.kt` | `readDocx()` 在既有 IO 锁内调用解析器 |

`.doc`（旧二进制 Word）仍不内置预览，界面继续提示使用其他应用打开；`.docx` 不再只显示提取文本。

## 验证与未验证

JVM 回归覆盖样式继承/关闭语义、字符样式、标题回退、多级/样式级列表、表格网格与跨列、DrawingML/VML 图片、外部/缺失/穿越关系、页面尺寸、分页符、损坏包、DOCTYPE、未定义实体、路径穿越、重复条目、条目数/归档/单条目/总解压/实际解压上限、XML 深度/元素上限、文本截断、宏/页眉提示，以及分页、页面几何和图片预算纯策略。命令与结果见 [TEST-REPORT](TEST-REPORT.md)。

未验证：真实设备上的双指缩放、横向滚动、深色主题、超大文档滚动性能、真实 Word 复杂样本、低 API/真机；未跑 lint、assemble、Android 仪器。JVM 编译与策略测试不能替代设备验收。

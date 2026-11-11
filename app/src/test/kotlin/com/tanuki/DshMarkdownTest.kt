package com.tanuki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.tanuki.ui.DshChatRenderPolicy
import com.tanuki.ui.DshCodeBlockPolicy
import com.tanuki.ui.DshListItem
import com.tanuki.ui.DshMarkdownBlock
import com.tanuki.ui.DshMarkdownParser
import com.tanuki.ui.DshTableAlign
import com.tanuki.ui.DshTablePolicy
import com.tanuki.ui.dshInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshMarkdownTest {
    @Test fun parsesHeadingsParagraphsAndRules() {
        val blocks = DshMarkdownParser.parse("# 标题\n\n第一行\n第二行\n\n---")
        assertEquals(DshMarkdownBlock.Heading(1, "标题"), blocks[0])
        assertEquals(DshMarkdownBlock.Paragraph("第一行\n第二行"), blocks[1])
        assertEquals(DshMarkdownBlock.Rule, blocks[2])
    }

    @Test fun parsesFencedCodeWithLanguageAndKeepsUnclosedFence() {
        val closed = DshMarkdownParser.parse("```kotlin\nval x = 1\n```\n后续")
        assertEquals(DshMarkdownBlock.Code("kotlin", "val x = 1"), closed[0])
        assertEquals(DshMarkdownBlock.Paragraph("后续"), closed[1])

        val unclosed = DshMarkdownParser.parse("```\n第一行\n第二行")
        assertEquals(DshMarkdownBlock.Code(null, "第一行\n第二行"), unclosed.single())
    }

    @Test fun fenceOnlyClosesWithSameCharacterAndLongEnoughMarker() {
        val tildes = DshMarkdownParser.parse("~~~\n```\n仍在代码\n~~~")
        assertEquals(DshMarkdownBlock.Code(null, "```\n仍在代码"), tildes.single())

        val longer = DshMarkdownParser.parse("````\n```\n````")
        assertEquals(DshMarkdownBlock.Code(null, "```"), longer.single())
    }

    @Test fun parsesQuotesNestedListsAndTasks() {
        val blocks = DshMarkdownParser.parse(
            "> 引用一\n> 引用二\n\n" +
                "- 项目一\n  - 子项目\n" +
                "- [x] 已完成\n- [ ] 未完成\n\n" +
                "1. 第一步\n2. 第二步"
        )
        assertEquals(DshMarkdownBlock.Quote("引用一\n引用二"), blocks[0])
        val unordered = blocks[1] as DshMarkdownBlock.ListBlock
        assertFalse(unordered.ordered)
        assertEquals(listOf("项目一", "子项目", "已完成", "未完成"), unordered.items.map { it.text })
        assertEquals(listOf(0, 1, 0, 0), unordered.items.map { it.depth })
        assertEquals(listOf(null, null, true, false), unordered.items.map { it.checked })
        val ordered = blocks[2] as DshMarkdownBlock.ListBlock
        assertEquals(listOf(1, 2), ordered.items.map { it.number })
        assertEquals(listOf("第一步", "第二步"), ordered.items.map { it.text })
    }

    @Test fun tableAlignmentEscapedPipeAndInlineCodePipe() {
        val blocks = DshMarkdownParser.parse(
            "| 名称 | 值 | 备注 |\n" +
                "| :--- | ---: | :---: |\n" +
                "| a | 1 | `x|y` |\n" +
                "| b\\|c | 2 | 普通 |"
        )
        val table = blocks.single() as DshMarkdownBlock.Table
        assertEquals(listOf("名称", "值", "备注"), table.headers)
        assertEquals(
            listOf(DshTableAlign.LEFT, DshTableAlign.RIGHT, DshTableAlign.CENTER),
            table.alignments
        )
        assertEquals(listOf("a", "1", "`x|y`"), table.rows[0])
        assertEquals(listOf("b|c", "2", "普通"), table.rows[1])
    }

    @Test fun parsesOrderedListsImagesAttachmentsAndCrlf() {
        val blocks = DshMarkdownParser.parse(
            "1. 第一步\r\n2. 第二步\r\n\r\n" +
                "![截图](https://example.invalid/a.png)\r\n\r\n" +
                "[报告](attachment:file-1)"
        )
        assertEquals(listOf("第一步", "第二步"), (blocks[0] as DshMarkdownBlock.ListBlock).items.map { it.text })
        assertEquals(DshMarkdownBlock.Image("截图", "https://example.invalid/a.png"), blocks[1])
        assertEquals(DshMarkdownBlock.Attachment("报告", "attachment:file-1"), blocks[2])
    }

    @Test fun inlineStylesCoverBoldItalicCombinedStrikeAndCode() {
        val text = dshInlineMarkdown("普通 **粗体**、*斜体*、***重点***、~~删除~~、`代码`", Color.Gray)
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(
            text.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold && it.item.fontStyle == FontStyle.Italic
            }
        )
        assertTrue(text.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(text.spanStyles.any { it.item.fontFamily != null })
    }

    @Test fun inlineLinkIsClickableAnnotationAndConfirmationText() {
        val text = dshInlineMarkdown("查看 [链接](https://example.invalid/doc)", Color.Gray)
        assertEquals("查看 链接", text.text)
        val links = text.getLinkAnnotations(0, text.length)
        assertEquals(1, links.size)
        val clickable = links.single().item as LinkAnnotation.Clickable
        assertEquals("https://example.invalid/doc", clickable.tag)
        assertTrue(clickable.styles?.style?.textDecoration == TextDecoration.Underline)
    }

    @Test fun linkColorsFollowTheExplicitThemeInAllStates() {
        for (tokens in listOf(com.tanuki.ui.design.TanukiColorTokens.Light, com.tanuki.ui.design.TanukiColorTokens.Dark)) {
            val color = Color(tokens.primary)
            val background = Color(tokens.surfaceMuted)
            val text = dshInlineMarkdown("[链接](https://example.invalid/doc)", background, linkColor = color)
            val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Clickable
            assertEquals(color, link.styles?.style?.color)
            assertEquals(color, link.styles?.pressedStyle?.color)
            assertTrue(com.tanuki.ui.design.TanukiColorMath.contrastRatio(tokens.primary, tokens.surfaceMuted) >= 4.5)
        }
    }

    @Test fun bareAndAngleBracketLinksBecomeClickable() {
        val bare = dshInlineMarkdown("访问 https://example.invalid/path。", Color.Gray)
        assertEquals("访问 https://example.invalid/path。", bare.text)
        assertEquals("https://example.invalid/path", (bare.getLinkAnnotations(0, bare.length).single().item as LinkAnnotation.Clickable).tag)

        val angle = dshInlineMarkdown("<https://example.invalid>", Color.Gray)
        assertEquals("https://example.invalid", angle.text)
        assertEquals(1, angle.getLinkAnnotations(0, angle.length).size)
    }

    @Test fun escapesPreventEmphasisAndCodeMarkers() {
        val text = dshInlineMarkdown("\\*不是斜体\\* 和 \\`不是代码\\`", Color.Gray)
        assertEquals("*不是斜体* 和 `不是代码`", text.text)
        assertTrue(text.spanStyles.none { it.item.fontStyle == FontStyle.Italic })
        assertTrue(text.spanStyles.none { it.item.fontFamily != null })
    }

    @Test fun malformedMarkdownFallsBackToLiteralText() {
        val text = dshInlineMarkdown("未闭合 `代码 和 [链接](", Color.Gray)
        assertTrue(text.text.contains("未闭合 `代码 和 [链接]("))
        assertEquals(listOf(DshMarkdownBlock.Paragraph("普通文本")), DshMarkdownParser.parse("普通文本"))
    }

    @Test fun longMessageAddsExplicitTruncationNotice() {
        val source = (1..DshChatRenderPolicy.MAX_MARKDOWN_BLOCKS + 1)
            .joinToString("\n\n") { "第 $it 段" }
        val blocks = DshMarkdownParser.parse(source)
        assertEquals(DshChatRenderPolicy.MAX_MARKDOWN_BLOCKS + 1, blocks.size)
        assertTrue(blocks.last() is DshMarkdownBlock.Truncated)
    }

    @Test fun codeBlockPolicyCapsLinesAndBuildsNumberPrefix() {
        val code = (1..10).joinToString("\n") { "line $it" }
        val view = DshCodeBlockPolicy.view(code, maxLines = 4)
        assertEquals(10, view.totalLines)
        assertEquals(4, view.lines.size)
        assertTrue(view.truncated)
        assertEquals("line 4", view.lines.last())
        assertEquals("  3 │ ", DshCodeBlockPolicy.lineNumberPrefix(3, 3))
        assertEquals(4, DshCodeBlockPolicy.numberWidth(1000))
    }

    @Test fun tablePolicyCapsRowsAndColumnsExplicitly() {
        val table = DshMarkdownBlock.Table(
            headers = listOf("a", "b", "c"),
            rows = (1..5).map { listOf("$it", "x", "y") },
            alignments = listOf(DshTableAlign.LEFT, DshTableAlign.RIGHT, DshTableAlign.CENTER)
        )
        val view = DshTablePolicy.view(table, maxRows = 2, maxColumns = 2)
        assertEquals(2, view.headers.size)
        assertEquals(2, view.rows.size)
        assertEquals(5, view.totalRows)
        assertTrue(view.truncatedRows)
        assertTrue(view.truncatedColumns)
        assertEquals(listOf(DshTableAlign.LEFT, DshTableAlign.RIGHT), view.alignments)
    }

    @Test fun listItemKeepsDepthAndTaskState() {
        val item = DshListItem("任务", depth = 2, ordered = true, number = 3, checked = true)
        assertEquals(2, item.depth)
        assertEquals(3, item.number)
        assertEquals(true, item.checked)
    }
}

package com.tanuki

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.tanuki.ui.DshMarkdownBlock
import com.tanuki.ui.DshMarkdownParser
import com.tanuki.ui.dshInlineMarkdown
import org.junit.Assert.assertEquals
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

    @Test fun parsesQuotesListsAndTables() {
        val blocks = DshMarkdownParser.parse(
            "> 引用一\n> 引用二\n\n- 项目一\n- 项目二\n\n| 名称 | 值 |\n| --- | --- |\n| a | 1 |\n| b | 2 |"
        )
        assertEquals(DshMarkdownBlock.Quote("引用一\n引用二"), blocks[0])
        assertEquals(DshMarkdownBlock.ListBlock(false, listOf("项目一", "项目二")), blocks[1])
        val table = blocks[2] as DshMarkdownBlock.Table
        assertEquals(listOf("名称", "值"), table.headers)
        assertEquals(listOf(listOf("a", "1"), listOf("b", "2")), table.rows)
    }

    @Test fun parsesOrderedListsImagesAndCrlf() {
        val blocks = DshMarkdownParser.parse("1. 第一步\r\n2. 第二步\r\n\r\n![截图](https://example.invalid/a.png)")
        assertEquals(DshMarkdownBlock.ListBlock(true, listOf("第一步", "第二步")), blocks[0])
        assertEquals(DshMarkdownBlock.Image("截图", "https://example.invalid/a.png"), blocks[1])
    }

    @Test fun inlineStylesCoverBoldCodeItalicAndLinks() {
        val text = dshInlineMarkdown("普通 **粗体**、`代码`、*斜体*、[链接](https://example.invalid)", Color.Gray)
        assertTrue(text.text.contains("普通 粗体、代码、斜体、链接"))
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(text.spanStyles.any { it.item.fontFamily != null })
        assertTrue(text.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough || it.item.textDecoration == TextDecoration.Underline })
        assertTrue(text.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
        assertTrue(text.text.contains("https://example.invalid"))
    }

    @Test fun malformedMarkdownFallsBackToLiteralText() {
        val text = dshInlineMarkdown("未闭合 `代码 和 [链接](", Color.Gray)
        assertTrue(text.text.contains("未闭合 `代码 和 [链接]("))
        assertEquals(listOf(DshMarkdownBlock.Paragraph("普通文本")), DshMarkdownParser.parse("普通文本"))
    }
}

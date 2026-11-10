package com.tanuki

import com.tanuki.ui.TextPageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 行号排版、搜索偏移换算与 4 MiB 截断边界。 */
class TextPageLayoutTest {
    @Test fun numbersEveryLogicalLineFromTheRequestedStart() {
        val numbered = TextPageLayout.number("alpha\nbeta", startLine = 1)
        assertEquals("1  alpha\n2  beta", numbered.text)
        assertEquals(2, numbered.lineCount)
        assertEquals(3, numbered.prefixLength)
        assertEquals(listOf(0, 9), numbered.numberStarts)
    }

    @Test fun padsLineNumbersToTheWidestLineInThePage() {
        val numbered = TextPageLayout.number("a\nb", startLine = 9)
        // 最后一行是 10，两位行号；前缀固定为“两位数字 + 两个空格”。
        assertEquals(" 9  a\n10  b", numbered.text)
        assertEquals(4, numbered.prefixLength)
        assertEquals(listOf(0, 6), numbered.numberStarts)
    }

    @Test fun keepsTrailingNewlineAsAnExtraNumberedLine() {
        val numbered = TextPageLayout.number("one\n", startLine = 5)
        assertEquals("5  one\n6  ", numbered.text)
        assertEquals(2, numbered.lineCount)
    }

    @Test fun emptyTextStillShowsOneLineAndDisabledNumbersKeepRawText() {
        val empty = TextPageLayout.number("", startLine = 1)
        assertEquals("1  ", empty.text)
        assertEquals(1, empty.lineCount)

        val raw = TextPageLayout.number("a\nb", startLine = 7, enabled = false)
        assertEquals("a\nb", raw.text)
        assertEquals(0, raw.prefixLength)
        assertTrue(raw.numberStarts.isEmpty())
    }

    @Test fun mapsRawSearchOffsetsToNumberedText() {
        val text = "aa\nbb\ncc"
        val numbered = TextPageLayout.number(text, startLine = 1)
        // 前缀长度 3：第一行内容起始 3，第二行起始 9，第三行起始 15。
        assertEquals(3, TextPageLayout.displayOffset(0, text, numbered.prefixLength))
        assertEquals(9, TextPageLayout.displayOffset(3, text, numbered.prefixLength))
        assertEquals(15, TextPageLayout.displayOffset(6, text, numbered.prefixLength))
        // 关闭行号时偏移不变。
        assertEquals(6, TextPageLayout.displayOffset(6, text, 0))
        // 越界偏移收敛到文本长度，不抛异常。
        assertEquals(numbered.text.length, TextPageLayout.displayOffset(999, text, numbered.prefixLength))

        // 批量换算（搜索实际走这条路径）必须与逐个换算一致，并按行线性推进。
        val rawOffsets = listOf(0, 1, 3, 5, 6)
        assertEquals(
            rawOffsets.map { TextPageLayout.displayOffset(it, text, numbered.prefixLength) },
            TextPageLayout.displayOffsets(rawOffsets, text, numbered.prefixLength),
        )
        assertEquals(listOf(4, 9, 15), TextPageLayout.displayOffsets(listOf(1, 3, 6), text, numbered.prefixLength))
        assertEquals(rawOffsets, TextPageLayout.displayOffsets(rawOffsets, text, 0))
        assertEquals(emptyList<Int>(), TextPageLayout.displayOffsets(emptyList(), text, numbered.prefixLength))
    }

    @Test fun continuesLineNumbersAcrossPagesIncludingSplitLines() {
        // 段末没有换行：下一段第一行仍是同一逻辑行的续行，行号不递增。
        assertEquals(10, TextPageLayout.nextStartLine(10, "abc"))
        // 段末有一个换行：下一行是新的逻辑行。
        assertEquals(11, TextPageLayout.nextStartLine(10, "abc\n"))
        // 三段文本没有末尾换行：下一段仍从第 12 行继续，不跳到 13。
        assertEquals(12, TextPageLayout.nextStartLine(10, "a\nb\nc"))
        assertEquals(1, TextPageLayout.nextStartLine(0, "x"))
    }

    @Test fun stopsPagingAtFileEndOrFourMebibyteCap() {
        assertFalse(TextPageLayout.canAdvance(0, 100))
        assertTrue(TextPageLayout.canAdvance(64 * 1024, 1024L * 1024))
        assertFalse(TextPageLayout.canAdvance(1_000, 1_000))
        assertFalse(TextPageLayout.canAdvance(TextPageLayout.MAX_VIEW_BYTES, 8L * 1024 * 1024))
        assertTrue(TextPageLayout.canAdvance(TextPageLayout.MAX_VIEW_BYTES - 1, 8L * 1024 * 1024))
        assertEquals(4L * 1024 * 1024, TextPageLayout.MAX_VIEW_BYTES)
    }

    @Test fun unknownTotalSizeStillAllowsPagingUntilHasMoreIsFalseOrCap() {
        assertTrue(TextPageLayout.canAdvance(64 * 1024, totalBytes = -1, hasMore = true))
        assertFalse(TextPageLayout.canAdvance(64 * 1024, totalBytes = -1, hasMore = false))
        assertFalse(TextPageLayout.canAdvance(TextPageLayout.MAX_VIEW_BYTES, totalBytes = -1, hasMore = true))
    }

    @Test fun reportsTruncationWhenPagingStopsBeforeContentEnds() {
        val cap = TextPageLayout.MAX_VIEW_BYTES
        // 还有下一页时不算截断。
        assertFalse(TextPageLayout.isTruncated(cap, totalBytes = cap * 2, hasMore = true))
        // 停止翻页但总大小大于已显示游标：达到上限，或上限切在多字节字符内部。
        assertTrue(TextPageLayout.isTruncated(cap, totalBytes = cap * 2, hasMore = false))
        assertTrue(TextPageLayout.isTruncated(cap - 1, totalBytes = cap * 2, hasMore = false))
        assertFalse(TextPageLayout.isTruncated(cap, totalBytes = cap, hasMore = false))
        // 顺序流不知道总大小时，停止翻页且到达上限仍按截断提示。
        assertFalse(TextPageLayout.isTruncated(cap, totalBytes = -1, hasMore = true))
        assertTrue(TextPageLayout.isTruncated(cap, totalBytes = -1, hasMore = false))
    }

    @Test fun countsLogicalLines() {
        assertEquals(1, TextPageLayout.lineCount(""))
        assertEquals(1, TextPageLayout.lineCount("abc"))
        assertEquals(2, TextPageLayout.lineCount("a\n"))
        assertEquals(3, TextPageLayout.lineCount("a\nb\n"))
    }
}

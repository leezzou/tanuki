package com.tanuki

import com.tanuki.storage.XlsxValueFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class XlsxValueFormatterTest {
    @Test fun formatsGeneralNumbersWithoutBinaryTail() {
        assertEquals("42", XlsxValueFormatter.number(42.0, "General", false))
        assertEquals("3.5", XlsxValueFormatter.number(3.5, "General", false))
        assertEquals("0.1", XlsxValueFormatter.number(0.1, "General", false))
        assertEquals("1200.5", XlsxValueFormatter.number(1200.5, null, false))
    }

    @Test fun formatsDecimalsThousandSeparatorsAndPercent() {
        assertEquals("1200.50", XlsxValueFormatter.number(1200.5, "0.00", false))
        assertEquals("1,234.50", XlsxValueFormatter.number(1234.5, "#,##0.00", false))
        assertEquals("12.50%", XlsxValueFormatter.number(0.125, "0.00%", false))
        assertEquals("13%", XlsxValueFormatter.number(0.125, "0%", false))
    }

    @Test fun formatsDateAndTimeWithExcelEpochAndLeapBugApproximation() {
        assertEquals("2023-01-01", XlsxValueFormatter.number(44927.0, "yyyy-mm-dd", false))
        assertEquals("1900-01-01", XlsxValueFormatter.number(1.0, "yyyy-mm-dd", false))
        assertEquals("1900-03-01", XlsxValueFormatter.number(61.0, "yyyy-mm-dd", false))
        assertEquals("2023-01-01 12:00", XlsxValueFormatter.number(44927.5, "yyyy-mm-dd h:mm", false))
        // 时间格式里的 m 是分钟，不能误判成日期。
        assertEquals("12:00:00", XlsxValueFormatter.number(0.5, "h:mm:ss", false))
        // mm:ss 只显示时内分钟与秒；0.5 天是 12:00:00，对应 00:00。
        assertEquals("00:00", XlsxValueFormatter.number(0.5, "mm:ss", false))
        assertEquals("02:30", XlsxValueFormatter.number((2 * 60 + 30) / 86400.0, "mm:ss", false))
        assertEquals("1904-01-01", XlsxValueFormatter.number(0.0, "yyyy-mm-dd", true))
    }

    @Test fun formatsBooleanAndKeepsUnknownNumbers() {
        assertEquals("TRUE", XlsxValueFormatter.booleanText("1"))
        assertEquals("FALSE", XlsxValueFormatter.booleanText("0"))
        assertEquals("不是数字", XlsxValueFormatter.number("不是数字", "0.00", false))
    }
}

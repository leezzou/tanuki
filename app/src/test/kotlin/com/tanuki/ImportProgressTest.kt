package com.tanuki

import com.tanuki.storage.ImportProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 导入进度必须使用真实总量；不完整的大小不能折算成百分比。 */
class ImportProgressTest {
    @Test fun totalRequiresEverySizeToBeKnownAndNonNegative() {
        assertEquals(ImportProgress.UNKNOWN_TOTAL, ImportProgress.totalBytes(emptyList()))
        assertEquals(15L, ImportProgress.totalBytes(listOf(5L, 10L)))
        assertEquals(ImportProgress.UNKNOWN_TOTAL, ImportProgress.totalBytes(listOf(5L, null)))
        assertEquals(ImportProgress.UNKNOWN_TOTAL, ImportProgress.totalBytes(listOf(5L, -1L)))
    }

    @Test fun overflowingTotalsStayUnknownInsteadOfWrapping() {
        assertEquals(ImportProgress.UNKNOWN_TOTAL,
            ImportProgress.totalBytes(listOf(Long.MAX_VALUE, 1L)))
    }

    @Test fun fractionRequiresAPositiveKnownTotal() {
        assertNull(ImportProgress.fraction(0, -1))
        assertNull(ImportProgress.fraction(0, 0))
        assertEquals(0f, ImportProgress.fraction(0, 100)!!, 0f)
        assertEquals(0.25f, ImportProgress.fraction(25, 100)!!, 0f)
        assertEquals(1f, ImportProgress.fraction(120, 100)!!, 0f)
    }
}

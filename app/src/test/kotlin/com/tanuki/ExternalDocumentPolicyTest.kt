package com.tanuki

import com.tanuki.storage.BoundedCopy
import com.tanuki.storage.ExternalDocumentDescriptor
import com.tanuki.storage.ExternalDocumentPolicy
import com.tanuki.storage.ExternalDocumentSaveState
import com.tanuki.storage.WorkspaceFileManager.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

class ExternalDocumentPolicyTest {
    private val good = "content://com.android.providers.downloads.documents/document/msf%3A42"

    @Test fun acceptsOnlyWellFormedContentUris() {
        assertTrue(ExternalDocumentPolicy.isAcceptedUri(good))
        assertTrue(ExternalDocumentPolicy.isAcceptedUri("content://com.example.provider/a/b?x=1"))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri(null))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri(""))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri("file:///sdcard/a.pdf"))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri("https://example.com/a.pdf"))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri("content:///missing-authority"))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri("content://user@host/a"))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri("content://host/a\nb"))
        assertFalse(ExternalDocumentPolicy.isAcceptedUri("content://host/" + "a".repeat(5000)))
    }

    @Test fun sanitizesDisplayNameForUiAndFallbackImport() {
        assertEquals("报告.pdf", ExternalDocumentPolicy.sanitizeDisplayName("../../报告.pdf"))
        assertEquals("报告.pdf", ExternalDocumentPolicy.sanitizeDisplayName("C:\\temp\\报告.pdf"))
        assertEquals("外部文件", ExternalDocumentPolicy.sanitizeDisplayName(null))
        assertEquals("外部文件", ExternalDocumentPolicy.sanitizeDisplayName(".."))
        assertEquals("ab", ExternalDocumentPolicy.sanitizeDisplayName("a\u0000b"))
        assertEquals(ExternalDocumentPolicy.MAX_DISPLAY_NAME,
            ExternalDocumentPolicy.sanitizeDisplayName("x".repeat(1000)).length)
    }

    @Test fun onlyOpensSupportedTypesDirectlyAndKeepsCopyExplicit() {
        for (kind in listOf(FileKind.TEXT, FileKind.IMAGE, FileKind.PDF, FileKind.DOCX, FileKind.XLSX)) {
            assertTrue("$kind 应支持内置预览", ExternalDocumentPolicy.builtInPreview(kind))
            assertFalse("$kind 不应默认复制", ExternalDocumentPolicy.copyIsExplicitFallback(kind))
        }
        for (kind in listOf(FileKind.DOC, FileKind.OTHER)) {
            assertFalse(ExternalDocumentPolicy.builtInPreview(kind))
            assertTrue("$kind 保留显式复制回退", ExternalDocumentPolicy.copyIsExplicitFallback(kind))
        }
    }

    @Test fun largeFilesAreRejectedBeforeDecodeWithChineseLimitMessage() {
        val oversizedPdf = ExternalDocumentPolicy.MAX_PDF_BYTES + 1
        val pdf = ExternalDocumentPolicy.decide(good, FileKind.PDF, oversizedPdf, true)
        assertEquals(ExternalDocumentPolicy.Status.TOO_LARGE, pdf.status)
        assertTrue(pdf.message!!.contains("超过内置预览上限"))
        assertTrue(pdf.copyFallback)

        val docx = ExternalDocumentPolicy.decide(good, FileKind.DOCX, ExternalDocumentPolicy.MAX_OFFICE_BYTES + 1, true)
        assertEquals(ExternalDocumentPolicy.Status.TOO_LARGE, docx.status)
        val image = ExternalDocumentPolicy.decide(good, FileKind.IMAGE, ExternalDocumentPolicy.MAX_IMAGE_BYTES + 1, true)
        assertEquals(ExternalDocumentPolicy.Status.TOO_LARGE, image.status)
        // 文本分页读取，不设整文件大小上限。
        assertEquals(ExternalDocumentPolicy.Status.READY,
            ExternalDocumentPolicy.decide(good, FileKind.TEXT, ExternalDocumentPolicy.MAX_PDF_BYTES * 4, true).status)
    }

    @Test fun permissionPersistenceChangesOnlyTheWarningNotReadability() {
        val persisted = ExternalDocumentPolicy.decide(good, FileKind.PDF, 1024, persistedRead = true)
        assertEquals(ExternalDocumentPolicy.Status.READY, persisted.status)
        assertNull(persisted.message)

        val transient = ExternalDocumentPolicy.decide(good, FileKind.PDF, 1024, persistedRead = false)
        assertEquals(ExternalDocumentPolicy.Status.READY, transient.status)
        assertTrue(transient.message!!.contains("长期读取权限"))
    }

    @Test fun invalidatedOrUnsupportedUrisProduceCopyFallback() {
        for (failure in listOf(SecurityException("revoked"), FileNotFoundException("gone"))) {
            val decision = ExternalDocumentPolicy.decide(good, FileKind.PDF, 1024, true, failure)
            assertEquals(ExternalDocumentPolicy.Status.PERMISSION_LOST, decision.status)
            assertTrue(decision.message!!.contains("读取权限已失效"))
            assertTrue(decision.copyFallback)
        }
        val unsupported = ExternalDocumentPolicy.decide(good, FileKind.PDF, 1024, true, UnsupportedOperationException())
        assertEquals(ExternalDocumentPolicy.Status.UNSUPPORTED_PROVIDER, unsupported.status)
        assertTrue(unsupported.message!!.contains("不支持直接读取"))
        assertTrue(unsupported.copyFallback)

        val invalid = ExternalDocumentPolicy.decide("file:///sdcard/a.pdf", FileKind.PDF, 1024, true)
        assertEquals(ExternalDocumentPolicy.Status.INVALID_URI, invalid.status)
        assertFalse(invalid.copyFallback)
    }

    @Test fun savedDescriptorRoundTripsAndRejectsForeignSchemes() {
        val descriptor = ExternalDocumentDescriptor(good, "报告.pdf", "application/pdf", 4096, persistedRead = true, openToken = 7)
        val fields = descriptor.savedFields()
        val restored = ExternalDocumentDescriptor.restore(
            uri = fields["uri"] as String,
            name = fields["name"] as String,
            mime = fields["mime"] as String,
            bytes = fields["bytes"] as Long,
            persisted = fields["persisted"] as Boolean,
            openToken = fields["openToken"] as Long,
        )
        assertEquals(descriptor, restored)
        assertNull(ExternalDocumentDescriptor.restore("file:///sdcard/a.pdf", "a.pdf", "application/pdf", 1, true))
        val unknown = ExternalDocumentDescriptor.restore("content://host/a", null, null, -1, false)
        assertNotNull(unknown)
        assertEquals("application/octet-stream", unknown!!.mimeType)
    }

    @Test fun reopeningSameUriGetsANewViewerAndPreviewRead() {
        assertNotEquals(
            ExternalDocumentPolicy.contentKey(good, openToken = 1),
            ExternalDocumentPolicy.contentKey(good, openToken = 2),
        )
        val first = ExternalDocumentDescriptor(good, "a.pdf", "application/pdf", 1, true, openToken = 1)
        val reauthorized = ExternalDocumentDescriptor(good, "a.pdf", "application/pdf", 1, true, openToken = 2)
        assertNotEquals(first.openToken, reauthorized.openToken)
        assertNotEquals(ExternalDocumentPolicy.viewerKey(first), ExternalDocumentPolicy.viewerKey(reauthorized))
        // Activity 恢复 token=1 后，第一次重选必须得到 2，而不是复用 1。
        val restoredSequence = ExternalDocumentPolicy.restoredTokenSequence(0, restoredToken = 1)
        assertEquals(2L, restoredSequence + 1)
    }

    @Test fun consumedLaunchIntentIsNotReplayedOnActivityRestore() {
        val requestA = ExternalDocumentPolicy.requestId("android.intent.action.VIEW", good, null)
        val requestB = ExternalDocumentPolicy.requestId("android.intent.action.VIEW", "content://host/b", null)
        assertNotNull(requestA)
        assertNotNull(requestB)
        assertTrue(ExternalDocumentPolicy.shouldConsume(false, null, requestA))
        assertFalse(ExternalDocumentPolicy.shouldConsume(true, requestA, requestA))
        assertTrue(ExternalDocumentPolicy.shouldConsume(true, requestA, requestB))
        assertTrue(ExternalDocumentPolicy.shouldConsume(true, null, requestB))
        assertTrue(ExternalDocumentPolicy.shouldConsume(false, requestA, requestA))
        assertNull(ExternalDocumentPolicy.requestId("android.intent.action.MAIN", null, null))
        assertNull(ExternalDocumentPolicy.requestId("android.intent.action.VIEW", "file:///sdcard/a.pdf", null))
    }

    @Test fun closedDocumentStillSavesConsumedRequestForRestore() {
        val requestA = ExternalDocumentPolicy.requestId("android.intent.action.VIEW", good, null)
        assertNotNull(requestA)
        // 查看器已关闭：descriptor 为空，但已消费请求必须随 Activity 状态保存。
        val entries = ExternalDocumentSaveState(descriptor = null, consumedRequest = requestA).entries()
        assertEquals(requestA, entries["externalConsumedRequest"])
        assertFalse(entries.containsKey("externalUri"))
        val restored = ExternalDocumentSaveState.restore(
            uri = entries["externalUri"] as String?,
            name = entries["externalName"] as String?,
            mime = entries["externalMime"] as String?,
            bytes = entries["externalBytes"] as? Long ?: -1L,
            persisted = entries["externalPersisted"] as? Boolean ?: false,
            openToken = entries["externalOpenToken"] as? Long ?: 0L,
            consumedRequest = entries["externalConsumedRequest"] as String?,
        )
        assertNull(restored.descriptor)
        assertEquals(requestA, restored.consumedRequest)
        assertFalse(ExternalDocumentPolicy.shouldConsume(true, restored.consumedRequest, requestA))
    }

    @Test fun oversizedPreviewStillAllowsOriginalUriTransfer() {
        val oversized = ExternalDocumentPolicy.decide(good, FileKind.PDF, ExternalDocumentPolicy.MAX_PDF_BYTES + 1, true)
        assertEquals(ExternalDocumentPolicy.Status.TOO_LARGE, oversized.status)
        assertTrue("超限仍可交给其他应用打开", ExternalDocumentPolicy.canTransferOriginal(good))
        assertFalse(ExternalDocumentPolicy.canTransferOriginal("file:///sdcard/a.pdf"))
    }}

class BoundedCopyTest {
    @Test fun copiesWithinLimitAndReportsRealProgress() {
        val payload = ByteArray(10_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        var last = 0L
        val copied = BoundedCopy.copy(ByteArrayInputStream(payload), output, 10_000) { last = it }
        assertEquals(10_000L, copied)
        assertEquals(10_000L, last)
        assertTrue(payload.contentEquals(output.toByteArray()))
    }

    @Test fun abortsBeforeWritingPastLimit() {
        val payload = ByteArray(10_000) { 1 }
        val output = ByteArrayOutputStream()
        try {
            BoundedCopy.copy(ByteArrayInputStream(payload), output, 2_000)
            fail("超过上限应失败")
        } catch (failure: java.io.IOException) {
            assertTrue(failure.message!!.contains("大小上限"))
            assertEquals(0, output.size())
        }
    }

    @Test fun exactLimitIsAccepted() {
        val payload = ByteArray(2_000) { 1 }
        val output = ByteArrayOutputStream()
        assertEquals(2_000L, BoundedCopy.copy(ByteArrayInputStream(payload), output, 2_000))
        assertEquals(2_000, output.size())
    }
}

package com.tanuki

import com.tanuki.web.DshAttachmentKind
import com.tanuki.web.DshAttachmentReadBudget
import com.tanuki.web.DshComposerDragPolicy
import com.tanuki.web.DshComposerPolicy
import com.tanuki.web.DshComposerProtocol
import com.tanuki.web.DshImageLimits
import com.tanuki.web.DshImageMetadata
import com.tanuki.web.DshMediaTypes
import com.tanuki.web.DshOutgoingPart
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshComposerProtocolTest {
    @Test fun promptBuildsOfficialContentInOrder() {
        val args = DshComposerProtocol.promptArgs(
            requestId = "req-1",
            sessionId = "session-1",
            mode = "queue",
            parts = listOf(
                DshOutgoingPart.Image("image/png", "AAAA", "a.png"),
                DshOutgoingPart.File("receipt-1"),
                DshOutgoingPart.Text("你好")
            ),
            clientTimeZone = "Asia/Shanghai"
        )
        val request = args.getJSONObject("request")
        assertEquals("req-1", request.getString("requestId"))
        assertEquals("session-1", request.getString("sessionId"))
        assertEquals("queue", request.getString("mode"))
        assertEquals("Asia/Shanghai", request.getString("clientTimeZone"))
        val content = request.getJSONArray("content")
        assertEquals(3, content.length())
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("image/png", content.getJSONObject(0).getString("mediaType"))
        assertEquals("AAAA", content.getJSONObject(0).getString("data"))
        assertEquals("a.png", content.getJSONObject(0).getString("name"))
        assertEquals("file", content.getJSONObject(1).getString("type"))
        assertEquals("receipt-1", content.getJSONObject(1).getString("receiptId"))
        assertEquals("text", content.getJSONObject(2).getString("type"))
        assertEquals("你好", content.getJSONObject(2).getString("text"))
    }

    @Test fun promptRejectsInvalidIdentityModeAndEmptyContent() {
        assertFails { DshComposerProtocol.promptArgs("", "s1", "queue", listOf(DshOutgoingPart.Text("x")), null) }
        assertFails { DshComposerProtocol.promptArgs("r1", "s 1", "queue", listOf(DshOutgoingPart.Text("x")), null) }
        assertFails { DshComposerProtocol.promptArgs("r1", "s1", "later", listOf(DshOutgoingPart.Text("x")), null) }
        assertFails { DshComposerProtocol.promptArgs("r1", "s1", "queue", emptyList(), null) }
        assertFails { DshComposerProtocol.promptArgs("r1", "s1", "queue", listOf(DshOutgoingPart.Text("   ")), null) }
    }

    @Test fun uploadAndReferenceArgsUseAgentScope() {
        val upload = DshComposerProtocol.uploadArgs("session-1", "a.txt", "QUJD")
        assertEquals("session-1", upload.getString("agentId"))
        assertEquals("QUJD", upload.getJSONObject("request").getString("data"))
        assertEquals("a.txt", upload.getJSONObject("request").getString("name"))

        val references = DshComposerProtocol.fileReferencesArgs("session-1", "src/")
        assertEquals("session-1", references.getString("agentId"))
        assertEquals("src/", references.getString("query"))
        assertEquals("session-1", DshComposerProtocol.commandsArgs("session-1").getString("agentId"))
        assertEquals(
            "session-1",
            DshComposerProtocol.projectionArgs("session-1").getJSONObject("request").getString("sessionId")
        )
        assertEquals(
            "session-1",
            DshComposerProtocol.sessionReferencesArgs("session-1", "标题").getString("agentId")
        )
    }

    @Test fun cancelArgsScopesToSessionRequest() {
        val args = DshComposerProtocol.cancelArgs("session-1")
        assertEquals("session-1", args.getJSONObject("request").getString("sessionId"))
        assertFails { DshComposerProtocol.cancelArgs("bad id") }
    }

    @Test fun mentionInsertionOnlyReplacesActiveQuery() {
        val text = "前 @sr 后"
        val query = DshComposerPolicy.mentionQuery(text, 5)!!
        val edit = DshComposerPolicy.insertMention(text, query, "@src/Main.kt")
        assertEquals("前 @src/Main.kt 后", edit.text)
        assertEquals(14, edit.cursor)
        // 查询后面的正文不能被吞掉；替换只发生在 @ 到当前光标之间。
        assertTrue(edit.text.endsWith(" 后"))
    }

    @Test fun commandInsertionPreservesArgumentsAndNewlines() {
        assertEquals("/compact ", DshComposerPolicy.insertCommand("/co", 3, "compact").text)
        assertEquals(
            "/compact extra\n下一行",
            DshComposerPolicy.insertCommand("/co extra\n下一行", 3, "compact").text
        )
        // 非法命令名不会破坏输入框内容，只保持原样。
        val unchanged = DshComposerPolicy.insertCommand("/co", 3, "bad name")
        assertEquals("/co", unchanged.text)
        assertEquals(3, unchanged.cursor)
    }

    @Test fun commandSearchMatchesNameAndDescriptionAndCandidateMoveWraps() {
        val commands = listOf(
            com.tanuki.web.DshCommand("compact", "压缩上下文", null, false),
            com.tanuki.web.DshCommand("goal", "查看长期目标", null, false),
            com.tanuki.web.DshCommand("export", "导出", "目标文件", false)
        )
        assertEquals(listOf("compact"), DshComposerPolicy.searchCommands(commands, "comp").map { it.name })
        assertEquals(listOf("goal", "export"), DshComposerPolicy.searchCommands(commands, "目标").map { it.name })
        assertEquals(0, DshComposerPolicy.moveCandidate(2, 3, 1))
        assertEquals(2, DshComposerPolicy.moveCandidate(0, 3, -1))
        assertEquals(0, DshComposerPolicy.moveCandidate(0, 0, 1))
    }

    @Test fun commandExecuteRejectsTextParts() {
        val args = DshComposerProtocol.commandExecuteArgs(
            "session-1",
            "/compact",
            listOf(DshOutgoingPart.Image("image/png", "AAAA", null), DshOutgoingPart.File("r1"))
        )
        assertEquals("session-1", args.getString("agentId"))
        assertEquals("/compact", args.getString("line"))
        assertEquals(2, args.getJSONArray("submittedAttachments").length())
        assertFails {
            DshComposerProtocol.commandExecuteArgs("session-1", "/compact", listOf(DshOutgoingPart.Text("x")))
        }
    }

    @Test fun parsesImageLimitsFromProjectionAndUsesDefaultsForInvalidValues() {
        val projection = JSONObject().put(
            "kind", "sequenced"
        ).put(
            "values",
            JSONObject().put(
                "imageLimits",
                JSONObject()
                    .put("maxImageBytes", 8 * 1024 * 1024)
                    .put("maxImagesPerMessage", 3)
                    .put("maxMessageImageBytes", 12 * 1024 * 1024)
                    .put("maxImagePixels", 12_000_000L)
                    .put("maxImageDimension", 4096)
                    .put("mediaTypes", JSONArray().put("image/png").put("image/gif"))
            )
        )
        val limits = DshComposerProtocol.parseImageLimits(projection)
        assertEquals(8 * 1024 * 1024, limits.maxImageBytes)
        assertEquals(3, limits.maxImagesPerMessage)
        assertEquals(12 * 1024 * 1024, limits.maxMessageImageBytes)
        assertEquals(12_000_000L, limits.maxImagePixels)
        assertEquals(4096, limits.maxImageDimension)
        assertEquals(setOf("image/png", "image/gif"), limits.mediaTypes)

        val fallback = DshComposerProtocol.parseImageLimits(JSONObject().put("kind", "migration-required"))
        assertEquals(DshImageLimits(), fallback)
        val clamped = DshComposerProtocol.parseImageLimits(
            JSONObject().put(
                "imageLimits",
                JSONObject().put("maxImageBytes", 1).put("maxImagesPerMessage", 0)
                    .put("maxImagePixels", 1L).put("maxImageDimension", 0)
            )
        )
        assertTrue(clamped.maxImageBytes >= 64 * 1024)
        assertTrue(clamped.maxImagesPerMessage >= 1)
        assertTrue(clamped.maxImagePixels >= 1)
        assertTrue(clamped.maxImageDimension >= 1)
    }

    @Test fun parsesCommandsAndDropsInvalidNames() {
        val array = JSONArray()
            .put(JSONObject().put("name", "compact").put("description", "压缩上下文"))
            .put(
                JSONObject().put("name", "goal").put("description", "目标")
                    .put("input", JSONObject().put("hint", "目标内容").put("attachments", true))
            )
            .put(JSONObject().put("name", "Bad Name").put("description", "无效"))
            .put(JSONObject().put("name", "").put("description", "空"))
        val commands = DshComposerProtocol.parseCommands(array)
        assertEquals(listOf("compact", "goal"), commands.map { it.name })
        assertFalse(commands[0].acceptsAttachments)
        assertTrue(commands[1].acceptsAttachments)
        assertEquals("目标内容", commands[1].inputHint)
    }

    @Test fun parsesFileReferencesAndFormatsQuotedMentions() {
        val array = JSONArray()
            .put(JSONObject().put("path", "src/Main.kt").put("kind", "file"))
            .put(JSONObject().put("path", "my dir").put("kind", "directory"))
            .put(JSONObject().put("path", "bad\npath").put("kind", "file"))
            .put(JSONObject().put("path", "link").put("kind", "symlink"))
        val references = DshComposerProtocol.parseFileReferences(array, "")
        assertEquals(listOf("src/Main.kt", "my dir"), references.map { it.path })
        assertEquals("@src/Main.kt", references[0].mention)
        assertEquals("@\"my dir/", references[1].mention)
        val quoted = DshComposerProtocol.parseFileReferences(
            JSONArray().put(JSONObject().put("path", "a b.txt").put("kind", "file")),
            "\"a"
        )
        assertEquals("@\"a b.txt\"", quoted.single().mention)
    }

    @Test fun parsesSessionReferencesAndRejectsUnsafeMentions() {
        val array = JSONArray()
            .put(
                JSONObject()
                    .put("mention", "@[修复登录](dsh-session:abc)")
                    .put("sessionId", "abc")
                    .put("label", "修复登录")
                    .put("displayTitle", "修复登录")
                    .put("sameWorkspace", true)
                    .put("createdAt", 123L)
            )
            .put(JSONObject().put("mention", "plain").put("sessionId", "def"))
            .put(JSONObject().put("mention", "@[x](dsh-session:bad)").put("sessionId", "bad\nid"))
        val references = DshComposerProtocol.parseSessionReferences(array)
        assertEquals(1, references.size)
        assertEquals("abc", references.single().sessionId)
        assertEquals("修复登录", references.single().displayTitle)
        assertTrue(references.single().sameWorkspace)
    }

    @Test fun sniffsSupportedImagesAndReadsIntrinsicDimensions() {
        val png = png(640, 480)
        val jpeg = jpeg(320, 200)
        val gif = gif(10, 20)
        val webp = webpVp8x(100, 50)
        assertEquals("image/png", DshMediaTypes.sniff(png))
        assertEquals("image/jpeg", DshMediaTypes.sniff(jpeg))
        assertEquals("image/gif", DshMediaTypes.sniff(gif))
        assertEquals("image/webp", DshMediaTypes.sniff(webp))
        assertEquals(640, DshImageMetadata.read(png)?.width)
        assertEquals(480, DshImageMetadata.read(png)?.height)
        assertEquals(320, DshImageMetadata.read(jpeg)?.width)
        assertEquals(200, DshImageMetadata.read(jpeg)?.height)
        assertEquals(10, DshImageMetadata.read(gif)?.width)
        assertEquals(20, DshImageMetadata.read(gif)?.height)
        assertEquals(100, DshImageMetadata.read(webp)?.width)
        assertEquals(50, DshImageMetadata.read(webp)?.height)
        assertNull(DshMediaTypes.sniff("not an image".toByteArray()))
        assertNull(DshImageMetadata.read(byteArrayOf(1, 2, 3)))
    }

    @Test fun validatesImageLimitsWithChineseErrors() {
        val limits = DshImageLimits(
            maxImageBytes = 1024,
            maxImagesPerMessage = 2,
            maxMessageImageBytes = 2048,
            maxImagePixels = 900_000,
            maxImageDimension = 1000
        )
        assertTrue(
            DshComposerPolicy.validateImage(
                "image/svg+xml", 100, 10, 10, limits, 0, 0, 0
            )!!.contains("不支持")
        )
        assertTrue(
            DshComposerPolicy.validateImage(
                "image/png", 2048, 10, 10, limits, 0, 0, 0
            )!!.contains("单张图片")
        )
        assertTrue(
            DshComposerPolicy.validateImage(
                "image/png", 100, 10, 10, limits, 2, 0, 0
            )!!.contains("最多添加")
        )
        assertTrue(
            DshComposerPolicy.validateImage(
                "image/png", 100, 10, 10, limits, 0, 2000, 2000
            )!!.contains("图片总大小")
        )
        assertTrue(
            DshComposerPolicy.validateImage(
                "image/png", 100, 2000, 10, limits, 0, 0, 0
            )!!.contains("宽高")
        )
        assertTrue(
            DshComposerPolicy.validateImage(
                "image/png", 100, 1000, 1000, limits, 0, 0, 0
            )!!.contains("像素")
        )
        assertNull(
            DshComposerPolicy.validateImage(
                "image/png", 100, 640, 480, limits, 0, 0, 0
            )
        )
    }

    @Test fun batchBudgetStopsBeforeOverAllocating() {
        val budget = DshAttachmentReadBudget(
            maxItems = 10,
            remainingBytes = 50L * 1024 * 1024,
            maxItemBytes = 20 * 1024 * 1024,
            totalLabelBytes = 50L * 1024 * 1024
        )
        assertEquals(20 * 1024 * 1024, budget.limitFor(0, 0))
        assertEquals(20 * 1024 * 1024, budget.limitFor(1, 20L * 1024 * 1024))
        assertEquals(10 * 1024 * 1024, budget.limitFor(2, 40L * 1024 * 1024))
        assertNull(budget.limitFor(3, 50L * 1024 * 1024))
        assertTrue(budget.exhaustionMessage(3).contains("50 MB"))
        val countBudget = budget.copy(maxItems = 2, remainingBytes = 100L * 1024 * 1024)
        assertNull(countBudget.limitFor(2, 0))
        assertTrue(countBudget.exhaustionMessage(2).contains("还能添加 2 个附件"))
    }

    @Test fun mixedDropKeepsFileTypeQuotaAfterImages() {
        val fileBudget = DshAttachmentReadBudget(
            maxItems = 10,
            remainingBytes = 50L * 1024 * 1024,
            maxItemBytes = 20 * 1024 * 1024,
            totalLabelBytes = 50L * 1024 * 1024,
            typeMaxItems = 5,
            typeLabel = "文件"
        )
        val afterFiveImages = fileBudget.afterImages(5, 5L * 1024)
        assertEquals(5, afterFiveImages.maxItems)
        assertEquals(5, afterFiveImages.typeMaxItems)
        assertNotNull(afterFiveImages.limitFor(0, 0))
        assertNull(afterFiveImages.limitFor(5, 0))

        val afterOneImage = fileBudget.afterImages(1, 1024)
        assertEquals(9, afterOneImage.maxItems)
        assertEquals(5, afterOneImage.typeMaxItems)
        assertNotNull(afterOneImage.limitFor(4, 4L * 1024))
        assertNull(afterOneImage.limitFor(5, 5L * 1024))

        val imageBudget = fileBudget.copy(typeMaxItems = 5, typeLabel = "图片")
        assertNull(imageBudget.limitFor(5, 0))
    }

    @Test fun blockedFileBudgetReturnsChineseMessage() {
        val budget = DshAttachmentReadBudget(
            maxItems = 0,
            remainingBytes = 50L * 1024 * 1024,
            maxItemBytes = 20 * 1024 * 1024,
            totalLabelBytes = 50L * 1024 * 1024,
            typeMaxItems = 5,
            typeLabel = "文件"
        )
        assertEquals("还能添加 0 个附件", budget.blockedMessage())
        val noFileQuota = budget.copy(maxItems = 10, typeMaxItems = 0)
        assertTrue(noFileQuota.blockedMessage().orEmpty().contains("文件"))
    }

    @Test fun dragPolicyUsesStartMetadataWithoutClipData() {
        assertTrue(DshComposerDragPolicy.accepts(setOf("text/uri-list")))
        assertTrue(DshComposerDragPolicy.accepts(setOf("image/png")))
        assertFalse(DshComposerDragPolicy.accepts(emptySet()))
    }

    @Test fun validatesFileLimitsWithChineseErrors() {
        assertTrue(
            DshComposerPolicy.validateFile(DshComposerProtocol.MAX_FILE_BYTES + 1, 0, 0)!!.contains("单个文件")
        )
        assertTrue(
            DshComposerPolicy.validateFile(100, DshComposerProtocol.MAX_FILES_PER_MESSAGE, 0)!!.contains("最多添加")
        )
        assertTrue(
            DshComposerPolicy.validateFile(100, 0, DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES.toLong())!!.contains("附件总大小")
        )
        assertNull(DshComposerPolicy.validateFile(100, 0, 0))
    }

    @Test fun mentionQueryHandlesQuotesWhitespaceAndEmailLikeText() {
        val plain = DshComposerPolicy.mentionQuery("hello @src/ma", "hello @src/ma".length)
        assertNotNull(plain)
        assertEquals("src/ma", plain!!.query)
        assertEquals(6, plain.start)
        assertNull(DshComposerPolicy.mentionQuery("mail a@b", "mail a@b".length))
        val quoted = DshComposerPolicy.mentionQuery("x @\"my file", "x @\"my file".length)
        assertNotNull(quoted)
        assertTrue(quoted!!.quoted)
        assertEquals("my file", quoted.query)
        assertNull(DshComposerPolicy.mentionQuery("x @\"my file\" done", "x @\"my file\" done".length))
        val secondLine = DshComposerPolicy.mentionQuery("first line\n@x", "first line\n@x".length)
        assertNotNull(secondLine)
        assertEquals("x", secondLine!!.query)
        assertNull(DshComposerPolicy.mentionQuery("first line @x\ny", "first line @x\ny".length))
    }

    @Test fun slashQueryAndKnownCommandDetection() {
        assertEquals("com", DshComposerPolicy.slashQuery("/com", 4))
        assertNull(DshComposerPolicy.slashQuery("/compact now", 12))
        assertNull(DshComposerPolicy.slashQuery("hello /com", 10))
        val commands = DshComposerProtocol.parseCommands(
            JSONArray().put(JSONObject().put("name", "compact").put("description", "压缩"))
        )
        assertEquals("compact", DshComposerPolicy.commandFor("/compact now", commands)?.name)
        assertNull(DshComposerPolicy.commandFor("/unknown", commands))
        assertNull(DshComposerPolicy.commandFor("普通文本", commands))
    }

    @Test fun sanitizesNamesAndFormatsBytes() {
        assertEquals("a.txt", DshComposerProtocol.sanitizeFileName("../a.txt"))
        assertEquals("a.txt", DshComposerProtocol.sanitizeFileName("C:\\tmp\\a.txt"))
        assertNull(DshComposerProtocol.sanitizeFileName("   "))
        assertNull(DshComposerProtocol.sanitizeFileName("..."))
        assertEquals("512 B", DshComposerPolicy.formatBytes(512))
        assertEquals("1 KB", DshComposerPolicy.formatBytes(1024))
        assertEquals("1.5 KB", DshComposerPolicy.formatBytes(1536))
        assertEquals("2 MB", DshComposerPolicy.formatBytes(2 * 1024 * 1024))
    }

    @Test fun policyUsesEffectiveClientCapsForServerValues() {
        val huge = DshImageLimits(maxImageBytes = 32 * 1024 * 1024, maxMessageImageBytes = 64 * 1024 * 1024)
        assertEquals(DshComposerProtocol.CLIENT_MAX_IMAGE_BYTES, huge.effectiveMaxImageBytes())
        assertEquals(DshComposerProtocol.CLIENT_MAX_MESSAGE_IMAGE_BYTES, huge.effectiveMaxMessageImageBytes())
    }

    private fun assertFails(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte()
        )
        return header + be32(width) + be32(height)
    }

    private fun gif(width: Int, height: Int): ByteArray =
        "GIF89a".toByteArray() + byteArrayOf(
            (width and 0xff).toByte(), (width shr 8).toByte(),
            (height and 0xff).toByte(), (height shr 8).toByte()
        )

    private fun jpeg(width: Int, height: Int): ByteArray {
        val segment = byteArrayOf(
            0xff.toByte(), 0xc0.toByte(), 0x00, 0x11, 0x08,
            (height shr 8).toByte(), (height and 0xff).toByte(),
            (width shr 8).toByte(), (width and 0xff).toByte(),
            0x03, 0x01, 0x11, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01
        )
        return byteArrayOf(0xff.toByte(), 0xd8.toByte()) + segment
    }

    private fun webpVp8x(width: Int, height: Int): ByteArray {
        val data = ByteArray(10)
        val w = width - 1
        val h = height - 1
        data[4] = (w and 0xff).toByte()
        data[5] = ((w shr 8) and 0xff).toByte()
        data[6] = ((w shr 16) and 0xff).toByte()
        data[7] = (h and 0xff).toByte()
        data[8] = ((h shr 8) and 0xff).toByte()
        data[9] = ((h shr 16) and 0xff).toByte()
        val chunk = "VP8X".toByteArray() + le32(10) + data
        return "RIFF".toByteArray() + le32(4 + chunk.size) + "WEBP".toByteArray() + chunk
    }

    private fun be32(value: Int) = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()
    )

    private fun le32(value: Int) = byteArrayOf(
        value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()
    )
}

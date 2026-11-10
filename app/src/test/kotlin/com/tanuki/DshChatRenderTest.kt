package com.tanuki

import com.tanuki.ui.DshAttachmentPolicy
import com.tanuki.ui.DshChatRenderPolicy
import com.tanuki.ui.DshErrorKind
import com.tanuki.ui.DshErrorPresenter
import com.tanuki.ui.DshImageKind
import com.tanuki.ui.DshImagePolicy
import com.tanuki.ui.DshLinkKind
import com.tanuki.ui.DshLinkPolicy
import com.tanuki.ui.DshStreamPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshChatRenderTest {
    @Test fun windowKeepsTailAndReportsHiddenCount() {
        val items = (1..100).toList()
        val window = DshChatRenderPolicy.window(items, limit = 80)
        assertEquals(80, window.items.size)
        assertEquals(21, window.items.first())
        assertEquals(100, window.items.last())
        assertEquals(20, window.hiddenBefore)
        assertTrue(window.hasEarlier)

        val all = DshChatRenderPolicy.window(items.take(20), limit = 80)
        assertEquals(0, all.hiddenBefore)
        assertFalse(all.hasEarlier)
    }

    @Test fun windowLimitIsBoundedAndGrowsInSteps() {
        assertEquals(1, DshChatRenderPolicy.window((1..5).toList(), limit = 0).items.size)
        assertEquals(200, DshChatRenderPolicy.window((1..200).toList(), limit = 1_000).items.size)
        assertEquals(DshChatRenderPolicy.WINDOW_SIZE + DshChatRenderPolicy.WINDOW_STEP, DshChatRenderPolicy.nextWindow(DshChatRenderPolicy.WINDOW_SIZE))
        assertEquals(DshChatRenderPolicy.MAX_WINDOW, DshChatRenderPolicy.nextWindow(DshChatRenderPolicy.MAX_WINDOW))
    }

    @Test fun bottomDetectionNeedsLastItemAndOffset() {
        assertTrue(DshChatRenderPolicy.isAtBottom(9, 10, 1_000, 1_000))
        assertTrue(DshChatRenderPolicy.isAtBottom(9, 10, 1_010, 1_000))
        assertFalse(DshChatRenderPolicy.isAtBottom(8, 10, 1_000, 1_000))
        assertFalse(DshChatRenderPolicy.isAtBottom(9, 10, 1_200, 1_000))
        assertFalse(DshChatRenderPolicy.isAtBottom(0, 0, 0, 0))
    }

    @Test fun followStopsWhileDraggingAndResumesAtBottom() {
        assertFalse(DshChatRenderPolicy.followAfterDrag(dragging = true, atBottom = true, following = true))
        assertTrue(DshChatRenderPolicy.followAfterDrag(dragging = false, atBottom = true, following = false))
        assertFalse(DshChatRenderPolicy.followAfterDrag(dragging = false, atBottom = false, following = false))
        // 程序滚动造成的“未在底部”不改变原来的跟随状态。
        assertTrue(DshChatRenderPolicy.followAfterDrag(dragging = false, atBottom = false, following = true))
    }

    @Test fun streamMergeHandlesCumulativeDeltaDuplicateAndReset() {
        assertEquals("abc", DshStreamPolicy.merge("a", "abc"))
        assertEquals("abcd", DshStreamPolicy.merge("abc", "d"))
        assertEquals("abc", DshStreamPolicy.merge("abc", "bc"))
        assertEquals("abc", DshStreamPolicy.merge("abc", "abc"))
        assertEquals("", DshStreamPolicy.merge("abc", ""))
        assertEquals("新", DshStreamPolicy.merge("", "新"))
        assertTrue(DshStreamPolicy.shouldPublish("新", "旧"))
        assertFalse(DshStreamPolicy.shouldPublish("同", "同"))
        assertFalse(DshStreamPolicy.shouldPublish(null, "旧"))
    }

    @Test fun streamMergeNeverExceedsMessageLimit() {
        val merged = DshStreamPolicy.merge("a".repeat(DshStreamPolicy.MAX_TEXT_CHARS), "增量")
        assertEquals(DshStreamPolicy.MAX_TEXT_CHARS, merged.length)
    }

    @Test fun linkPolicyClassifiesSupportedAndDangerousSchemes() {
        assertEquals(DshLinkKind.WEB, DshLinkPolicy.classify("https://example.invalid/a").kind)
        assertTrue(DshLinkPolicy.classify("http://127.0.0.1:8080").canOpen)
        assertEquals(DshLinkKind.MAIL, DshLinkPolicy.classify("mailto:a@example.invalid").kind)
        assertEquals(DshLinkKind.PHONE, DshLinkPolicy.classify("tel:10086").kind)
        assertEquals(DshLinkKind.ATTACHMENT, DshLinkPolicy.classify("attachment:file-1").kind)
        assertEquals(DshLinkKind.ATTACHMENT, DshLinkPolicy.classify("./docs/readme.md").kind)

        listOf("javascript:alert(1)", "data:text/html,hi", "intent://x", "file:///etc/passwd").forEach { url ->
            val decision = DshLinkPolicy.classify(url)
            assertEquals(DshLinkKind.BLOCKED, decision.kind)
            assertFalse(decision.canOpen)
            assertTrue(decision.problem!!.isNotBlank())
        }
    }

    @Test fun linkPolicyRejectsEmptyLongAndControlCharacters() {
        assertEquals(DshLinkKind.BLOCKED, DshLinkPolicy.classify("   ").kind)
        assertEquals(DshLinkKind.BLOCKED, DshLinkPolicy.classify("https://example.invalid/" + "a".repeat(2_100)).kind)
        val control = "https://example.invalid/\u0001"
        assertEquals(DshLinkKind.BLOCKED, DshLinkPolicy.classify(control).kind)
        assertEquals("https://example.invalid/a", DshLinkPolicy.classify(" <https://example.invalid/a> ").url)
    }

    @Test fun imagePolicyKeepsRemotePlaceholderAndExplainsFailures() {
        val remote = DshImagePolicy.describe("截图", "https://example.invalid/a.png")
        assertEquals(DshImageKind.REMOTE, remote.kind)
        assertFalse(remote.canPreview)
        assertTrue(remote.message.contains("未自动加载"))

        val local = DshImagePolicy.describe("本地", "content://media/1")
        assertEquals(DshImageKind.LOCAL, local.kind)
        assertTrue(local.canPreview)

        val embedded = DshImagePolicy.describe("", "data:image/png;base64,AAAA")
        assertEquals(DshImageKind.EMBEDDED, embedded.kind)
        assertTrue(embedded.canPreview)

        val invalid = DshImagePolicy.describe("坏图", "not a url")
        assertEquals(DshImageKind.INVALID, invalid.kind)
        assertTrue(invalid.message.contains("无法加载"))
    }

    @Test fun attachmentPolicyNeverOpensWorkspaceFilesSilently() {
        val local = DshAttachmentPolicy.describe("报告", "attachment:file-1")
        assertEquals("报告", local.name)
        assertFalse(local.canOpen)
        assertTrue(local.message.contains("授权"))

        val remote = DshAttachmentPolicy.describe("说明", "https://example.invalid/a.txt")
        assertTrue(remote.canOpen)

        val invalid = DshAttachmentPolicy.describe("", "")
        assertTrue(invalid.name.isNotBlank())
        assertTrue(invalid.message.isNotBlank())
    }

    @Test fun errorPresenterMapsCommonFailuresToChineseHint() {
        assertEquals(DshErrorKind.AUTH, DshErrorPresenter.describe("官方接口返回 401").kind)
        assertEquals(DshErrorKind.BUSY, DshErrorPresenter.describe("session/agent-busy").kind)
        assertEquals(DshErrorKind.TIMEOUT, DshErrorPresenter.describe("connect timed out").kind)
        assertEquals(DshErrorKind.NETWORK, DshErrorPresenter.describe("WebSocket 已断开").kind)
        assertEquals(DshErrorKind.PROTOCOL, DshErrorPresenter.describe("官方接口响应格式不正确").kind)
        assertEquals(DshErrorKind.CANCELLED, DshErrorPresenter.describe("操作已取消").kind)
        val unknown = DshErrorPresenter.describe(null)
        assertEquals(DshErrorKind.UNKNOWN, unknown.kind)
        assertEquals("未知错误", unknown.detail)
        assertTrue(unknown.hint.isNotBlank())
        assertTrue(DshErrorPresenter.describe("会话已结束").detail.contains("会话已结束"))
    }
}

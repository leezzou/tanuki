package com.tanuki

import com.tanuki.ui.DshChatAnchor
import com.tanuki.ui.DshChatAnchorPolicy
import com.tanuki.ui.DshChatWindowPolicy
import com.tanuki.ui.DshMessageLedger
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolStatus
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshRiskAssessment
import com.tanuki.web.DshRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 聊天列表锚定、向前分页、内存窗口与消息账本的纯策略回归。
 * 设备/Compose 手势未覆盖，这里只验证可确定性复算的部分。
 */
class DshChatWindowTest {
    private fun message(seq: Int, text: String = "m$seq", role: DshMessageRole = DshMessageRole.ASSISTANT) =
        DshMessage(seq = seq, role = role, text = text, time = seq.toLong())

    private fun tool(seq: Int) = DshToolItem(
        seq = seq,
        time = seq.toLong(),
        callId = "call-$seq",
        name = "Bash",
        arguments = null,
        status = DshToolStatus.RUNNING,
        output = null,
        error = null,
        command = null,
        cwd = null,
        diffs = emptyList(),
        files = emptyList(),
        outputTruncated = false,
        risk = DshRiskAssessment(DshRiskLevel.LOW, "只读"),
        startedAt = seq.toLong()
    )

    private fun keyOf(item: DshTimelineItem): String = when (item) {
        is DshTimelineItem.Text -> "message-${item.message.seq}"
        is DshTimelineItem.Tool -> item.tool.key
    }

    @Test fun anchorSplitPutsAnchorInHistoryAndNewRowsInLiveSegment() {
        val items = (1..5).map { DshTimelineItem.Text(message(it)) }
        val split = DshChatAnchorPolicy.split(items, "message-3") { keyOf(it) }
        assertEquals(listOf(1, 2, 3), split!!.history.map { it.seq })
        assertEquals(listOf(4, 5), split.live.map { it.seq })
        assertTrue(split.hasLive)
        assertNull(DshChatAnchorPolicy.split(items, "message-9") { keyOf(it) })
        assertNull(DshChatAnchorPolicy.split(items, null) { keyOf(it) })
    }

    @Test fun prependingHistoryKeepsAnchorAndLiveSegmentStable() {
        val anchor = DshChatAnchor("message-14", 14)
        val original = (10..15).map { DshTimelineItem.Text(message(it)) }
        val before = DshChatAnchorPolicy.split(original, anchor.key) { keyOf(it) }!!
        // 向前分页只在历史段头部插入，锚点行之后的新消息段不变。
        val older = (5..9).map { DshTimelineItem.Text(message(it)) }
        val after = DshChatAnchorPolicy.split(older + original, anchor.key) { keyOf(it) }!!
        assertEquals(14, after.history.last().seq)
        assertEquals(listOf(15), after.live.map { it.seq })
        assertEquals(before.live, after.live)
        assertEquals(before.history.size + older.size, after.history.size)
    }

    @Test fun restoreIndexFollowsPrependShiftAndRejectsReleasedRows() {
        val original = (10..20).map { "message-$it" }
        val afterPrepend = (1..9).map { "message-$it" } + original
        // 头部插入 9 行后，同一内容的列表索引整体后移；顶部固定提示行另加 1。
        assertEquals(1, DshChatAnchorPolicy.restoreIndex(original, "message-10"))
        assertEquals(10, DshChatAnchorPolicy.restoreIndex(afterPrepend, "message-10"))
        assertEquals(0, DshChatAnchorPolicy.restoreIndex(original, "message-10", leadingRows = 0))
        // 该行已被窗口释放时返回 null，界面不再强行滚动。
        assertNull(DshChatAnchorPolicy.restoreIndex(afterPrepend, "message-99"))
        assertNull(DshChatAnchorPolicy.restoreIndex(afterPrepend, ""))
    }

    @Test fun unseenCountsOnlyDurableTextRows() {
        val pending = DshMessage(
            seq = -1,
            role = DshMessageRole.USER,
            text = "发送中",
            time = 9L,
            pending = true
        )
        val live = listOf(
            DshTimelineItem.Text(message(7)),
            DshTimelineItem.Tool(tool(8)),
            DshTimelineItem.Text(message(9, role = DshMessageRole.USER)),
            DshTimelineItem.Text(pending)
        )
        assertEquals(2, DshChatAnchorPolicy.unseenMessages(live))
        assertEquals(0, DshChatAnchorPolicy.unseenMessages(emptyList()))
    }

    @Test fun detachOnlyOnUserScrollAwayFromBottom() {
        // 用户上滑且已离开底部：立即冻结锚点。
        assertTrue(
            DshChatAnchorPolicy.shouldDetachOnUserScroll(
                userInput = true, delta = -24f, atBottom = false, following = true
            )
        )
        // 仍在底部的小幅回弹不脱离，避免出现没有新消息的胶囊。
        assertFalse(
            DshChatAnchorPolicy.shouldDetachOnUserScroll(
                userInput = true, delta = -4f, atBottom = true, following = true
            )
        )
        // 程序滚动（自动贴底/恢复视口）不能误判成用户上翻。
        assertFalse(
            DshChatAnchorPolicy.shouldDetachOnUserScroll(
                userInput = false, delta = -80f, atBottom = false, following = true
            )
        )
        assertFalse(
            DshChatAnchorPolicy.shouldDetachOnUserScroll(
                userInput = true, delta = 0f, atBottom = false, following = true
            )
        )
        // 已经脱离后重复手势不再改写锚点。
        assertFalse(
            DshChatAnchorPolicy.shouldDetachOnUserScroll(
                userInput = true, delta = -24f, atBottom = false, following = false
            )
        )
    }

    @Test fun trimBoundaryKeepsBudgetAndReleasesOnlyAboveViewport() {
        val messages = (1..501).map { message(it) }
        val exactly = DshChatWindowPolicy.trim(messages.take(500), keepFromSeq = null)
        assertEquals(0, exactly.releasedMessages)
        assertFalse(exactly.trimmed)

        val trimmed = DshChatWindowPolicy.trim(messages, keepFromSeq = null)
        assertEquals(1, trimmed.releasedMessages)
        assertEquals(1, trimmed.cutSeq)
        assertEquals(500, trimmed.messages.size)
        assertEquals(2, trimmed.messages.first().seq)
        assertEquals(501, trimmed.messages.last().seq)
    }

    @Test fun trimNeverReleasesTheRowBeingRead() {
        val messages = (1..600).map { message(it) }
        // 视口顶部在 200：只允许释放 1..199，且最多释放到预算。
        val trimmed = DshChatWindowPolicy.trim(messages, keepFromSeq = 200)
        assertEquals(100, trimmed.releasedMessages)
        assertEquals(100, trimmed.cutSeq)
        assertEquals(500, trimmed.messages.size)
        assertTrue(trimmed.messages.any { it.seq == 200 })
    }

    @Test fun trimAtTopReleasesNothingAndCanStayAboveBudget() {
        val messages = (1..600).map { message(it) }
        val trimmed = DshChatWindowPolicy.trim(messages, keepFromSeq = 1)
        assertEquals(0, trimmed.releasedMessages)
        assertEquals(600, trimmed.messages.size)
    }

    @Test fun trimSkipsPendingPlaceholdersAndKeepsThemLast() {
        val messages = (1..501).map { message(it) } + DshMessage(
            seq = -1,
            role = DshMessageRole.USER,
            text = "发送中",
            time = 9L,
            pending = true
        )
        val trimmed = DshChatWindowPolicy.trim(messages, keepFromSeq = null)
        assertEquals(1, trimmed.releasedMessages)
        assertEquals(501, trimmed.messages.size)
        assertTrue(trimmed.messages.last().pending)
    }

    @Test fun toolTrimFollowsReleasedMessageBoundary() {
        val tools = listOf(tool(5), tool(150), tool(550))
        val messages = (1..10).map { message(it) }
        val trimmed = DshChatWindowPolicy.trim(
            messages = messages,
            tools = tools,
            keepFromSeq = null,
            messageBudget = 5
        )
        assertEquals((6..10).toList(), trimmed.messages.map { it.seq })
        // 与已释放消息同段更早的工具随消息一起释放，较新的工具保留。
        assertEquals(listOf(150, 550), trimmed.tools.map { it.seq })
        assertEquals(1, trimmed.releasedTools)
    }

    @Test fun toolBudgetReleasesOnlyToolsAboveViewport() {
        val tools = (1..250).map { tool(it) }
        val underBudget = listOf(message(300))
        // 视口在 1（顶部）时不释放正在阅读的工具；可以暂时超过预算。
        val atTop = DshChatWindowPolicy.trim(underBudget, tools, keepFromSeq = 1)
        assertEquals(250, atTop.tools.size)
        assertEquals(0, atTop.releasedTools)

        // 没有可用视口（列表尚未布局）或已向下滚过旧工具时，按预算保留最新 200 张。
        val trimmed = DshChatWindowPolicy.trim(underBudget, tools, keepFromSeq = null)
        assertEquals(50, trimmed.releasedTools)
        assertEquals(200, trimmed.tools.size)
        assertEquals(51, trimmed.tools.first().seq)
        assertEquals(250, trimmed.tools.last().seq)
        assertEquals(0, trimmed.releasedMessages)
    }

    @Test fun renderKeysMatchActualListOrderAroundAnchor() {
        val timeline = (1..6).map { "message-$it" }
        val keys = DshChatAnchorPolicy.renderKeys(
            historyKeys = timeline.take(4),
            liveKeys = timeline.drop(4),
            topKey = "history-top",
            anchorKey = "chat-anchor"
        )
        assertEquals(
            listOf(
                "history-top", "message-1", "message-2", "message-3", "message-4",
                "chat-anchor", "message-5", "message-6"
            ),
            keys
        )
        // 锚点前、锚点本身、锚点后的消息都按真实渲染索引恢复。
        assertEquals(3, DshChatAnchorPolicy.restoreIndex(keys, "message-3", leadingRows = 0))
        assertEquals(4, DshChatAnchorPolicy.restoreIndex(keys, "message-4", leadingRows = 0))
        assertEquals(5, DshChatAnchorPolicy.restoreIndex(keys, "chat-anchor", leadingRows = 0))
        assertEquals(6, DshChatAnchorPolicy.restoreIndex(keys, "message-5", leadingRows = 0))
        assertEquals(7, DshChatAnchorPolicy.restoreIndex(keys, "message-6", leadingRows = 0))

        // 跟随时没有锚点行，索引只比时间线多顶部提示。
        val noAnchor = DshChatAnchorPolicy.renderKeys(
            historyKeys = timeline,
            liveKeys = emptyList(),
            topKey = "history-top",
            anchorKey = null
        )
        assertEquals(4, DshChatAnchorPolicy.restoreIndex(noAnchor, "message-4", leadingRows = 0))
    }

    @Test fun oldestSeqAndBeforeSeqUseMessagesAndTools() {
        assertNull(DshChatWindowPolicy.oldestSeq(emptyList(), emptyList()))
        assertEquals(5, DshChatWindowPolicy.oldestSeq(listOf(message(90)), listOf(tool(5))))
        assertEquals(89, DshChatWindowPolicy.beforeSeq(90))
        assertNull(DshChatWindowPolicy.beforeSeq(0))
        assertNull(DshChatWindowPolicy.beforeSeq(null))
    }

    @Test fun mergeDeduplicatesSameSeqAndSortsBySeq() {
        val merged = DshMessageLedger.merge(
            current = listOf(message(2, "旧二"), message(1)),
            incoming = listOf(message(3), message(2, "新二"), message(3))
        )
        assertEquals(listOf(1, 2, 3), merged.map { it.seq })
        assertEquals("新二", merged[1].text)
        assertEquals(3, merged.size)
        // 幂等：同一批事件再次投递不新增行。
        assertEquals(merged, DshMessageLedger.merge(merged, listOf(message(2, "新二"), message(3))))
    }

    @Test fun mergeKeepsPendingUntilOfficialUserMessageArrives() {
        val pending = DshMessage(-1, DshMessageRole.USER, "你好", 9L, pending = true)
        val before = DshMessageLedger.merge(listOf(message(1), pending), emptyList())
        assertEquals(2, before.size)
        assertTrue(before.last().pending)
        val after = DshMessageLedger.merge(before, listOf(message(2, "你好", DshMessageRole.USER)))
        assertEquals(listOf(1, 2), after.map { it.seq })
        assertFalse(after.any { it.pending })
    }

    @Test fun reconcileSnapshotKeepsOlderPagesAndNewerLiveRows() {
        val current = listOf(
            message(1, "更早分页"),
            message(4, "旧快照四"),
            message(5, "旧快照五"),
            message(9, "断线后实时行")
        )
        val snapshot = listOf(message(4, "快照四"), message(5, "快照五"))
        val reconciled = DshMessageLedger.reconcile(current, snapshot)
        assertEquals(listOf(1, 4, 5, 9), reconciled.map { it.seq })
        assertEquals("更早分页", reconciled[0].text)
        assertEquals("快照四", reconciled[1].text)
        assertEquals("断线后实时行", reconciled[3].text)
        // 对账可重复执行，不改变结果、不插入重复行。
        assertEquals(reconciled, DshMessageLedger.reconcile(reconciled, snapshot))
    }

    @Test fun emptySnapshotNeverClearsLoadedHistory() {
        val current = listOf(message(1), message(2))
        assertEquals(current, DshMessageLedger.reconcile(current, emptyList()))
    }

    @Test fun reconcileKeepsLiveRowWithSameSeqAsSnapshotNewerText() {
        val current = listOf(message(7, "实时版本"))
        val snapshot = listOf(message(7, "快照版本"))
        val reconciled = DshMessageLedger.reconcile(current, snapshot)
        assertEquals(1, reconciled.size)
        assertEquals("快照版本", reconciled.single().text)
    }
}

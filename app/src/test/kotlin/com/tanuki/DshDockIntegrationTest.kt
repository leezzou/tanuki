package com.tanuki

import com.tanuki.ui.DshDockKind
import com.tanuki.ui.DshDockLayout
import com.tanuki.ui.DshDockPlacement
import com.tanuki.ui.DshDockRenderPlan
import com.tanuki.ui.DshDockStateMapper
import com.tanuki.ui.GoalDockStatus
import com.tanuki.ui.TodoDockItemStatus
import com.tanuki.web.DshGoalProjection
import com.tanuki.web.DshTodoItemProjection
import com.tanuki.web.DshTodoProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 停靠条接线回归：真实投影 → 纯状态 → 折叠/展开渲染计划，以及输入栏不被停靠条推动。
 * 像素级布局与动画仍需设备验证，这里锁定可确定性复算的契约。
 */
class DshDockIntegrationTest {
    private fun goal(summary: String = "补齐停靠条", status: String? = "running") =
        DshGoalProjection(seq = 1, summary = summary, status = status)

    private fun todo(status: String? = "in_progress") = DshTodoProjection(
        seq = 2,
        items = listOf(
            DshTodoItemProjection("t1", "接线目标条", "completed"),
            DshTodoItemProjection("t2", "跑完整 JVM", status)
        )
    )

    @Test fun goalStatusesMapToComponentStatesAndUnknownHide() {
        assertEquals(GoalDockStatus.RUNNING, DshDockStateMapper.goal(goal(status = "active"))!!.status)
        assertEquals(GoalDockStatus.PAUSED, DshDockStateMapper.goal(goal(status = "paused"))!!.status)
        assertEquals(GoalDockStatus.BLOCKED, DshDockStateMapper.goal(goal(status = "blocked"))!!.status)
        // 已完成/未知状态不能猜成进行中，整条隐藏。
        assertNull(DshDockStateMapper.goal(goal(status = "completed")))
        assertNull(DshDockStateMapper.goal(goal(status = null)))
        assertNull(DshDockStateMapper.goal(goal(summary = "   ")))
        assertNull(DshDockStateMapper.goal(null))
    }

    @Test fun todoStatusesMapAndEmptyListHidesBar() {
        val state = DshDockStateMapper.todo(todo())!!
        assertEquals(2, state.total)
        assertEquals(1, state.completed)
        assertEquals(TodoDockItemStatus.COMPLETED, state.items[0].status)
        assertEquals(TodoDockItemStatus.IN_PROGRESS, state.items[1].status)
        assertEquals(50, state.progressPercent)

        assertNull(DshDockStateMapper.todo(null))
        assertNull(DshDockStateMapper.todo(DshTodoProjection(3, emptyList())))
        assertNull(
            DshDockStateMapper.todo(
                DshTodoProjection(4, listOf(DshTodoItemProjection("blank", "   ")))
            )
        )
    }

    @Test fun threeRealDocksCollapseThenExpandWithoutDuplicates() {
        val goal = DshDockStateMapper.goal(goal())
        val todo = DshDockStateMapper.todo(todo())
        assertTrue(goal != null && todo != null)

        val collapsed = DshDockLayout.resolve(
            approvalVisible = true,
            goalVisible = goal != null,
            todoVisible = todo != null
        )
        assertEquals(listOf(DshDockKind.APPROVAL, DshDockKind.GOAL), collapsed.visible)
        assertEquals(listOf(DshDockKind.TODO), collapsed.folded)
        assertEquals(1, collapsed.overflowCount)
        val collapsedPlan = DshDockRenderPlan.of(collapsed)
        assertEquals(collapsedPlan.bars.size, collapsedPlan.bars.toSet().size)

        val expanded = DshDockLayout.resolve(
            approvalVisible = true,
            goalVisible = goal != null,
            todoVisible = todo != null,
            overflowExpanded = true
        )
        val plan = DshDockRenderPlan.of(expanded)
        assertEquals(
            listOf(DshDockKind.APPROVAL, DshDockKind.GOAL, DshDockKind.TODO),
            plan.bars
        )
        // 展开后每条只出现一次，不会同时从常驻和折叠两路渲染。
        assertEquals(plan.bars.size, plan.bars.toSet().size)
        assertEquals(1, plan.overflowCount)
    }

    @Test fun missingGoalOrTodoDoesNotCreateAnEmptySlot() {
        val todoOnly = DshDockLayout.resolve(
            approvalVisible = false,
            goalVisible = false,
            todoVisible = true
        )
        assertEquals(listOf(DshDockKind.TODO), todoOnly.visible)
        assertEquals(0, todoOnly.overflowCount)
        assertTrue(
            DshDockLayout.resolve(
                approvalVisible = false,
                goalVisible = false,
                todoVisible = false
            ).visible.isEmpty()
        )
    }

    @Test fun composerTopStaysFixedWhileDockHeightChanges() {
        val total = 2_400
        val composer = 360
        // 停靠条 0/1/2/3 条时高度变化只能压缩消息区，输入栏顶部位置不变。
        val composerTops = listOf(0, 120, 240, 360).map {
            DshDockPlacement.composerTopPx(total, composer)
        }
        assertEquals(1, composerTops.toSet().size)
        assertEquals(total - composer, composerTops.first())

        val viewports = listOf(0, 120, 240, 360).map {
            DshDockPlacement.messageViewportPx(total, 400, it, composer)
        }
        assertEquals(viewports.sortedDescending(), viewports)
        assertEquals(total - 400 - 360, viewports.first())
        assertFalse(DshDockPlacement.messageViewportPx(total, 400, 4_000, composer) < 0)
    }
}

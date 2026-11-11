package com.tanuki.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 文件阅读页底部操作栏、传输浮层与横屏紧凑布局的共享尺寸。
 *
 * 操作栏和传输胶囊同为 48dp；胶囊若只按导航 inset + 12dp 定位，会压住操作栏约 36dp，
 * 导致“导出此文件”“复制到工作区”等按钮无法点击。这里把避让距离集中成可单测的纯计算。
 *
 * 横屏只保留一条 48dp 顶部栏、一行 20dp 编码提示和一条 48dp 底部工具条，其余高度全部给正文；
 * 竖屏继续使用原有布局，不进入紧凑分支。
 */
internal object FileViewerLayout {
    /** 底部操作栏的最小高度，与查看器操作按钮的 48dp 触控区一致。 */
    val ActionBarHeight: Dp = 48.dp

    /** 传输胶囊自身高度。 */
    val TransferPillHeight: Dp = 48.dp

    /** 悬浮控件与相邻内容之间的间距。 */
    val FloatingGap: Dp = 12.dp

    /** 横屏顶部栏：返回、文件名、Aa 工具入口和编辑共用一行。 */
    val CompactHeaderHeight: Dp = 48.dp

    /** 横屏编码提示折叠为一行小字；关闭提示后这 24dp 归还正文（1.3x 字体也不裁行）。 */
    val CompactEncodingHintHeight: Dp = 24.dp

    /** 横屏底部工具条：上一段/下一段、导出与更多操作共用一条 48dp。 */
    val CompactBottomBarHeight: Dp = 48.dp

    /** 横屏正文至少占查看器可用高度的 60%。 */
    const val CompactMinContentFraction: Float = 0.60f

    /**
     * 横屏判定使用实际可用宽高而不是固定机型；分屏、平板和折叠屏同样按宽大于高进入紧凑布局。
     */
    fun isCompact(width: Dp, height: Dp): Boolean = width > height

    /**
     * 计算横屏查看器的高度预算。
     *
     * @param availableHeight 已扣除系统栏 inset 的查看器可用高度
     * @param showEncodingHint 当前是否显示编码提示行
     */
    fun compactHeightBudget(availableHeight: Dp, showEncodingHint: Boolean): ViewerHeightBudget {
        val total = availableHeight.coerceAtLeast(0.dp)
        val hint = if (showEncodingHint) CompactEncodingHintHeight else 0.dp
        val content = (total - CompactHeaderHeight - hint - CompactBottomBarHeight).coerceAtLeast(0.dp)
        return ViewerHeightBudget(total, CompactHeaderHeight, hint, CompactBottomBarHeight, content)
    }

    /**
     * 极短横屏显示编码提示会跌破 60% 时先收起提示；当前编码仍会显示在底部工具条，
     * 用户仍可从 Aa 面板重新打开编码菜单。常见横屏高度不受影响。
     */
    fun shouldShowCompactEncodingHint(viewerHeight: Dp, hasEncodingHint: Boolean): Boolean =
        hasEncodingHint && compactHeightBudget(viewerHeight, showEncodingHint = true)
            .contentFraction >= CompactMinContentFraction

    /**
     * 传输胶囊底边距窗口底部的距离。
     *
     * @param bottomInset 系统导航栏等底部安全 inset
     * @param actionBarVisible 阅读页底部操作栏是否可见；列表页或全屏阅读时为 false
     */
    fun transferPillBottomPadding(bottomInset: Dp, actionBarVisible: Boolean): Dp =
        bottomInset + FloatingGap + if (actionBarVisible) ActionBarHeight else 0.dp
}

/** 横屏高度预算的纯数据结果，供界面与 JVM 回归共用。 */
internal data class ViewerHeightBudget(
    val total: Dp,
    val header: Dp,
    val hint: Dp,
    val bottomBar: Dp,
    val content: Dp,
) {
    val contentFraction: Float
        get() = if (total.value <= 0f) 0f else content.value / total.value
}

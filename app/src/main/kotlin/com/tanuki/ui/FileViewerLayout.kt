package com.tanuki.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 文件阅读页底部操作栏与传输浮层的共享尺寸。
 *
 * 操作栏和传输胶囊同为 48dp；胶囊若只按导航 inset + 12dp 定位，会压住操作栏约 36dp，
 * 导致“导出此文件”“复制到工作区”等按钮无法点击。这里把避让距离集中成可单测的纯计算。
 */
internal object FileViewerLayout {
    /** 底部操作栏的最小高度，与查看器操作按钮的 48dp 触控区一致。 */
    val ActionBarHeight: Dp = 48.dp

    /** 传输胶囊自身高度。 */
    val TransferPillHeight: Dp = 48.dp

    /** 悬浮控件与相邻内容之间的间距。 */
    val FloatingGap: Dp = 12.dp

    /**
     * 传输胶囊底边距窗口底部的距离。
     *
     * @param bottomInset 系统导航栏等底部安全 inset
     * @param actionBarVisible 阅读页底部操作栏是否可见；列表页或全屏阅读时为 false
     */
    fun transferPillBottomPadding(bottomInset: Dp, actionBarVisible: Boolean): Dp =
        bottomInset + FloatingGap + if (actionBarVisible) ActionBarHeight else 0.dp
}

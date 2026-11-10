package com.tanuki.ui

import com.tanuki.storage.FileLocation
import com.tanuki.storage.WorkspaceFileManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/** 一次列表显示请求：来源目录、原始条目与筛选/排序条件。 */
internal class FileDisplayRequest(
    val source: String,
    val entries: List<WorkspaceFileManager.Entry>,
    val query: String,
    val hidden: Boolean,
    val sort: Int,
)

/** 已完成的列表结果；request 用于判断是否落后于最新请求。 */
internal class FileDisplayResult(val request: FileDisplayRequest, val entries: List<WorkspaceFileManager.Entry>)

/**
 * 文件列表显示策略。抽成纯 Kotlin 后可在宿主 JVM 回归：
 * 同一目录刷新先保留旧列表（不闪空、不重置滚动），换目录必须清空旧结果，
 * 内容未变化时复用旧条目列表以保持列表项身份稳定。
 */
internal object FileListPresentation {
    fun source(location: FileLocation, directory: String): String = "${location.name}:$directory"

    fun isStale(previous: FileDisplayResult?, request: FileDisplayRequest): Boolean = previous?.request !== request

    fun visible(previous: FileDisplayResult?, request: FileDisplayRequest): List<WorkspaceFileManager.Entry> = when {
        previous == null -> emptyList()
        previous.request === request -> previous.entries
        // 同一目录的刷新、搜索或排序：旧条目保留到新结果就绪，避免列表闪成空列表后滚动归零。
        previous.request.source == request.source -> previous.entries
        // 换目录或换位置：绝不能把上一目录的筛选结果当作新目录内容显示。
        else -> emptyList()
    }

    /** 内容未变化时复用旧列表实例，LazyColumn/Grid 的 key 与条目对象保持稳定。 */
    fun result(previous: FileDisplayResult?, next: FileDisplayResult): FileDisplayResult =
        if (previous != null && previous.entries == next.entries) FileDisplayResult(next.request, previous.entries) else next

    /** 后台筛选与排序；长目录按固定间隔检查取消，避免离开页面后继续占用 CPU。 */
    suspend fun prepare(request: FileDisplayRequest): List<WorkspaceFileManager.Entry> {
        val context = currentCoroutineContext()
        val visible = ArrayList<WorkspaceFileManager.Entry>(request.entries.size)
        for ((index, entry) in request.entries.withIndex()) {
            if (index % 256 == 0) context.ensureActive()
            if ((request.hidden || !entry.name.startsWith('.')) && entry.name.contains(request.query, ignoreCase = true)) visible += entry
        }
        val ordering = when (request.sort) {
            1 -> compareByDescending<WorkspaceFileManager.Entry> { it.modified }
            2 -> compareByDescending { it.bytes }
            else -> compareBy { it.name.lowercase(Locale.ROOT) }
        }
        val comparator = compareByDescending<WorkspaceFileManager.Entry> { it.directory }.then(ordering).thenBy { it.name }
        var comparisons = 0
        visible.sortWith { left, right ->
            if (comparisons++ % 256 == 0) context.ensureActive()
            comparator.compare(left, right)
        }
        context.ensureActive()
        return visible.toList()
    }
}

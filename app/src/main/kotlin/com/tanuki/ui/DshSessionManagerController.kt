package com.tanuki.ui

import com.tanuki.runtime.Redact
import com.tanuki.web.DshAdminSession
import com.tanuki.web.DshApiException
import com.tanuki.web.DshCommand
import com.tanuki.web.DshModelCatalog
import com.tanuki.web.DshModelSelection
import com.tanuki.web.DshPermissionCatalog
import com.tanuki.web.DshSessionAdminSource
import com.tanuki.web.DshSessionSearchHit
import com.tanuki.web.DshSkill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

/** 单次批量归档上限；超过上限时只处理前 200 个，避免一次点击制造不可控的串行网络请求。 */
private const val MAX_BATCH_ARCHIVE = 200

/** 会话列表筛选；未读是本机记录，不是官方字段。 */
enum class DshSessionFilter(val label: String) {
    ALL("全部"), RUNNING("运行中"), UNREAD("未读")
}

/** 纯过滤函数，便于单测；标题或会话标识命中即可。 */
object DshSessionFiltering {
    fun visible(
        sessions: List<DshAdminSession>,
        query: String,
        filter: DshSessionFilter,
        unreadIds: Set<String>,
        pinnedIds: Set<String> = emptySet()
    ): List<DshAdminSession> {
        val text = query.trim()
        return sessions.asSequence()
            .filter { session ->
                val matches = text.isEmpty() ||
                    session.displayTitle.contains(text, ignoreCase = true) ||
                    session.id.contains(text, ignoreCase = true)
                matches && when (filter) {
                    DshSessionFilter.ALL -> true
                    DshSessionFilter.RUNNING -> session.running
                    DshSessionFilter.UNREAD -> session.id in unreadIds
                }
            }
            // 置顶是本机显示偏好：只影响本页顺序，不写回官方会话。
            .sortedWith(
                compareByDescending<DshAdminSession> { it.id in pinnedIds }
                    .thenByDescending { it.updatedAt }
            )
            .toList()
    }
}

/** 本机“最后查看时间”存储；只用于未读提示，不写回官方会话。 */
interface DshReadStore {
    fun lastRead(sessionId: String): Long
    fun markRead(sessionId: String, updatedAt: Long)
}

/**
 * 本机置顶存储。
 *
 * 置顶只改变原生列表顺序，不是官方会话字段；存储失败时页面会明确提示，
 * 不能把未保存的置顶状态继续当成已生效。
 */
interface DshSessionPinStore {
    fun pinnedIds(): Set<String>
    fun setPinned(sessionId: String, pinned: Boolean)
}

/** 不提供本机置顶时的空实现；测试和只读场景可直接使用。 */
object NoopDshSessionPinStore : DshSessionPinStore {
    override fun pinnedIds(): Set<String> = emptySet()
    override fun setPinned(sessionId: String, pinned: Boolean) = Unit
}

/** 模型/权限切换失败；绑定发起会话，避免延迟失败串到其他会话面板。 */
data class DshSelectionFailure(val sessionId: String, val message: String)

data class DshSessionManagerState(
    val available: Boolean = false,
    val loading: Boolean = false,
    /** 已有列表时的下拉/手动刷新；与首次加载分开，避免整页闪空。 */
    val refreshing: Boolean = false,
    val sessions: List<DshAdminSession> = emptyList(),
    val query: String = "",
    val filter: DshSessionFilter = DshSessionFilter.ALL,
    val unreadIds: Set<String> = emptySet(),
    /** 本机置顶；排序时优先，刷新后保留。 */
    val pinnedIds: Set<String> = emptySet(),
    /** 批量归档进度；为 0 时表示没有进行中的批量操作。 */
    val batchDone: Int = 0,
    val batchTotal: Int = 0,
    val contentHits: List<DshSessionSearchHit> = emptyList(),
    val contentHasMore: Boolean = false,
    val contentSearching: Boolean = false,
    val contentSearched: Boolean = false,
    val busy: Boolean = false,
    /** 写操作失败；与列表读取失败分开，避免错误态被误判。 */
    val error: String? = null,
    /** 官方会话列表读取失败；有旧列表时保留旧列表并提示。 */
    val loadError: String? = null,
    val notice: String? = null,
    val selectedId: String? = null,
    val models: DshModelCatalog? = null,
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    /** 模型切换失败的中文原因，只在对应会话的模型面板展示。 */
    val modelSelectionError: DshSelectionFailure? = null,
    val permissions: DshPermissionCatalog? = null,
    val permissionsLoading: Boolean = false,
    val permissionsError: String? = null,
    /** 权限切换失败的中文原因，只在对应会话的权限面板展示。 */
    val permissionSelectionError: DshSelectionFailure? = null,
    val skills: List<DshSkill> = emptyList(),
    val commands: List<DshCommand> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    /** 安全模式/试用模式下禁止写操作，仅保留只读目录。 */
    val readOnly: Boolean = false,
    val hasLoaded: Boolean = false
) {
    val visibleSessions: List<DshAdminSession>
        get() = DshSessionFiltering.visible(sessions, query, filter, unreadIds, pinnedIds)

    val selected: DshAdminSession?
        get() = sessions.firstOrNull { it.id == selectedId }

    val canWrite: Boolean get() = !readOnly && !busy

    /** 首次加载失败且没有任何可展示旧数据；页面据此显示错误态而不是空态。 */
    val initialLoadFailed: Boolean
        get() = !loading && sessions.isEmpty() && !hasLoaded && loadError != null
}

/**
 * 原生会话管理状态机。
 *
 * 会话标题、运行状态、模型与权限都来自官方接口；未读仅在本机记录最后查看的
 * `updatedAt`。列表失败不回退成假数据；写操作在安全模式下直接拒绝。
 */
class DshSessionManagerController(
    private val source: DshSessionAdminSource,
    private val readStore: DshReadStore,
    private val scope: CoroutineScope,
    private val pinStore: DshSessionPinStore = NoopDshSessionPinStore
) {
    private val _state = MutableStateFlow(
        DshSessionManagerState(pinnedIds = runCatching { pinStore.pinnedIds() }.getOrDefault(emptySet()))
    )
    val state: StateFlow<DshSessionManagerState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var actionJob: Job? = null
    private var searchJob: Job? = null
    private var modelJob: Job? = null
    private var permissionJob: Job? = null
    private var catalogJob: Job? = null

    /** 当前会话管理页绑定的实际 loopback origin；端点变化时清空目录缓存。 */
    private var endpointIdentity: String? = null
    private var listGeneration = 0
    private var searchGeneration = 0
    private var modelGeneration = 0
    private var permissionGeneration = 0
    private var catalogGeneration = 0
    private var pendingSelection: String? = null

    fun setReadOnly(value: Boolean) {
        _state.update { it.copy(readOnly = value) }
    }

    fun refresh() {
        // 写操作进行中不叠加列表刷新，避免旧读取覆盖操作后的新列表。
        if (_state.value.busy) return
        val token = ++listGeneration
        listJob?.cancel()
        val hasContent = _state.value.sessions.isNotEmpty()
        // 有旧列表时只显示下拉刷新，不清空列表；首次读取才显示整页加载态。
        _state.update {
            it.copy(
                loading = !hasContent,
                refreshing = hasContent,
                error = null,
                loadError = null,
                notice = null
            )
        }
        listJob = scope.launch {
            val result = source.sessions()
            if (token != listGeneration) return@launch
            result.fold(
                onSuccess = { sessions ->
                    val target = pendingSelection
                    pendingSelection = null
                    if (target != null && sessions.none { it.id == target }) {
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                sessions = sessions,
                                unreadIds = unreadOf(sessions),
                                available = true,
                                hasLoaded = true,
                                selectedId = it.selectedId?.takeIf { id -> sessions.any { session -> session.id == id } },
                                loadError = null,
                                error = "目标会话不在当前列表，可能已归档"
                            )
                        }
                    } else {
                        publishSessions(
                            sessions = sessions,
                            selectedId = target ?: _state.value.selectedId,
                            notice = null,
                            keepError = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            loadError = messageOf(error)
                        )
                    }
                }
            )
        }
    }

    fun setQuery(query: String) {
        val next = query.take(200)
        val changed = next != _state.value.query
        if (changed) {
            // 查询词变化后旧结果不再属于当前条件，直接丢弃在途全文搜索。
            searchGeneration++
            searchJob?.cancel()
        }
        _state.update { state ->
            state.copy(
                query = next,
                contentHits = if (next.isBlank() || changed) emptyList() else state.contentHits,
                contentHasMore = if (next.isBlank() || changed) false else state.contentHasMore,
                contentSearching = if (changed) false else state.contentSearching,
                contentSearched = if (next.isBlank() || changed) false else state.contentSearched
            )
        }
    }

    fun setFilter(filter: DshSessionFilter) {
        _state.update { it.copy(filter = filter) }
    }

    /**
     * 切换本机置顶。
     *
     * 置顶不是官方写操作，安全模式下仍可调整本机顺序；但存储失败必须回退并
     * 提示，不能显示一个并未保存的置顶标记。
     */
    fun togglePinned(sessionId: String) {
        if (!validId(sessionId)) return
        val next = sessionId !in _state.value.pinnedIds
        val stored = runCatching { pinStore.setPinned(sessionId, next) }
        if (stored.isFailure) {
            _state.update { it.copy(error = "置顶保存失败：${messageOf(stored.exceptionOrNull()!!)}") }
            return
        }
        val pinned = runCatching { pinStore.pinnedIds() }.getOrDefault(_state.value.pinnedIds)
        _state.update {
            it.copy(
                pinnedIds = pinned,
                notice = if (next) "已置顶（仅本机，不写回官方）" else "已取消置顶",
                error = null
            )
        }
    }

    /** 打开会话详情并记为本机已读；目标不在列表时等待刷新后再选中。 */
    fun openSession(sessionId: String) {
        if (!validId(sessionId)) return
        val session = _state.value.sessions.firstOrNull { it.id == sessionId }
        if (session == null) {
            pendingSelection = sessionId
            refresh()
            return
        }
        markRead(session)
        _state.update { it.copy(selectedId = sessionId) }
    }

    fun closeDetail() {
        catalogJob?.cancel()
        catalogGeneration++
        _state.update {
            it.copy(
                selectedId = null,
                skills = emptyList(),
                commands = emptyList(),
                catalogLoading = false,
                catalogError = null,
                modelSelectionError = null,
                permissionSelectionError = null
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, loadError = null, notice = null) }
    }

    fun createSession(title: String = "") {
        if (!beginAction()) return
        val clean = title.trim()
        if (!validTitle(clean, allowEmpty = true)) {
            failAction(IllegalArgumentException("标题不能超过 200 字或包含控制字符"))
            return
        }
        actionJob = scope.launch {
            source.createSession().fold(
                onSuccess = { id ->
                    var notice = "已创建会话"
                    if (clean.isNotEmpty()) {
                        source.renameSession(id, clean).fold(
                            onSuccess = { notice = "已创建并命名会话" },
                            onFailure = { notice = "会话已创建，但重命名失败：${messageOf(it)}" }
                        )
                    }
                    readStore.markRead(id, System.currentTimeMillis())
                    loadAfterAction(selectedId = id, notice = notice)
                },
                onFailure = { failAction(it) }
            )
        }
    }

    fun renameSession(sessionId: String, title: String) {
        if (!validId(sessionId) || !beginAction()) return
        val clean = title.trim()
        if (!validTitle(clean, allowEmpty = false)) {
            failAction(IllegalArgumentException("标题不能为空、不能超过 200 字或包含控制字符"))
            return
        }
        actionJob = scope.launch {
            source.renameSession(sessionId, clean).fold(
                onSuccess = { loadAfterAction(selectedId = sessionId, notice = "已重命名") },
                onFailure = { failAction(it) }
            )
        }
    }

    /**
     * 归档会话。运行中的会话需要显式 `stopActivity=true`，由官方先请求停止
     * 当前回合/子代理/任务，再提交归档；失败时保留列表并提示原因。
     */
    fun archiveSession(sessionId: String, stopActivity: Boolean) {
        if (!validId(sessionId) || !beginAction()) return
        actionJob = scope.launch {
            source.archiveSession(sessionId, stopActivity).fold(
                onSuccess = { archived ->
                    // 归档响应已确认的会话即使列表刷新稍慢也不再显示，避免“已归档但仍可点”。
                    loadAfterAction(
                        selectedId = null,
                        notice = if (stopActivity) "已停止运行并归档" else "已归档",
                        excludeIds = archived.toSet()
                    )
                },
                onFailure = { failAction(it) }
            )
        }
    }

    /**
     * 批量归档。
     *
     * 官方没有批量端点，因此逐个提交 `workspace/archiveSession`；运行中的会话在
     * `stopRunning=true` 时先请求停止。部分失败不回滚已成功的归档，列表刷新后
     * 保留未归档项，并给出失败数量与短原因。硬删除没有端点，本方法不伪造删除。
     */
    fun archiveSessions(sessionIds: Collection<String>, stopRunning: Boolean) {
        val ids = sessionIds.filter(::validId).distinct().take(MAX_BATCH_ARCHIVE)
        if (ids.isEmpty()) return
        if (!beginAction()) return
        val runningById = _state.value.sessions.associate { it.id to it.running }
        _state.update { it.copy(batchDone = 0, batchTotal = ids.size) }
        actionJob = scope.launch {
            val archived = LinkedHashSet<String>()
            val failures = mutableListOf<String>()
            ids.forEachIndexed { index, id ->
                val stop = stopRunning && runningById[id] == true
                source.archiveSession(id, stop).fold(
                    onSuccess = { result -> archived += result.ifEmpty { listOf(id) } },
                    onFailure = { failures += "${id.take(8)}：${messageOf(it)}" }
                )
                _state.update { it.copy(batchDone = index + 1) }
            }
            val notice = if (failures.isEmpty()) {
                "已归档 ${archived.size} 个会话"
            } else {
                "已归档 ${archived.size} 个会话，${failures.size} 个失败"
            }
            loadAfterAction(selectedId = null, notice = notice, excludeIds = archived)
            _state.update { it.copy(batchDone = 0, batchTotal = 0) }
            if (failures.isNotEmpty()) {
                val summary = failures.take(3).joinToString("；")
                val suffix = if (failures.size > 3) "…" else ""
                _state.update { it.copy(error = "未归档：$summary$suffix") }
            }
        }
    }

    fun searchContent() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(contentHits = emptyList(), contentHasMore = false, contentSearching = false, contentSearched = false) }
            return
        }
        val token = ++searchGeneration
        searchJob?.cancel()
        _state.update { it.copy(contentSearching = true, error = null) }
        searchJob = scope.launch {
            source.search(query).fold(
                onSuccess = { result ->
                    if (token != searchGeneration) return@fold
                    _state.update {
                        it.copy(
                            contentSearching = false,
                            contentSearched = true,
                            contentHits = result.items,
                            contentHasMore = result.hasMore
                        )
                    }
                },
                onFailure = { error ->
                    if (token != searchGeneration) return@fold
                    // 搜索失败时不能显示“没有匹配”，否则会把错误说成空结果。
                    _state.update { it.copy(contentSearching = false, contentSearched = false, error = messageOf(error)) }
                }
            )
        }
    }

    fun clearContentSearch() {
        searchGeneration++
        searchJob?.cancel()
        _state.update { it.copy(contentHits = emptyList(), contentHasMore = false, contentSearching = false, contentSearched = false) }
    }

    fun loadModels(force: Boolean = false) {
        if (!force && (_state.value.models != null || _state.value.modelsLoading)) return
        val token = ++modelGeneration
        modelJob?.cancel()
        _state.update { it.copy(modelsLoading = true, modelsError = null, modelSelectionError = null) }
        modelJob = scope.launch {
            source.models().fold(
                onSuccess = { catalog ->
                    if (token != modelGeneration) return@fold
                    _state.update { it.copy(modelsLoading = false, models = catalog, modelsError = null) }
                },
                onFailure = { error ->
                    if (token != modelGeneration) return@fold
                    _state.update { it.copy(modelsLoading = false, modelsError = messageOf(error)) }
                }
            )
        }
    }

    fun loadPermissions(force: Boolean = false) {
        if (!force && (_state.value.permissions != null || _state.value.permissionsLoading)) return
        val token = ++permissionGeneration
        permissionJob?.cancel()
        _state.update { it.copy(permissionsLoading = true, permissionsError = null, permissionSelectionError = null) }
        permissionJob = scope.launch {
            source.permissions().fold(
                onSuccess = { catalog ->
                    if (token != permissionGeneration) return@fold
                    _state.update { it.copy(permissionsLoading = false, permissions = catalog, permissionsError = null) }
                },
                onFailure = { error ->
                    if (token != permissionGeneration) return@fold
                    _state.update { it.copy(permissionsLoading = false, permissionsError = messageOf(error)) }
                }
            )
        }
    }

    fun loadSessionCatalog(sessionId: String) {
        if (!validId(sessionId)) return
        val token = ++catalogGeneration
        catalogJob?.cancel()
        _state.update {
            it.copy(
                catalogLoading = true,
                catalogError = null,
                skills = emptyList(),
                commands = emptyList()
            )
        }
        catalogJob = scope.launch {
            val skillsResult = source.skills(sessionId)
            val commandsResult = source.commands(sessionId)
            if (token != catalogGeneration) return@launch
            val skills = skillsResult.getOrNull()
            val commands = commandsResult.getOrNull()
            val error = when {
                skills == null && commands == null -> messageOf(skillsResult.exceptionOrNull() ?: commandsResult.exceptionOrNull()!!)
                skills == null -> "技能目录读取失败：${messageOf(skillsResult.exceptionOrNull()!!)}"
                commands == null -> "命令目录读取失败：${messageOf(commandsResult.exceptionOrNull()!!)}"
                else -> null
            }
            _state.update {
                it.copy(
                    catalogLoading = false,
                    skills = skills.orEmpty(),
                    commands = commands.orEmpty(),
                    catalogError = error
                )
            }
        }
    }

    fun selectModel(sessionId: String, selection: DshModelSelection) {
        if (!validId(sessionId) || !beginAction()) return
        _state.update { it.copy(modelSelectionError = null) }
        actionJob = scope.launch {
            source.selectModel(sessionId, selection).fold(
                onSuccess = { loadAfterAction(selectedId = sessionId, notice = "已切换模型") },
                onFailure = { error ->
                    val message = messageOf(error)
                    _state.update {
                        it.copy(
                            busy = false,
                            loading = false,
                            refreshing = false,
                            modelSelectionError = DshSelectionFailure(sessionId, message),
                            // 面板可能已关闭或切到别的会话；全局提示仍绑定发起会话。
                            error = "会话 ${sessionId.take(8)} 模型切换失败：$message"
                        )
                    }
                }
            )
        }
    }

    /**
     * 当前会话权限通过官方 `/permission` 命令切换，不直接写 sandbox/approval
     * 事件。完全权限必须由界面二次确认后才会走到这里。
     */
    fun selectPermission(sessionId: String, preset: String, allowFullAccess: Boolean = false) {
        if (!validId(sessionId) || !beginAction()) return
        if (!preset.matches(Regex("[a-z0-9-]{1,64}")) || preset == "custom") {
            failAction(IllegalArgumentException("权限模式不可选择"))
            return
        }
        if (preset == "danger-full-access" && !allowFullAccess) {
            failAction(IllegalArgumentException("完全权限需要用户明确确认"))
            return
        }
        _state.update { it.copy(permissionSelectionError = null) }
        actionJob = scope.launch {
            source.selectPermission(sessionId, preset).fold(
                onSuccess = { loadAfterAction(selectedId = sessionId, notice = "已切换权限模式") },
                onFailure = { error ->
                    val message = messageOf(error)
                    _state.update {
                        it.copy(
                            busy = false,
                            loading = false,
                            refreshing = false,
                            permissionSelectionError = DshSelectionFailure(sessionId, message),
                            // 面板可能已关闭或切到别的会话；全局提示仍绑定发起会话。
                            error = "会话 ${sessionId.take(8)} 权限切换失败：$message"
                        )
                    }
                }
            )
        }
    }

    /** 服务停止时清空列表与选中状态，保留安全模式与本机置顶；Cookie 在 close() 中一并丢弃。 */
    fun clearForStopped() {
        close()
        endpointIdentity = null
        _state.value = DshSessionManagerState(
            readOnly = _state.value.readOnly,
            pinnedIds = runCatching { pinStore.pinnedIds() }.getOrDefault(_state.value.pinnedIds)
        )
    }

    fun close() {
        listGeneration++
        searchGeneration++
        modelGeneration++
        permissionGeneration++
        catalogGeneration++
        listJob?.cancel()
        actionJob?.cancel()
        searchJob?.cancel()
        modelJob?.cancel()
        permissionJob?.cancel()
        catalogJob?.cancel()
        source.reset()
        _state.update {
            it.copy(
                loading = false,
                refreshing = false,
                busy = false,
                batchDone = 0,
                batchTotal = 0,
                contentSearching = false,
                modelsLoading = false,
                permissionsLoading = false,
                catalogLoading = false
            )
        }
    }

    /**
     * 端点切换（远程→本地、本地→远程、端口变化）时丢弃旧端点的会话/目录缓存，
     * 取消在途请求并递增代次，避免旧响应发布到新端点界面。
     */
    fun onEndpointChanged(endpoint: String?) {
        if (endpoint == endpointIdentity) return
        endpointIdentity = endpoint
        listGeneration++
        searchGeneration++
        modelGeneration++
        permissionGeneration++
        catalogGeneration++
        listJob?.cancel()
        actionJob?.cancel()
        searchJob?.cancel()
        modelJob?.cancel()
        permissionJob?.cancel()
        catalogJob?.cancel()
        source.reset()
        _state.update {
            it.copy(
                available = false,
                hasLoaded = false,
                loading = false,
                refreshing = false,
                busy = false,
                batchDone = 0,
                batchTotal = 0,
                sessions = emptyList(),
                unreadIds = emptySet(),
                selectedId = null,
                contentHits = emptyList(),
                contentHasMore = false,
                contentSearching = false,
                contentSearched = false,
                models = null,
                modelsLoading = false,
                modelsError = null,
                modelSelectionError = null,
                permissions = null,
                permissionsLoading = false,
                permissionsError = null,
                permissionSelectionError = null,
                skills = emptyList(),
                commands = emptyList(),
                catalogLoading = false,
                catalogError = null,
                error = null,
                loadError = null,
                notice = null
            )
        }
    }

    private fun beginAction(): Boolean {
        if (_state.value.readOnly) {
            _state.update { it.copy(error = "安全模式下会话管理为只读，不能修改会话") }
            return false
        }
        if (_state.value.busy) return false
        // 写操作前让在途列表/搜索请求失效，避免旧结果覆盖操作后的新列表。
        listGeneration++
        listJob?.cancel()
        actionJob?.cancel()
        // 已取消的列表读取不能继续显示加载态，否则失败后刷新按钮会被永久禁用。
        _state.update { it.copy(busy = true, loading = false, refreshing = false, batchDone = 0, batchTotal = 0, error = null, notice = null) }
        return true
    }

    private fun failAction(error: Throwable) {
        _state.update { it.copy(busy = false, loading = false, refreshing = false, batchDone = 0, batchTotal = 0, error = messageOf(error)) }
    }

    private suspend fun loadAfterAction(
        selectedId: String?,
        notice: String,
        excludeIds: Set<String> = emptySet()
    ) {
        val result = source.sessions()
        result.fold(
            onSuccess = { sessions ->
                publishSessions(
                    sessions = sessions.filterNot { it.id in excludeIds },
                    selectedId = selectedId,
                    notice = notice,
                    keepError = false
                )
            },
            onFailure = { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        loading = false,
                        refreshing = false,
                        selectedId = selectedId ?: it.selectedId,
                        notice = notice,
                        error = "操作已完成，但列表刷新失败：${messageOf(error)}"
                    )
                }
            }
        )
    }

    private fun publishSessions(
        sessions: List<DshAdminSession>,
        selectedId: String?,
        notice: String?,
        keepError: Boolean
    ) {
        val keep = selectedId?.takeIf { id -> sessions.any { it.id == id } }
            ?: _state.value.selectedId?.takeIf { id -> sessions.any { it.id == id } }
        _state.update {
            it.copy(
                loading = false,
                refreshing = false,
                busy = false,
                sessions = sessions,
                unreadIds = unreadOf(sessions),
                selectedId = keep,
                available = true,
                hasLoaded = true,
                notice = notice ?: it.notice,
                loadError = null,
                modelSelectionError = null,
                permissionSelectionError = null,
                error = if (keepError) it.error else null
            )
        }
        keep?.let { id -> sessions.firstOrNull { it.id == id }?.let(::markRead) }
    }

    private fun markRead(session: DshAdminSession) {
        readStore.markRead(session.id, session.updatedAt)
        _state.update { it.copy(unreadIds = it.unreadIds - session.id) }
    }

    private fun unreadOf(sessions: List<DshAdminSession>): Set<String> = sessions
        .filter { it.updatedAt > 0 && !it.blank && it.updatedAt > readStore.lastRead(it.id) }
        .mapTo(LinkedHashSet()) { it.id }

    private fun validId(id: String): Boolean =
        id.isNotBlank() && id.length <= 128 && id.all { it.code in 0x21..0x7e }

    private fun validTitle(title: String, allowEmpty: Boolean): Boolean {
        if (title.isEmpty()) return allowEmpty
        return title.length <= 200 && title.none { it.code < 0x20 || it.code == 0x7f }
    }

    private fun messageOf(error: Throwable): String = when (error) {
        is DshApiException -> when (error.code) {
            "workspace/session-active" -> "会话仍在运行，请确认停止当前工作后再归档"
            "session/conflict" -> "会话目录冲突，未执行操作"
            "session/agent-busy" -> "会话正被其他 Agent 使用"
            "session/title-invalid" -> "会话标题不合法"
            "session/provider-credentials-unavailable" -> "模型服务凭据不可用"
            "session/provider-models-unavailable" -> "模型目录暂时不可用"
            "session/model-unavailable" -> "所选模型不可用"
            "session/model-unsupported" -> "当前会话不支持所选模型"
            "session/migration-required" -> "会话需要迁移，原生管理暂不可用"
            "permission/preset-unknown" -> "官方不支持该权限预设"
            "permission/denied" -> "官方拒绝了权限切换"
            "commands/unknown" -> "官方未识别该命令"
            "workspace/session-not-found" -> "会话不存在或已被归档"
            else -> "官方接口返回 ${error.code}"
        }
        is JSONException, is IllegalArgumentException ->
            error.message?.takeIf { it.any { char -> char in '\u4e00'..'\u9fff' } }
                ?: "官方接口响应格式不正确"
        else -> Redact.text(error.message ?: "未知错误").take(200)
    }
}

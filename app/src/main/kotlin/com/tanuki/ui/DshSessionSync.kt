package com.tanuki.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 一次会话列表变更；`origin` 用于让发起方忽略自己的广播，避免重复刷新。 */
data class DshSessionChange(val revision: Long, val origin: Any? = null)

/**
 * 进程内会话变更总线。
 *
 * 聊天页与会话管理器不共享可变的会话副本：任一侧创建、重命名或归档成功后只
 * 广播“列表已失效”，另一侧回到官方 `session/list` 重新读取。这样两边看到的
 * 始终是同一份官方数据，也不会各自保留需要手动刷新的旧列表。
 */
class DshSessionSync {
    private val _changes = MutableStateFlow(DshSessionChange(0L))
    val changes: StateFlow<DshSessionChange> = _changes.asStateFlow()

    fun publish(origin: Any? = null) {
        _changes.update { current -> DshSessionChange(current.revision + 1, origin) }
    }
}

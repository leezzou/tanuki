package com.tanuki.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class AppearanceMode(val storedValue: String, val title: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStoredValue(value: String?): AppearanceMode = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

/** 仅保存普通外观偏好，不共享账号或运行环境存储。 */
internal interface AppearancePreferenceStorage {
    fun read(): String?
    fun write(value: String)
}

internal enum class AppearanceSaveError { LOAD, SAVE }

internal data class AppearancePreferenceState(
    val mode: AppearanceMode = AppearanceMode.SYSTEM,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: AppearanceSaveError? = null,
)

/** 只有持久化成功才发布生效选择；IO与界面生命周期无关。 */
internal class AppearancePreferenceController(
    private val storage: AppearancePreferenceStorage,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(AppearancePreferenceState())
    val state: StateFlow<AppearancePreferenceState> = mutable.asStateFlow()

    suspend fun load() = mutex.withLock {
        // 进入原子读写阶段后完成状态发布，避免取消落在磁盘成功与UI更新之间。
        withContext(io + NonCancellable) {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                val mode = AppearanceMode.fromStoredValue(storage.read())
                mutable.value = AppearancePreferenceState(mode = mode, loading = false)
            } catch (cancelled: CancellationException) {
                mutable.value = mutable.value.copy(loading = false)
                throw cancelled
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(loading = false, error = AppearanceSaveError.LOAD)
            }
        }
    }

    suspend fun select(mode: AppearanceMode) = mutex.withLock {
        if (mutable.value.loading || mutable.value.error == AppearanceSaveError.LOAD) return@withLock
        withContext(io + NonCancellable) {
            val previous = mutable.value
            if (mode == previous.mode) {
                mutable.value = previous.copy(error = null)
                return@withContext
            }
            mutable.value = previous.copy(saving = true, error = null)
            try {
                storage.write(mode.storedValue)
                mutable.value = previous.copy(mode = mode, saving = false, error = null)
            } catch (cancelled: CancellationException) {
                mutable.value = previous.copy(saving = false)
                throw cancelled
            } catch (_: Exception) {
                mutable.value = previous.copy(saving = false, error = AppearanceSaveError.SAVE)
            }
        }
    }
}

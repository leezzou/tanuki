package com.tanuki.ui

import android.content.Context
import com.tanuki.storage.FileLocation
import com.tanuki.storage.TextFileReader

/**
 * 阅读页正文的轻量显示状态。
 *
 * 只保存重新打开同一文件所需的定位与显示偏好，不保存文件内容、文件大小或
 * 任何用户文本；配置变更和进程重建都复用它。
 */
data class ReaderViewState(
    val encoding: String = "自动",
    val scrollOffset: Int = 0,
    val showLineNumbers: Boolean = true,
) {
    /** 把外部/旧版本数据收敛到可接受范围，避免坏值导致无法恢复或越界滚动。 */
    fun normalized(): ReaderViewState = copy(
        encoding = encoding.takeIf { it in TextFileReader.ENCODINGS } ?: "自动",
        scrollOffset = scrollOffset.coerceIn(0, MAX_SCROLL_OFFSET),
        showLineNumbers = showLineNumbers,
    )

    private companion object {
        /** 上限只用于拒绝明显损坏的偏好值，不限制正常长文本滚动。 */
        const val MAX_SCROLL_OFFSET = 10_000_000
    }
}

/**
 * 当前阅读会话。
 *
 * `path`/`name`/`linked` 是恢复文件身份所需的最小字段；`linked` 必须保留，
 * 否则重建后会把符号链接误判成可编辑普通文件。`location` 用于确认恢复时
 * 当前文件区仍匹配（手机存储未授权时不会擅自打开）。
 */
data class ReaderSessionState(
    val path: String,
    val name: String,
    val linked: Boolean,
    val location: FileLocation,
    val view: ReaderViewState = ReaderViewState(),
) {
    fun normalized(): ReaderSessionState = copy(
        path = path.take(MAX_PATH_CHARS),
        name = name.take(MAX_NAME_CHARS),
        view = view.normalized(),
    )

    private companion object {
        const val MAX_PATH_CHARS = 4_096
        const val MAX_NAME_CHARS = 512
    }
}

/** 阅读状态存储；抽象成接口便于 JVM 回归使用内存实现。 */
interface ReaderStateStore {
    fun load(): ReaderSessionState?
    fun save(state: ReaderSessionState)
    fun clear()
}

/**
 * 应用私有偏好实现。
 *
 * `apply()` 会立即更新进程内缓存，因此同一进程内 Activity 重建后马上读取也能
 * 得到最新值；磁盘落盘由系统异步完成。只写入路径、编码、偏移和行号开关。
 */
@Suppress("UseKtx") // 偏好写入很小，沿用现有 SharedPreferences API，不为状态存储新增 core-ktx 依赖。
class PreferencesReaderStateStore(context: Context) : ReaderStateStore {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)

    override fun load(): ReaderSessionState? {
        val path = preferences.getString(KEY_PATH, null)?.takeIf { it.isNotBlank() } ?: return null
        val location = runCatching {
            FileLocation.valueOf(preferences.getString(KEY_LOCATION, "").orEmpty())
        }.getOrNull() ?: return null
        return ReaderSessionState(
            path = path,
            name = preferences.getString(KEY_NAME, "").orEmpty(),
            linked = preferences.getBoolean(KEY_LINKED, false),
            location = location,
            view = ReaderViewState(
                encoding = preferences.getString(KEY_ENCODING, "自动").orEmpty(),
                scrollOffset = preferences.getInt(KEY_SCROLL, 0),
                showLineNumbers = preferences.getBoolean(KEY_LINE_NUMBERS, true),
            ),
        ).normalized()
    }

    override fun save(state: ReaderSessionState) {
        val clean = state.normalized()
        preferences.edit()
            .putString(KEY_PATH, clean.path)
            .putString(KEY_NAME, clean.name)
            .putBoolean(KEY_LINKED, clean.linked)
            .putString(KEY_LOCATION, clean.location.name)
            .putString(KEY_ENCODING, clean.view.encoding)
            .putInt(KEY_SCROLL, clean.view.scrollOffset)
            .putBoolean(KEY_LINE_NUMBERS, clean.view.showLineNumbers)
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(KEY_PATH)
            .remove(KEY_NAME)
            .remove(KEY_LINKED)
            .remove(KEY_LOCATION)
            .remove(KEY_ENCODING)
            .remove(KEY_SCROLL)
            .remove(KEY_LINE_NUMBERS)
            .apply()
    }

    companion object {
        const val PREFERENCE_FILE = "native-reader"
        private const val KEY_PATH = "path"
        private const val KEY_NAME = "name"
        private const val KEY_LINKED = "linked"
        private const val KEY_LOCATION = "location"
        private const val KEY_ENCODING = "encoding"
        private const val KEY_SCROLL = "scroll"
        private const val KEY_LINE_NUMBERS = "line-numbers"
    }
}

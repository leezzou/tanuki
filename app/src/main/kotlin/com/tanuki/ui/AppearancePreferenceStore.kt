package com.tanuki.ui

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** 独立的小型原子文件，不访问现有首选项、凭据或工作区。 */
private class AppearanceFileStorage(private val file: File) : AppearancePreferenceStorage {
    private val atomic = AtomicFile(file)

    override fun read(): String? = try {
        atomic.openRead().use { input ->
            val buffer = ByteArray(33)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            if (count > 32) throw IOException("外观偏好长度无效")
            String(buffer, 0, count, Charsets.UTF_8)
        }
    } catch (_: FileNotFoundException) {
        // 仅真正没有设置时使用默认值；已有文件无法读取须保留失败状态。
        if (file.exists() || File(file.path + ".bak").exists()) throw IOException("外观偏好不可读")
        null
    }

    override fun write(value: String) {
        val output = atomic.startWrite()
        try {
            output.write(value.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
    }
}

/** 应用级持有提交，不因离开设置页中断已开始的保存。 */
internal class AppearancePreferenceStore private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controller = AppearancePreferenceController(
        AppearanceFileStorage(File(context.filesDir, "appearance/theme-mode")),
    )
    val state = controller.state

    init { reload() }
    fun reload() { scope.launch { controller.load() } }
    fun select(mode: AppearanceMode) { scope.launch { controller.select(mode) } }

    companion object {
        @Volatile private var instance: AppearancePreferenceStore? = null
        fun get(context: Context): AppearancePreferenceStore = instance ?: synchronized(this) {
            instance ?: AppearancePreferenceStore(context.applicationContext).also { instance = it }
        }
    }
}

@Composable
internal fun rememberAppearancePreferenceStore(): AppearancePreferenceStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppearancePreferenceStore.get(context) }
}

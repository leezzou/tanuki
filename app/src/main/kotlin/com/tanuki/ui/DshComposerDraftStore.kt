package com.tanuki.ui

import android.content.Context
import com.tanuki.web.DshComposerProtocol

/** 按会话保存输入草稿；只保存文本，不保存临时附件 URI 或文件字节。 */
interface DshDraftStore {
    fun read(sessionId: String): String
    fun write(sessionId: String, text: String)
    fun clear(sessionId: String)
}

/** 应用私有 SharedPreferences 实现；单条草稿有长度上限，避免异常输入撑大偏好文件。 */
@Suppress("UseKtx") // 草稿写入很小，沿用现有 SharedPreferences API，不为此新增 core-ktx 依赖。
class SharedPreferencesDshDraftStore(context: Context) : DshDraftStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(sessionId: String): String {
        if (!DshComposerProtocol.validSessionId(sessionId)) return ""
        return runCatching { preferences.getString(key(sessionId), "").orEmpty() }
            .getOrDefault("")
            .take(DshComposerProtocol.MAX_TEXT_CHARS)
    }

    override fun write(sessionId: String, text: String) {
        if (!DshComposerProtocol.validSessionId(sessionId)) return
        val value = text.take(DshComposerProtocol.MAX_TEXT_CHARS)
        runCatching {
            preferences.edit().apply {
                if (value.isBlank()) remove(key(sessionId)) else putString(key(sessionId), value)
            }.apply()
        }
    }

    override fun clear(sessionId: String) {
        if (!DshComposerProtocol.validSessionId(sessionId)) return
        runCatching { preferences.edit().remove(key(sessionId)).apply() }
    }

    private fun key(sessionId: String): String = "draft:$sessionId"

    private companion object {
        const val FILE = "dsh-composer"
    }
}

/** JVM 测试与无持久化场景使用的内存实现。 */
class MemoryDshDraftStore : DshDraftStore {
    private val drafts = HashMap<String, String>()

    override fun read(sessionId: String): String = synchronized(drafts) { drafts[sessionId].orEmpty() }

    override fun write(sessionId: String, text: String) {
        synchronized(drafts) {
            if (text.isBlank()) drafts.remove(sessionId)
            else drafts[sessionId] = text.take(DshComposerProtocol.MAX_TEXT_CHARS)
        }
    }

    override fun clear(sessionId: String) {
        synchronized(drafts) { drafts.remove(sessionId) }
    }
}

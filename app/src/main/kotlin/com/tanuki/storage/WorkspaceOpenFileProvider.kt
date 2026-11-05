package com.tanuki.storage

import android.net.Uri
import android.os.ParcelFileDescriptor

/** 只共享用户选中的文件副本，拒绝写模式，不开放工作区或其他私有根目录。 */
class WorkspaceOpenFileProvider : androidx.core.content.FileProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        check(mode == "r") { "文件打开仅提供只读权限" }
        return super.openFile(uri, "r") ?: throw java.io.FileNotFoundException("无法打开所选文件")
    }
}

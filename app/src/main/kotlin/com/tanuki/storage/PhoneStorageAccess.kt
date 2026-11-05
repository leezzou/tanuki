package com.tanuki.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File

/** 手机共享存储需要用户授权；权限不会开放其他应用的私有数据。 */
object PhoneStorageAccess {
    /** 文件页复制的是原生手机工具的逻辑路径，不是可由客体直接打开的挂载路径。 */
    fun guestPath(root: File, relative: String = ""): String =
        SharedStoragePaths.guestPath(root, Environment.getExternalStorageDirectory(), relative)

    /** 只枚举系统报告且已获授权的共享卷，供原生 RPC 匹配，不创建客体挂载。 */
    fun toolRoots(context: Context): Map<String, File> {
        val volumes = roots(context)
        if (volumes.isEmpty()) return emptyMap()
        val privateRoot = File(context.applicationInfo.dataDir).canonicalFile.toPath()
        val result = linkedMapOf<String, File>()
        for (volume in volumes.values) {
            val root = volume.canonicalFile
            if (!root.isDirectory || root.toPath().startsWith(privateRoot) || root.path.any { it == ':' || it == '\u0000' }) continue
            result[guestPath(root)] = root
        }
        // 权限可在枚举期间被系统收回；不把旧枚举结果当成仍然有效的授权。
        return if (isGranted(context)) result.toSortedMap() else emptyMap()
    }

    fun isGranted(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** 旧系统把系统读取和写入授权分开；原生应用内的 AI 写授权还需单独检查。 */
    fun isWriteGranted(context: Context): Boolean = isGranted(context) && (Build.VERSION.SDK_INT >= 30 ||
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    fun roots(context: Context): Map<String, File> {
        if (!isGranted(context)) return emptyMap()
        val result = linkedMapOf<String, File>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary.isDirectory) result["内部存储"] = primary
        val storage = context.getSystemService(StorageManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            storage.storageVolumes.forEach { volume ->
                val directory = volume.directory
                if (directory != null && directory.isDirectory && result.values.none { it == directory }) {
                    val label = volume.getDescription(context).replace('/', '_').replace('\\', '_')
                    result["${label.ifBlank { "外部存储" }} (${volume.uuid ?: result.size})"] = directory
                }
            }
        } else {
            // 旧系统从应用的外部目录推导卷根；不扫描系统或其他应用的私有根。
            context.getExternalFilesDirs(null).filterNotNull().forEach { own ->
                val volume = own.parentFile?.parentFile?.parentFile?.parentFile
                if (volume != null && volume.isDirectory && result.values.none { it == volume }) {
                    result["外部存储 (${result.size})"] = volume
                }
            }
        }
        return result
    }
}

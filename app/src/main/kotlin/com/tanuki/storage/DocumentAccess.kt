package com.tanuki.storage

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import com.tanuki.storage.docx.DocxDocument
import java.io.OutputStream

/**
 * 内置查看器统一文档源。
 *
 * 工作区路径与外部 content:// 共用同一套只读预览、导出和“其他应用打开”入口；
 * 查看器不再直接持有 WorkspaceFileManager 与相对路径，从而避免外部文件必须先复制
 * 到工作区才能预览。实现只暴露受控模型和流，不向界面暴露真实文件路径或授予写权限。
 */
internal interface OpenDocument {
    /** 稳定身份，用于 Compose remember 键；外部 URI 或工作区位置+路径。 */
    val key: String
    val displayName: String
    /** 外部来源的只读提示；工作区文件为空。 */
    val accessWarning: String? get() = null
    /** 复制给用户/DSH 的路径或 URI；工作区是客体重路径，外部是 content://。 */
    val copyablePath: String
    suspend fun describe(): WorkspaceFileManager.FileInfo
    suspend fun previewImage(): WorkspaceFileManager.Preview
    suspend fun readTextPage(offset: Long, encoding: String): WorkspaceFileManager.TextPage
    suspend fun openPreviewDescriptor(): ParcelFileDescriptor
    suspend fun readDocx(): DocxDocument
    suspend fun readXlsx(): XlsxWorkbook
    suspend fun prepareOpen(context: Context, onProgress: (Long, Long) -> Unit): Intent
    suspend fun export(output: OutputStream, onProgress: (Long, Long) -> Unit)
}

/** 工作区/手机存储文件继续走既有的路径校验、容量检查和只读描述符。 */
internal class WorkspaceDocument(
    private val files: WorkspaceFileManager,
    private val path: String,
    override val displayName: String,
) : OpenDocument {
    override val key: String = "workspace:${files.location.name}:$path"
    override val copyablePath: String get() = files.guestPath(path)

    override suspend fun describe(): WorkspaceFileManager.FileInfo = files.describe(path)
    override suspend fun previewImage(): WorkspaceFileManager.Preview = files.preview(path)
    override suspend fun readTextPage(offset: Long, encoding: String): WorkspaceFileManager.TextPage =
        files.readTextPage(path, offset, encoding)
    override suspend fun openPreviewDescriptor(): ParcelFileDescriptor = files.openPreviewDescriptor(path)
    override suspend fun readDocx(): DocxDocument = files.readDocx(path)
    override suspend fun readXlsx(): XlsxWorkbook = files.readXlsx(path)
    override suspend fun prepareOpen(context: Context, onProgress: (Long, Long) -> Unit): Intent =
        files.prepareOpen(context, path, onProgress)
    override suspend fun export(output: OutputStream, onProgress: (Long, Long) -> Unit) =
        files.exportFile(path, output, onProgress)
}

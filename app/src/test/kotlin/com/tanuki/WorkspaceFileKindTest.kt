package com.tanuki

import com.tanuki.storage.WorkspaceFileManager
import com.tanuki.storage.WorkspaceFileManager.FileKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceFileKindTest {
    @Test fun mapsPdfDocxAndLegacyDocWithoutPretendingSupport() {
        assertEquals(FileKind.PDF, WorkspaceFileManager.classifyFileKind("report.PDF", "application/pdf"))
        assertEquals(FileKind.PDF, WorkspaceFileManager.classifyFileKind("manual.pdf", null))
        assertEquals(FileKind.DOCX, WorkspaceFileManager.classifyFileKind("方案.docx", null))
        assertEquals(FileKind.DOCX, WorkspaceFileManager.classifyFileKind("方案.DOCX", "application/octet-stream"))
        assertEquals(FileKind.DOC, WorkspaceFileManager.classifyFileKind("旧版.doc", "application/msword"))
        assertEquals(FileKind.DOC, WorkspaceFileManager.classifyFileKind("旧版.DOC", null))
        // 外部 content:// 提供方可能只给 MIME、显示名没有扩展名。
        assertEquals(FileKind.PDF, WorkspaceFileManager.classifyFileKind("document", "application/pdf"))
        assertEquals(FileKind.DOCX, WorkspaceFileManager.classifyFileKind(
            "document", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(FileKind.DOC, WorkspaceFileManager.classifyFileKind("document", "application/msword"))
    }

    @Test fun mapsXlsxWithoutTreatingLegacyXlsAsSupported() {
        assertEquals(FileKind.XLSX, WorkspaceFileManager.classifyFileKind("报表.XLSX", null))
        assertEquals(FileKind.XLSX, WorkspaceFileManager.classifyFileKind("报表.xlsx", "application/octet-stream"))
        assertEquals(FileKind.OTHER, WorkspaceFileManager.classifyFileKind("旧版.xls", "application/vnd.ms-excel"))
        assertEquals(FileKind.OTHER, WorkspaceFileManager.classifyFileKind("宏.xlsm", null))
        assertEquals(FileKind.XLSX, WorkspaceFileManager.classifyFileKind(
            "sheet", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    }

    @Test fun keepsExistingImageAudioVideoTextAndOtherKinds() {
        assertEquals(FileKind.IMAGE, WorkspaceFileManager.classifyFileKind("照片.png", "image/png"))
        assertEquals(FileKind.AUDIO, WorkspaceFileManager.classifyFileKind("录音.mp3", "audio/mpeg"))
        assertEquals(FileKind.VIDEO, WorkspaceFileManager.classifyFileKind("片段.mp4", "video/mp4"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("说明.md", "text/markdown"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("README", null))
        assertEquals(FileKind.OTHER, WorkspaceFileManager.classifyFileKind("归档.zip", "application/zip"))
        assertEquals(FileKind.OTHER, WorkspaceFileManager.classifyFileKind("未知.bin", null))
    }
}

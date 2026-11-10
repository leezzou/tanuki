package com.tanuki

import com.tanuki.storage.FileTypeCatalog
import com.tanuki.storage.WorkspaceFileManager
import com.tanuki.storage.WorkspaceFileManager.FileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 扩展名/MIME 分类边界；纯 JVM，不依赖 Android 的 MimeTypeMap。 */
class FileTypeCatalogTest {
    @Test fun mapsCAndCppFamilyToReadableText() {
        val names = listOf(
            "main.c", "main.cpp", "main.cc", "main.cxx", "main.c++", "main.C",
            "util.h", "util.hpp", "util.hh", "util.hxx", "util.h++", "inline.inl", "impl.ipp", "template.tpp",
            "module.m", "module.mm", "kernel.cu", "kernel.cuh", "boot.s", "start.asm",
        )
        for (name in names) {
            assertEquals("C/C++ 家族必须按文本查看：$name", FileKind.TEXT, WorkspaceFileManager.classifyFileKind(name, null))
        }
        assertEquals("text/x-csrc", FileTypeCatalog.mime("main.c"))
        assertEquals("text/x-c++src", FileTypeCatalog.mime("main.cpp"))
        assertEquals("text/x-chdr", FileTypeCatalog.mime("util.h"))
    }

    @Test fun prefersTextExtensionOverConflictingSystemMime() {
        // Android 的 MimeTypeMap 把 .ts 映射成 video/mp2t；源码必须优先按文本分类。
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("app.ts", "video/mp2t"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("app.tsx", "text/plain"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("main.c", "application/octet-stream"))
        assertEquals("text/typescript", FileTypeCatalog.mime("app.ts"))
    }

    @Test fun mapsCommonProgrammingLanguagesToText() {
        val names = listOf(
            "Main.java", "Main.kt", "build.kts", "app.py", "index.js", "index.mjs", "index.jsx",
            "main.go", "lib.rs", "script.sh", "script.bash", "run.zsh", "run.fish", "task.ps1", "run.bat",
            "App.swift", "app.dart", "app.rb", "app.php", "app.scala", "app.cs", "app.lua", "app.r",
        )
        for (name in names) {
            assertEquals("常见语言源码必须按文本查看：$name", FileKind.TEXT, WorkspaceFileManager.classifyFileKind(name, null))
            assertTrue("应提供规范 MIME：$name", FileTypeCatalog.mime(name) != null)
        }
    }

    @Test fun mapsMarkupConfigAndDataFormatsToText() {
        val names = listOf(
            "README.md", "notes.markdown", "doc.mdx", "app.json", "data.jsonl", "feed.ndjson", "config.json5",
            "pom.xml", "style.xsl", "schema.xsd", "config.yaml", "config.yml", "Cargo.toml", "app.ini",
            "app.cfg", "app.conf", "app.properties", "index.html", "index.htm", "style.css", "style.scss",
            "query.sql", "schema.graphql", "records.csv", "records.tsv", "server.log", "build.out", "readme.txt",
            "captions.srt", "captions.vtt", "changes.diff", "changes.patch", "icon.svg",
        )
        for (name in names) {
            assertEquals("文本/代码类格式必须可查看：$name", FileKind.TEXT, WorkspaceFileManager.classifyFileKind(name, null))
        }
        assertEquals("application/json", FileTypeCatalog.mime("app.json"))
        assertEquals("text/csv", FileTypeCatalog.mime("records.csv"))
        assertEquals("text/markdown", FileTypeCatalog.mime("README.md"))
    }

    @Test fun mapsImageAudioAndVideoExtensionsWithCanonicalMime() {
        val images = mapOf("a.png" to "image/png", "a.jpg" to "image/jpeg", "a.jpeg" to "image/jpeg", "a.gif" to "image/gif",
            "a.webp" to "image/webp", "a.bmp" to "image/bmp", "a.heic" to "image/heic", "a.avif" to "image/avif", "a.tiff" to "image/tiff")
        val audio = mapOf("a.mp3" to "audio/mpeg", "a.flac" to "audio/flac", "a.wav" to "audio/wav", "a.m4a" to "audio/mp4",
            "a.ogg" to "audio/ogg", "a.opus" to "audio/ogg", "a.amr" to "audio/amr")
        val video = mapOf("a.mp4" to "video/mp4", "a.mkv" to "video/x-matroska", "a.webm" to "video/webm", "a.mov" to "video/quicktime",
            "a.avi" to "video/x-msvideo", "a.3gp" to "video/3gpp", "a.m2ts" to "video/mp2t")
        for ((name, mime) in images) {
            assertEquals("图片扩展名：$name", FileKind.IMAGE, WorkspaceFileManager.classifyFileKind(name, null))
            assertEquals(mime, FileTypeCatalog.mime(name))
        }
        for ((name, mime) in audio) {
            assertEquals("音频扩展名：$name", FileKind.AUDIO, WorkspaceFileManager.classifyFileKind(name, null))
            assertEquals(mime, FileTypeCatalog.mime(name))
        }
        for ((name, mime) in video) {
            assertEquals("视频扩展名：$name", FileKind.VIDEO, WorkspaceFileManager.classifyFileKind(name, null))
            assertEquals(mime, FileTypeCatalog.mime(name))
        }
    }

    @Test fun mapsNativePreviewKindsAndKeepsLegacyOfficeUnsupported() {
        assertEquals(FileKind.PDF, WorkspaceFileManager.classifyFileKind("report.pdf", null))
        assertEquals(FileKind.DOCX, WorkspaceFileManager.classifyFileKind("plan.docx", null))
        assertEquals(FileKind.DOC, WorkspaceFileManager.classifyFileKind("old.doc", null))
        assertEquals(FileKind.XLSX, WorkspaceFileManager.classifyFileKind("sheet.xlsx", null))
        for (name in listOf("old.xls", "macro.xlsm", "slides.ppt", "slides.pptx", "text.odt", "sheet.ods")) {
            assertEquals("旧版 Office 不能冒充文本或内置预览：$name", FileKind.OTHER, WorkspaceFileManager.classifyFileKind(name, null))
        }
        assertEquals("application/pdf", FileTypeCatalog.mime("report.pdf"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            FileTypeCatalog.mime("plan.docx"),
        )
        assertEquals("application/vnd.ms-excel", FileTypeCatalog.mime("old.xls"))
    }

    @Test fun keepsArchivesAndBinariesUnknownEvenWhenMimeSuggestsText() {
        for (name in listOf("archive.zip", "archive.tar.gz", "app.apk", "lib.so", "data.bin", "Main.class", "font.ttf", "db.sqlite")) {
            assertEquals("归档/二进制不能按文本探测：$name", FileKind.OTHER, WorkspaceFileManager.classifyFileKind(name, "text/plain"))
            assertTrue(FileTypeCatalog.isBinaryExtension(name))
        }
        assertEquals(FileKind.OTHER, WorkspaceFileManager.classifyFileKind("unknown.xyz", null))
        assertNull(FileTypeCatalog.mime("unknown.xyz"))
    }

    @Test fun recognizesExtensionlessNamesAndDotConfigFiles() {
        for (name in listOf("Dockerfile", "Makefile", "CMakeLists.txt", "LICENSE", "README", "CHANGELOG", "Jenkinsfile",
            ".gitignore", ".gitattributes", ".editorconfig", ".env", ".env.local", ".npmrc", ".bashrc", ".profile")) {
            assertEquals("无扩展名文本必须可查看：$name", FileKind.TEXT, WorkspaceFileManager.classifyFileKind(name, null))
            assertTrue(FileTypeCatalog.isTextName(name))
        }
        assertFalse(FileTypeCatalog.isTextName("photo.png"))
        assertNull("无扩展名不强行给 MIME，由调用方回退 text/plain", FileTypeCatalog.mime("Dockerfile"))
    }

    @Test fun handlesUppercaseMixedPathsAndCompoundExtensions() {
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("SRC/MAIN.CPP", null))
        assertEquals(FileKind.IMAGE, WorkspaceFileManager.classifyFileKind("photos/IMG_001.JPG", null))
        assertEquals(FileKind.PDF, WorkspaceFileManager.classifyFileKind("docs/REPORT.PDF", null))
        assertEquals("cpp", FileTypeCatalog.extension("SRC/MAIN.CPP"))
        assertEquals("gz", FileTypeCatalog.extension("backup.tar.gz"))
        assertEquals("", FileTypeCatalog.extension("Dockerfile"))
        assertEquals("", FileTypeCatalog.extension(".gitignore"))
        assertEquals("", FileTypeCatalog.extension("trailing."))
        assertEquals("", FileTypeCatalog.extension("folder.v2/file"))
    }

    @Test fun fallsBackToMimeOnlyWhenExtensionIsUnknown() {
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("noext", "text/plain"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("noext", "text/plain; charset=utf-8"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("noext", "application/json"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("noext", "application/x-yaml"))
        assertEquals(FileKind.TEXT, WorkspaceFileManager.classifyFileKind("noext", "application/ld+json"))
        assertEquals(FileKind.IMAGE, WorkspaceFileManager.classifyFileKind("noext", "image/png"))
        assertEquals(FileKind.AUDIO, WorkspaceFileManager.classifyFileKind("noext", "audio/mpeg"))
        assertEquals(FileKind.VIDEO, WorkspaceFileManager.classifyFileKind("noext", "video/mp4"))
        assertEquals(FileKind.OTHER, WorkspaceFileManager.classifyFileKind("noext", "application/zip"))
        assertEquals(FileKind.OTHER, WorkspaceFileManager.classifyFileKind("noext", null))
    }
}

package com.tanuki

import com.tanuki.storage.ImportNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

/** 导入项目命名回归：可读文件名、重名 (2)/(3)、不改已有条目。 */
class ImportNamingTest {
    private val shanghai = TimeZone.getTimeZone("Asia/Shanghai")

    @Test fun singleFileUsesSourceNameWithoutExtension() {
        assertEquals("合同", ImportNaming.projectName(listOf("合同.pdf")))
        assertEquals("report.final", ImportNaming.projectName(listOf("report.final.docx")))
        assertEquals("README", ImportNaming.projectName(listOf("README")))
        // 隐藏文件的项目名不保留前导点，避免导入后在默认列表里看不到。
        assertEquals("env", ImportNaming.projectName(listOf(".env")))
    }

    @Test fun multipleFilesUseTimestampInsteadOfUuid() {
        val name = ImportNaming.projectName(
            listOf("a.txt", "b.png"),
            nowMillis = 1_760_000_000_000,
            timeZone = shanghai,
        )
        assertTrue(name, name.matches(Regex("导入-[0-9]{8}-[0-9]{4}")))
        assertFalse(name.contains("import-", ignoreCase = true))
    }

    @Test fun duplicateNameAddsParenthesizedSequenceAndNeverOverwrites() {
        val root = Files.createTempDirectory("tanuki-import-naming-").toFile()
        try {
            val existing = File(root, "合同").apply { writeText("已有项目") }
            val exists: (String) -> Boolean = { File(root, it).exists() }
            val second = ImportNaming.uniqueName("合同", exists)
            assertEquals("合同 (2)", second)
            assertTrue(File(root, second).mkdir())
            assertEquals("合同 (3)", ImportNaming.uniqueName("合同", exists))
            // 命名只做选择，不改动已有目录内容。
            assertEquals("已有项目", existing.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun requestedEditableNameIsSanitized() {
        assertEquals("我的 项目", ImportNaming.projectName(listOf("a.txt"), requested = "  我的 项目  "))
        assertEquals("a_b_c", ImportNaming.projectName(listOf("a.txt"), requested = "a/b\\c"))
        assertEquals("导入", ImportNaming.projectName(listOf("a.txt"), requested = ".."))
        assertFalse(ImportNaming.projectName(listOf("a.txt"), requested = ".workspace-tmp-x").startsWith("."))
    }

    @Test fun longChineseNamesStayWithinFileSystemByteLimit() {
        val long = "项目".repeat(200)
        val name = ImportNaming.uniqueName(long) { false }
        assertTrue("文件名不能超过 255 字节：${name.toByteArray(Charsets.UTF_8).size}",
            name.toByteArray(Charsets.UTF_8).size <= 240)
        assertFalse(name.isBlank())
    }

    @Test fun duplicateFilesKeepTheirExtension() {
        assertEquals("合同 (3).pdf", ImportNaming.uniqueFileName("合同.pdf") { it in setOf("合同.pdf", "合同 (2).pdf") })
        assertEquals(".env (2)", ImportNaming.uniqueFileName(".env") { it == ".env" })
    }

    @Test fun unicodeTruncationKeepsCodePointsAndFileExtension() {
        val desired = "😀".repeat(100) + ".pdf"
        val result = ImportNaming.uniqueFileName(desired) { false }
        assertTrue(result.endsWith(".pdf"))
        assertTrue(result.toByteArray(Charsets.UTF_8).size <= 240)
        assertEquals(result, result.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }

    @Test fun projectPublicationNeverOverwritesFilesDirectoriesOrDanglingLinks() {
        val root = Files.createTempDirectory("tanuki-import-publish-")
        try {
            Files.write(root.resolve("合同"), "existing".toByteArray())
            Files.createDirectory(root.resolve("合同 (2)"))
            Files.createSymbolicLink(root.resolve("合同 (3)"), root.resolve("absent"))
            val stage = Files.createDirectory(root.resolve(".import-synthetic"))
            Files.write(stage.resolve("合同.pdf"), "new".toByteArray())
            val target = ImportNaming.publishProject(stage, root, "合同")
            assertEquals("合同 (4)", target.fileName.toString())
            assertEquals("existing", File(root.toFile(), "合同").readText())
            assertEquals("new", target.resolve("合同.pdf").toFile().readText())
            assertFalse(Files.exists(stage))
        } finally { root.toFile().deleteRecursively() }
    }
}

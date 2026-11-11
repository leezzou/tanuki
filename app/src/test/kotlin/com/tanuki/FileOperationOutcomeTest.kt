package com.tanuki

import com.tanuki.storage.WorkspaceTransferMode
import com.tanuki.storage.WorkspaceTransferReport
import com.tanuki.storage.WorkspaceTransferResult
import com.tanuki.ui.FileOperationFailure
import com.tanuki.ui.FileOperationOutcome
import com.tanuki.ui.FileOperationReason
import com.tanuki.ui.FileOperationRecovery
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 复制/移动结果的原因分类、文案和恢复动作映射。 */
class FileOperationOutcomeTest {
    @Test fun duplicateFilesUseExplicitSkippedMessageAndViewAction() {
        val outcome = operation(
            completed = 0,
            failures = List(3) { FileOperationFailure("重复$it.txt", FileOperationReason.DUPLICATE) },
        )

        assertEquals("3 个文件同名已存在，已跳过（未覆盖）", outcome.message)
        assertEquals("查看", outcome.actionLabel)
        assertEquals(FileOperationRecovery.VIEW, outcome.recovery)
        assertEquals(FileOperationReason.DUPLICATE, outcome.groups.single().reason)
    }

    @Test fun partialCopyKeepsCompletedCountBeforeReason() {
        val outcome = operation(
            completed = 2,
            failures = listOf(FileOperationFailure("重复.txt", FileOperationReason.DUPLICATE)),
        )

        assertEquals("已完成 2 项；1 个文件同名已存在，已跳过（未覆盖）", outcome.message)
    }

    @Test fun spaceShortageOffersCleanupEntry() {
        val outcome = operation(
            completed = 0,
            failures = List(2) { FileOperationFailure("大文件$it.bin", FileOperationReason.NO_SPACE) },
        )

        assertEquals("2 个文件因空间不足未处理，请清理空间后重试", outcome.message)
        assertEquals("清理空间", outcome.actionLabel)
        assertEquals(FileOperationRecovery.FREE_SPACE, outcome.recovery)
    }

    @Test fun permissionFailureOffersGrantEntry() {
        val outcome = operation(
            completed = 0,
            failures = listOf(FileOperationFailure("只读.txt", FileOperationReason.PERMISSION)),
        )

        assertEquals("1 个文件因没有写入权限未处理，请授权后重试", outcome.message)
        assertEquals("去授权", outcome.actionLabel)
        assertEquals(FileOperationRecovery.GRANT_PERMISSION, outcome.recovery)
    }

    @Test fun mixedReasonsUseDetailsAndKeepEachGroup() {
        val outcome = operation(
            completed = 1,
            failures = listOf(
                FileOperationFailure("同名.txt", FileOperationReason.DUPLICATE),
                FileOperationFailure("空间.bin", FileOperationReason.NO_SPACE),
                FileOperationFailure("权限.txt", FileOperationReason.PERMISSION),
            ),
        )

        assertEquals("查看", outcome.actionLabel)
        assertEquals(FileOperationRecovery.VIEW, outcome.recovery)
        assertEquals(
            listOf(FileOperationReason.DUPLICATE, FileOperationReason.NO_SPACE, FileOperationReason.PERMISSION),
            outcome.reasons,
        )
        assertEquals("已完成 1 项，共 4 项", outcome.summary)
        assertTrue(outcome.message.contains("1 个文件同名已存在，已跳过（未覆盖）"))
        assertTrue(outcome.message.contains("1 个文件因空间不足未处理"))
        assertTrue(outcome.message.contains("1 个文件因没有写入权限未处理"))
    }

    @Test fun unknownReasonDoesNotInventCause() {
        val outcome = operation(
            completed = 0,
            failures = listOf(FileOperationFailure("链接", FileOperationReason.OTHER, "链接仅支持原目录重命名")),
        )

        assertEquals("1 个文件未处理，原因见详情", outcome.message)
        assertEquals("查看", outcome.actionLabel)
        assertEquals("链接仅支持原目录重命名", outcome.groups.single().failures.single().shortDetail)
    }

    @Test fun classificationReadsExceptionTypesAndChineseMessages() {
        assertEquals(
            FileOperationReason.DUPLICATE,
            FileOperationOutcome.classify(IllegalStateException("同名条目已存在，未覆盖")),
        )
        assertEquals(
            FileOperationReason.DUPLICATE,
            FileOperationOutcome.classify(FileAlreadyExistsException("exists")),
        )
        assertEquals(
            FileOperationReason.NO_SPACE,
            FileOperationOutcome.classify(IOException("设备剩余空间不足，复制已取消")),
        )
        assertEquals(
            FileOperationReason.NO_SPACE,
            FileOperationOutcome.classify(IOException("No space left on device")),
        )
        assertEquals(
            FileOperationReason.PERMISSION,
            FileOperationOutcome.classify(IOException("此位置只读；请在工作区编辑项目文件")),
        )
        assertEquals(
            FileOperationReason.PERMISSION,
            FileOperationOutcome.classify(SecurityException("Permission denied")),
        )
        assertEquals(
            FileOperationReason.OTHER,
            FileOperationOutcome.classify(IOException("链接仅支持原目录重命名")),
        )
    }

    @Test fun transferReportMapsSkippedAndFailedItemsIntoGroups() {
        val report = WorkspaceTransferReport(
            targetDirectory = "docs",
            results = listOf(
                WorkspaceTransferResult("成功.txt", "成功.txt", skipped = false),
                WorkspaceTransferResult("同名.txt", null, skipped = true),
                WorkspaceTransferResult("空间.bin", null, skipped = false, error = "设备剩余空间不足，复制已取消"),
                WorkspaceTransferResult("权限.txt", null, skipped = false, error = "Permission denied"),
            ),
            copiedBytes = 1024,
            totalBytes = 4096,
        )

        val outcome = FileOperationOutcome.fromTransfer(WorkspaceTransferMode.COPY, report)

        assertEquals("复制", outcome.action)
        assertEquals("工作区「docs」", outcome.target)
        assertEquals(1, outcome.completed)
        assertEquals(4, outcome.total)
        assertEquals(
            listOf(FileOperationReason.DUPLICATE, FileOperationReason.NO_SPACE, FileOperationReason.PERMISSION),
            outcome.reasons,
        )
        assertEquals("查看", outcome.actionLabel)
        assertTrue(outcome.message.startsWith("已完成 1 项；"))
    }

    @Test fun allSuccessHasNoFailureAction() {
        val outcome = operation(completed = 2, failures = emptyList())

        assertEquals("已完成 2 项", outcome.message)
        assertEquals(null, outcome.actionLabel)
        assertEquals(null, outcome.recovery)
        assertTrue(outcome.groups.isEmpty())
    }

    private fun operation(completed: Int, failures: List<FileOperationFailure>) = FileOperationOutcome(
        action = "复制",
        target = "工作区「docs」",
        completed = completed,
        total = completed + failures.size,
        failures = failures,
    )
}

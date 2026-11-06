package com.tanuki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tanuki.storage.DocxTextExtractor
import com.tanuki.storage.WorkspaceFileManager
import kotlinx.coroutines.CancellationException

/** DOCX 只读文本预览；明确说明复杂排版和图片不会保留。 */
@Composable
internal fun DocxTextPreview(files: WorkspaceFileManager, path: String, modifier: Modifier = Modifier) {
    var result by remember(path) { mutableStateOf<DocxTextExtractor.Result?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(true) }
    LaunchedEffect(path) {
        loading = true
        error = null
        try {
            result = files.extractDocxText(path)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = failure.message?.takeIf { message -> message.any { it in '\u4e00'..'\u9fff' } }
                ?: "无法读取此 DOCX，文件可能已加密或损坏。"
        } finally {
            loading = false
        }
    }
    Column(modifier.fillMaxWidth()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        result?.let { preview ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "只读文本预览：仅提取正文文字；复杂排版、表格样式、页眉页脚和图片不会显示，原文件不会被修改。",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (preview.truncated) Text(
                "文档文字超过内置预览上限，仅显示前 ${preview.text.length} 字。",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            SelectionContainer(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    preview.text.ifEmpty { "文档中没有可提取的文字。" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                "${preview.text.length} 字" + if (preview.truncated) " · 已截断" else " · 可长按选择复制",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

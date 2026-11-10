package com.tanuki.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import com.tanuki.web.DshAttachmentKind
import com.tanuki.web.DshAttachmentReadBudget
import com.tanuki.web.DshCommand
import com.tanuki.web.DshComposerDragPolicy
import com.tanuki.web.DshComposerPolicy
import com.tanuki.web.DshComposerProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 原生输入栏：多行输入、按会话草稿、@/斜杠补全、图片/文件附件与粘贴/拖入。
 *
 * 该组件只负责输入与附件呈现；发送后的消息流式更新由聊天主链路负责。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DshComposerBar(
    controller: DshComposerController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsState()
    var value by remember { mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length))) }
    var attachMenu by remember { mutableStateOf(false) }
    var dragActive by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<DshDraftAttachment?>(null) }
    var imagePickerToken by remember { mutableStateOf<DshComposerSessionToken?>(null) }
    var filePickerToken by remember { mutableStateOf<DshComposerSessionToken?>(null) }
    val latestState by rememberUpdatedState(state)

    LaunchedEffect(state.sessionId) {
        value = TextFieldValue(state.text, TextRange(state.text.length))
    }
    // 发送成功或切换草稿后同步文本；用户正常输入时 state.text 与 value.text 相同，不重置光标。
    LaunchedEffect(state.text) {
        if (state.text != value.text) value = TextFieldValue(state.text, TextRange(state.text.length))
    }
    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(2_000)
            controller.clearNotice()
        }
    }

    fun budgetFor(kind: DshAttachmentKind): DshAttachmentReadBudget {
        val current = latestState
        val usedTotal = current.attachments.sumOf { it.bytes.size.toLong() }
        val remainingTotal = (DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES - usedTotal).coerceAtLeast(0)
        return when (kind) {
            DshAttachmentKind.IMAGE -> {
                val imageCount = current.attachments.count { it.kind == DshAttachmentKind.IMAGE }
                val imageBytes = current.attachments
                    .filter { it.kind == DshAttachmentKind.IMAGE }
                    .sumOf { it.bytes.size.toLong() }
                val imageTotal = current.limits.effectiveMaxMessageImageBytes().toLong()
                val remainingImage = (imageTotal - imageBytes).coerceAtLeast(0)
                DshAttachmentReadBudget(
                    maxItems = (DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE - current.attachments.size)
                        .coerceAtLeast(0),
                    remainingBytes = minOf(remainingTotal, remainingImage),
                    maxItemBytes = current.limits.effectiveMaxImageBytes(),
                    totalLabelBytes = minOf(DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES.toLong(), imageTotal),
                    typeMaxItems = (current.limits.maxImagesPerMessage - imageCount).coerceAtLeast(0),
                    typeLabel = "图片"
                )
            }
            DshAttachmentKind.FILE -> {
                val fileCount = current.attachments.count { it.kind == DshAttachmentKind.FILE }
                DshAttachmentReadBudget(
                    maxItems = (DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE - current.attachments.size)
                        .coerceAtLeast(0),
                    remainingBytes = remainingTotal,
                    maxItemBytes = DshComposerProtocol.MAX_FILE_BYTES,
                    totalLabelBytes = DshComposerProtocol.MAX_TOTAL_ATTACHMENT_BYTES.toLong(),
                    typeMaxItems = (DshComposerProtocol.MAX_FILES_PER_MESSAGE - fileCount).coerceAtLeast(0),
                    typeLabel = "文件"
                )
            }
        }
    }

    fun applyRead(result: DshAttachmentReadResult, kind: DshAttachmentKind, token: DshComposerSessionToken?) {
        if (token != null && !controller.isCurrentSession(token)) {
            controller.showNotice("会话已切换，已丢弃上次附件选择")
            return
        }
        if (result.items.isNotEmpty()) {
            if (kind == DshAttachmentKind.IMAGE) controller.addImages(result.items, token)
            else controller.addFiles(result.items, token)
        }
        if (result.errors.isNotEmpty()) controller.showError(result.errors.joinToString("；"))
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val token = imagePickerToken
        imagePickerToken = null
        if (uris.isNotEmpty()) scope.launch {
            val result = withContext(Dispatchers.IO) {
                DshAttachmentReader.readUris(context, uris, DshAttachmentKind.IMAGE, budgetFor(DshAttachmentKind.IMAGE))
            }
            applyRead(result, DshAttachmentKind.IMAGE, token)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val token = filePickerToken
        filePickerToken = null
        if (uris.isNotEmpty()) scope.launch {
            val result = withContext(Dispatchers.IO) {
                DshAttachmentReader.readUris(context, uris, DshAttachmentKind.FILE, budgetFor(DshAttachmentKind.FILE))
            }
            applyRead(result, DshAttachmentKind.FILE, token)
        }
    }

    fun pasteImage() {
        val token = controller.captureSessionToken()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DshAttachmentReader.readClipboard(context, budgetFor(DshAttachmentKind.IMAGE))
            }
            applyRead(result, DshAttachmentKind.IMAGE, token)
        }
    }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                dragActive = true
            }

            override fun onExited(event: DragAndDropEvent) {
                dragActive = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                dragActive = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragActive = false
                val dragEvent = event.toAndroidDragEvent()
                // 外部 provider 通过临时 URI 授权共享；权限必须保留到异步读取结束。
                val permissions = context.findActivity()?.requestDragAndDropPermissions(dragEvent)
                val clip = dragEvent.clipData
                if (clip == null) {
                    permissions?.release()
                    return false
                }
                val token = controller.captureSessionToken()
                val imageBudget = budgetFor(DshAttachmentKind.IMAGE)
                val fileBudget = budgetFor(DshAttachmentKind.FILE)
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            DshAttachmentReader.readDrop(context, clip, imageBudget, fileBudget)
                        }
                        if (!controller.isCurrentSession(token)) {
                            controller.showNotice("会话已切换，已丢弃上次拖入的附件")
                            return@launch
                        }
                        if (result.items.isNotEmpty()) {
                            val images = result.items.filter { it.kind == DshAttachmentKind.IMAGE }
                            val files = result.items.filter { it.kind == DshAttachmentKind.FILE }
                            if (images.isNotEmpty()) controller.addImages(images, token)
                            if (files.isNotEmpty()) controller.addFiles(files, token)
                        }
                        if (result.errors.isNotEmpty()) controller.showError(result.errors.joinToString("；"))
                        else if (result.items.isEmpty()) controller.showError("拖入内容没有可读取的文件")
                    } finally {
                        permissions?.release()
                    }
                }
                return true
            }
        }
    }

    val dragModifier = Modifier.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            // ACTION_DRAG_STARTED 时 clipData 尚未提供，只能读 clipDescription/mimeTypes。
            DshComposerDragPolicy.accepts(event.mimeTypes())
        },
        target = dropTarget
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        tonalElevation = 2.dp,
        border = if (dragActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.fillMaxWidth().then(dragModifier)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.error?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = controller::clearError) { Text("关闭") }
                    }
                }
            }
            state.notice?.let { message ->
                Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.attachments.isNotEmpty()) {
                AttachmentRail(
                    attachments = state.attachments,
                    onRemove = controller::removeAttachment,
                    onRetry = controller::retryAttachment,
                    onPreview = { preview = it }
                )
            }
            val mention = state.mentionQuery
            if (mention != null) {
                MentionMenu(
                    state = state,
                    onPick = { candidate ->
                        val insertion = candidate.insertion + if (candidate is DshMentionCandidate.File && candidate.reference.kind == "directory") "" else " "
                        val next = value.text.replaceRange(mention.start, mention.end, insertion)
                        val cursor = mention.start + insertion.length
                        value = TextFieldValue(next, TextRange(cursor))
                        controller.updateText(next, cursor)
                        // 目录引用保持补全打开；文件/会话引用插入后结束本次 @ 查询。
                        if (candidate !is DshMentionCandidate.File || candidate.reference.kind != "directory") {
                            controller.clearMention()
                        }
                    }
                )
            }
            val slash = DshComposerPolicy.slashQuery(value.text, value.selection.start)
            if (slash != null && state.commandCandidates.isNotEmpty()) {
                CommandMenu(
                    commands = state.commandCandidates,
                    onPick = { command ->
                        val end = value.text.indexOfFirst { it.isWhitespace() }.let { if (it < 0) value.text.length else it }
                        val rest = value.text.substring(end).trimStart()
                        val insertion = "/${command.name} " + rest
                        value = TextFieldValue(insertion, TextRange(insertion.length))
                        controller.updateText(insertion, insertion.length)
                        controller.clearCommandCandidates()
                    }
                )
            }
            TextField(
                value = value,
                onValueChange = { next ->
                    value = next
                    controller.updateText(next.text, next.selection.start)
                },
                modifier = Modifier.fillMaxWidth().onKeyEvent { event ->
                    // Ctrl/⌘ + Enter 发送；普通 Enter 保留换行，避免移动端多行输入误发。
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter &&
                        (event.isCtrlPressed || event.isMetaPressed)
                    ) {
                        controller.send()
                        true
                    } else false
                },
                placeholder = { Text("输入消息，@ 引用会话或文件，/ 查看命令；可粘贴图片或拖入文件") },
                minLines = 1,
                maxLines = 6,
                shape = RoundedCornerShape(18.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box {
                    TextButton(onClick = { attachMenu = true }) { Text("+ 附件") }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("选择图片") },
                            onClick = {
                                attachMenu = false
                                imagePickerToken = controller.captureSessionToken()
                                imagePicker.launch(arrayOf("image/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("选择文件") },
                            onClick = {
                                attachMenu = false
                                filePickerToken = controller.captureSessionToken()
                                filePicker.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("粘贴图片") },
                            onClick = {
                                attachMenu = false
                                pasteImage()
                            }
                        )
                    }
                }
                TextButton(onClick = { insertAtCursor("@", value) { next, cursor -> value = TextFieldValue(next, TextRange(cursor)); controller.updateText(next, cursor) } }) { Text("@") }
                TextButton(onClick = { insertAtCursor("/", value) { next, cursor -> value = TextFieldValue(next, TextRange(cursor)); controller.updateText(next, cursor) } }) { Text("/") }
                if (state.uploadBusy) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (state.uploadBusy) "附件上传中" else "Ctrl/⌘+Enter 发送",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = controller::send, enabled = state.canSend) {
                    Text(if (state.sending) "发送中" else "发送")
                }
            }
        }
    }
    preview?.let { attachment ->
        AttachmentPreviewDialog(attachment = attachment, onDismiss = { preview = null })
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun insertAtCursor(
    text: String,
    value: TextFieldValue,
    apply: (String, Int) -> Unit
) {
    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(start, value.text.length)
    val next = value.text.substring(0, start) + text + value.text.substring(end)
    apply(next, start + text.length)
}

@Composable
private fun AttachmentRail(
    attachments: List<DshDraftAttachment>,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPreview: (DshDraftAttachment) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            AttachmentCard(attachment, onRemove, onRetry, onPreview)
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: DshDraftAttachment,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPreview: (DshDraftAttachment) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.widthIn(min = 190.dp, max = 250.dp)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (attachment.kind == DshAttachmentKind.IMAGE) {
                AttachmentThumbnail(
                    attachment = attachment,
                    modifier = Modifier.size(52.dp).clickable { onPreview(attachment) }
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            attachment.name.substringAfterLast('.', "FILE").take(4).uppercase(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    attachment.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(attachment.sizeLabel)
                        append(" · ")
                        append(
                            when (attachment.status) {
                                DshAttachmentStatus.READY -> "待发送"
                                DshAttachmentStatus.UPLOADING -> "上传中"
                                DshAttachmentStatus.UPLOADED -> "已上传"
                                DshAttachmentStatus.FAILED -> "上传失败"
                            }
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (attachment.status == DshAttachmentStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (attachment.status == DshAttachmentStatus.FAILED) {
                    TextButton(onClick = { onRetry(attachment.id) }) { Text("重试") }
                }
            }
            TextButton(onClick = { onRemove(attachment.id) }) { Text("×") }
        }
    }
}

@Composable
private fun AttachmentThumbnail(attachment: DshDraftAttachment, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.id) {
        value = withContext(Dispatchers.IO) {
            decodeSampled(attachment.bytes, attachment.width, attachment.height, 256)
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = attachment.name, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp))
        } else {
            Box(contentAlignment = Alignment.Center) { Text("图", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun MentionMenu(state: DshComposerState, onPick: (DshMentionCandidate) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (state.mentionLoading) {
                Text("正在查找引用…", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            } else if (state.mentionCandidates.isEmpty()) {
                Text(
                    state.mentionError ?: "没有匹配的文件或会话",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(state.mentionCandidates, key = { it.insertion }) { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(candidate) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                candidate.subtitle?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("引用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandMenu(commands: List<DshCommand>, onPick: (DshCommand) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(Modifier.heightIn(max = 220.dp).padding(vertical = 4.dp)) {
            items(commands, key = { it.name }) { command ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(command) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("/${command.name}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            command.description.ifBlank { command.inputHint.orEmpty() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (command.allowsAttachments) {
                        Text("可带附件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewDialog(attachment: DshDraftAttachment, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(attachment.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.id) {
                    value = withContext(Dispatchers.IO) {
                        decodeSampled(attachment.bytes, attachment.width, attachment.height, 1600)
                    }
                }
                val image = bitmap
                if (image != null) {
                    Image(
                        image,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                    )
                } else {
                    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                Text(
                    "${attachment.mediaType} · ${attachment.sizeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

private fun decodeSampled(bytes: ByteArray, width: Int?, height: Int?, target: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sourceWidth = width?.takeIf { it > 0 } ?: bounds.outWidth
        val sourceHeight = height?.takeIf { it > 0 } ?: bounds.outHeight
        var sample = 1
        if (sourceWidth > 0 && sourceHeight > 0) {
            while (sourceWidth / (sample * 2) >= target && sourceHeight / (sample * 2) >= target) sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrNull()
}

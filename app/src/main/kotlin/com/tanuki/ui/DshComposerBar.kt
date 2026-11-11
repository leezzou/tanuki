package com.tanuki.ui

import com.tanuki.ui.design.TanukiDesign
import androidx.compose.ui.graphics.Color

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.tanuki.web.DshTextEdit
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
    var speechToken by remember { mutableStateOf<DshComposerSessionToken?>(null) }
    var mentionIndex by remember { mutableIntStateOf(0) }
    var commandIndex by remember { mutableIntStateOf(0) }
    // 选择器/粘贴/拖入读取期间显示不定进度；官方读取没有可用的字节级回调。
    var readingAttachments by remember { mutableIntStateOf(0) }
    // 用实际文本布局行数驱动胶囊圆角与高度，避免按字符数猜测换行。
    var lineCount by remember { mutableIntStateOf(1) }
    val mentionListState = rememberLazyListState()
    val commandListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
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

    // 当前 @ 查询以状态机里的 start/end 为准；斜杠查询直接跟随最新光标/选区末端。
    val slash = DshComposerPolicy.slashQuery(value.text, value.selection.max)
    val mentionMenuVisible = state.mentionQuery != null && state.mentionCandidates.isNotEmpty()
    val commandMenuVisible = slash != null && state.commandCandidates.isNotEmpty()

    LaunchedEffect(state.mentionQuery?.query, state.mentionCandidates.size) {
        mentionIndex = 0
    }
    LaunchedEffect(slash, state.commandCandidates.size) {
        commandIndex = 0
    }
    LaunchedEffect(mentionIndex, state.mentionCandidates.size, mentionMenuVisible) {
        if (mentionMenuVisible && state.mentionCandidates.isNotEmpty()) {
            mentionListState.scrollToItem(mentionIndex.coerceIn(0, state.mentionCandidates.lastIndex))
        }
    }
    LaunchedEffect(commandIndex, state.commandCandidates.size, commandMenuVisible) {
        if (commandMenuVisible && state.commandCandidates.isNotEmpty()) {
            commandListState.scrollToItem(commandIndex.coerceIn(0, state.commandCandidates.lastIndex))
        }
    }

    fun focusInput() {
        // 部分宿主在输入法收起/重建时可能暂时没有可聚焦节点；失败不应影响发送结果。
        runCatching { focusRequester.requestFocus() }
    }

    fun applyEdit(edit: DshTextEdit) {
        value = TextFieldValue(edit.text, TextRange(edit.cursor))
        controller.updateText(edit.text, edit.cursor)
    }

    fun pickMention(candidate: DshMentionCandidate) {
        val query = state.mentionQuery ?: return
        val directory = candidate is DshMentionCandidate.File && candidate.reference.kind == "directory"
        // 仅在后面还没有空白时补分隔空格，避免覆盖已有空格造成双空格。
        val separator = !directory && (query.end >= value.text.length || !value.text[query.end].isWhitespace())
        val insertion = candidate.insertion + if (separator) " " else ""
        applyEdit(DshComposerPolicy.insertMention(value.text, query, insertion))
        // 目录引用保持补全打开；文件/会话引用插入后结束本次 @ 查询。
        if (!directory) controller.clearMention()
        focusInput()
    }

    fun pickCommand(command: DshCommand) {
        applyEdit(DshComposerPolicy.insertCommand(value.text, value.selection.max, command.name))
        controller.clearCommandCandidates()
        focusInput()
    }

    fun insertToken(token: String) {
        insertAtCursor(token, value) { next, cursor ->
            value = TextFieldValue(next, TextRange(cursor))
            controller.updateText(next, cursor)
        }
        focusInput()
    }

    fun submit() {
        controller.send()
        // 点击发送按钮或快捷键后把焦点还给输入框，连续输入/失败重发不需要再点一次。
        focusInput()
    }

    val speechInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val token = speechToken
        speechToken = null
        if (result.resultCode == Activity.RESULT_OK) {
            if (token != null && controller.isCurrentSession(token)) {
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let(::insertToken)
            } else controller.showNotice("会话已切换，已丢弃上次语音输入")
        }
    }

    fun startSpeech() {
        speechToken = controller.captureSessionToken()
        runCatching {
            speechInput.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "说出要发送的消息")
            })
        }.onFailure {
            speechToken = null
            controller.showNotice("系统语音输入不可用，请使用键盘输入")
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
        if (token == null) {
            controller.showNotice("无法确认附件所属会话，已丢弃选择结果")
            return
        }
        if (!controller.isCurrentSession(token)) {
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
            if (token == null) {
                controller.showNotice("无法确认附件所属会话，已丢弃选择结果")
                return@launch
            }
            readingAttachments += 1
            val result = try {
                withContext(Dispatchers.IO) {
                    DshAttachmentReader.readUris(context, uris, DshAttachmentKind.IMAGE, budgetFor(DshAttachmentKind.IMAGE))
                }
            } finally {
                readingAttachments = (readingAttachments - 1).coerceAtLeast(0)
            }
            applyRead(result, DshAttachmentKind.IMAGE, token)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val token = filePickerToken
        filePickerToken = null
        if (uris.isNotEmpty()) scope.launch {
            if (token == null) {
                controller.showNotice("无法确认附件所属会话，已丢弃选择结果")
                return@launch
            }
            readingAttachments += 1
            val result = try {
                withContext(Dispatchers.IO) {
                    DshAttachmentReader.readUris(context, uris, DshAttachmentKind.FILE, budgetFor(DshAttachmentKind.FILE))
                }
            } finally {
                readingAttachments = (readingAttachments - 1).coerceAtLeast(0)
            }
            applyRead(result, DshAttachmentKind.FILE, token)
        }
    }

    fun pasteImage() {
        val token = controller.captureSessionToken()
        scope.launch {
            readingAttachments += 1
            val result = try {
                withContext(Dispatchers.IO) {
                    DshAttachmentReader.readClipboard(context, budgetFor(DshAttachmentKind.IMAGE))
                }
            } finally {
                readingAttachments = (readingAttachments - 1).coerceAtLeast(0)
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
                        readingAttachments += 1
                        val result = try {
                            withContext(Dispatchers.IO) {
                                DshAttachmentReader.readDrop(context, clip, imageBudget, fileBudget)
                            }
                        } finally {
                            readingAttachments = (readingAttachments - 1).coerceAtLeast(0)
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

    val capsuleCorner by animateDpAsState(
        targetValue = if (lineCount > 1) 18.dp else 28.dp,
        animationSpec = TanukiDesign.motion.toggle(),
        label = "composer-corner"
    )
    val showStop = state.generationRunning || state.stopping
    val showMic = !showStop && state.text.isBlank() && state.attachments.isEmpty()
    val sendStopEnabled = if (showStop) !state.stopping else if (showMic) state.sessionId != null && !state.sending else state.canSend
    val sendStopContainer = if (sendStopEnabled) {
        Color(com.tanuki.ui.design.TanukiDesign.tokens.colors.primaryFill)
    } else Color(TanukiDesign.tokens.colors.surface)
    val sendStopContent = if (sendStopEnabled) {
        Color(com.tanuki.ui.design.TanukiDesign.tokens.colors.onPrimary)
    } else Color(TanukiDesign.tokens.colors.textSecondary).copy(alpha = 0.55f)

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 0.dp,
        border = if (dragActive) BorderStroke(1.5.dp, Color(TanukiDesign.tokens.colors.primary)) else null,
        // 作为独立组件也保证软键盘不遮住输入框；父级已消费的 IME inset 不会重复叠加。
        modifier = modifier.fillMaxWidth().imePadding().then(dragModifier)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.error?.let { message ->
                Surface(
                    color = Color(TanukiDesign.tokens.colors.dangerContainer),
                    contentColor = Color(TanukiDesign.tokens.colors.onDangerContainer),
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
                Text(message, style = MaterialTheme.typography.labelSmall, color = Color(TanukiDesign.tokens.colors.textSecondary))
            }
            // 停止结果独立显示，避免底部操作行在窄屏把状态文案挤掉。
            state.stopOutcome?.takeIf { state.notice == null }?.let { outcome ->
                val text = when {
                    state.stopping -> "正在结束本次回复"
                    outcome == DshStopOutcome.REQUESTED -> "正在结束本次回复，仍在收尾"
                    outcome == DshStopOutcome.STOPPED -> "已结束本次回复"
                    else -> "停止失败，可重试"
                }
                val color = when {
                    state.stopping || outcome == DshStopOutcome.REQUESTED ->
                        Color(TanukiDesign.tokens.colors.textSecondary)
                    outcome == DshStopOutcome.STOPPED -> Color(TanukiDesign.tokens.colors.primary)
                    else -> Color(TanukiDesign.tokens.colors.danger)
                }
                Text(text, style = MaterialTheme.typography.labelMedium, color = color)
            }
            if (state.attachments.isNotEmpty()) {
                AttachmentRail(
                    attachments = state.attachments,
                    onRemove = controller::removeAttachment,
                    onRetry = controller::retryAttachment,
                    onPreview = { preview = it }
                )
            }
            if (state.mentionQuery != null) {
                MentionMenu(
                    state = state,
                    selectedIndex = mentionIndex,
                    listState = mentionListState,
                    onPick = ::pickMention
                )
            }
            if (commandMenuVisible) {
                CommandMenu(
                    commands = state.commandCandidates,
                    selectedIndex = commandIndex,
                    listState = commandListState,
                    onPick = ::pickCommand
                )
            }
            // 胶囊单行 56dp；换行立即布局，仅圆角走 S-toggle，避免与停靠条叠加高度动画。
            Surface(
                color = Color(TanukiDesign.tokens.colors.surface),
                shape = RoundedCornerShape(capsuleCorner),
                tonalElevation = 2.dp,
                border = BorderStroke(
                    1.dp,
                    if (dragActive) Color(TanukiDesign.tokens.colors.primary)
                    else Color(TanukiDesign.tokens.colors.outline).copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        ComposerCircleButton(
                            description = "添加附件",
                            onClick = { attachMenu = true },
                            container = Color(TanukiDesign.tokens.colors.surface),
                            contentColor = Color(TanukiDesign.tokens.colors.textSecondary)
                        ) {
                            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(text = { Text("@ 引用会话或文件") }, onClick = { attachMenu = false; insertToken("@") })
                            DropdownMenuItem(text = { Text("/ 查看命令") }, onClick = { attachMenu = false; insertToken("/") })
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
                    BasicTextField(
                        value = value,
                        onValueChange = { next ->
                            value = next
                            // 有选区时以选区末端作为 @ 查询终点，插入时整段替换。
                            controller.updateText(next.text, next.selection.max)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            .focusRequester(focusRequester).onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                // Ctrl/⌘ + Enter 发送；普通 Enter 保留换行，避免移动端多行输入误发。
                                if (event.key == Key.Enter && (event.isCtrlPressed || event.isMetaPressed)) {
                                    submit()
                                    return@onPreviewKeyEvent true
                                }
                                if (mentionMenuVisible) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            mentionIndex = DshComposerPolicy.moveCandidate(
                                                mentionIndex, state.mentionCandidates.size, 1
                                            )
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.DirectionUp -> {
                                            mentionIndex = DshComposerPolicy.moveCandidate(
                                                mentionIndex, state.mentionCandidates.size, -1
                                            )
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Enter, Key.Tab -> {
                                            state.mentionCandidates.getOrNull(mentionIndex)?.let(::pickMention)
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Escape -> {
                                            controller.clearMention()
                                            return@onPreviewKeyEvent true
                                        }
                                        else -> Unit
                                    }
                                }
                                if (commandMenuVisible) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            commandIndex = DshComposerPolicy.moveCandidate(
                                                commandIndex, state.commandCandidates.size, 1
                                            )
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.DirectionUp -> {
                                            commandIndex = DshComposerPolicy.moveCandidate(
                                                commandIndex, state.commandCandidates.size, -1
                                            )
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Enter, Key.Tab -> {
                                            state.commandCandidates.getOrNull(commandIndex)?.let(::pickCommand)
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Escape -> {
                                            controller.clearCommandCandidates()
                                            return@onPreviewKeyEvent true
                                        }
                                        else -> Unit
                                    }
                                }
                                false
                            },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(TanukiDesign.tokens.colors.textPrimary)
                        ),
                        cursorBrush = SolidColor(Color(TanukiDesign.tokens.colors.primary)),
                        // 随内容自动增高到 6 行，超过后在输入框内部滚动。
                        minLines = 1,
                        maxLines = 6,
                        onTextLayout = { lineCount = it.lineCount.coerceAtLeast(1) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        decorationBox = { inner ->
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                if (value.text.isEmpty()) {
                                    Text(
                                        DshChatRenderPolicy.composerPlaceholder(state.generationRunning),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(TanukiDesign.tokens.colors.textSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    ComposerContextUsage()
                    ComposerCircleButton(
                        description = if (showStop) "停止生成" else if (showMic) "语音输入" else "发送",
                        enabled = sendStopEnabled,
                        onClick = {
                            if (showStop) controller.requestStop() else if (showMic) startSpeech() else submit()
                        },
                        container = sendStopContainer,
                        contentColor = sendStopContent
                    ) {
                        // 只让图标形变，避免整条输入框闪烁。
                        TanukiIconSwap(if (showStop) "stop" else if (showMic) "mic" else "send", Modifier.size(24.dp)) { action ->
                            if (action == "mic") ComposerMicIcon(sendStopContent)
                            else Text(if (action == "stop") "■" else "↑", fontSize = if (action == "stop") 13.sp else 20.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // 运行态静默降级：只额外开放发送，不夺走主键上的停止能力。
                if (state.runStateStale && state.generationRunning && !state.stopping) {
                    TextButton(onClick = ::submit, enabled = state.canSend) { Text("继续发送") }
                }
                if (readingAttachments > 0 || state.uploadBusy || state.stopping || state.sending) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                val statusText = when {
                    readingAttachments > 0 -> "正在读取附件…"
                    state.stopping -> "正在结束本次回复…"
                    state.uploadBusy -> "附件上传中（可取消）"
                    state.stopOutcome == DshStopOutcome.REQUESTED -> "正在结束本次回复，仍在收尾"
                    state.stopOutcome == DshStopOutcome.STOPPED -> "已结束本次回复"
                    state.stopOutcome == DshStopOutcome.FAILED -> "停止失败，可重试"
                    state.sendFailed -> "发送失败，内容已保留，可重发"
                    state.sending -> "发送中…"
                    state.runStateStale -> "回复较久没有更新，可继续发送或结束本次回复"
                    state.generationRunning -> "助手正在回复，可编辑下一条消息或结束本次回复"
                    else -> "Ctrl/⌘+Enter 发送"
                }
                val statusFailed = state.sendFailed || state.stopOutcome == DshStopOutcome.FAILED
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (statusFailed) Color(TanukiDesign.tokens.colors.danger)
                    else Color(TanukiDesign.tokens.colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    preview?.let { attachment ->
        AttachmentPreviewDialog(attachment = attachment, onDismiss = { preview = null })
    }
}

/** 使用当前内容色绘制麦克风轮廓，不为单个图标增加依赖。 */
@Composable
private fun ComposerMicIcon(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .05f),
            size = androidx.compose.ui.geometry.Size(size.width * .3f, size.height * .5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .15f), style = stroke)
        drawArc(color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .2f),
            size = androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .55f), style = stroke)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .75f),
            androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .95f), strokeWidth = stroke.width)
    }
}

/** 没有协议用量时只画静态轮廓，不展示虚构百分比或不定进度。 */
@Composable
private fun ComposerContextUsage() {
    val colors = TanukiDesign.tokens.colors
    var details by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(min = 32.dp).heightIn(min = 48.dp).clickable(onClickLabel = "上下文用量说明") {
        details = true
    }.semantics { contentDescription = "上下文用量未提供" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
            drawCircle(Color(colors.outline), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        Text(DshChatRenderPolicy.contextPercent(null, null), fontSize = 11.sp, color = Color(colors.textSecondary))
    }
    if (details) androidx.compose.material3.AlertDialog(
        onDismissRequest = { details = false }, title = { Text("上下文用量") },
        text = { Text("暂未获取到用量与容量信息，暂时无法计算使用比例。") },
        confirmButton = { TextButton(onClick = { details = false }) { Text("关闭") } }
    )
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

/** 圆键保持 48dp 触控区域；禁用时保留状态但不响应点击。 */
@Composable
private fun ComposerCircleButton(
    description: String,
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        Modifier.size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.7f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
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
        color = Color(TanukiDesign.tokens.colors.surface),
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
                    color = Color(TanukiDesign.tokens.colors.primaryContainer),
                    contentColor = Color(TanukiDesign.tokens.colors.textPrimary),
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
                                DshAttachmentStatus.UPLOADING -> "上传中（可取消）"
                                DshAttachmentStatus.UPLOADED -> "已上传"
                                DshAttachmentStatus.FAILED -> "上传失败"
                            }
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (attachment.status == DshAttachmentStatus.FAILED) {
                        Color(TanukiDesign.tokens.colors.danger)
                    } else Color(TanukiDesign.tokens.colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (attachment.status == DshAttachmentStatus.UPLOADING) {
                    // 官方 unary 上传没有字节级回调，使用不定进度，不伪造百分比。
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (attachment.status == DshAttachmentStatus.FAILED) {
                    TextButton(onClick = { onRetry(attachment.id) }) { Text("重试") }
                }
            }
            TextButton(onClick = { onRemove(attachment.id) }) {
                Text(if (attachment.status == DshAttachmentStatus.UPLOADING) "取消" else "×")
            }
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
        color = Color(TanukiDesign.tokens.colors.canvas),
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
private fun MentionMenu(
    state: DshComposerState,
    selectedIndex: Int,
    listState: LazyListState,
    onPick: (DshMentionCandidate) -> Unit
) {
    Surface(
        color = Color(TanukiDesign.tokens.colors.canvas),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (state.mentionLoading) {
                Text("正在查找引用…", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
            } else if (state.mentionCandidates.isEmpty()) {
                Text(
                    state.mentionError ?: "没有匹配的文件或会话，继续输入可搜索",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(TanukiDesign.tokens.colors.textSecondary)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 220.dp), state = listState) {
                    itemsIndexed(state.mentionCandidates, key = { _, candidate -> candidate.insertion }) { index, candidate ->
                        val selected = index == selectedIndex
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (selected) Color(TanukiDesign.tokens.colors.primaryContainer) else Color.Transparent)
                                .clickable { onPick(candidate) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                candidate.subtitle?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color(TanukiDesign.tokens.colors.textSecondary))
                                }
                            }
                            Text("引用", style = MaterialTheme.typography.labelSmall, color = Color(TanukiDesign.tokens.colors.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandMenu(
    commands: List<DshCommand>,
    selectedIndex: Int,
    listState: LazyListState,
    onPick: (DshCommand) -> Unit
) {
    Surface(
        color = Color(TanukiDesign.tokens.colors.canvas),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(Modifier.heightIn(max = 220.dp).padding(vertical = 4.dp), state = listState) {
            itemsIndexed(commands, key = { _, command -> command.name }) { index, command ->
                val selected = index == selectedIndex
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (selected) Color(TanukiDesign.tokens.colors.primaryContainer) else Color.Transparent)
                        .clickable { onPick(command) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("/${command.name}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            command.description.ifBlank { command.inputHint.orEmpty() },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(TanukiDesign.tokens.colors.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (command.acceptsAttachments) {
                        Text("可带附件", style = MaterialTheme.typography.labelSmall, color = Color(TanukiDesign.tokens.colors.primary))
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
            color = Color(TanukiDesign.tokens.colors.surface),
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
                    color = Color(TanukiDesign.tokens.colors.textSecondary)
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

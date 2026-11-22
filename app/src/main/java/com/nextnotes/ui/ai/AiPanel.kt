package com.nextnotes.ui.ai

import com.nextnotes.ui.theme.softShadow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextnotes.ai.Role
import com.xnotes.R
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The assistant, floating over the note like a chat app's window: the model at the top, the
 * conversation, and a composer with the page that goes along. [capture] takes a picture of what
 * the AI should see when a question is sent: the selection if there is one, else the whole page.
 * [onDrag] moves the panel sideways by the pixels its handle was dragged.
 */
@Composable
fun AiPanel(
    state: AiPaneState,
    capture: () -> AiSnapshot?,
    onInsertImage: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    /** Puts an answer's marks on the page its picture came from; how many went on, or -1 if that page is gone. */
    onMark: (AiSnapshot, List<com.nextnotes.ai.Mark>) -> Int = { _, _ -> -1 },
    onDrag: (Float) -> Unit = {},
) {
    val host = remember(onInsertImage) {
        ArtifactHost(onRevise = { state.input = "修改「${it.title}」：" }, onInsert = onInsertImage)
    }
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val panel = palette.panel.toComposeColor()
    val surface = palette.surface.toComposeColor()
    val border = palette.border.toComposeColor()

    // About a third of a tablet; nearly all of a phone.
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
    // The width is the user's once they drag the left edge; until then a third of the screen.
    val maxWidth = (screen * 0.7f).coerceAtLeast(MIN_PANEL)
    val panelWidth = if (screen < 640f) screen - 16f
    else (state.panelWidth.takeIf { it > 0f } ?: (screen * 0.34f).coerceIn(360f, 440f)).coerceIn(MIN_PANEL, maxWidth)
    val shape = RoundedCornerShape(28.dp)
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Box(modifier.padding(12.dp)) {
    Column(
        Modifier
            .width(panelWidth.dp)
            .fillMaxHeight()
            .then(Modifier.softShadow(10.dp, shape, palette.isDark))
            .clip(shape)
            .background(panel)
            .border(1.dp, border.copy(alpha = if (palette.isDark) 1f else 0.55f), shape),
    ) {
        // The handle: dragged sideways, it carries the panel off whatever it covers.
        Box(
            Modifier.fillMaxWidth().height(18.dp).pointerInput(Unit) {
                detectHorizontalDragGestures { change, amount -> change.consume(); onDrag(amount) }
            },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(dim.copy(alpha = 0.35f))) }

        Header(state, text, dim)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.turns.isEmpty()) {
                EmptyHint(state, text, dim)
            } else {
                Conversation(state, host, onMark, onInsertImage)
            }
        }

        Composer(state, capture, text, dim, panel, surface, border)
    }
    // The left edge's grip: dragged sideways, it makes the panel wider or narrower.
    if (screen >= 640f) {
        val widthNow = androidx.compose.runtime.rememberUpdatedState(panelWidth)
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = (-10).dp).width(20.dp).height(120.dp)
                .pointerInput(maxWidth) {
                    var w = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { w = widthNow.value },
                        onDragEnd = { state.savePanelWidth() },
                    ) { change, amount ->
                        change.consume()
                        w = (w - amount / density).coerceIn(MIN_PANEL, maxWidth)
                        state.panelWidth = w
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.width(5.dp).height(44.dp).shadow(2.dp, CircleShape).clip(CircleShape)
                    .background(if (palette.isDark) surface else panel)
                    .border(1.dp, dim.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
    }
    if (state.settingsOpen) AiSettingsDialog(state) { state.settingsOpen = false }
}

/** The model in use, which switches it, then a new chat and close. */
@Composable
private fun Header(state: AiPaneState, text: Color, dim: Color) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            val profile = state.activeProfile()
            Row(
                Modifier.clip(CircleShape).clickable { menu = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    profile?.let { it.model.ifBlank { it.name } } ?: "未配置服务", color = text, fontSize = 16.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp),
                )
                Icon(Icons.Outlined.ExpandMore, "切换模型", tint = dim, modifier = Modifier.size(18.dp))
            }
            com.xnotes.ui.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                val active = profile?.id
                for (p in state.profiles.profiles()) {
                    DropdownMenuItem(
                        text = { Text(p.model.ifBlank { p.name }, fontWeight = if (p.id == active) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { state.profiles.setActive(p.id); state.profilesChanged(); menu = false },
                    )
                }
                DropdownMenuItem(text = { Text("AI 设置…") }, onClick = { menu = false; state.settingsOpen = true })
            }
        }
        Spacer(Modifier.weight(1f))
        Box {
            var history by remember { mutableStateOf(false) }
            PlainIcon(Icons.Outlined.History, "对话历史", dim) { state.loadPast(); history = true }
            com.xnotes.ui.DropdownMenu(expanded = history, onDismissRequest = { history = false }) {
                if (state.past.isEmpty()) {
                    DropdownMenuItem(text = { Text("这篇笔记还没有更早的对话", color = dim) }, onClick = {}, enabled = false)
                }
                for (p in state.past) {
                    DropdownMenuItem(
                        text = {
                            Column(Modifier.widthIn(min = 200.dp, max = 280.dp)) {
                                Text(p.title.ifBlank { "（只有图片的提问）" }, color = text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    android.text.format.DateUtils.getRelativeTimeSpanString(p.id).toString(),
                                    color = dim, fontSize = 12.sp,
                                )
                            }
                        },
                        trailingIcon = {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).clickable { state.deletePast(p.id) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Close, "删除这段对话", tint = dim, modifier = Modifier.size(16.dp)) }
                        },
                        onClick = { history = false; state.openPast(p.id) },
                    )
                }
            }
        }
        PlainIcon(Icons.Outlined.EditNote, "新对话", dim) { state.newConversation() }
        PlainIcon(Icons.Outlined.Close, "关闭", dim) { state.open = false }
    }
}

@Composable
private fun PlainIcon(icon: ImageVector, desc: String, tint: Color, size: Int = 22, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun EmptyHint(state: AiPaneState, text: Color, dim: Color) {
    val accent = LocalPalette.current.accent.toComposeColor()
    Column(
        Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("有什么想问的？", color = text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.size(10.dp))
        if (state.activeProfile() == null) {
            Text("还没有配置 AI 服务", color = dim, fontSize = 14.sp)
            Text("去配置", color = accent, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp).clickable { state.settingsOpen = true })
        } else {
            Text("圈选一道题再问会更准", color = dim, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The turns, kept at the newest while you stay at the bottom. Scroll up and it stays where you
 * put it while the answer streams on; come back to the bottom and it follows again.
 */
@Composable
private fun Conversation(
    state: AiPaneState,
    host: ArtifactHost,
    onMark: (AiSnapshot, List<com.nextnotes.ai.Mark>) -> Int,
    onInsertImage: (ByteArray) -> Unit,
) {
    val list = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        // A finger on the list stops the following; where the scroll comes to rest decides whether it resumes.
        launch {
            list.interactionSource.interactions.collect { if (it is DragInteraction.Start) follow = false }
        }
        snapshotFlow { list.isScrollInProgress }.collect { moving -> if (!moving) follow = !list.canScrollForward }
    }
    // A new question always brings the bottom back.
    LaunchedEffect(state.turns.size) {
        follow = true
        if (state.turns.isNotEmpty()) list.scrollToItem(state.turns.lastIndex, Int.MAX_VALUE / 2)
    }
    val last = state.turns.lastOrNull()
    LaunchedEffect(last) {
        if (last == null) return@LaunchedEffect
        snapshotFlow { last.text.length + last.reasoning.length }.collectLatest {
            if (follow && list.canScrollForward) list.scrollToItem(state.turns.lastIndex, Int.MAX_VALUE / 2)
        }
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(state.turns) { turn ->
            ProvideArtifactScope(ArtifactScope(host, turn.streaming)) {
                TurnView(turn, onMark, onInsertImage)
            }
        }
    }
}

@Composable
private fun TurnView(
    turn: AiTurn,
    onMark: (AiSnapshot, List<com.nextnotes.ai.Mark>) -> Int,
    onInsertImage: (ByteArray) -> Unit,
) {
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val surface = palette.surface.toComposeColor()
    val border = palette.border.toComposeColor()
    if (turn.role == Role.USER) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            turn.snapshot?.let { bmp ->
                var zoomed by remember { mutableStateOf(false) }
                Image(
                    bmp.asImageBitmap(), turn.snapshotLabel, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(64.dp).height(82.dp).clip(RoundedCornerShape(12.dp))
                        .border(1.dp, border, RoundedCornerShape(12.dp)).background(Color.White)
                        .clickable { zoomed = true },
                )
                if (zoomed) ImagePreview(bmp, turn.snapshotLabel) { zoomed = false }
            }
            Text(
                turn.text, color = text, fontSize = 15.sp, lineHeight = 22.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp)).background(surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Thinking(turn, dim)
        // The marks block is for the page, not for reading.
        val (shown, marks) = remember(turn.text) { com.nextnotes.ai.Marks.split(turn.text) }
        // The answer as drawn, kept so it can go into the note as a picture, formulas and all.
        val layer = rememberGraphicsLayer()
        val paper = palette.panel.toComposeColor()
        if (turn.text.isNotEmpty()) {
            Box(Modifier.drawWithContent {
                layer.record { drawRect(paper); this@drawWithContent.drawContent() }
                drawLayer(layer)
            }) { MarkdownMath(shown, text, dim, surface, palette.text) }
        }
        turn.notice?.let { Text(it, color = dim, fontSize = 12.sp) }
        turn.error?.let { Text(it, color = palette.danger.toComposeColor(), fontSize = 13.sp) }
        if (!turn.streaming && turn.text.isNotEmpty()) {
            val clipboard = LocalClipboardManager.current
            val scope = rememberCoroutineScope()
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlainIcon(Icons.Outlined.ContentCopy, "复制", dim, size = 18) { clipboard.setText(AnnotatedString(turn.text)) }
                PlainIcon(Icons.Outlined.AddPhotoAlternate, "插入到笔记", dim, size = 19) {
                    scope.launch {
                        val shot = layer.toImageBitmap().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            java.io.ByteArrayOutputStream().also { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        }
                        onInsertImage(bytes)
                    }
                }
                val source = turn.source
                if (marks.isNotEmpty() && source != null) MarkBar(turn, marks, source, onMark)
            }
        }
    }
}

/**
 * How the model got to its answer. While it thinks, its last few lines roll by under a running
 * clock, so a long think never looks like a hang; once it answers, one line that opens the rest.
 */
@Composable
private fun Thinking(turn: AiTurn, dim: Color) {
    val palette = LocalPalette.current
    val thinking = turn.streaming && turn.text.isEmpty()
    if (turn.reasoning.isBlank()) {
        if (thinking) Row(verticalAlignment = Alignment.CenterVertically) { Dots(); Text("正在读取笔记", color = dim, fontSize = 14.sp) }
        return
    }
    if (thinking) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dots()
                Text("思考中 · ${((now - turn.startedAt) / 1000).coerceAtLeast(0)} 秒", color = dim, fontSize = 14.sp)
            }
            Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(4.dp))) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(palette.border.toComposeColor()))
                    Box(Modifier.fillMaxSize().padding(start = 12.dp)) {
                        Text(
                            turn.reasoning, color = dim, fontSize = 13.sp, lineHeight = 21.sp,
                            // Measured at full height and pinned to the bottom: the newest lines show, the oldest scroll off the top.
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true).align(Alignment.BottomStart),
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(32.dp).background(
                        Brush.verticalGradient(listOf(palette.panel.toComposeColor(), Color.Transparent)),
                    ),
                )
            }
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    val took = turn.thoughtMs?.let { "已思考 ${(it / 1000).coerceAtLeast(1)} 秒" } ?: "思考过程"
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(took, color = dim, fontSize = 14.sp)
        Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, tint = dim, modifier = Modifier.size(16.dp))
    }
    if (open) {
        Row {
            Box(Modifier.width(2.dp).heightIn(min = 20.dp).background(palette.border.toComposeColor()))
            Text(turn.reasoning, color = dim, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Three dots that breathe in turn: the model is at work. */
@Composable
private fun Dots() {
    val accent = LocalPalette.current.accent.toComposeColor()
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "phase")
    Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 3) {
            val lit = 1f - (((phase - i) % 3f + 3f) % 3f) / 3f
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent.copy(alpha = 0.3f + 0.7f * lit)))
        }
    }
}

/** Offers to put an answer's marks on the page, then says how it went. */
@Composable
private fun MarkBar(
    turn: AiTurn,
    marks: List<com.nextnotes.ai.Mark>,
    shot: AiSnapshot,
    onMark: (AiSnapshot, List<com.nextnotes.ai.Mark>) -> Int,
) {
    val palette = LocalPalette.current
    val dim = palette.textDim.toComposeColor()
    val placed = turn.placed
    when {
        placed == null -> Row(
            Modifier.padding(start = 6.dp).clip(CircleShape).border(1.dp, palette.border.toComposeColor(), CircleShape)
                .clickable { turn.placed = onMark(shot, marks) }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp, 10.dp).border(1.6.dp, Color(0xFFD93B3B), CircleShape))
            Text("标到页面上 · ${marks.size} 处", color = palette.text.toComposeColor(), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        }
        placed > 0 -> Text("已标出 $placed 处，可撤销", color = dim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        placed == 0 -> Text("这些位置不在页面上", color = dim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        else -> Text("回到那篇笔记才能标出", color = dim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * The question box: the page that goes along as a thumbnail on top (× to send none), a + to
 * choose, the field, and send, which turns into stop while an answer streams.
 */
@Composable
private fun Composer(
    state: AiPaneState,
    capture: () -> AiSnapshot?,
    text: Color,
    dim: Color,
    panel: Color,
    surface: Color,
    border: Color,
) {
    val palette = LocalPalette.current
    val accent = palette.accent.toComposeColor()
    val onAccent = palette.onAccent.toComposeColor()
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 6.dp)
            .then(Modifier.softShadow(5.dp, shape, palette.isDark)).clip(shape)
            .background(if (palette.isDark) surface else panel).border(1.dp, border.copy(alpha = if (palette.isDark) 1f else 0.5f), shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val preview = state.preview
        // Once the conversation is under way, the page folds into a small tag beside the + so the
        // composer stays low and the answers keep the room.
        val folded = state.turns.isNotEmpty() && state.imageAttachment == null
        var zoomed by remember { mutableStateOf(false) }
        if (zoomed && preview != null) ImagePreview(preview.bitmap, preview.label) { zoomed = false }
        if (preview != null && !folded) {
            Box(Modifier.padding(start = 6.dp, top = 4.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        preview.bitmap.asImageBitmap(), preview.label, contentScale = ContentScale.Crop,
                        modifier = Modifier.width(48.dp).height(60.dp).clip(RoundedCornerShape(10.dp))
                            .border(1.dp, border, RoundedCornerShape(10.dp)).background(Color.White)
                            .clickable { zoomed = true },
                    )
                    Text(preview.label, color = dim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(start = 40.dp).size(20.dp).clip(CircleShape).background(text)
                        .clickable { state.chooseNoteAttachment(Attach.NONE, capture) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, "不附带", tint = if (palette.isDark) Color.Black else Color.White, modifier = Modifier.size(12.dp)) }
            }
        }
        if (state.readingImage) {
            Text(stringResource(R.string.nn_loading_clipboard_image), color = dim, fontSize = 12.sp)
        }
        state.attachmentError?.let {
            Text(stringResource(it), color = text, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.height(FIELD_ROW), contentAlignment = Alignment.Center) {
                PlainIcon(Icons.Outlined.Add, "附带内容", dim) { menu = true }
                com.xnotes.ui.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("附带笔记（选区或整页）") },
                        onClick = { menu = false; state.chooseNoteAttachment(Attach.AUTO, capture) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.nn_paste_clipboard_image)) },
                        onClick = { menu = false; state.pasteImage(reportMissing = true) },
                    )
                    DropdownMenuItem(
                        text = { Text("不附带") },
                        onClick = { menu = false; state.chooseNoteAttachment(Attach.NONE, capture) },
                    )
                }
            }
            if (folded && state.attach == Attach.AUTO && preview != null) Box(Modifier.height(FIELD_ROW), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.clip(CircleShape).background(if (palette.isDark) panel else surface)
                        .padding(start = 4.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        preview.bitmap.asImageBitmap(), preview.label, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(20.dp, 26.dp).clip(RoundedCornerShape(4.dp)).background(Color.White)
                            .clickable { zoomed = true },
                    )
                    Text(preview.label, color = text, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp).clickable { zoomed = true })
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).clickable { state.chooseNoteAttachment(Attach.NONE, capture) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Close, "不附带", tint = dim, modifier = Modifier.size(14.dp)) }
                }
            }
            TextField(
                value = state.input,
                onValueChange = { state.input = it },
                placeholder = { Text("询问", fontSize = 16.sp, color = dim) },
                modifier = Modifier.weight(1f).heightIn(max = 160.dp).onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    key.action == android.view.KeyEvent.ACTION_DOWN && key.isCtrlPressed &&
                        key.keyCode == android.view.KeyEvent.KEYCODE_V && state.pasteImage(reportMissing = false)
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = text),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = accent,
                ),
            )
            val busy = state.busy
            Box(Modifier.height(FIELD_ROW), contentAlignment = Alignment.Center) { Box(
                Modifier.padding(end = 2.dp).size(40.dp).clip(CircleShape)
                    .background(if (busy) text else accent.copy(alpha = if (state.readingImage) 0.45f else 1f))
                    .clickable(enabled = busy || !state.readingImage) { if (busy) state.stop() else state.send(null, capture) },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) Icon(Icons.Filled.Stop, "停止", tint = if (palette.isDark) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                else Icon(Icons.Outlined.ArrowUpward, "发送", tint = onAccent, modifier = Modifier.size(20.dp))
            } }
        }
    }
}

/** The height of the composer's one-line field, which its buttons centre on. */
private val FIELD_ROW = 56.dp

/** The narrowest the panel may be dragged, in dp. */
private const val MIN_PANEL = 320f

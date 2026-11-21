package com.nextnotes.ui.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextnotes.ai.Prompts
import com.nextnotes.ai.Role
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * The assistant panel beside the note. [capture] takes a picture of what the AI should see: the
 * selection if there is one, otherwise what is on screen. It runs when a question is sent, so the
 * AI reads the page as it is at that moment.
 */
@Composable
fun AiPanel(state: AiPaneState, capture: () -> AiSnapshot?, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val border = palette.border.toComposeColor()
    val accent = palette.accent.toComposeColor()
    val surface = palette.surface.toComposeColor()

    // About a third of a tablet; nearly all of a phone, where side by side cannot work anyway.
    val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
    val panelWidth = if (screen < 640f) screen * 0.92f else (screen * 0.36f).coerceIn(340f, 460f)
    Column(
        modifier
            .width(panelWidth.dp)
            .fillMaxHeight()
            .background(palette.panel.toComposeColor()),
    ) {
        // Header: title, the active service, new chat, settings, close.
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = accent, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text("AI 助手", color = text, fontSize = 16.sp)
                val profile = state.activeProfile()
                Text(
                    profile?.let { "${it.name} · ${it.model}" } ?: "未配置服务",
                    color = dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { state.settingsOpen = true },
                )
            }
            HeaderButton(Icons.Outlined.AddComment, "新对话", dim) { state.newConversation() }
            HeaderButton(Icons.Outlined.Settings, "AI 设置", dim) { state.settingsOpen = true }
            HeaderButton(Icons.Outlined.Close, "关闭", dim) { state.open = false }
        }
        Box(Modifier.fillMaxWidth().padding(top = 4.dp).background(border).heightIn(min = 1.dp, max = 1.dp))

        // The conversation.
        val list = rememberLazyListState()
        val lastText = state.turns.lastOrNull()?.text
        LaunchedEffect(state.turns.size, lastText?.length) {
            if (state.turns.isNotEmpty()) list.animateScrollToItem(state.turns.size - 1)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.turns.isEmpty()) {
                EmptyHint(state, dim, accent)
            } else {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.turns) { turn -> TurnView(turn, text, dim, surface, border, palette) }
                }
            }
        }

        // What will be sent along, and how.
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AttachChip(state.attach, accent, dim) {
                    state.attach = if (state.attach == Attach.AUTO) Attach.NONE else Attach.AUTO
                    state.refreshPreview(capture)
                }
                Spacer(Modifier.width(8.dp))
                val preview = state.preview
                if (state.attach == Attach.AUTO && preview != null) {
                    Image(
                        preview.bitmap.asImageBitmap(), "将发送的内容预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(48.dp, 36.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, border, RoundedCornerShape(4.dp)).background(Color.White),
                    )
                    Text(preview.kind.label, color = dim, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                if (state.attach == Attach.AUTO) HeaderButton(Icons.Outlined.Refresh, "刷新预览", dim) { state.refreshPreview(capture) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (action in Prompts.quickActions) {
                    Text(
                        action.label, color = text, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                            .clickable(enabled = !state.busy) { state.send(action.prompt, capture) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    value = state.input,
                    onValueChange = { state.input = it },
                    placeholder = { Text("问问 AI…（不输入直接发送也行）", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f).heightIn(max = 160.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = text),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = surface, unfocusedContainerColor = surface,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = accent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(6.dp))
                val busy = state.busy
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(accent)
                        .clickable { if (busy) state.stop() else state.send(null, capture) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        if (busy) "停止" else "发送",
                        tint = palette.onAccent.toComposeColor(),
                    )
                }
            }
        }
    }
    if (state.settingsOpen) AiSettingsDialog(state) { state.settingsOpen = false }
}

@Composable
private fun HeaderButton(icon: ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AttachChip(attach: Attach, accent: Color, dim: Color, onClick: () -> Unit) {
    val on = attach == Attach.AUTO
    Text(
        attach.label, fontSize = 12.sp, color = if (on) accent else dim,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (on) accent else dim, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyHint(state: AiPaneState, dim: Color, accent: Color) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = accent, modifier = Modifier.size(36.dp))
        Spacer(Modifier.size(12.dp))
        if (state.activeProfile() == null) {
            Text("还没有配置 AI 服务", color = dim, fontSize = 14.sp)
            Text(
                "去配置", color = accent, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp).clickable { state.settingsOpen = true },
            )
        } else {
            Text(
                "圈选一道题，或者直接提问。\nAI 会看到你的选区；没有选区时看到当前页面。",
                color = dim, fontSize = 14.sp, lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TurnView(turn: AiTurn, text: Color, dim: Color, surface: Color, border: Color, palette: com.xnotes.ui.theme.Palette) {
    if (turn.role == Role.USER) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            turn.snapshot?.let { bmp ->
                Image(
                    bmp.asImageBitmap(), turn.snapshotLabel, contentScale = ContentScale.Fit,
                    modifier = Modifier.widthIn(max = 220.dp).heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(8.dp)).border(1.dp, border, RoundedCornerShape(8.dp)).background(Color.White),
                )
                Spacer(Modifier.size(4.dp))
            }
            Text(
                turn.text, color = text, fontSize = 15.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(surface).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        if (turn.reasoning.isNotBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                if (expanded) "▾ 思考过程" else "▸ 思考过程", color = dim, fontSize = 12.sp,
                modifier = Modifier.clickable { expanded = !expanded }.padding(bottom = 4.dp),
            )
            if (expanded) Text(turn.reasoning, color = dim, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (turn.text.isNotEmpty()) {
            MarkdownMath(turn.text, text, dim, surface, palette.text)
        } else if (turn.streaming) {
            Text(if (turn.reasoning.isNotBlank()) "思考中…" else "正在读取笔记…", color = dim, fontSize = 14.sp)
        }
        turn.error?.let { Text(it, color = palette.danger.toComposeColor(), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (!turn.streaming && turn.text.isNotEmpty()) {
            val clipboard = LocalClipboardManager.current
            Row(Modifier.padding(top = 2.dp)) {
                HeaderButton(Icons.Outlined.ContentCopy, "复制", dim) { clipboard.setText(AnnotatedString(turn.text)) }
            }
        }
    }
}

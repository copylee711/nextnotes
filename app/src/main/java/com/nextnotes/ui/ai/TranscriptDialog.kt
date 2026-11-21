package com.nextnotes.ui.ai

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nextnotes.ui.theme.LocalChrome
import com.nextnotes.ui.theme.sticker
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * What the handwriting reads as, to check and correct before it goes on the page. [onPlace] puts
 * the text there, in place of the ink when its flag is true or under it when false.
 */
@Composable
fun TranscriptDialog(t: Transcript, onClose: () -> Unit, onPlace: (String, Boolean) -> Unit) {
    val palette = LocalPalette.current
    val chrome = LocalChrome.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val accent = palette.accent.toComposeColor()
    // Follows the stream until it ends, then belongs to the user to correct.
    var edit by remember(t) { mutableStateOf("") }
    LaunchedEffect(t.text, t.busy) { if (t.busy || edit.isEmpty()) edit = t.text }
    val clipboard = LocalClipboardManager.current
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onClose) {
        com.nextnotes.ui.KeepBarsHidden()
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .padding(end = chrome.drop, bottom = chrome.drop)
                .sticker(chrome, palette.panel.toComposeColor(), shape).clip(shape)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.TextFields, null, tint = palette.onAccent.toComposeColor(), modifier = Modifier.size(18.dp))
                }
                Text("手写转文字", color = text, fontSize = 18.sp, fontFamily = chrome.display, modifier = Modifier.padding(start = 10.dp).weight(1f))
                if (t.busy) CircularProgressIndicator(Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
            }
            val error = t.error
            when {
                error != null -> Text(error, color = palette.danger.toComposeColor(), fontSize = 14.sp)
                t.busy && edit.isEmpty() -> Text("正在识别…", color = dim, fontSize = 14.sp)
                else -> OutlinedTextField(
                    value = edit,
                    onValueChange = { edit = it },
                    enabled = !t.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = text),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent),
                )
            }
            if (!t.busy && error == null) Text("认错的地方可以直接改。", color = dim, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("取消", color = dim) }
                Spacer(Modifier.weight(1f))
                val ready = !t.busy && edit.isNotBlank()
                TextButton(onClick = { clipboard.setText(AnnotatedString(edit)); onClose() }, enabled = ready) { Text("复制") }
                TextButton(onClick = { onPlace(edit.trim(), false) }, enabled = ready) { Text("插入到下方") }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { onPlace(edit.trim(), true) }, enabled = ready,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (ready) accent else palette.surface.toComposeColor()),
                ) { Text("替换笔迹", color = if (ready) palette.onAccent.toComposeColor() else dim) }
            }
        }
    }
}

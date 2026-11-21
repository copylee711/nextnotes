package com.nextnotes.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextnotes.ui.ai.MarkdownMath
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** Runs the launch check once, and shows the offer whenever there is one (from About too). */
@Composable
fun UpdatePrompt() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Updates.checkOnLaunch(ctx) }
    val release = Updates.offer ?: return
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val accent = palette.accent.toComposeColor()
    var stopAuto by remember { mutableStateOf(!Updates.autoCheck(ctx)) }

    fun close() {
        Updates.setAutoCheck(ctx, !stopAuto)
        Updates.offer = null
    }

    AlertDialog(
        onDismissRequest = ::close,
        containerColor = palette.panel.toComposeColor(),
        title = { Text("发现新版本 ${release.version}", color = text) },
        text = {
            Column {
                Text("当前版本 ${Versions.clean(Updates.currentVersion(ctx))}", color = dim, fontSize = 13.sp)
                if (release.notes.isNotBlank()) {
                    Column(Modifier.padding(top = 10.dp).heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        MarkdownMath(release.notes, text, dim, palette.surface.toComposeColor(), palette.text)
                    }
                }
                Row(
                    Modifier.padding(top = 8.dp).clickable { stopAuto = !stopAuto },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(stopAuto, { stopAuto = it }, colors = CheckboxDefaults.colors(checkedColor = accent))
                    Text("以后不自动检查更新", color = dim, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton({ Updates.setAutoCheck(ctx, !stopAuto); Updates.download(ctx, release) }) {
                Text("下载更新", color = accent)
            }
        },
        dismissButton = {
            Row {
                TextButton({ Updates.setAutoCheck(ctx, !stopAuto); Updates.skip(ctx, release) }) { Text("忽略此版本", color = dim) }
                TextButton(::close) { Text("稍后", color = dim) }
            }
        },
    )
}

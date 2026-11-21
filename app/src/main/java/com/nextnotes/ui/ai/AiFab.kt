package com.nextnotes.ui.ai

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextnotes.ui.theme.LocalChrome
import com.nextnotes.ui.theme.stickerButton
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** The "AI 助手" button that opens the assistant. */
@Composable
fun AiFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val chrome = LocalChrome.current
    Row(
        modifier
            .padding(16.dp)
            .stickerButton(palette.accent.toComposeColor(), RoundedCornerShape(16.dp), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = palette.onAccent.toComposeColor(), modifier = Modifier.size(18.dp))
        Text(
            "AI 助手", color = palette.onAccent.toComposeColor(), fontSize = 15.sp, fontFamily = chrome.display,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

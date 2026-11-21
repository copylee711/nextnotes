package com.nextnotes.ui.ai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nextnotes.ui.theme.stickerButton
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** The round sparkle button that opens the assistant. */
@Composable
fun AiFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier
            .padding(16.dp)
            .size(52.dp)
            .stickerButton(palette.accent.toComposeColor(), CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.AutoAwesome, "AI 助手", tint = palette.onAccent.toComposeColor(), modifier = Modifier.size(26.dp))
    }
}

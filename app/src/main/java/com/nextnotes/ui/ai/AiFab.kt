package com.nextnotes.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/** The small "AI" pill in the corner of the note that opens the assistant. */
@Composable
fun AiFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .padding(16.dp)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(palette.accent.toComposeColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = palette.onAccent.toComposeColor(), modifier = Modifier.size(18.dp))
        Text("AI", color = palette.onAccent.toComposeColor(), fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

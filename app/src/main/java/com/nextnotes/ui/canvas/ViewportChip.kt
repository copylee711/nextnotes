package com.nextnotes.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.R
import com.xnotes.ui.InfiniteEditor
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor

/**
 * The canvas's view at a glance, in a corner: how far it is zoomed, and whether the zoom is locked.
 * A tap opens what to do with the view: back to 100%, show everything, step in or out, lock the
 * zoom, and the minimap.
 */
@Composable
fun ViewportChip(canvas: InfiniteEditor, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val text = palette.text.toComposeColor()
    val dim = palette.textDim.toComposeColor()
    val accent = palette.accent.toComposeColor()
    val mist = com.nextnotes.ui.theme.LocalChrome.current.theme == com.nextnotes.ui.theme.NnTheme.MIST
    var open by remember { mutableStateOf(false) }
    val locked = canvas.zoomLocked
    Box(modifier.padding(com.xnotes.ui.LocalToolbarCover.current).padding(12.dp)) {
        Row(
            Modifier
                .shadow(if (mist && !palette.isDark) 4.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background((if (mist) palette.panel else palette.surface).toComposeColor())
                .then(if (mist && !palette.isDark) Modifier else Modifier.border(1.dp, palette.border.toComposeColor(), CircleShape))
                .clickable { open = true }
                .height(36.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (locked) {
                Icon(XnotesIcons.lock, stringResource(R.string.unlock_zoom), tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text("${canvas.zoomPercent}%", color = text, fontSize = 13.sp)
        }
        com.xnotes.ui.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Item(XnotesIcons.zoomIn, stringResource(R.string.zoom_in), enabled = !locked) { canvas.zoomBy(InfiniteEditor.ZOOM_STEP) }
            Item(XnotesIcons.zoomOut, stringResource(R.string.zoom_out), enabled = !locked) { canvas.zoomBy(1.0 / InfiniteEditor.ZOOM_STEP) }
            Item(null, stringResource(R.string.nn_zoom_reset), enabled = !locked) { canvas.resetZoom(); open = false }
            Item(XnotesIcons.fit, stringResource(R.string.fit_all), enabled = !locked) { canvas.zoomToFit(); open = false }
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = palette.border.toComposeColor())
            Item(
                if (locked) XnotesIcons.unlock else XnotesIcons.lock,
                stringResource(if (locked) R.string.unlock_zoom else R.string.lock_zoom),
            ) { canvas.toggleZoomLock(); open = false }
            Item(XnotesIcons.map, stringResource(R.string.toolbar_minimap), checked = canvas.minimapVisible) { canvas.toggleMinimap() }
        }
    }
}

@Composable
private fun Item(icon: ImageVector?, label: String, enabled: Boolean = true, checked: Boolean? = null, onClick: () -> Unit) {
    val palette = LocalPalette.current
    DropdownMenuItem(
        text = { Text(label, fontSize = 15.sp) },
        enabled = enabled,
        leadingIcon = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (icon != null) Icon(icon, null, modifier = Modifier.size(18.dp))
                else Text("1:1", fontSize = 11.sp, color = palette.textDim.toComposeColor())
            }
        },
        trailingIcon = if (checked == true) {
            { Icon(XnotesIcons.check, null, tint = palette.accent.toComposeColor(), modifier = Modifier.size(18.dp)) }
        } else null,
        onClick = onClick,
    )
}

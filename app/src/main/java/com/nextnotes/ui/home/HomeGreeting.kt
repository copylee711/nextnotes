package com.nextnotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextnotes.ui.theme.LocalChrome
import com.nextnotes.ui.theme.stickerButton
import com.xnotes.R
import com.xnotes.ui.icons.XnotesIcons
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The note to pick up again: its name and how to open it. */
class LastNote(val name: String, val open: () -> Unit)

/**
 * What heads the home shelves in NextNotes' looks: a greeting for the time of day, then large cards
 * for picking up the last note and for making something new, so a fresh start is one tap away.
 */
@Composable
fun HomeGreeting(
    last: LastNote?,
    onNewNote: () -> Unit,
    onNewCanvas: () -> Unit,
    onImportPdf: () -> Unit,
) {
    val palette = LocalPalette.current
    val chrome = LocalChrome.current
    val scheme = MaterialTheme.colorScheme
    val mint = if (palette.isDark) Color(0xFF1F4A3C) else Color(0xFFDDF5EA)
    val greeting = stringResource(
        when (LocalTime.now().hour) {
            in 5..10 -> R.string.nn_greet_morning
            in 11..12 -> R.string.nn_greet_noon
            in 13..17 -> R.string.nn_greet_afternoon
            in 18..22 -> R.string.nn_greet_evening
            else -> R.string.nn_greet_night
        },
    )
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) }
    val mist = chrome.theme == com.nextnotes.ui.theme.NnTheme.MIST
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)) {
        if (mist) {
            // The date over a bold title, as the system apps head a page.
            Text(today, color = palette.textDim.toComposeColor(), fontSize = 14.sp)
            Text(greeting, color = palette.text.toComposeColor(), fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        } else {
            Text(greeting, color = palette.text.toComposeColor(), fontSize = 30.sp, fontFamily = chrome.display)
            Spacer(Modifier.height(2.dp))
            Text(today, color = palette.textDim.toComposeColor(), fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        val tiles = mistTiles(palette.isDark)
        val cards = buildList {
            if (last != null) add(Card(XnotesIcons.clock, stringResource(R.string.nn_continue_last), last.name, scheme.primaryContainer, last.open, tiles[0]))
            add(Card(XnotesIcons.edit, stringResource(R.string.new_note_menu), stringResource(R.string.nn_new_note_hint), mint, onNewNote, tiles[1]))
            add(Card(XnotesIcons.canvas, stringResource(R.string.new_canvas_menu), stringResource(R.string.nn_new_canvas_hint), scheme.secondaryContainer, onNewCanvas, tiles[2]))
            add(Card(XnotesIcons.importDoc, stringResource(R.string.import_pdf), stringResource(R.string.nn_import_pdf_hint), scheme.tertiaryContainer, onImportPdf, tiles[3]))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Four across on a tablet, two by two on a phone.
            val perRow = if (maxWidth >= 640.dp) cards.size else 2
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                cards.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { QuickCard(it, Modifier.weight(1f)) }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private class Card(
    val icon: ImageVector, val title: String, val detail: String, val fill: Color, val onClick: () -> Unit,
    /** The mist look's icon tile: its fill, then the icon's colour. */
    val tile: Pair<Color, Color>,
)

/** Blue, green, orange and red tiles in the light; lime, sky, orange and pink glows in the dark. */
private fun mistTiles(dark: Boolean): List<Pair<Color, Color>> = if (!dark) listOf(
    Color(0xFFE3ECFE) to Color(0xFF1F5FD6), Color(0xFFDFF5E8) to Color(0xFF16794A),
    Color(0xFFFDEFD9) to Color(0xFF9A5400), Color(0xFFFDE4E1) to Color(0xFFB3261E),
) else listOf(
    Color(0x24C8F169) to Color(0xFFC8F169), Color(0x247CC4FF) to Color(0xFF7CC4FF),
    Color(0x24FFB86B) to Color(0xFFFFB86B), Color(0x24FF8FA3) to Color(0xFFFF8FA3),
)

@Composable
private fun QuickCard(card: Card, modifier: Modifier) {
    val palette = LocalPalette.current
    val chrome = LocalChrome.current
    val shape = RoundedCornerShape(18.dp)
    if (chrome.theme == com.nextnotes.ui.theme.NnTheme.MIST) {
        // A white card; the colour lives in the icon's tile.
        Row(
            modifier.height(88.dp).stickerButton(palette.panel.toComposeColor(), RoundedCornerShape(20.dp), onClick = card.onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).background(card.tile.first, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(card.icon, null, tint = card.tile.second, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, color = palette.text.toComposeColor(), fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
                Text(card.detail, color = palette.textDim.toComposeColor(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    Box(modifier.padding(end = chrome.drop, bottom = chrome.drop)) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).stickerButton(card.fill, shape, onClick = card.onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(card.icon, null, tint = palette.text.toComposeColor(), modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, color = palette.text.toComposeColor(), fontSize = 16.sp, fontFamily = chrome.display, maxLines = 1)
                Text(card.detail, color = palette.textDim.toComposeColor(), fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

package com.nextnotes.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.launch

/** Where each settings section starts in the scrolling column, by its title, so the list beside it can jump there. */
@Stable
class SectionAnchors {
    val top = mutableStateMapOf<String, Int>()
}

val LocalSectionAnchors = compositionLocalOf<SectionAnchors?> { null }

/** One entry in the list of settings sections: its title, which is also its anchor, and its icon tile. */
class SettingsCategory(val title: String, val icon: ImageVector, val tile: Color)

/**
 * The column of settings sections beside the settings themselves, after the system Settings app:
 * a coloured icon tile and a name for each, the one in view highlighted, a tap scrolling to it.
 */
@Composable
fun SettingsNav(categories: List<SettingsCategory>, anchors: SectionAnchors, scroll: ScrollState, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    // The section in view: the last whose title has scrolled to (or past) the top.
    val current by remember(categories) {
        derivedStateOf {
            val at = scroll.value + 24
            categories.lastOrNull { (anchors.top[it.title] ?: Int.MAX_VALUE) <= at }?.title ?: categories.firstOrNull()?.title
        }
    }
    Column(
        modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 20.dp, bottom = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.panel.toComposeColor())) {
            categories.forEachIndexed { i, c ->
                val selected = c.title == current
                Row(
                    Modifier.fillMaxWidth().height(52.dp)
                        .background(if (selected) palette.materialColors.primaryContainer.toComposeColor() else Color.Transparent)
                        .clickable { anchors.top[c.title]?.let { y -> scope.launch { scroll.animateScrollTo(y) } } }
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(c.tile), contentAlignment = Alignment.Center) {
                        Icon(c.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Text(c.title, color = palette.text.toComposeColor(), fontSize = 15.sp, maxLines = 1)
                        if (i < categories.lastIndex && !selected) {
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(palette.border.toComposeColor()))
                        }
                    }
                }
            }
        }
    }
}

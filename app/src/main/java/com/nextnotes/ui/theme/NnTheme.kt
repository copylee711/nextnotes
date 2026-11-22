package com.nextnotes.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xnotes.R
import com.xnotes.core.model.Rgba
import com.xnotes.ui.theme.MaterialColors
import com.xnotes.ui.theme.Palette

/**
 * NextNotes' looks. [MIST] is the default: white cards on a pale grey desk with one blue accent,
 * after the system notes and settings apps, and graphite with a lime accent in the dark.
 * [STICKER] has thick ink outlines and hard offset shadows. [CALM] keeps those colours with thin lines and soft shadows. [COOL] is
 * a quiet slate-and-indigo look with hairlines. [MATERIAL] is xnotes' own seeded Material scheme,
 * with every colour control it had.
 */
enum class NnTheme(val id: String) {
    MIST("mist"),
    STICKER("sticker"),
    CALM("calm"),
    COOL("cool"),
    MATERIAL("material");

    companion object {
        fun fromId(id: String?): NnTheme = entries.firstOrNull { it.id == id } ?: MIST
    }
}

/** How the chrome around the paper is drawn, beyond its colours. */
@Immutable
data class Chrome(
    val theme: NnTheme,
    /** The outline round panels, buttons and cards. */
    val outline: Dp,
    val outlineColor: Color,
    /** A hard shadow this far right and down, in [outlineColor]; zero for none. */
    val drop: Dp,
    /** A soft shadow's elevation, for themes without a hard one. */
    val elevation: Dp,
    /** Headings and other places with a voice of their own. */
    val display: FontFamily,
) {
    val isSticker: Boolean get() = theme == NnTheme.STICKER

    companion object {
        val displayFont = FontFamily(Font(R.font.nn_display))

        fun of(theme: NnTheme, palette: Palette): Chrome = when (theme) {
            // No outlines: cards stand off the desk by their colour and a whisper of shadow.
            NnTheme.MIST -> Chrome(
                theme, 0.dp, palette.border.toColor(), 0.dp, if (palette.isDark) 0.dp else 1.dp, FontFamily.Default,
            )
            NnTheme.STICKER -> Chrome(
                theme, 2.dp, NnColors.ink(palette.isDark).toColor(), 3.dp, 0.dp, displayFont,
            )
            NnTheme.CALM -> Chrome(
                theme, 1.dp, palette.border.toColor(), 0.dp, 2.dp, FontFamily.Default,
            )
            NnTheme.COOL -> Chrome(
                theme, 1.dp, palette.border.toColor(), 0.dp, 3.dp, FontFamily.Default,
            )
            NnTheme.MATERIAL -> Chrome(
                theme, 1.dp, palette.border.toColor(), 0.dp, 0.dp, FontFamily.Default,
            )
        }
    }
}

val LocalChrome = staticCompositionLocalOf {
    Chrome(NnTheme.MATERIAL, 1.dp, Color.Gray, 0.dp, 0.dp, FontFamily.Default)
}

@Composable
fun ProvideChrome(chrome: Chrome, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalChrome provides chrome, content = content)

/**
 * A panel, card or button in the current theme's manner: filled with [fill], outlined, and dropped
 * on its shadow. [pressed] sinks it onto the shadow, the way a sticker button gives under a finger.
 */
fun Modifier.sticker(
    chrome: Chrome,
    fill: Color,
    shape: Shape = RoundedCornerShape(16.dp),
    pressed: Boolean = false,
): Modifier = stickerAt(chrome, fill, shape, if (pressed) 1f else 0f)

/** [sticker] part way pressed: [press] runs from 0, resting, to 1, fully sunk onto its shadow. */
fun Modifier.stickerAt(chrome: Chrome, fill: Color, shape: Shape, press: Float): Modifier {
    val drop = chrome.drop * (1f - press * 2f / 3f)
    var m: Modifier = this
    // Pressed, the face moves onto its shadow by what the shadow lost.
    if (press > 0f && chrome.drop > 0.dp) m = m.offset(chrome.drop - drop, chrome.drop - drop)
    if (drop > 0.dp) {
        val color = chrome.outlineColor
        m = m.drawBehind {
            val d = drop.toPx()
            val outline: Outline = shape.createOutline(size, layoutDirection, this)
            translate(d, d) { drawOutline(outline, color) }
        }
    } else if (chrome.elevation > 0.dp) {
        m = m.shadow(chrome.elevation, shape, clip = false)
    }
    return m.background(fill, shape).border(chrome.outline, chrome.outlineColor, shape)
}

/** A [sticker] that is a button: it sinks onto its shadow while pressed. */
@Composable
fun Modifier.stickerButton(
    fill: Color,
    shape: Shape = RoundedCornerShape(14.dp),
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val chrome = LocalChrome.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Sinks and springs back rather than jumping between the two.
    val press by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 1f else 0f, androidx.compose.animation.core.tween(if (pressed) 60 else 160), label = "stickerPress",
    )
    return this.stickerAt(chrome, fill, shape, press).clip(shape)
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
}

private fun Rgba.toColor() = Color(r, g, b, a)

/** The hand-picked colours of [NnTheme.STICKER] and [NnTheme.CALM]. */
object NnColors {
    val pink = rgb(0xFF6FA3)
    val violet = rgb(0x7A6BFF)
    val sun = rgb(0xFFD24D)
    val mint = rgb(0x4FCB9C)
    val sky = rgb(0x4EA8FF)

    val amber = rgb(0xF5A623)

    fun ink(dark: Boolean) = if (dark) rgb(0x0B0B0D) else rgb(0x1F1F1F)

    fun palette(theme: NnTheme, dark: Boolean): Palette {
        val t = when (theme) {
            NnTheme.MIST -> mist(dark)
            NnTheme.CALM -> calm(dark)
            NnTheme.COOL -> cool(dark)
            else -> sticker(dark)
        }
        val ink = ink(dark)
        val m = MaterialColors.seeded(pink, dark).copy(
            primary = t.accent,
            onPrimary = if (dark) ink else rgb(0xFFFFFF),
            primaryContainer = t.primaryTint,
            onPrimaryContainer = t.text,
            secondary = t.second,
            secondaryContainer = t.secondTint,
            onSecondaryContainer = t.text,
            tertiary = sun,
            tertiaryContainer = t.sunTint,
            onTertiaryContainer = t.text,
            background = t.bg,
            onBackground = t.text,
            surface = t.panel,
            onSurface = t.text,
            surfaceVariant = t.surface,
            onSurfaceVariant = t.textDim,
            // Mist's hairlines are faint; what outlines a control (a switch turned off) needs more.
            outline = if (theme == NnTheme.MIST) rgb(if (dark) 0x6E7686 else 0x8A8F98) else t.border,
            outlineVariant = t.border,
            surfaceDim = t.surface,
            surfaceBright = t.panel,
            surfaceContainerLowest = t.panel,
            surfaceContainerLow = t.panel,
            surfaceContainer = t.panel,
            surfaceContainerHigh = t.surface,
            surfaceContainerHighest = t.surfaceHi,
        )
        return Palette(
            bg = t.bg,
            panel = t.panel,
            paper = t.paper,
            paperBorder = if (theme == NnTheme.STICKER) ink else t.border,
            accent = t.accent,
            border = t.border,
            text = t.text,
            textDim = t.textDim,
            surface = t.surface,
            surfaceHi = t.surfaceHi,
            menuBg = t.panel,
            isDark = dark,
            materialColors = m,
        )
    }

    private class Tones(
        val bg: Rgba, val panel: Rgba, val paper: Rgba, val accent: Rgba, val border: Rgba,
        val text: Rgba, val textDim: Rgba, val surface: Rgba, val surfaceHi: Rgba,
        /** Behind selected chips and the first quick card; the accent's own light tint. */
        val primaryTint: Rgba,
        /** The second colour and its tint, for what sits beside the accent. */
        val second: Rgba, val secondTint: Rgba,
        val sunTint: Rgba,
    )

    private fun sticker(dark: Boolean) = if (!dark) Tones(
        // After the system notes app: a quiet neutral desk, white cards and one amber accent.
        bg = rgb(0xF5F5F5), panel = rgb(0xFFFFFF), paper = rgb(0xFFFFFF), accent = amber,
        border = rgb(0xE3E3E3), text = rgb(0x1F1F1F), textDim = rgb(0x8A8A8A),
        surface = rgb(0xF0F0F0), surfaceHi = rgb(0xE6E6E6),
        primaryTint = rgb(0xFFF0D4), second = sky, secondTint = rgb(0xE3EEFF), sunTint = rgb(0xFDE8E3),
    ) else Tones(
        bg = rgb(0x1C1C1E), panel = rgb(0x2A2A2C), paper = rgb(0x232325), accent = rgb(0xFFB52E),
        border = rgb(0x3A3A3C), text = rgb(0xF2F2F2), textDim = rgb(0xA0A0A5),
        surface = rgb(0x2E2E30), surfaceHi = rgb(0x3A3A3C),
        primaryTint = rgb(0x4A3A1A), second = sky, secondTint = rgb(0x1E3050), sunTint = rgb(0x4A2A26),
    )

    private fun mist(dark: Boolean) = if (!dark) Tones(
        bg = rgb(0xF2F3F5), panel = rgb(0xFFFFFF), paper = rgb(0xFFFFFF), accent = rgb(0x1F5FD6),
        border = rgb(0xE2E4E8), text = rgb(0x1C1C1E), textDim = rgb(0x6B6F76),
        surface = rgb(0xEEEFF2), surfaceHi = rgb(0xE6E8EC),
        primaryTint = rgb(0xE3ECFE), second = rgb(0x1F9D55), secondTint = rgb(0xDFF5E8), sunTint = rgb(0xFDEFD9),
    ) else Tones(
        bg = rgb(0x0F1115), panel = rgb(0x1B1F26), paper = rgb(0x171A20), accent = rgb(0xC8F169),
        border = rgb(0x2A303A), text = rgb(0xECEEF2), textDim = rgb(0xA3AAB6),
        surface = rgb(0x232831), surfaceHi = rgb(0x2C323C),
        primaryTint = rgb(0x2C3620), second = rgb(0x7CC4FF), secondTint = rgb(0x1C2E40), sunTint = rgb(0x3A2C1A),
    )

    private fun calm(dark: Boolean) = if (!dark) Tones(
        bg = rgb(0xF5F3F0), panel = rgb(0xFFFFFF), paper = rgb(0xFFFFFF), accent = rgb(0xD24A80),
        border = rgb(0xE4DFE8), text = rgb(0x2A2733), textDim = rgb(0x736C80),
        surface = rgb(0xF1EEF4), surfaceHi = rgb(0xE8E3EE),
        primaryTint = rgb(0xFBE4EE), second = violet, secondTint = rgb(0xEDEBFB), sunTint = rgb(0xFBF3D8),
    ) else Tones(
        bg = rgb(0x1D1C22), panel = rgb(0x28272F), paper = rgb(0x24232A), accent = rgb(0xF07BA8),
        border = rgb(0x3D3B47), text = rgb(0xEEEBF4), textDim = rgb(0xA9A3B5),
        surface = rgb(0x33313C), surfaceHi = rgb(0x3D3B47),
        primaryTint = rgb(0x4A2A3B), second = violet, secondTint = rgb(0x34315A), sunTint = rgb(0x463E22),
    )

    private fun cool(dark: Boolean) = if (!dark) Tones(
        bg = rgb(0xEDF0F4), panel = rgb(0xFFFFFF), paper = rgb(0xFFFFFF), accent = rgb(0x3B5BDB),
        border = rgb(0xDADFE7), text = rgb(0x18202D), textDim = rgb(0x5E6979),
        surface = rgb(0xF2F4F8), surfaceHi = rgb(0xE5E9F0),
        primaryTint = rgb(0xE5ECFF), second = violet, secondTint = rgb(0xEBEAFB), sunTint = rgb(0xE2F3F5),
    ) else Tones(
        bg = rgb(0x11141A), panel = rgb(0x191D25), paper = rgb(0x151920), accent = rgb(0x7D9BFF),
        border = rgb(0x2A303B), text = rgb(0xE5E9F0), textDim = rgb(0x97A1B2),
        surface = rgb(0x21262F), surfaceHi = rgb(0x2A303B),
        primaryTint = rgb(0x253055), second = violet, secondTint = rgb(0x2C2A4A), sunTint = rgb(0x1E3A3F),
    )

    private fun rgb(v: Int) = Rgba((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF, 255)
}

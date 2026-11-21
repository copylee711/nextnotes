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
 * NextNotes' looks. [STICKER] is the default: thick ink outlines, hard offset shadows and the pink
 * and violet of the N icon. [CALM] keeps those colours with thin lines and soft shadows. [COOL] is
 * a quiet slate-and-indigo look with hairlines. [MATERIAL] is xnotes' own seeded Material scheme,
 * with every colour control it had.
 */
enum class NnTheme(val id: String) {
    STICKER("sticker"),
    CALM("calm"),
    COOL("cool"),
    MATERIAL("material");

    companion object {
        fun fromId(id: String?): NnTheme = entries.firstOrNull { it.id == id } ?: STICKER
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
): Modifier {
    val drop = if (pressed) chrome.drop / 3 else chrome.drop
    var m: Modifier = this
    // Pressed, the face moves onto its shadow by what the shadow lost.
    if (pressed && chrome.drop > 0.dp) m = m.offset(chrome.drop - drop, chrome.drop - drop)
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
    return this.sticker(chrome, fill, shape, pressed).clip(shape)
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

    fun ink(dark: Boolean) = if (dark) rgb(0x0B0A12) else rgb(0x25212F)

    fun palette(theme: NnTheme, dark: Boolean): Palette {
        val t = when (theme) {
            NnTheme.CALM -> calm(dark)
            NnTheme.COOL -> cool(dark)
            else -> sticker(dark)
        }
        val ink = ink(dark)
        val m = MaterialColors.seeded(pink, dark).copy(
            primary = t.accent,
            onPrimary = if (dark) ink else rgb(0xFFFFFF),
            primaryContainer = t.pinkTint,
            onPrimaryContainer = t.text,
            secondary = violet,
            secondaryContainer = t.violetTint,
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
            outline = t.border,
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
        val pinkTint: Rgba, val violetTint: Rgba, val sunTint: Rgba,
    )

    private fun sticker(dark: Boolean) = if (!dark) Tones(
        // A cool periwinkle desk, so the warm sticker colours stand out on it.
        bg = rgb(0xEDF0FA), panel = rgb(0xFFFFFF), paper = rgb(0xFFFFFF), accent = rgb(0xE8457E),
        border = rgb(0xD3D6E8), text = rgb(0x25212F), textDim = rgb(0x646A80),
        surface = rgb(0xF3F5FC), surfaceHi = rgb(0xE3E7F5),
        pinkTint = rgb(0xFFE1EC), violetTint = rgb(0xEAE7FF), sunTint = rgb(0xFFF2C2),
    ) else Tones(
        bg = rgb(0x2B2740), panel = rgb(0x3A3555), paper = rgb(0x2F2B44), accent = rgb(0xFF7FB0),
        border = rgb(0x5A527E), text = rgb(0xF4F1FF), textDim = rgb(0xB9B2D6),
        surface = rgb(0x46406A), surfaceHi = rgb(0x524B7A),
        pinkTint = rgb(0x5A2E48), violetTint = rgb(0x3E3970), sunTint = rgb(0x5A4A1E),
    )

    private fun calm(dark: Boolean) = if (!dark) Tones(
        bg = rgb(0xF5F3F0), panel = rgb(0xFFFFFF), paper = rgb(0xFFFFFF), accent = rgb(0xD24A80),
        border = rgb(0xE4DFE8), text = rgb(0x2A2733), textDim = rgb(0x736C80),
        surface = rgb(0xF1EEF4), surfaceHi = rgb(0xE8E3EE),
        pinkTint = rgb(0xFBE4EE), violetTint = rgb(0xEDEBFB), sunTint = rgb(0xFBF3D8),
    ) else Tones(
        bg = rgb(0x1D1C22), panel = rgb(0x28272F), paper = rgb(0x24232A), accent = rgb(0xF07BA8),
        border = rgb(0x3D3B47), text = rgb(0xEEEBF4), textDim = rgb(0xA9A3B5),
        surface = rgb(0x33313C), surfaceHi = rgb(0x3D3B47),
        pinkTint = rgb(0x4A2A3B), violetTint = rgb(0x34315A), sunTint = rgb(0x463E22),
    )

    private fun cool(dark: Boolean) = if (!dark) Tones(
        bg = rgb(0xEDF0F4), panel = rgb(0xFFFFFF), paper = rgb(0xFFFFFF), accent = rgb(0x3B5BDB),
        border = rgb(0xDADFE7), text = rgb(0x18202D), textDim = rgb(0x5E6979),
        surface = rgb(0xF2F4F8), surfaceHi = rgb(0xE5E9F0),
        pinkTint = rgb(0xE5ECFF), violetTint = rgb(0xEBEAFB), sunTint = rgb(0xE2F3F5),
    ) else Tones(
        bg = rgb(0x11141A), panel = rgb(0x191D25), paper = rgb(0x151920), accent = rgb(0x7D9BFF),
        border = rgb(0x2A303B), text = rgb(0xE5E9F0), textDim = rgb(0x97A1B2),
        surface = rgb(0x21262F), surfaceHi = rgb(0x2A303B),
        pinkTint = rgb(0x253055), violetTint = rgb(0x2C2A4A), sunTint = rgb(0x1E3A3F),
    )

    private fun rgb(v: Int) = Rgba((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF, 255)
}

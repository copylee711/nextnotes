package com.nextnotes.ui.ai

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.xnotes.core.model.Rgba
import com.xnotes.platform.AndroidText
import com.xnotes.platform.MathRendering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TablesExtension

/** Markdown with LaTeX maths pulled out first, so `_` and `*` inside a formula stay maths. */
internal class MathText(val markdown: String, val formulas: List<String>) {
    companion object {
        /** Inline formulas become `n`; display formulas take a paragraph of their own as `n`. */
        private val display = Regex("""\$\$(.+?)\$\$|\\\[(.+?)\\]""", RegexOption.DOT_MATCHES_ALL)
        private val inline = Regex("""\\\((.+?)\\\)|(?<![\\$])\$(?![\s$])([^$\n]+?)(?<!\s)\$(?!\d)""")

        fun of(source: String): MathText {
            val formulas = mutableListOf<String>()
            // Code spans and blocks are left alone: a `$` in code is not maths.
            val pieces = Regex("""```[\s\S]*?(```|$)|`[^`\n]*`""").split(source)
            val codes = Regex("""```[\s\S]*?(```|$)|`[^`\n]*`""").findAll(source).map { it.value }.toList()
            val out = StringBuilder()
            pieces.forEachIndexed { i, piece ->
                var p = display.replace(piece) { m ->
                    formulas += (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
                    "\n\n${formulas.size - 1}\n\n"
                }
                p = inline.replace(p) { m ->
                    formulas += (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
                    "${formulas.size - 1}"
                }
                out.append(p)
                if (i < codes.size) out.append(codes[i])
            }
            return MathText(out.toString(), formulas)
        }

        val inlineToken = Regex("(\\d+)")
        val displayToken = Regex("^(\\d+)$")
    }
}

/** Formula rasters, shared by every answer on screen. */
private object FormulaCache {
    class Formula(val bitmap: Bitmap, val widthEm: Float, val heightEm: Float)

    private val cache = LruCache<String, Formula>(256)
    private const val SIZE_PT = 24.0

    /** Off the main thread: it may wait for the math engine. Null for LaTeX that will not set. */
    fun get(latex: String, display: Boolean, color: Rgba): Formula? {
        val key = "$display|${color.toArgb()}|$latex"
        cache.get(key)?.let { return it }
        val (bmp, box) = MathRendering.formulaBitmap(latex, SIZE_PT, color, display) ?: return null
        val em = SIZE_PT * AndroidText.POINTS_TO_PX
        return Formula(bmp, (box.width / em).toFloat(), (box.height / em).toFloat()).also { cache.put(key, it) }
    }
}

/** GitHub's tables and strikethrough, which models use freely, on top of CommonMark. */
internal val parser: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
    .build()

/**
 * Column widths that fit [avail]: columns narrower than an even share keep their width, and what
 * is left is shared evenly by the rest, which wrap their words; none goes under [min] or its own
 * [floor], what it cannot wrap. Widths that
 * already fit come back as they are.
 */
internal fun fitColumns(natural: IntArray, avail: Int, min: Int, floor: IntArray = IntArray(natural.size)): IntArray {
    if (natural.sum() <= avail || natural.isEmpty()) return natural
    val out = IntArray(natural.size)
    val open = natural.indices.toMutableList()
    var left = avail
    while (open.isNotEmpty()) {
        val share = left / open.size
        val narrow = open.filter { natural[it] <= share }
        if (narrow.isEmpty()) break
        for (i in narrow) {
            out[i] = natural[i]
            left -= natural[i]
        }
        open.removeAll(narrow)
    }
    for (i in open) out[i] = maxOf(min, left / open.size)
    // A column never squeezes past what it cannot wrap, even if the table then runs wide.
    for (i in out.indices) out[i] = maxOf(out[i], floor[i])
    return out
}

/** How tables set: scrolling sideways at their natural width, or squeezed to the panel and wrapped. */
object TableLook {
    private const val PREFS = "nextnotes_ai"
    private const val KEY = "table_fit"
    var fit by androidx.compose.runtime.mutableStateOf(false)
        private set
    private var loaded = false

    fun load(context: android.content.Context) {
        if (loaded) return
        loaded = true
        fit = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)
    }

    fun set(context: android.content.Context, value: Boolean) {
        fit = value
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}

/** A table's rows in order, each flagged when it is the header, with its cells. */
internal fun tableRows(table: Node): List<Pair<Boolean, List<TableCell>>> {
    val rows = ArrayList<Pair<Boolean, List<TableCell>>>()
    var section = table.firstChild
    while (section != null) {
        var row = section.firstChild
        while (row != null) {
            val cells = ArrayList<TableCell>()
            var cell = row.firstChild
            while (cell != null) {
                if (cell is TableCell) cells += cell
                cell = cell.next
            }
            rows += (section is TableHead) to cells
            row = row.next
        }
        section = section.next
    }
    return rows
}

/** An AI answer: Markdown blocks with inline and display maths. */
@Composable
fun MarkdownMath(source: String, color: Color, dim: Color, codeBg: Color, ink: Rgba, modifier: Modifier = Modifier) {
    val parsed = remember(source) {
        val math = MathText.of(source)
        math to parser.parse(math.markdown)
    }
    val (math, doc) = parsed
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Blocks(doc, math, color, dim, codeBg, ink)
        }
    }
}

@Composable
private fun Blocks(parent: Node, math: MathText, color: Color, dim: Color, codeBg: Color, ink: Rgba) {
    var node = parent.firstChild
    while (node != null) {
        Block(node, math, color, dim, codeBg, ink)
        node = node.next
    }
}

@Composable
private fun Block(node: Node, math: MathText, color: Color, dim: Color, codeBg: Color, ink: Rgba) {
    when (node) {
        is Paragraph -> {
            val plain = (node.firstChild as? org.commonmark.node.Text)?.takeIf { node.firstChild === node.lastChild }?.literal
            val display = plain?.trim()?.let { MathText.displayToken.find(it) }
            if (display != null) {
                DisplayFormula(math.formulas[display.groupValues[1].toInt()], ink, color)
            } else {
                InlineText(node, math, color, codeBg, ink, 15.sp)
            }
        }
        is Heading -> InlineText(node, math, color, codeBg, ink, if (node.level <= 2) 18.sp else 16.sp, bold = true)
        is BulletList, is OrderedList -> {
            val ordered = node as? OrderedList
            var index = ordered?.markerStartNumber ?: 1
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        Row {
                            Text(if (ordered != null) "${index++}." else "•", color = dim, fontSize = 15.sp, modifier = Modifier.width(22.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Blocks(item, math, color, dim, codeBg, ink) }
                        }
                    }
                    item = item.next
                }
            }
        }
        is FencedCodeBlock -> com.nextnotes.ai.Artifacts.of(node.info, node.literal)
            ?.let { ArtifactCard(it) }
            ?: CodeBox(node.literal.trimEnd(), color, codeBg)
        is IndentedCodeBlock -> CodeBox(node.literal.trimEnd(), color, codeBg)
        is BlockQuote -> Row {
            Box(Modifier.width(3.dp).padding(vertical = 2.dp).background(dim).size(3.dp, 24.dp))
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Blocks(node, math, dim, dim, codeBg, ink)
            }
        }
        is ThematicBreak -> HorizontalDivider(color = dim.copy(alpha = 0.4f))
        is TableBlock -> Table(node, math, color, dim, codeBg, ink)
        else -> Blocks(node, math, color, dim, codeBg, ink)
    }
}

/**
 * A table with its columns lined up: each as wide as its widest cell up to a cap, past which the
 * words wrap, and the whole table scrolling sideways when it is still wider than the panel.
 */
@Composable
private fun Table(table: TableBlock, math: MathText, color: Color, dim: Color, codeBg: Color, ink: Rgba) {
    val rows = remember(table) { tableRows(table) }
    val cols = rows.maxOfOrNull { it.second.size } ?: return
    val line = dim.copy(alpha = 0.3f)
    val head = codeBg
    val stripe = codeBg.copy(alpha = codeBg.alpha * 0.4f)
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { TableLook.load(context) }
    val fit = TableLook.fit
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    // The panel's width, read before the sideways scroll takes the limit away.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val avail = constraints.maxWidth
    Box(
        Modifier.horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(8.dp)).border(1.dp, line, RoundedCornerShape(8.dp)),
    ) {
        androidx.compose.ui.layout.Layout(
            content = {
                rows.forEachIndexed { r, (header, cells) ->
                    for (c in 0 until cols) {
                        val bg = when {
                            header -> head
                            r % 2 == 0 -> stripe
                            else -> Color.Transparent
                        }
                        Box(Modifier.background(bg).border(0.5.dp, line).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            cells.getOrNull(c)?.let { InlineText(it, math, color, codeBg, ink, 14.sp, bold = header) }
                        }
                    }
                }
            },
        ) { measurables, _ ->
            val cap = 220.dp.roundToPx()
            val natural = IntArray(cols)
            // What a column cannot go under: its widest word or formula, which does not wrap.
            val floor = IntArray(cols)
            measurables.forEachIndexed { i, m ->
                val inf = androidx.compose.ui.unit.Constraints.Infinity
                natural[i % cols] = maxOf(natural[i % cols], minOf(cap, m.maxIntrinsicWidth(inf)))
                floor[i % cols] = maxOf(floor[i % cols], m.minIntrinsicWidth(inf))
            }
            for (c in 0 until cols) natural[c] = maxOf(natural[c], floor[c])
            // Fitting, the columns share the panel's width as far as their formulas let them, and
            // the scroll catches what still sticks out; otherwise each keeps its own width.
            val widths = if (fit) fitColumns(natural, avail, 48.dp.roundToPx(), floor) else natural
            val heights = IntArray(rows.size)
            measurables.forEachIndexed { i, m ->
                heights[i / cols] = maxOf(heights[i / cols], m.minIntrinsicHeight(widths[i % cols]))
            }
            val placeables = measurables.mapIndexed { i, m ->
                m.measure(androidx.compose.ui.unit.Constraints.fixed(widths[i % cols], heights[i / cols]))
            }
            layout(widths.sum(), heights.sum()) {
                var y = 0
                for (r in rows.indices) {
                    var x = 0
                    for (c in 0 until cols) {
                        placeables[r * cols + c].place(x, y)
                        x += widths[c]
                    }
                    y += heights[r]
                }
            }
        }
    }
    }
    Text(
        if (fit) "↔ 改为横向滚动" else "⇥ 适应宽度", color = dim, fontSize = 12.sp,
        modifier = Modifier.align(androidx.compose.ui.Alignment.End).clip(RoundedCornerShape(6.dp))
            .clickable { TableLook.set(context, !fit) }.padding(horizontal = 6.dp, vertical = 2.dp),
    )
    }
}

@Composable
private fun CodeBox(code: String, color: Color, bg: Color) {
    Box(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(6.dp)).horizontalScroll(rememberScrollState()).padding(10.dp),
    ) {
        Text(code, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun DisplayFormula(latex: String, ink: Rgba, fallback: Color) {
    val formula by produceState<FormulaCache.Formula?>(null, latex, ink) {
        value = withContext(Dispatchers.Default) { FormulaCache.get(latex, true, ink) }
    }
    val f = formula
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        if (f == null) {
            Text(latex, color = fallback, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        } else {
            val fontPx = with(density) { 17.sp.toDp() }
            Image(
                f.bitmap.asImageBitmap(), contentDescription = latex, contentScale = ContentScale.FillBounds,
                modifier = Modifier.size(fontPx * f.widthEm, fontPx * f.heightEm),
            )
        }
    }
}

/** A run of inline Markdown, with formulas set inline at the text's size. */
@Composable
private fun InlineText(node: Node, math: MathText, color: Color, codeBg: Color, ink: Rgba, size: androidx.compose.ui.unit.TextUnit, bold: Boolean = false) {
    val raw = remember(node) { buildAnnotatedString { appendInlines(node, codeBg) } }
    val ids = remember(raw) { MathText.inlineToken.findAll(raw.text).map { it.groupValues[1].toInt() }.distinct().toList() }
    val formulas by produceState(emptyMap<Int, FormulaCache.Formula?>(), ids, ink) {
        value = withContext(Dispatchers.Default) { ids.associateWith { FormulaCache.get(math.formulas[it], false, ink) } }
    }
    // Swap each token for an inline slot (or the source, while it sets or if it will not).
    val text = remember(raw, formulas) {
        buildAnnotatedString {
            var last = 0
            for (m in MathText.inlineToken.findAll(raw.text)) {
                append(raw.subSequence(last, m.range.first))
                val id = m.groupValues[1].toInt()
                if (formulas[id] != null) appendInlineContent("f$id", math.formulas[id])
                else withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(math.formulas[id]) }
                last = m.range.last + 1
            }
            append(raw.subSequence(last, raw.length))
        }
    }
    val inline = formulas.mapNotNull { (id, f) ->
        f ?: return@mapNotNull null
        "f$id" to InlineTextContent(Placeholder(f.widthEm.em, f.heightEm.em, PlaceholderVerticalAlign.TextCenter)) {
            Image(f.bitmap.asImageBitmap(), contentDescription = math.formulas[id], contentScale = ContentScale.FillBounds)
        }
    }.toMap()
    Text(
        text, color = color, fontSize = size, lineHeight = size * 1.5,
        fontWeight = if (bold) FontWeight.SemiBold else null, inlineContent = inline,
    )
}

private fun AnnotatedString.Builder.appendInlines(parent: Node, codeBg: Color) {
    var n = parent.firstChild
    while (n != null) {
        when (n) {
            is org.commonmark.node.Text -> append(n.literal)
            is SoftLineBreak -> append(" ")
            is HardLineBreak -> append("\n")
            is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(n.literal) }
            is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInlines(n, codeBg) }
            is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(n, codeBg) }
            is Link -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { appendInlines(n, codeBg) }
            is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInlines(n, codeBg) }
            else -> appendInlines(n, codeBg)
        }
        n = n.next
    }
}

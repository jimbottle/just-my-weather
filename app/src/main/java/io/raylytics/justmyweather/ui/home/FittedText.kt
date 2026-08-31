package io.raylytics.justmyweather.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/*
 * A value that grows to the size its tile allows. This is what makes "width is
 * prominence" literally true on the glance: the same reading is hero-sized at
 * full width and steps down as its tile narrows, and a long phrase — "Chance
 * Showers And Thunderstorms" — shrinks to fit instead of becoming a tower of
 * 120sp words or breaking "Thunderstorms" across two lines.
 *
 * Why this is a Layout of its own and not a Text with a size picked from the
 * constraints: the grid sizes each row to its tallest tile with
 * `IntrinsicSize.Min`, and Compose's constraint-reading composable
 * (BoxWithConstraints) throws when asked for intrinsics. Measuring the text
 * ourselves inside a MeasurePolicy answers both questions — "how tall am I at
 * this width?" during the intrinsic pass and "what do I draw?" during measure
 * — from the same fit, in the same frame, with no first-frame flash at the
 * wrong size.
 */

/**
 * Draw [text] at the largest size in `[floor, ceiling]` at which it fits the
 * width it is given in at most [maxLines] lines with no word broken across
 * lines. At the floor it is ellipsised rather than shrunk further.
 *
 * Only the size (and line height, in proportion) is chosen here; face, weight
 * and alignment come from [style]. The text is exposed to accessibility as
 * text, so a screen reader — and a UI test — sees "72°" exactly as it would on
 * a Text.
 */
@Composable
internal fun FittedText(
    text: String,
    style: TextStyle,
    ceiling: TextUnit,
    floor: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
) {
    val measurer = rememberTextMeasurer()
    val aligned = style.copy(textAlign = TextAlign.Center, color = color)
    val fitter = remember(text, aligned, ceiling, floor, maxLines, measurer) {
        TextFitter(text, aligned, ceiling, floor, maxLines, measurer)
    }
    Layout(
        modifier =
            modifier
                .semantics { this.text = AnnotatedString(text) }
                .drawBehind { fitter.last?.let { drawText(it) } },
        measurePolicy = fitter,
    )
}

/**
 * The fit itself, doubling as the measure policy so the intrinsic and measure
 * passes share one answer per width. Stateful only as a cache of the last fit:
 * the row asks for the intrinsic height at a width and then measures at the
 * same width, and a binary search over font sizes should run once per pass,
 * not twice.
 */
private class TextFitter(
    private val text: String,
    private val style: TextStyle,
    private val ceiling: TextUnit,
    private val floor: TextUnit,
    private val maxLines: Int,
    private val measurer: TextMeasurer,
) : MeasurePolicy {
    /** The fit for [lastWidth], read by the draw pass. */
    var last: TextLayoutResult? = null
        private set
    private var lastWidth = -1

    private val words = text.split(' ').filter { it.isNotEmpty() }

    private fun Density.fit(maxWidth: Int): TextLayoutResult {
        last?.takeIf { lastWidth == maxWidth }?.let { return it }
        // Whole sp steps: a binary search over integers finds the largest size
        // that fits in a handful of measurements, and sub-sp precision is
        // invisible next to the tile's own rounding.
        var lo = floor.value.roundToInt()
        var hi = ceiling.value.roundToInt().coerceAtLeast(lo)
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (fits(mid.sp, maxWidth)) lo = mid else hi = mid - 1
        }
        // Line height is settled after the size, by line count: wrapped text
        // needs air between its lines, but a single line keeps the style's
        // own ratio — the hero's is 1.0, and a fitted hero must not stand
        // taller than the unfitted one it replaces.
        val wrapped = measure(lo.sp, maxWidth, TextOverflow.Ellipsis, MULTILINE_RATIO)
        val result = if (wrapped.lineCount > 1) wrapped else measure(lo.sp, maxWidth, TextOverflow.Ellipsis, ownRatio)
        last = result
        lastWidth = maxWidth
        return result
    }

    /** Fits means: no word needs breaking, and the whole thing lands in
     * [maxLines] with nothing clipped. Line height plays no part in either,
     * so the search measures at one ratio and lets [fit] choose the final. */
    private fun Density.fits(size: TextUnit, maxWidth: Int): Boolean {
        val longestWord =
            words.maxOfOrNull { word ->
                measurer
                    .measure(AnnotatedString(word), styleAt(size, ownRatio), softWrap = false, density = this)
                    .size
                    .width
            } ?: 0
        if (longestWord > maxWidth) return false
        return !measure(size, maxWidth, TextOverflow.Clip, MULTILINE_RATIO).hasVisualOverflow
    }

    private fun Density.measure(size: TextUnit, maxWidth: Int, overflow: TextOverflow, ratio: Float): TextLayoutResult =
        measurer.measure(
            text = AnnotatedString(text),
            style = styleAt(size, ratio),
            overflow = overflow,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = maxWidth),
            density = this,
        )

    /** The style's own line-height ratio, or a plain one where it has none. */
    private val ownRatio: Float =
        if (style.lineHeight.isSp && style.fontSize.isSp) style.lineHeight.value / style.fontSize.value else 1f

    private fun styleAt(size: TextUnit, ratio: Float): TextStyle =
        style.copy(fontSize = size, lineHeight = size * ratio)

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val result = fit(constraints.maxWidth)
        val width = constraints.constrainWidth(result.size.width)
        val height = constraints.constrainHeight(result.size.height)
        return layout(width, height) {}
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        fit(width).size.height

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        fit(width).size.height

    // Width intrinsics are asked of tiles only when a row has no width to
    // give, which the grid never does — but they must still be sane, and
    // "the text at its floor on one line" is the honest minimum.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurer.measure(AnnotatedString(text), styleAt(floor, ownRatio), softWrap = false, density = this).size.width

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurer.measure(AnnotatedString(text), styleAt(ceiling, ownRatio), softWrap = false, density = this).size.width

    private companion object {
        /** Line height for wrapped text, as a multiple of the font size. */
        const val MULTILINE_RATIO = 1.15f
    }
}

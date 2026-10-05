package io.raylytics.justmyweather.widget

import kotlin.math.ceil

/**
 * The widget's stand-in for the glance's fitted text.
 *
 * On the glance a value is measured against its tile and drawn at the largest
 * size that fits, which is what makes "size is prominence" true. A widget is
 * drawn by the launcher from a description, and there is nothing to measure
 * against — so this ESTIMATES: a glyph is taken to be about [GLYPH_WIDTH] of
 * the font size wide and a line [LINE_HEIGHT] of it tall, and the size is
 * stepped down from the ceiling until the text fits the box in at most
 * [maxLines] lines. The factors are tuned for the digits and short words a
 * tile shows at the app's light weight; a long phrase wraps the way the
 * launcher wraps it, and the estimate only has to be close enough that the
 * phrase is not clipped.
 *
 * Pure, so the arithmetic tests on the JVM: it reads no screen, no font.
 */
object TextFit {
    /** Average glyph width as a share of font size: digits and lower-case
     * letters at a light weight. Wide enough to be safe for "72°". */
    const val GLYPH_WIDTH = 0.58f

    /** A line's height as a share of font size, with the launcher's own
     * leading: the glance's hero style sets lineHeight = fontSize, but
     * RemoteViews adds a little above and below that we do not control. */
    const val LINE_HEIGHT = 1.15f

    /** Sizes step down by this much per try: fine enough that the result
     * never looks a size short, coarse enough to be a handful of steps. */
    private const val STEP = 2f

    /**
     * The largest size in `[floor, ceiling]` (sp) at which [text] is
     * estimated to fit a [widthDp] × [heightDp] box in at most [maxLines]
     * lines. At the floor the text is drawn there anyway — the launcher
     * ellipsises what still does not fit, as the glance does.
     */
    fun sp(
        text: String,
        widthDp: Float,
        heightDp: Float,
        ceilingSp: Float,
        floorSp: Float,
        maxLines: Int = 1,
    ): Float {
        if (text.isEmpty() || widthDp <= 0f || heightDp <= 0f) return floorSp
        var size = ceilingSp
        while (size > floorSp) {
            if (fits(text, widthDp, heightDp, size, maxLines)) return size
            size -= STEP
        }
        return floorSp
    }

    private fun fits(text: String, widthDp: Float, heightDp: Float, sizeSp: Float, maxLines: Int): Boolean {
        val lineCapacity = (widthDp / (sizeSp * GLYPH_WIDTH)).toInt()
        if (lineCapacity < 1) return false
        // Words are never broken across lines, so a single word longer than
        // a line cannot fit at this size whatever the line count.
        if (text.split(' ').any { it.length > lineCapacity }) return false
        val lines = ceil(text.length / lineCapacity.toFloat()).toInt().coerceAtLeast(1)
        return lines <= maxLines && lines * sizeSp * LINE_HEIGHT <= heightDp
    }
}

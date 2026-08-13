package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.view.DisplayValue
import io.raylytics.justmyweather.view.WeatherField
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Width rules for the glance's label · value rows.
 *
 * These exist because this one Row has regressed twice — first a long label
 * starved the value, then the fix hard-capped both sides at 50% — and each time
 * the whole JVM suite stayed green and Maestro stayed green, because both
 * check that text is PRESENT, not how much room it got. The only way to catch
 * a layout rule is to measure it, so these assert real widths.
 */
class FieldRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private val longValue = "Thunderstorm Light Rain Fog/Mist"
    private val longLabel = "Sky conditions at the nearest reporting station right now"

    private fun show(label: String, value: String, width: Dp = 240.dp) {
        compose.setContent {
            // A hard container width stands in for "screen minus padding",
            // which is what the real block is bounded by.
            Box(Modifier.width(width)) {
                FieldRows(
                    rows = listOf(DisplayValue(WeatherField.CONDITIONS, label, value)),
                    blockMaxWidth = 240.dp,
                    rowSpacing = 6.dp,
                )
            }
        }
    }

    // right - left rather than the DpRect.width extension: `width` is also the
    // layout modifier imported above, and the two collide.
    private fun widthOf(text: String): Dp =
        compose.onNodeWithText(text).getUnclippedBoundsInRoot().let { it.right - it.left }

    private fun leftOf(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().left

    private fun rightOf(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().right

    @Test
    fun shortLabelLeavesTheValueMostOfTheRow() {
        // The case that distinguishes "shared width" from a 50/50 cap: under
        // the cap this value could never exceed half the row no matter how
        // short the label was.
        show(label = "Sky", value = longValue)
        assertTrue(
            "value should get well over half the row, got ${widthOf(longValue)}",
            widthOf(longValue) > 140.dp,
        )
    }

    @Test
    fun aRunawayLabelStillLeavesTheValueReadable() {
        // The original defect: an uncapped free-text label consumed the row and
        // hid the fact the row exists to show.
        show(label = longLabel, value = "72°")
        assertTrue(
            "label must be capped near 60%, got ${widthOf(longLabel)}",
            widthOf(longLabel) <= 240.dp * 0.62f,
        )
        // The COMPLEMENT, not `> 0`. A width greater than zero is true by
        // construction here — the value is weighted, so it always receives
        // row minus label, and the label can never exceed the cap. Asserting
        // the actual floor is what catches a change that keeps the label bound
        // honest while squeezing the value some other way (bounding it, or
        // dropping weight(1f)), which is the shape of the 50/50 regression
        // this suite exists to remember.
        assertTrue(
            "value must get the whole remainder, got ${widthOf("72°")}",
            widthOf("72°") >= 240.dp * (1f - 0.62f),
        )
    }

    @Test
    fun aShortPairIsNotStretchedApart() {
        show(label = "Wind", value = "8 mph")
        assertTrue(widthOf("Wind") < 100.dp)

        // The point of the pair, and the thing every width assertion in this
        // file is blind to: WHERE the value starts. The value is weighted, so
        // its width is the row's remainder under any arrangement — only its
        // left edge moves, which is why no width check can see this and why
        // reverting the layout once left the whole suite green.
        //
        // Under SpaceBetween the weighted value ate all the slack, so its node
        // sat flush against the label (gap 0) while TextAlign.End threw the
        // glyphs to the far side of the row — and a lone word out there was
        // twice mistaken for a button.
        //
        // What this pins is the arrangement. The alignment inside the node is
        // NOT observable through bounds: with a weighted child, End and Start
        // give identical node edges and differ only in where the glyphs land.
        // TextAlign.Start is also Compose's default, so losing it takes an
        // explicit TextAlign.End rather than a deletion — a deliberate act,
        // not a silent drift.
        val gap = leftOf("8 mph") - rightOf("Wind")
        assertTrue(
            "value must start one gap after the label, got $gap (expected about $VALUE_GAP)",
            gap > VALUE_GAP - 2.dp && gap < VALUE_GAP + 2.dp,
        )
    }

    @Test
    fun theCapFollowsTheACTUALRowWidthNotTheDensityConstant() {
        // The finding this test was added for. The block's nominal ceiling is
        // 240dp, but a narrow window (small screen, or a large display-size
        // setting) gives the row far less. A cap computed from the constant
        // would let the label take 144dp of a 180dp row — 80% — and, because
        // the label is un-weighted and measured first, the value would be left
        // with the scraps it is supposed to be guaranteed.
        val narrow = 180.dp
        show(label = longLabel, value = "72°", width = narrow)
        assertTrue(
            "label must be capped against the real $narrow row, got ${widthOf(longLabel)}",
            widthOf(longLabel) <= narrow * 0.62f,
        )
        // The same complement as the wide case, against the REAL row width.
        // "> 0" would be vacuous here for exactly the reason stated above: the
        // value is weighted, so it can only fail by being eliminated outright.
        assertTrue(
            "value must get the whole remainder at $narrow, got ${widthOf("72°")}",
            widthOf("72°") >= narrow * (1f - 0.62f),
        )
    }
}

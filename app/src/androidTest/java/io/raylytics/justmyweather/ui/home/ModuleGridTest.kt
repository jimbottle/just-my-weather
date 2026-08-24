package io.raylytics.justmyweather.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.ui.theme.JustMyWeatherTheme
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ModuleSpan
import io.raylytics.justmyweather.view.ModuleValue
import io.raylytics.justmyweather.view.WeatherField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The two things about the module grid that only a device can answer: what the
 * flow packing actually MEASURES to, and whether the arrange operations are
 * reachable without a gesture.
 *
 * Layout is asserted in real dp rather than by counting composables — the
 * whole point of a span is the width it occupies, and a grid that composed the
 * right tiles at the wrong sizes would pass any structural check. (This is the
 * gap FieldRowsTest used to cover for the old row layout, which the grid
 * replaced.)
 *
 * The gesture path itself — long-press, wiggle, drag, tap-to-cycle — is
 * verified by .maestro/06-arrange.yaml, which drives real touch events;
 * Compose's test gestures do not reproduce the pointer-stream subtleties this
 * grid was debugged against.
 */
class ModuleGridTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Wide enough that a quarter tile is comfortably above any minimum, and
     * fixed so the expected column arithmetic below is exact. */
    private val gridWidth = 400.dp

    private val moves = mutableListOf<Pair<WeatherField, Int>>()
    private val resizes = mutableListOf<WeatherField>()

    private fun show(vararg modules: ModuleValue) {
        compose.setContent {
            JustMyWeatherTheme {
                Box(Modifier.width(gridWidth)) {
                    ModuleGrid(
                        modules = modules.toList(),
                        arranging = false,
                        spec = Density.COMFORTABLE.spec(),
                        onStartArranging = {},
                        onCycleSpan = { resizes += it },
                        onMove = { field, index -> moves += field to index },
                    )
                }
            }
        }
    }

    /** `DpRect.width` is shadowed here by the `Modifier.width` import, and an
     * alias would read worse than the subtraction it hides. */
    private fun DpRect.span(): androidx.compose.ui.unit.Dp = right - left

    private fun module(field: WeatherField, span: ModuleSpan) =
        ModuleValue(field = field, label = field.defaultLabel, value = "—", span = span)

    private fun actionsOn(field: WeatherField): List<CustomAccessibilityAction> =
        compose
            .onNodeWithTag("module_${field.key}")
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.CustomActions)
            .orEmpty()

    private fun invoke(field: WeatherField, label: String) {
        val action =
            actionsOn(field).firstOrNull { it.label == label }
                ?: error("no '$label' action on ${field.key}; has ${actionsOn(field).map { it.label }}")
        compose.runOnUiThread { action.action() }
    }

    @Test
    fun spansMeasureToTheirShareOfTheFourColumnGrid() {
        show(
            module(WeatherField.TEMPERATURE, ModuleSpan.FULL),
            module(WeatherField.WIND, ModuleSpan.QUARTER),
            module(WeatherField.PRESSURE, ModuleSpan.QUARTER),
        )
        val full = compose.onNodeWithTag("module_temperature").getUnclippedBoundsInRoot()
        val wind = compose.onNodeWithTag("module_wind").getUnclippedBoundsInRoot()
        val pressure = compose.onNodeWithTag("module_pressure").getUnclippedBoundsInRoot()

        // The full tile spans the grid; a quarter is about a quarter of it.
        // Tolerances absorb the inter-tile gap, which is real but small.
        assertTrue("full tile spans the grid, was ${full.span()}", full.span() > gridWidth * 0.9f)
        assertTrue("quarter is roughly a quarter, was ${wind.span()}", wind.span() < gridWidth * 0.3f)
        assertTrue("quarter is not hairline, was ${wind.span()}", wind.span() > gridWidth * 0.15f)

        // The two quarters share a row: same top, side by side, in order.
        assertEquals("the two quarters share a row", wind.top.value, pressure.top.value, 0.5f)
        assertTrue("wind sits left of pressure", wind.left < pressure.left)
        // …and that row is BELOW the full tile, which took its own.
        assertTrue("the full tile got its own row", wind.top > full.bottom - 1.dp)
    }

    @Test
    fun aHalfAndAQuarterLeaveTheirGapEmptyRatherThanStretching() {
        // The packing promise: 2 + 1 columns used, the 4th stays empty — the
        // tiles keep their widths instead of growing to fill the row, which is
        // what makes the grid legible as a grid.
        show(
            module(WeatherField.CONDITIONS, ModuleSpan.HALF),
            module(WeatherField.WIND, ModuleSpan.QUARTER),
        )
        val half = compose.onNodeWithTag("module_conditions").getUnclippedBoundsInRoot()
        val quarter = compose.onNodeWithTag("module_wind").getUnclippedBoundsInRoot()
        assertEquals("they share one row", half.top.value, quarter.top.value, 0.5f)
        assertTrue("half is about twice the quarter", half.span() > quarter.span() * 1.7f)
        assertTrue("the fourth column is left empty", quarter.right < gridWidth * 0.85f)
    }

    @Test
    fun everyTileOffersResizeWithoutAnyGesture() {
        show(
            module(WeatherField.TEMPERATURE, ModuleSpan.FULL),
            module(WeatherField.CONDITIONS, ModuleSpan.HALF),
        )
        // Named for where it lands, so the action itself says what it will do.
        invoke(WeatherField.TEMPERATURE, "Resize to quarter") // full wraps to quarter
        invoke(WeatherField.CONDITIONS, "Resize to full")
        assertEquals(listOf(WeatherField.TEMPERATURE, WeatherField.CONDITIONS), resizes)
    }

    @Test
    fun moveActionsReorderAndStopAtTheEnds() {
        show(
            module(WeatherField.TEMPERATURE, ModuleSpan.FULL),
            module(WeatherField.CONDITIONS, ModuleSpan.HALF),
            module(WeatherField.WIND, ModuleSpan.QUARTER),
        )
        invoke(WeatherField.CONDITIONS, "Move up")
        invoke(WeatherField.CONDITIONS, "Move down")
        assertEquals(
            listOf(WeatherField.CONDITIONS to 0, WeatherField.CONDITIONS to 2),
            moves,
        )
        // The first tile cannot move up and the last cannot move down: an
        // action that would be a no-op is absent, not merely ignored, so a
        // screen reader never offers a move that does nothing.
        assertNull(actionsOn(WeatherField.TEMPERATURE).firstOrNull { it.label == "Move up" })
        assertNull(actionsOn(WeatherField.WIND).firstOrNull { it.label == "Move down" })
        assertTrue(actionsOn(WeatherField.TEMPERATURE).any { it.label == "Move down" })
        assertTrue(actionsOn(WeatherField.WIND).any { it.label == "Move up" })
    }

    @Test
    fun aTileAnnouncesItsWidthAsState() {
        show(module(WeatherField.WIND, ModuleSpan.QUARTER))
        val state =
            compose
                .onNodeWithTag("module_wind")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
        assertEquals("Quarter width", state)
        compose.onNodeWithTag("module_wind").assertIsDisplayed()
    }

    @Test
    fun theSoleTileHasNoMoveActionsAtAll() {
        // One module is a legal config; offering "Move up"/"Move down" on a
        // grid of one would be offering to reorder nothing.
        show(module(WeatherField.TEMPERATURE, ModuleSpan.FULL))
        assertEquals(listOf("Resize to quarter"), actionsOn(WeatherField.TEMPERATURE).map { it.label })
    }
}

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.ui.theme.JustMyWeatherTheme
import io.raylytics.justmyweather.view.DailyStyle
import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleContent
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.ModuleValue
import io.raylytics.justmyweather.view.WeatherField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The two things about the module grid that only a device can answer: what the
 * lattice packing actually MEASURES to, and whether the arrange operations are
 * reachable without a gesture.
 *
 * Layout is asserted in real dp rather than by counting composables — the
 * whole point of a size is the cells it occupies, and a grid that composed the
 * right tiles at the wrong sizes would pass any structural check. (This is the
 * gap FieldRowsTest used to cover for the old row layout, which the grid
 * replaced.)
 *
 * The gesture path itself — long-press, wiggle, drag, corner-drag — is
 * verified by .maestro/06-arrange.yaml, which drives real touch events;
 * Compose's test gestures do not reproduce the pointer-stream subtleties this
 * grid was debugged against.
 */
class ModuleGridTest {
    /** Shorthand: every reading module is `ModuleKey.Reading(field)`, and
     * spelling that out inline costs more width than it earns in clarity. */
    private fun reading(field: WeatherField) = ModuleKey.Reading(field)

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Wide enough that a quarter tile is comfortably above any minimum, and
     * fixed so the expected column arithmetic below is exact. */
    private val gridWidth = 400.dp

    private val moves = mutableListOf<Pair<ModuleKey, Int>>()
    private val resizes = mutableListOf<Pair<ModuleKey, ModuleSize>>()

    private fun show(vararg modules: ModuleValue) {
        compose.setContent {
            JustMyWeatherTheme {
                Box(Modifier.width(gridWidth)) {
                    ModuleGrid(
                        modules = modules.toList(),
                        arranging = false,
                        spec = Density.COMFORTABLE.spec(),
                        onStartArranging = {},
                        onResize = { field, size -> resizes += field to size },
                        onMove = { field, index -> moves += field to index },
                        onSetForecastMode = {},
                    )
                }
            }
        }
    }

    /** `DpRect.width` is shadowed here by the `Modifier.width` import, and an
     * alias would read worse than the subtraction it hides. */
    private fun DpRect.span(): androidx.compose.ui.unit.Dp = right - left

    private fun module(field: WeatherField, columns: Int, rows: Int = 1) =
        module(ModuleKey.Reading(field), columns, rows)

    private fun module(key: ModuleKey, columns: Int, rows: Int = 1) =
        ModuleValue(
            module = key,
            label = key.defaultLabel,
            size = ModuleSize(columns, rows),
            content =
                when (key) {
                    is ModuleKey.Reading -> ModuleContent.Reading("—")
                    ModuleKey.Sun -> ModuleContent.Sun(sunDays, ZoneId.systemDefault())
                    ModuleKey.Forecast ->
                        ModuleContent.Forecast(
                            hours = null,
                            periods = null,
                            error = null,
                            mode = ForecastMode.DEFAULT,
                            dailyStyle = DailyStyle.DEFAULT,
                            zone = ZoneId.systemDefault(),
                        )
                },
        )

    /** Two days, so the full-width table has rows to draw. */
    private val sunDays =
        listOf(
            SunDay(
                LocalDate.of(2026, 8, 15),
                Instant.parse("2026-08-15T10:57:00Z"),
                Instant.parse("2026-08-16T00:36:00Z"),
            ),
            SunDay(
                LocalDate.of(2026, 8, 16),
                Instant.parse("2026-08-16T10:58:00Z"),
                Instant.parse("2026-08-17T00:35:00Z"),
            ),
        )

    private fun actionsOn(field: WeatherField): List<CustomAccessibilityAction> = actionsOn(ModuleKey.Reading(field))

    private fun actionsOn(key: ModuleKey): List<CustomAccessibilityAction> =
        compose
            .onNodeWithTag("module_${key.key}")
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
    fun sizesMeasureToTheirShareOfTheFourColumnGrid() {
        show(
            module(WeatherField.TEMPERATURE, 4, 2),
            module(WeatherField.WIND, 1),
            module(WeatherField.PRESSURE, 1),
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
        // Two rows of cells is about twice one — the lattice is a lattice.
        assertTrue("the 4×2 hero is about twice a cell tall", full.height() > wind.height() * 1.8f)
    }

    /** Same shadowing story as [span]: `DpRect.height` vs the height import. */
    private fun DpRect.height(): androidx.compose.ui.unit.Dp = bottom - top

    @Test
    fun aTallTileKeepsItsNeighboursBesideItInTheCellsItLeaves() {
        // 2×2 at the origin, then two 2×1: they stack beside the tall one, on
        // its two rows, rather than dropping below it.
        show(
            module(WeatherField.CONDITIONS, 2, 2),
            module(WeatherField.WIND, 2),
            module(WeatherField.PRESSURE, 2),
        )
        val tall = compose.onNodeWithTag("module_conditions").getUnclippedBoundsInRoot()
        val wind = compose.onNodeWithTag("module_wind").getUnclippedBoundsInRoot()
        val pressure = compose.onNodeWithTag("module_pressure").getUnclippedBoundsInRoot()
        assertEquals("wind shares the tall tile's top", tall.top.value, wind.top.value, 0.5f)
        assertTrue("wind sits right of the tall tile", wind.left > tall.right - 1.dp)
        assertTrue("pressure sits under wind", pressure.top > wind.bottom - 1.dp)
        assertTrue("and still beside the tall tile", pressure.bottom <= tall.bottom + 1.dp)
    }

    @Test
    fun aHalfAndAQuarterLeaveTheirGapEmptyRatherThanStretching() {
        // The packing promise: 2 + 1 columns used, the 4th stays empty — the
        // tiles keep their widths instead of growing to fill the row, which is
        // what makes the grid legible as a grid.
        show(
            module(WeatherField.CONDITIONS, 2),
            module(WeatherField.WIND, 1),
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
            module(WeatherField.TEMPERATURE, 4, 2),
            module(WeatherField.CONDITIONS, 2),
        )
        // One cell in one direction per action, and each says which.
        invoke(WeatherField.TEMPERATURE, "Narrower")
        invoke(WeatherField.TEMPERATURE, "Shorter")
        invoke(WeatherField.CONDITIONS, "Wider")
        invoke(WeatherField.CONDITIONS, "Taller")
        assertEquals(
            listOf(
                reading(WeatherField.TEMPERATURE) to ModuleSize(3, 2),
                reading(WeatherField.TEMPERATURE) to ModuleSize(4, 1),
                reading(WeatherField.CONDITIONS) to ModuleSize(3, 1),
                reading(WeatherField.CONDITIONS) to ModuleSize(2, 2),
            ),
            resizes,
        )
    }

    @Test
    fun resizeActionsStopAtTheGridAndAtTheModulesMinimum() {
        // The hero at full width cannot go wider; conditions at its 2-wide
        // floor cannot go narrower, and at one row cannot go shorter. An
        // action that would do nothing is absent, not merely ignored.
        show(
            module(WeatherField.TEMPERATURE, 4, 2),
            module(WeatherField.CONDITIONS, 2),
        )
        val hero = actionsOn(WeatherField.TEMPERATURE).map { it.label }
        assertTrue("$hero", "Wider" !in hero && "Narrower" in hero && "Taller" in hero && "Shorter" in hero)
        val prose = actionsOn(WeatherField.CONDITIONS).map { it.label }
        assertTrue("$prose", "Wider" in prose && "Narrower" !in prose && "Taller" in prose && "Shorter" !in prose)
    }

    @Test
    fun moveActionsReorderAndStopAtTheEnds() {
        show(
            module(WeatherField.TEMPERATURE, 4, 2),
            module(WeatherField.CONDITIONS, 2),
            module(WeatherField.WIND, 1),
        )
        invoke(WeatherField.CONDITIONS, "Move up")
        invoke(WeatherField.CONDITIONS, "Move down")
        assertEquals(
            listOf(
                reading(WeatherField.CONDITIONS) to 0,
                reading(WeatherField.CONDITIONS) to 2,
            ),
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
    fun aTileAnnouncesItsSizeAsState() {
        show(module(WeatherField.WIND, 1))
        val state =
            compose
                .onNodeWithTag("module_wind")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.StateDescription)
        assertEquals("1 wide, 1 tall", state)
        compose.onNodeWithTag("module_wind").assertIsDisplayed()
    }

    @Test
    fun sunModuleKeepsBothDatedRowsAtFullWidth() {
        // The reason sun times became a span-adaptive module rather than two
        // value tiles: at full width the day rows survive, and each row says
        // which date its times belong to. Between sunrise and sunset "the next
        // sunrise" and "the next sunset" fall on different dates, which is
        // exactly what flattening would lose.
        show(module(ModuleKey.Sun, 4, 2))
        compose.onNodeWithText("Sunrise").assertIsDisplayed()
        compose.onNodeWithText("Sunset").assertIsDisplayed()
        compose.onNodeWithText("Aug 15").assertIsDisplayed()
        compose.onNodeWithText("Aug 16").assertIsDisplayed()
    }

    @Test
    fun sunModuleCondensesToTodaysPairWhenNarrower() {
        // Smaller: today's pair only — and still labelled in words rather
        // than reduced to arrows, because at this size there is no column
        // position left to carry the distinction. The second day's row is
        // dropped rather than squeezed.
        show(module(ModuleKey.Sun, 2))
        compose.onNodeWithText("Sunrise").assertIsDisplayed()
        compose.onNodeWithText("Sunset").assertIsDisplayed()
        compose.onNodeWithText("Aug 16").assertDoesNotExist()
    }

    @Test
    fun aLongValueShrinksToStayInsideItsTile() {
        // The value is fitted, not styled per size: a conditions phrase in a
        // one-row tile would otherwise break "Thunderstorms" across lines, run
        // past the border, or stand taller than its cells. Size is prominence,
        // and a value that escapes its tile has no size to be prominent within.
        val phrase = "Chance Showers And Thunderstorms"
        show(
            ModuleValue(
                module = reading(WeatherField.CONDITIONS),
                label = "Conditions",
                size = ModuleSize(2, 1),
                content = ModuleContent.Reading(phrase),
            ),
        )
        val tile = compose.onNodeWithTag("module_conditions").getUnclippedBoundsInRoot()
        val value = compose.onNodeWithText(phrase, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("value starts inside its tile", value.left >= tile.left)
        assertTrue("value ends inside its tile, ${value.right} vs ${tile.right}", value.right <= tile.right)
        assertTrue("value is not taller than its tile", value.bottom <= tile.bottom)
    }

    @Test
    fun theSoleTileHasNoMoveActionsAtAll() {
        // One module is a legal config; offering "Move up"/"Move down" on a
        // grid of one would be offering to reorder nothing.
        show(module(WeatherField.TEMPERATURE, 4, 2))
        assertEquals(listOf("Narrower", "Taller", "Shorter"), actionsOn(WeatherField.TEMPERATURE).map { it.label })
    }

    @Test
    fun theForecastIsATileWithTheSameActionsAndItsOwnHeader() {
        // The ask: every tile carries the same interaction. So the forecast
        // module offers the same move and resize actions as a reading, and
        // draws its framing toggle inside its own tile.
        show(
            module(WeatherField.TEMPERATURE, 4, 2),
            module(ModuleKey.Forecast, 4, 4),
        )
        val actions = actionsOn(ModuleKey.Forecast).map { it.label }
        assertTrue("$actions", "Move up" in actions && "Narrower" in actions && "Shorter" in actions)
        assertTrue("$actions", "Wider" !in actions && "Taller" !in actions)
        compose.onNodeWithTag("forecast_hourly", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("forecast_daily", useUnmergedTree = true).assertIsDisplayed()
        // Four rows of cells: the tallest tile on the grid, about twice the hero.
        val hero = compose.onNodeWithTag("module_temperature").getUnclippedBoundsInRoot()
        val forecast = compose.onNodeWithTag("module_forecast").getUnclippedBoundsInRoot()
        assertTrue("4×4 is about twice a 4×2", forecast.height() > hero.height() * 1.8f)
    }

    @Test
    fun aSingleCellTemperatureFitsInsideItsCell() {
        // The ask that started the lattice: the hero shrunk to one cell. The
        // value must land inside the cell in both directions — height is
        // what bounds a short reading, and a 1×1 has little of it.
        show(
            ModuleValue(
                module = reading(WeatherField.TEMPERATURE),
                label = "Temperature",
                size = ModuleSize.CELL,
                content = ModuleContent.Reading("72°"),
            ),
        )
        val tile = compose.onNodeWithTag("module_temperature").getUnclippedBoundsInRoot()
        val value = compose.onNodeWithText("72°", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("a cell is about a quarter of the grid, was ${tile.span()}", tile.span() < gridWidth * 0.3f)
        assertTrue("value ends inside its cell", value.right <= tile.right && value.bottom <= tile.bottom)
        assertTrue("value starts inside its cell", value.left >= tile.left && value.top >= tile.top)
    }
}

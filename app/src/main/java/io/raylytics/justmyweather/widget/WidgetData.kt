package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.DailyDays
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleKey
import java.time.Instant

/**
 * What the refresh worker last fetched for the widgets, kept so a widget can
 * be drawn at any moment — a launcher asks for a redraw on resize, on theme
 * change, on reboot — without a network round trip of its own.
 *
 * ONE of these for every widget on the phone, not one each: the app shows a
 * single place, every widget shows that place, and one fetch per tick is the
 * whole point of refreshing in the background. Each list is null when no
 * widget needed it ([WidgetNeeds]), so a phone with only a temperature widget
 * never fetches a forecast.
 *
 * [location] rides along because the sun module is computed from it at draw
 * time, not fetched; [fetchedAt] is when this fetch succeeded, which is what
 * the widget ages against when the station omitted its own time.
 */
data class WidgetData(
    val location: WeatherLocation,
    val snapshot: WeatherSnapshot?,
    val hours: List<ForecastPoint>? = null,
    val periods: List<DailyPeriod>? = null,
    val extended: List<ExtendedDay>? = null,
    val fetchedAt: Instant,
    /** The fetch's failure, if any, in the user's words. A failed fetch keeps
     * the previous data and reports beside it, as the glance does. */
    val error: String? = null,
)

/**
 * What a set of widgets needs fetched, worked out from their configs so the
 * worker asks NWS for exactly that. Pure, so the rule is testable: hours for
 * an hourly forecast; periods AND hours for a daily one (the day grouping
 * borrows today's warmest remaining hour for a leading night); the extended
 * source only when some widget reaches past NWS's week.
 */
data class WidgetNeeds(
    val hours: Boolean = false,
    val periods: Boolean = false,
    val extended: Boolean = false,
) {
    companion object {
        val NONE = WidgetNeeds()

        fun of(configs: Collection<WidgetConfig>): WidgetNeeds =
            configs
                .filter { it.module == ModuleKey.Forecast }
                .fold(NONE) { needs, config ->
                    when (config.view.defaultForecastMode) {
                        ForecastMode.HOURLY -> needs.copy(hours = true)
                        ForecastMode.DAILY ->
                            needs.copy(
                                hours = true,
                                periods = true,
                                extended = needs.extended || config.view.dailyDays > DailyDays.NWS_REACH,
                            )
                    }
                }
    }
}

package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.CachedSnapshot
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.DailyDays
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleKey
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

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
    val hours: Fetched<ForecastPoint>? = null,
    val periods: Fetched<DailyPeriod>? = null,
    val extended: Fetched<ExtendedDay>? = null,
    val fetchedAt: Instant,
    /** The fetch's failure, if any, in the user's words. A failed fetch keeps
     * the previous data and reports beside it, as the glance does. */
    val error: String? = null,
) {
    /**
     * Whether this data describes [location] — the gate on reusing it after
     * a failed fetch. The user may have picked another place, or moved,
     * since it was fetched, and a widget that keeps the old place's
     * temperature while its sun times switch to the new one is lying twice.
     * The same tolerance as the app's remembered reading
     * ([CachedSnapshot.MAX_DEGREES_AWAY]): a fix drifts by metres between
     * polls, and one NWS grid cell is the thing the data is drawn from.
     */
    fun isAbout(location: WeatherLocation): Boolean =
        abs(location.latitude - this.location.latitude) <= CachedSnapshot.MAX_DEGREES_AWAY &&
            abs(location.longitude - this.location.longitude) <= CachedSnapshot.MAX_DEGREES_AWAY
}

/**
 * A forecast list with the moment it was fetched. Each list carries its
 * own clock because each ages on its own: a phone with an hourly widget
 * fetches hours every tick while its periods, which nothing needs, sit
 * still — and one shared clock, reset by the hours, would have kept those
 * periods "fresh" for a week (roborev 5378).
 */
data class Fetched<T>(
    val items: List<T>,
    val at: Instant,
) {
    /** Whether this list is at most [maxAge] old at [now]. A clock that
     * went backwards reads as stale: its age cannot be known. */
    fun isFresh(now: Instant, maxAge: Duration): Boolean {
        val age = Duration.between(at, now)
        return !age.isNegative && age <= maxAge
    }
}

/**
 * What a tick stores for one forecast framing — the pure rule, so it tests
 * on the JVM:
 *
 *  - needed and [fetched] landed: the new list, stamped [now];
 *  - needed and the fetch failed ([fetched] null): the [previous] list
 *    stands, with the time it was really fetched at, as the glance keeps
 *    its last forecast beside a failed refresh;
 *  - not needed: the [previous] list is CARRIED while it is within
 *    [maxAge], else dropped. The carry exists for one case — a widget
 *    reconfigured during a tick, whose one-off refresh just fetched the
 *    list this tick did not know to ask for (roborev 5376) — and that case
 *    is over within a tick or two. Past the window the list goes, so a
 *    framing nobody has needed for a week cannot come back as the current
 *    forecast (roborev 5377).
 *
 * [previous] must already be the same place's; the worker gates on that.
 */
internal fun <T> settle(
    needed: Boolean,
    fetched: List<T>?,
    previous: Fetched<T>?,
    now: Instant,
    maxAge: Duration,
): Fetched<T>? =
    when {
        needed && fetched != null -> Fetched(fetched, now)
        needed -> previous
        else -> previous?.takeIf { it.isFresh(now, maxAge) }
    }

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

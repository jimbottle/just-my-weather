package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ForecastPoint
import java.time.Instant
import java.time.ZoneId

/**
 * Everything a rule can be evaluated against. [NOW][AlertWindow.NOW] rules read
 * only [snapshot]; forecast-window rules also read [forecast], measured from
 * [now] in [zone].
 *
 * The clock and zone are passed in rather than read inside the evaluator, so
 * evaluation stays a pure function (no clock, no Android) and a test can pin
 * "now" exactly. The worker supplies the real clock at the edge.
 */
data class WeatherContext(
    val snapshot: WeatherSnapshot,
    val now: Instant,
    val forecast: List<ForecastPoint> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

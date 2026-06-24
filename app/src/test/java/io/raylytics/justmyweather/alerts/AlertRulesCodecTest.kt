package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlertRulesCodecTest {
    @Test
    fun `round-trips a rule list`() {
        val rules =
            listOf(
                AlertRule("a", WeatherField.TEMPERATURE, Comparison.BELOW, 32.0, enabled = true),
                AlertRule("b", WeatherField.WIND, Comparison.ABOVE, 20.0, enabled = false),
            )
        assertEquals(rules, AlertRulesCodec.decode(AlertRulesCodec.encode(rules)))
    }

    @Test
    fun `absent or corrupt data decodes to an empty list`() {
        assertEquals(emptyList<AlertRule>(), AlertRulesCodec.decode(null))
        assertEquals(emptyList<AlertRule>(), AlertRulesCodec.decode(""))
        assertEquals(emptyList<AlertRule>(), AlertRulesCodec.decode("{garbage"))
    }

    @Test
    fun `a rule with an unknown field or comparison key is dropped, not crashed`() {
        val raw =
            """
            [{"id":"a","field":"humidity","comparison":"below","threshold":50.0,"enabled":true},
             {"id":"b","field":"wind","comparison":"sideways","threshold":20.0,"enabled":true},
             {"id":"c","field":"temperature","comparison":"below","threshold":32.0,"enabled":true}]
            """.trimIndent()
        val rules = AlertRulesCodec.decode(raw)
        assertEquals(listOf("c"), rules.map { it.id })
    }
}

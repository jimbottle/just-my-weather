package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.view.ModuleSpan
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/*
 * The sun module's drawing, at each width it can be given.
 *
 * Sun times are the one module whose content is a table rather than a value,
 * and the table is not decoration: between sunrise and sunset, "the next
 * sunrise" and "the next sunset" fall on different dates, so a row that
 * carries its own date says which is which without hanging a "tomorrow" off a
 * time. Rather than lose that by flattening it to fit a tile, the module
 * adapts — full width keeps the table, and narrower widths condense to today's
 * pair, where "today" is unambiguous because the label says so.
 */

/** Width of each sun-time column, sized for "12:00 AM" at titleMedium so the
 * two columns stay aligned down the rows regardless of the times in them. */
private val SUN_COLUMN_WIDTH = 92.dp

/** Draw sun times to suit the tile they were given. */
@Composable
internal fun SunModuleContent(
    days: List<SunDay>,
    span: ModuleSpan,
) {
    if (days.isEmpty()) {
        // Honest absence, the same em-dash an empty reading gets: the module is
        // on, we simply have no location to compute from yet.
        Text(
            text = "—",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    when (span) {
        ModuleSpan.FULL -> SunTimesTable(days)
        ModuleSpan.HALF, ModuleSpan.QUARTER -> SunPair(days.first())
    }
}

/**
 * Sun times as day rows, laid out like the daily forecast: the date beside its
 * values, sunrise and sunset in columns.
 *
 * Sunrise takes the emphasis and sunset the quieter tone, the same pairing the
 * daily high and low use, so the column a value sits in is not the only thing
 * telling them apart.
 */
@Composable
internal fun SunTimesTable(days: List<SunDay>) {
    val zone = ZoneId.systemDefault()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Column headings once at the top, not repeated per row: with two
        // values a row this is a small table, and repeating the words would
        // outweigh the times they label.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "Sunrise",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(SUN_COLUMN_WIDTH),
            )
            Text(
                text = "Sunset",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(SUN_COLUMN_WIDTH),
            )
        }
        days.forEach { day -> SunDayRow(day = day, zone = zone) }
    }
}

/**
 * Today's pair, for a tile too narrow for the table.
 *
 * Each time keeps its own word rather than an arrow or an icon: "Sunrise" and
 * "Sunset" survive being read aloud, and at this size there is no column
 * position left to carry the distinction.
 */
@Composable
private fun SunPair(day: SunDay) {
    val zone = ZoneId.systemDefault()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SunPairLine("Sunrise", day.sunrise, zone, MaterialTheme.colorScheme.onBackground)
        SunPairLine("Sunset", day.sunset, zone, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SunPairLine(
    label: String,
    event: Instant?,
    zone: ZoneId,
    color: Color,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = event?.atZone(zone)?.format(timeFormat) ?: "—",
        style = MaterialTheme.typography.titleMedium,
        color = color,
    )
}

@Composable
private fun SunDayRow(day: SunDay, zone: ZoneId) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same two-line date marker the forecast uses, so a date reads as a
        // date wherever it appears on this screen.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.date.format(weekdayFormat).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = day.date.format(monthDayFormat),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SunTimeCell(day.sunrise, zone, MaterialTheme.colorScheme.onBackground)
        SunTimeCell(day.sunset, zone, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One time in its column. A missing event renders as "—": polar night is a
 * real state, and an empty gap would read as a value that failed to load. */
@Composable
private fun SunTimeCell(
    event: Instant?,
    zone: ZoneId,
    color: Color,
) {
    Text(
        text = event?.atZone(zone)?.format(timeFormat) ?: "—",
        style = MaterialTheme.typography.titleMedium,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier.width(SUN_COLUMN_WIDTH),
    )
}

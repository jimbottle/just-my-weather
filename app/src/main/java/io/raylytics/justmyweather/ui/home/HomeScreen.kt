package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.WeatherSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The default minimalist view. One screen, one answer: where you are, how warm
 * it is, and a plain-language line of conditions. Everything is centred in a
 * field of whitespace so the temperature is readable in well under a second.
 *
 * This is intentionally close to the floor of the density spectrum the brief
 * describes — the customization layer builds *up* from here.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is HomeUiState.Loading ->
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                is HomeUiState.Error ->
                    ErrorView(message = state.message, onRefresh = onRefresh)

                is HomeUiState.Ready ->
                    GlanceView(
                        snapshot = state.snapshot,
                        refreshing = state.refreshing,
                        onRefresh = onRefresh,
                    )
            }
        }
    }
}

@Composable
private fun GlanceView(
    snapshot: WeatherSnapshot,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = snapshot.locationLabel.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = snapshot.temperatureF?.let { "${it.roundToInt()}°" } ?: "—",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        snapshot.conditions?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        // Only claim a time when the station actually reported one.
        observedLabel(snapshot)?.let { updated ->
            Text(
                text = "Updated $updated",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        // The button label reflects the refreshing flag, so a re-fetch is an
        // observable state change (and the flag stops being dead state).
        TextButton(onClick = onRefresh, enabled = !refreshing) {
            Text(if (refreshing) "Refreshing…" else "Refresh")
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRefresh: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRefresh) { Text("Try again") }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun observedLabel(snapshot: WeatherSnapshot): String? =
    snapshot.observedAt?.atZone(ZoneId.systemDefault())?.format(timeFormat)

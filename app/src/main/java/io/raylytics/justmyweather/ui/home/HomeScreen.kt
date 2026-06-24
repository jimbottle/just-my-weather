package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import io.raylytics.justmyweather.view.RenderedView
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.render
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home view. Out of the box it's a calm single glance; once the user edits
 * their config it's whatever they made it — same screen, driven by data. The
 * first visible field is the hero (large), the rest are compact rows. Centred
 * in whitespace so the hero is readable in well under a second.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onCustomize: () -> Unit,
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
                        config = state.config,
                        refreshing = state.refreshing,
                        onRefresh = onRefresh,
                        onCustomize = onCustomize,
                    )
            }
        }
    }
}

@Composable
private fun GlanceView(
    snapshot: WeatherSnapshot,
    config: ViewConfig,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onCustomize: () -> Unit,
) {
    val rendered: RenderedView = config.render(snapshot)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = snapshot.locationLabel.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Hero: the value speaks for itself, so no caption above it.
        Text(
            text = rendered.hero?.value ?: "—",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        // Secondary fields, in the user's order, as quiet label · value rows.
        if (rendered.rows.isNotEmpty()) {
            Column(
                modifier = Modifier.widthIn(max = 320.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rendered.rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = row.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
        }
        observedLabel(snapshot)?.let { updated ->
            Text(
                text = "Updated $updated",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The button label reflects the refreshing flag, so a re-fetch is an
            // observable state change (and the flag stops being dead state).
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "Refreshing…" else "Refresh")
            }
            TextButton(onClick = onCustomize) { Text("Customize") }
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

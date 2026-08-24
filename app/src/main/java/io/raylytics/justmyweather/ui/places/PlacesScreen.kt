package io.raylytics.justmyweather.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.places.Place
import io.raylytics.justmyweather.data.places.SavedPlaces

/**
 * Choose where the weather is for.
 *
 * Three ways in, in the order people need them: follow the device (the
 * default, and how the app has always worked), pick something already saved,
 * or find somewhere new. The search runs against a list bundled in the APK —
 * no geocoder, no key, no network — and the coordinate entry at the bottom is
 * the escape hatch for a spot no gazetteer lists, which on a weather app is a
 * real case rather than a theoretical one: trailheads, campsites, a cabin.
 */
@Composable
fun PlacesScreen(
    saved: SavedPlaces,
    results: List<Place>,
    query: String,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSave: (Place) -> Unit,
    onSaveCoordinates: (WeatherLocation) -> Unit,
    onSelect: (String?) -> Unit,
    onRemove: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Places", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text("Done") }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search towns and cities") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("place-search"),
            )

            // The list below is one scroller, not three: the saved places, the
            // search results and the coordinate entry are all "ways to pick a
            // place", and stacking three independently-scrolling boxes on a
            // phone is how a screen becomes unusable in landscape.
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (query.isBlank()) {
                    item {
                        ChoiceRow(
                            title = "Use my location",
                            subtitle = "Follows the device, the way the app starts out",
                            selected = saved.selected == null,
                            onClick = { onSelect(null) },
                            tag = "place-device",
                        )
                    }
                    if (saved.places.isNotEmpty()) {
                        item { SectionLabel("Saved") }
                        items(saved.places, key = { it.label }) { place ->
                            ChoiceRow(
                                title = place.label,
                                subtitle = coordinateLabel(place),
                                selected = saved.selected == place.label,
                                onClick = { onSelect(place.label) },
                                onRemove = { onRemove(place.label) },
                                tag = "place-saved",
                            )
                        }
                    }
                    item { CoordinateEntry(onSave = onSaveCoordinates) }
                } else {
                    when {
                        loading ->
                            item { Hint("Loading places…") }
                        results.isEmpty() ->
                            item {
                                // Names the escape hatch rather than just
                                // saying no: somewhere unlisted is exactly when
                                // coordinates are the answer, and that entry is
                                // one clear-the-box away.
                                Hint("No match. Clear the search to enter coordinates instead.")
                            }
                        else ->
                            items(results, key = { "${it.label}|${it.latitude},${it.longitude}" }) { place ->
                                ChoiceRow(
                                    title = place.label,
                                    subtitle = null,
                                    selected = false,
                                    onClick = {
                                        onSave(place)
                                        onQueryChange("")
                                    },
                                    tag = "place-result",
                                )
                            }
                    }
                }
            }
        }
    }
}

/** One tappable place. The current one is marked with a word, not a colour
 * alone — "selected" has to survive a colourblind reader and a screenshot. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Text(
                text = "Showing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        onRemove?.let {
            TextButton(onClick = it, modifier = Modifier.testTag("place-remove")) { Text("Remove") }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

/**
 * The escape hatch: a coordinate and a name for it.
 *
 * Validated before it can be saved, because a bad coordinate here is not a
 * cosmetic problem — it reaches the NWS point lookup, and a NaN throws on the
 * way. Out-of-range values are refused rather than clamped: silently moving
 * somebody's cabin to the nearest legal latitude is worse than saying no.
 */
@Composable
private fun CoordinateEntry(onSave: (WeatherLocation) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (!open) {
            TextButton(onClick = { open = true }, modifier = Modifier.testTag("place-coords-open")) {
                Text("Enter coordinates")
            }
            return@Column
        }
        Text("Coordinates", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Name (e.g. Cabin)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("coords-name"),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = lat,
                onValueChange = { lat = it },
                placeholder = { Text("Latitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("coords-lat"),
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                placeholder = { Text("Longitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("coords-lon"),
            )
        }
        val parsed = parseCoordinates(name, lat, lon)
        Text(
            text = "NWS covers the United States and its territories, so a point outside it has no forecast.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    parsed?.let(onSave)
                    name = ""
                    lat = ""
                    lon = ""
                    open = false
                },
                enabled = parsed != null,
                modifier = Modifier.testTag("coords-save"),
            ) { Text("Save place") }
            TextButton(onClick = { open = false }) { Text("Cancel") }
        }
    }
}

/**
 * A name and two numbers, or null if they are not yet a place. Pure and
 * `internal` so the rules are asserted directly rather than inferred from a
 * greyed-out button.
 */
internal fun parseCoordinates(name: String, latitude: String, longitude: String): WeatherLocation? {
    val lat = latitude.trim().toDoubleOrNull() ?: return null
    val lon = longitude.trim().toDoubleOrNull() ?: return null
    if (!lat.isFinite() || !lon.isFinite()) return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    val label = name.trim().ifBlank { "${"%.2f".format(lat)}, ${"%.2f".format(lon)}" }
    return WeatherLocation(latitude = lat, longitude = lon, label = label)
}

private fun coordinateLabel(location: WeatherLocation): String =
    "${"%.2f".format(location.latitude)}, ${"%.2f".format(location.longitude)}"

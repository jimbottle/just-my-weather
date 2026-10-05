package io.raylytics.justmyweather.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.billing.RemoveAdsStatus

/**
 * Settings that are about the app, not the canvas. Customize is for what the
 * glance shows and how it looks; anything else — handing readings to another
 * app, getting in touch with the developer, what the app is — belongs here, so Customize stays
 * a page about the glance.
 */
@Composable
fun AppSettingsScreen(
    gadgetbridgeEnabled: Boolean,
    onSetGadgetbridgeEnabled: (Boolean) -> Unit,
    adsRemoved: Boolean,
    /** Play's localised price ("$0.99"), or null while Play has not answered. */
    removeAdsPrice: String?,
    removeAdsStatus: RemoveAdsStatus,
    onBuyRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
    onReportBug: () -> Unit,
    /** Opens the Play listing, where the rating lives. */
    onRateApp: () -> Unit,
    /** "0.2.0 (build 2)" — the line a bug reply would otherwise ask for. */
    version: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("App settings", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDone) { Text("Done") }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AdsSection(
                    adsRemoved = adsRemoved,
                    price = removeAdsPrice,
                    status = removeAdsStatus,
                    onBuy = onBuyRemoveAds,
                    onRestore = onRestorePurchases,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                GadgetbridgeToggle(enabled = gadgetbridgeEnabled, onChange = onSetGadgetbridgeEnabled)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                HelpSection(onReportBug = onReportBug, onRateApp = onRateApp)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                AboutSection(version = version)
            }
        }
    }
}

/**
 * The banner and the one-time purchase that removes it. The price is Play's
 * own, in the user's currency, never typed here; until Play answers the row
 * says so rather than guessing. Restore is for a new phone or a reinstall.
 */
@Composable
private fun AdsSection(
    adsRemoved: Boolean,
    price: String?,
    status: RemoveAdsStatus,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Ads", style = MaterialTheme.typography.labelMedium)
        if (adsRemoved) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Ads removed", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Thank you. The banner is gone on every phone signed in to this Google account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val busy = status == RemoveAdsStatus.Busy || status == RemoveAdsStatus.Restoring
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy, onClick = onBuy)
                    .padding(vertical = 8.dp)
                    .testTag("remove-ads"),
        ) {
            Text(
                text = if (price != null) "Remove ads · $price" else "Remove ads",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (price != null) {
                        "A one-time purchase, no subscription. Removes the banner at the foot of the glance."
                    } else {
                        "Waiting for Google Play to confirm the price…"
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val note =
            when (status) {
                RemoveAdsStatus.Idle -> null
                RemoveAdsStatus.Busy -> "Opening Google Play…"
                RemoveAdsStatus.Restoring -> "Checking with Google Play…"
                RemoveAdsStatus.Pending ->
                    "Your purchase is pending. The banner goes once Google Play confirms the payment."
                is RemoveAdsStatus.Failed -> status.message
                RemoveAdsStatus.NothingToRestore -> "No purchase found for this Google account."
            }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (status is RemoveAdsStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        TextButton(onClick = onRestore, enabled = !busy, modifier = Modifier.testTag("restore-purchase")) {
            Text("Already bought it? Restore purchase")
        }
    }
}

/**
 * Opt-in hand-off of each reading to Gadgetbridge, which relays it to a paired
 * watch. Off by default: it sends data to another app, so it stays something
 * you go and switch on rather than something you discover already running.
 */
@Composable
private fun GadgetbridgeToggle(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Watch", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Send to Gadgetbridge", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enabled,
                onCheckedChange = onChange,
                modifier = Modifier.testTag("gadgetbridge-toggle"),
            )
        }
        Text(
            text = "Hands each new reading to Gadgetbridge, which passes it to a paired watch. " +
                "Does nothing if Gadgetbridge isn't installed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The way to the bug-report form, and the way to the store page for a
 * rating. Ideas are offered at the bottom of Customize instead, where
 * someone notices the option they wanted is missing. The rating row asks
 * once and plainly, here where someone has come looking, rather than
 * interrupting the glance with a pop-up. */
@Composable
private fun HelpSection(
    onReportBug: () -> Unit,
    onRateApp: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Help", style = MaterialTheme.typography.labelMedium)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReportBug)
                    .padding(vertical = 8.dp),
        ) {
            Text("Report a bug", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Something wrong? Email the developer, with your app version and device attached.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRateApp)
                    .padding(vertical = 8.dp)
                    .testTag("rate-app"),
        ) {
            Text("Rate Just My Weather", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "If it has earned a place on your phone, a rating on Google Play helps others find it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Where the policy lives. It is also the URL in the Play listing
 * (store-assets/play-store-listing.md); change both together. */
const val PRIVACY_POLICY_URL = "https://raylytics.io/justmyweather/privacy"

/**
 * What the app is and whose data it shows. The attribution is not a courtesy:
 * MET Norway's CC BY 4.0 licence asks for it wherever its data appears, and a
 * store build is the place a user would look for it.
 */
@Composable
private fun AboutSection(version: String) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("About", style = MaterialTheme.typography.labelMedium)
        Text(
            // The screen already says which app this is; the line names what
            // a bug reply would ask for.
            text = "Version $version",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp).testTag("about-version"),
        )
        AboutLink(
            title = "Privacy policy",
            detail = "No accounts. What leaves your phone, the ad included, and why.",
            onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) },
        )
        Text(
            text = "Weather data from the US National Weather Service (public domain). " +
                "Days eight and nine of the daily forecast from MET Norway (CC BY 4.0).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AboutLink(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    /** Whether Google's consent SDK says ads may run here. When not, there is
     * no banner, so no purchase to offer. */
    canShowAds: Boolean,
    /** Where a consent form applies, the user must be able to reopen it. */
    privacyOptionsRequired: Boolean,
    onPrivacyChoices: () -> Unit,
    /** "United Kingdom · °C, mph" — the Region & units row's second line. */
    regionSummary: String,
    onRegion: () -> Unit,
    /** Play's localised price ("$0.99"), or null while Play has not answered. */
    removeAdsPrice: String?,
    removeAdsStatus: RemoveAdsStatus,
    onBuyRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
    onReportBug: () -> Unit,
    onSubmitIdea: () -> Unit,
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
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Region", style = MaterialTheme.typography.labelMedium)
                    AboutLink(title = "Region & units", detail = regionSummary, onClick = onRegion)
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                AdsSection(
                    adsRemoved = adsRemoved,
                    canShowAds = canShowAds,
                    privacyOptionsRequired = privacyOptionsRequired,
                    onPrivacyChoices = onPrivacyChoices,
                    price = removeAdsPrice,
                    status = removeAdsStatus,
                    onBuy = onBuyRemoveAds,
                    onRestore = onRestorePurchases,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                HelpSection(onReportBug = onReportBug, onSubmitIdea = onSubmitIdea, onRateApp = onRateApp)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                AdvancedSection(
                    gadgetbridgeEnabled = gadgetbridgeEnabled,
                    onSetGadgetbridgeEnabled = onSetGadgetbridgeEnabled,
                )
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
    canShowAds: Boolean,
    privacyOptionsRequired: Boolean,
    onPrivacyChoices: () -> Unit,
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
        // Wherever a consent form applies, the choice can be changed here —
        // Google requires the entry, and it belongs beside the ads it governs.
        if (privacyOptionsRequired) {
            AboutLink(
                title = "Privacy choices",
                detail = "Change what you agreed to about ads.",
                onClick = onPrivacyChoices,
            )
        }
        if (!canShowAds) {
            // Nothing to remove: selling Remove Ads where no banner is shown
            // would be selling nothing.
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("No ads for now", style = MaterialTheme.typography.bodyLarge)
                Text(
                    // Consent wording only where a form applies; a first launch
                    // offline, anywhere, just hasn't reached Google yet (roborev 5436).
                    text =
                        if (privacyOptionsRequired) {
                            "Where the law asks first, the banner waits for your choice in the consent form."
                        } else {
                            "The banner appears once the app has reached Google."
                        },
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
 * Settings most people never need, folded away behind a switch so the page
 * stays short. The switch only shows or hides them — it changes nothing on
 * its own. It opens itself whenever something inside is on, so a setting
 * that is doing something is never hidden.
 */
@Composable
private fun AdvancedSection(
    gadgetbridgeEnabled: Boolean,
    onSetGadgetbridgeEnabled: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Keyed on the value, not read once: the stored setting arrives a frame
    // after the screen does, so an initial value would always read "off".
    LaunchedEffect(gadgetbridgeEnabled) { if (gadgetbridgeEnabled) expanded = true }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Advanced", style = MaterialTheme.typography.labelMedium)
            Switch(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier.testTag("advanced-toggle"),
            )
        }
        if (expanded) {
            GadgetbridgeToggle(enabled = gadgetbridgeEnabled, onChange = onSetGadgetbridgeEnabled)
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
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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

/** The ways to reach the developer — the bug-report form and the idea form —
 * and the way to the store page for a rating. The idea form is also offered
 * at the bottom of Customize, where someone notices the option they wanted
 * is missing; here is where someone comes looking for it on purpose. The
 * rating row asks once and plainly, here where someone has come looking,
 * rather than interrupting the glance with a pop-up. */
@Composable
private fun HelpSection(
    onReportBug: () -> Unit,
    onSubmitIdea: () -> Unit,
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
                    .clickable(onClick = onSubmitIdea)
                    .padding(vertical = 8.dp)
                    .testTag("submit-idea"),
        ) {
            Text("Submit your idea to the developer", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "A tile, an alert or an option you wish it had. " +
                    "Only the app version and phone model go with it.",
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
 * MET Norway's and GeoNames' CC BY 4.0 licences ask for it wherever their data
 * appears, and a store build is the place a user would look for it.
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
            text = "Weather data from the US National Weather Service (public domain) in the United " +
                "States, and from MET Norway (CC BY 4.0) everywhere else and for days eight and nine. " +
                "Places from the US Census Gazetteer (public domain) and GeoNames (CC BY 4.0).",
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

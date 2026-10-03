package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether this install has bought Remove Ads. The truth lives with Google
 * Play; this is the app's copy of it, kept on the device so the banner's
 * absence does not depend on a round trip — an owner opening the app offline
 * must not see an ad flash in while Play is asked. Written only by
 * [io.raylytics.justmyweather.billing.RemoveAdsManager], which asks Play on
 * every start and keeps this in step: a purchase grants it, a refund (the
 * purchase gone from Play's answer) takes it away again.
 */
class AdsEntitlementRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val adsRemoved: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY] ?: false }

    suspend fun setAdsRemoved(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY] = value }
    }

    private companion object {
        val KEY = booleanPreferencesKey("ads_removed")
    }
}

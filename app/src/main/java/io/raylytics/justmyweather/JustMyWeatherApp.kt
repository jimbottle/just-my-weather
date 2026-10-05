package io.raylytics.justmyweather

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.ads.MobileAds
import io.raylytics.justmyweather.alerts.AlertNotifier
import io.raylytics.justmyweather.alerts.AlertScheduling
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.billing.PlayBillingGateway
import io.raylytics.justmyweather.billing.RemoveAdsManager
import io.raylytics.justmyweather.data.AdsEntitlementRepository
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import io.raylytics.justmyweather.data.DataStoreLastLocationStore
import io.raylytics.justmyweather.data.DataStorePointCache
import io.raylytics.justmyweather.data.DataStoreSnapshotCache
import io.raylytics.justmyweather.data.GadgetbridgeSettingsRepository
import io.raylytics.justmyweather.data.ThemeConfigRepository
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.WidgetConfigRepository
import io.raylytics.justmyweather.data.WidgetDataStore
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeBroadcaster
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeExporter
import io.raylytics.justmyweather.data.metno.MetNoClient
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.OkHttpTransport
import io.raylytics.justmyweather.data.places.AssetPlaceSource
import io.raylytics.justmyweather.data.places.SavedPlacesRepository
import io.raylytics.justmyweather.location.LocationProvider
import io.raylytics.justmyweather.location.LocationResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cache
import java.io.File

/** App-wide DataStore for user settings (view config, alert rules). */
private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Plain manual dependency wiring — no DI framework. For an app this size a
 * single readable container beats annotations a first-time contributor would
 * have to learn: every dependency is constructed in one place, in plain sight.
 */
class AppContainer(context: Context, scope: CoroutineScope) {
    private val nwsClient = NwsClient(transport = OkHttpTransport())

    private val appContext = context.applicationContext

    // Only for days eight and nine of the Daily view, which NWS does not
    // forecast. Its OWN OkHttp client, with a small disk cache: MET's terms
    // ask that a client honour Expires and revalidate with If-Modified-Since,
    // which OkHttp does by itself once it has somewhere to keep the response.
    // NWS deliberately stays uncached — a tap on Refresh must reach the
    // network, and NWS's cache headers would otherwise swallow it.
    private val metNoClient =
        MetNoClient(
            transport =
                OkHttpTransport(
                    OkHttpTransport.defaultClient
                        .newBuilder()
                        .cache(Cache(File(appContext.cacheDir, "metno-http"), MET_NO_CACHE_BYTES))
                        .build(),
                ),
        )

    // Both caches are persisted so a cold start has something to work with: the
    // point cache reuses the resolved grid instead of re-hitting /points +
    // /stations, and the snapshot cache gives the home screen a real reading to
    // paint while the live fetch is in flight.
    val weatherRepository =
        WeatherRepository(
            nws = nwsClient,
            metNo = metNoClient,
            pointCache = DataStorePointCache(appContext.dataStore),
            snapshotCache = DataStoreSnapshotCache(appContext.dataStore),
        )
    val locationProvider = LocationProvider(appContext)

    // Everything that needs a place asks this, not the provider directly: a
    // moment without a fix must fall back to where we last knew the user to
    // be, not to a default city. The background poll is the caller that most
    // depends on it — it never gets a fix at all.
    val savedPlacesRepository = SavedPlacesRepository(appContext.dataStore)

    /** The gazetteer is opened only when the places screen asks; nothing here
     * holds 32k rows for the life of the process. */
    val placeSource = AssetPlaceSource(appContext)

    // A chosen place outranks the device fix, for the alert worker as much as
    // for the glance — they share this one resolver, which is why the choice
    // reaches background polling without any extra wiring.
    val locationResolver =
        LocationResolver(
            locationProvider,
            DataStoreLastLocationStore(appContext.dataStore),
            chosenPlace = savedPlacesRepository::current,
        )
    val viewConfigRepository = ViewConfigRepository(appContext.dataStore)
    val themeConfigRepository = ThemeConfigRepository(appContext.dataStore)
    val alertRulesRepository = AlertRulesRepository(appContext.dataStore)
    val alertSettingsRepository = AlertSettingsRepository(appContext.dataStore)
    val alertNotifier = AlertNotifier(appContext)

    // Home screen widgets: each one's settings, and the one fetched dataset
    // they all draw from. Both are read by the launcher-driven draw and the
    // refresh worker, which is why they live here and not in an Activity.
    val widgetConfigRepository = WidgetConfigRepository(appContext.dataStore)
    val widgetDataStore = WidgetDataStore(appContext.dataStore)

    // Optional hand-off of each reading to Gadgetbridge, which relays it to a
    // paired watch. Constructed unconditionally but inert until switched on:
    // the exporter reads the setting before building anything.
    val gadgetbridgeSettingsRepository = GadgetbridgeSettingsRepository(appContext.dataStore)
    val gadgetbridgeExporter =
        GadgetbridgeExporter(
            settings = gadgetbridgeSettingsRepository,
            broadcaster = GadgetbridgeBroadcaster(appContext),
        )

    // The banner and the $0.99 that removes it. The entitlement is the
    // device's copy of what Play says; the manager keeps the two in step and
    // is the only thing that talks to Play Billing.
    val adsEntitlementRepository = AdsEntitlementRepository(appContext.dataStore)
    val removeAdsManager = RemoveAdsManager(PlayBillingGateway(appContext), adsEntitlementRepository, scope)
}

class JustMyWeatherApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        container.alertNotifier.ensureChannel()
        // Schedule the hourly check only when rules are live, so a quiet install
        // does no background work; a launch check gives any standing rule timely
        // feedback. Reading the rule list is suspending, hence the scope.
        appScope.launch {
            val rules = container.alertRulesRepository.rules.first()
            val settings = container.alertSettingsRepository.current()
            // Same predicate the ViewModel uses, not a second copy of the `||`.
            val hasWork = AlertScheduling.hasWork(rules, settings)
            AlertWorker.sync(this@JustMyWeatherApp, hasWork, settings.pollMinutes)
            if (hasWork) AlertWorker.runOnce(this@JustMyWeatherApp)
        }
        appScope.launch {
            // An owner's phone never starts the ads SDK at all — nothing to
            // show, so nothing to initialise, and no network it would wake
            // for. Everyone else initialises it here, off the main thread,
            // where it costs no frame. Then Play is asked what the account
            // owns, which is what turns a reinstall back into an owner.
            if (!container.adsEntitlementRepository.adsRemoved.first()) {
                MobileAds.initialize(this@JustMyWeatherApp) {}
            }
            container.removeAdsManager.start()
        }
    }
}

/** One megabyte: a MET response is ~40 KB and a user watches a handful of places. */
private const val MET_NO_CACHE_BYTES = 1L shl 20

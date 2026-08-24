package io.raylytics.justmyweather

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.raylytics.justmyweather.alerts.AlertNotifier
import io.raylytics.justmyweather.alerts.AlertScheduling
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import io.raylytics.justmyweather.data.DataStoreLastLocationStore
import io.raylytics.justmyweather.data.DataStorePointCache
import io.raylytics.justmyweather.data.DataStoreSnapshotCache
import io.raylytics.justmyweather.data.GadgetbridgeSettingsRepository
import io.raylytics.justmyweather.data.ThemeConfigRepository
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeBroadcaster
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeExporter
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

/** App-wide DataStore for user settings (view config, alert rules). */
private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Plain manual dependency wiring — no DI framework. For an app this size a
 * single readable container beats annotations a first-time contributor would
 * have to learn: every dependency is constructed in one place, in plain sight.
 */
class AppContainer(context: Context) {
    private val nwsClient = NwsClient(transport = OkHttpTransport())
    private val appContext = context.applicationContext

    // Both caches are persisted so a cold start has something to work with: the
    // point cache reuses the resolved grid instead of re-hitting /points +
    // /stations, and the snapshot cache gives the home screen a real reading to
    // paint while the live fetch is in flight.
    val weatherRepository =
        WeatherRepository(
            nws = nwsClient,
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

    // Optional hand-off of each reading to Gadgetbridge, which relays it to a
    // paired watch. Constructed unconditionally but inert until switched on:
    // the exporter reads the setting before building anything.
    val gadgetbridgeSettingsRepository = GadgetbridgeSettingsRepository(appContext.dataStore)
    val gadgetbridgeExporter =
        GadgetbridgeExporter(
            settings = gadgetbridgeSettingsRepository,
            broadcaster = GadgetbridgeBroadcaster(appContext),
        )
}

class JustMyWeatherApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
    }
}

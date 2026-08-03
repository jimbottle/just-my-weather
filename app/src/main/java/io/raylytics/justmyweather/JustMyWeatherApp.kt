package io.raylytics.justmyweather

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.raylytics.justmyweather.alerts.AlertNotifier
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.AlertSettingsRepository
import io.raylytics.justmyweather.data.DataStorePointCache
import io.raylytics.justmyweather.data.GadgetbridgeSettingsRepository
import io.raylytics.justmyweather.data.ThemeConfigRepository
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeBroadcaster
import io.raylytics.justmyweather.data.gadgetbridge.GadgetbridgeExporter
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.OkHttpTransport
import io.raylytics.justmyweather.location.LocationProvider
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

    // The point cache is persisted so a cold start reuses the resolved grid
    // instead of re-hitting /points + /stations.
    val weatherRepository = WeatherRepository(nwsClient, DataStorePointCache(appContext.dataStore))
    val locationProvider = LocationProvider(appContext)
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
            val hasRules = container.alertRulesRepository.rules.first().any { it.enabled }
            val cadence = container.alertSettingsRepository.current().pollMinutes
            AlertWorker.sync(this@JustMyWeatherApp, hasRules, cadence)
            if (hasRules) AlertWorker.runOnce(this@JustMyWeatherApp)
        }
    }
}

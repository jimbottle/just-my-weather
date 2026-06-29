package io.raylytics.justmyweather

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.raylytics.justmyweather.alerts.AlertNotifier
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.data.AlertRulesRepository
import io.raylytics.justmyweather.data.DataStorePointCache
import io.raylytics.justmyweather.data.ThemeConfigRepository
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.OkHttpTransport
import io.raylytics.justmyweather.location.LocationProvider

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
    val alertNotifier = AlertNotifier(appContext)
}

class JustMyWeatherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Set up alerting infrastructure. Both are idempotent and cheap; the
        // worker self-guards to a no-op while the user has no rules.
        container.alertNotifier.ensureChannel()
        AlertWorker.schedule(this)
        // A check on launch gives a freshly-added rule timely feedback; the
        // hourly schedule covers the background case. Both dedup by firing state.
        AlertWorker.runOnce(this)
    }
}

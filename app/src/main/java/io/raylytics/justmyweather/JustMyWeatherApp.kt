package io.raylytics.justmyweather

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.raylytics.justmyweather.data.ViewConfigRepository
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.OkHttpTransport
import io.raylytics.justmyweather.location.LocationProvider

/** App-wide DataStore for user settings (view config today, more later). */
private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Plain manual dependency wiring — no DI framework. For an app this size a
 * single readable container beats annotations a first-time contributor would
 * have to learn: every dependency is constructed in one place, in plain sight.
 */
class AppContainer(context: Context) {
    private val nwsClient = NwsClient(transport = OkHttpTransport())
    private val appContext = context.applicationContext

    val weatherRepository = WeatherRepository(nwsClient)
    val locationProvider = LocationProvider(appContext)
    val viewConfigRepository = ViewConfigRepository(appContext.dataStore)
}

class JustMyWeatherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

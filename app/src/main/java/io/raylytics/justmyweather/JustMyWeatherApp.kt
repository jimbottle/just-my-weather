package io.raylytics.justmyweather

import android.app.Application
import android.content.Context
import io.raylytics.justmyweather.data.WeatherRepository
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.OkHttpTransport
import io.raylytics.justmyweather.location.LocationProvider

/**
 * Plain manual dependency wiring — no DI framework. For an app this size a
 * single readable container beats annotations a first-time contributor would
 * have to learn: every dependency is constructed in one place, in plain sight.
 */
class AppContainer(context: Context) {
    private val nwsClient = NwsClient(transport = OkHttpTransport())

    val weatherRepository = WeatherRepository(nwsClient)
    val locationProvider = LocationProvider(context.applicationContext)
}

class JustMyWeatherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

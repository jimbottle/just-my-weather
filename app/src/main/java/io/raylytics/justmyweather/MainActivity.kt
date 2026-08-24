package io.raylytics.justmyweather

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.ui.alerts.AlertsScreen
import io.raylytics.justmyweather.ui.alerts.AlertsViewModel
import io.raylytics.justmyweather.ui.customize.CustomizeScreen
import io.raylytics.justmyweather.ui.customize.CustomizeViewModel
import io.raylytics.justmyweather.ui.home.HomeScreen
import io.raylytics.justmyweather.ui.home.HomeViewModel
import io.raylytics.justmyweather.ui.home.SUN_TICK
import io.raylytics.justmyweather.ui.places.PlacesScreen
import io.raylytics.justmyweather.ui.places.PlacesViewModel
import io.raylytics.justmyweather.ui.theme.JustMyWeatherTheme
import io.raylytics.justmyweather.ui.theme.ThemeViewModel
import io.raylytics.justmyweather.ui.theme.themeResolvesToDark
import io.raylytics.justmyweather.view.ThemeConfig
import kotlinx.coroutines.delay

/** The screens this app has. A plain enum + state switch is all the navigation
 * a handful of destinations need — no nav library to learn or wire. */
private enum class Screen { HOME, CUSTOMIZE, ALERTS, PLACES }

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as JustMyWeatherApp).container }

    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    container.weatherRepository,
                    container.locationResolver,
                    container.viewConfigRepository,
                    // No-op unless the user switched the hand-off on.
                    container.gadgetbridgeExporter::export,
                )
            }
        }
    }

    private val customizeViewModel: CustomizeViewModel by viewModels {
        viewModelFactory {
            initializer {
                CustomizeViewModel(
                    container.viewConfigRepository,
                    container.gadgetbridgeSettingsRepository,
                )
            }
        }
    }

    private val placesViewModel: PlacesViewModel by viewModels {
        viewModelFactory {
            initializer {
                PlacesViewModel(
                    container.savedPlacesRepository,
                    loadCatalog = container.placeSource::load,
                )
            }
        }
    }

    private val themeViewModel: ThemeViewModel by viewModels {
        viewModelFactory {
            initializer { ThemeViewModel(container.themeConfigRepository) }
        }
    }

    private val alertsViewModel: AlertsViewModel by viewModels {
        viewModelFactory {
            initializer {
                AlertsViewModel(
                    container.alertRulesRepository,
                    container.alertSettingsRepository,
                    // Pass-through: the ViewModel decides whether the worker
                    // should run (rules OR safety alerts) and at what cadence,
                    // so the predicate is testable instead of buried here.
                    onWorkChanged = { hasWork, minutes ->
                        AlertWorker.sync(applicationContext, hasWork, minutes)
                    },
                    onRuleActivated = { AlertWorker.runOnce(applicationContext) },
                )
            }
        }
    }

    // Coarse location is optional: granting it re-fetches for the real place,
    // declining leaves the default location in place. Either way the app works.
    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) homeViewModel.refresh()
        }

    // Notification permission is requested when the user opens Alerts — that's
    // the moment they're opting in. Declining is fine; rules just won't post
    // until it's granted in system settings.
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ forces edge-to-edge for targetSdk 35; opting in explicitly
        // makes every OS version render the same way, so one inset strategy
        // (the safeDrawingPadding below) covers them all.
        enableEdgeToEdge()

        if (!container.locationProvider.hasPermission()) {
            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        setContent {
            val themeConfig by themeViewModel.config.collectAsStateWithLifecycle()
            // The bars sit on the app-painted background, and the user can
            // force a mood against the system setting — so bar icon contrast
            // must follow the app's resolved mood, not the system default
            // that the argless enableEdgeToEdge() above assumes.
            val dark = themeResolvesToDark(themeConfig)
            LaunchedEffect(dark) {
                val bars =
                    if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        // The darkScrim only shows on API 24/25, which can't
                        // render dark nav-bar icons: a translucent dark bar
                        // keeps the white buttons visible over a light theme.
                        SystemBarStyle.light(Color.TRANSPARENT, Color.argb(0x80, 0x1B, 0x1B, 0x1B))
                    }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            JustMyWeatherTheme(themeConfig) {
                // Surface Compose testTags as resource-ids so UI tests (Maestro)
                // can target controls that carry no stable text, like switches.
                // The background paints the full edge-to-edge window (so the
                // areas behind the system bars match the app), then the padding
                // keeps every screen's content clear of bars and cutouts.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .safeDrawingPadding()
                        .semantics { testTagsAsResourceId = true },
                ) {
                    App(
                        homeViewModel = homeViewModel,
                        customizeViewModel = customizeViewModel,
                        alertsViewModel = alertsViewModel,
                        placesViewModel = placesViewModel,
                        themeConfig = themeConfig,
                        onThemeChange = themeViewModel::save,
                        onEnterAlerts = ::requestNotificationsIfNeeded,
                    )
                }
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun App(
    homeViewModel: HomeViewModel,
    customizeViewModel: CustomizeViewModel,
    alertsViewModel: AlertsViewModel,
    placesViewModel: PlacesViewModel,
    themeConfig: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    onEnterAlerts: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // Keep "next sunrise / next sunset" actually next. The value decays with
    // the clock rather than with the data, so nothing about a fetch would
    // catch it: a glance left open — or backgrounded at 5am and reopened at
    // 9am — would otherwise name a sunrise that has already happened.
    //
    // Gated on RESUMED so it re-works the moment the user comes back and
    // costs nothing while they are elsewhere. Driven from here rather than
    // from inside HomeScreen because this is the edge that already knows
    // about lifecycle, and it leaves the screen a pure function of its state.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, homeViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                homeViewModel.refreshSunTimes()
                delay(SUN_TICK)
            }
        }
    }

    when (screen) {
        Screen.HOME -> {
            val state by homeViewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRefresh = homeViewModel::refresh,
                onSetMode = homeViewModel::setForecastMode,
                onCycleSpan = homeViewModel::cycleModuleSpan,
                onPlaces = { screen = Screen.PLACES },
                onMoveModule = homeViewModel::moveModule,
                onCustomize = { screen = Screen.CUSTOMIZE },
                onAlerts = {
                    onEnterAlerts()
                    screen = Screen.ALERTS
                },
            )
        }

        Screen.CUSTOMIZE -> {
            // System back returns to the glance rather than exiting the app.
            BackHandler { screen = Screen.HOME }
            val config by customizeViewModel.config.collectAsStateWithLifecycle()
            val gadgetbridgeEnabled by customizeViewModel.gadgetbridgeEnabled.collectAsStateWithLifecycle()
            CustomizeScreen(
                config = config,
                onToggle = customizeViewModel::toggle,
                onRelabel = customizeViewModel::relabel,
                onSetSpan = customizeViewModel::setSpan,
                onMoveUp = customizeViewModel::moveUp,
                onMoveDown = customizeViewModel::moveDown,
                onSetDensity = customizeViewModel::setDensity,
                onSetShowForecast = customizeViewModel::setShowForecast,
                onSetDefaultForecastMode = customizeViewModel::setDefaultForecastMode,
                onSetDailyStyle = customizeViewModel::setDailyStyle,
                onSetAlertBannerPosition = customizeViewModel::setAlertBannerPosition,
                theme = themeConfig,
                onThemeChange = onThemeChange,
                gadgetbridgeEnabled = gadgetbridgeEnabled,
                onSetGadgetbridgeEnabled = customizeViewModel::setGadgetbridgeEnabled,
                onDone = { screen = Screen.HOME },
            )
        }

        Screen.PLACES -> {
            BackHandler { screen = Screen.HOME }
            val saved by placesViewModel.saved.collectAsStateWithLifecycle()
            val results by placesViewModel.results.collectAsStateWithLifecycle()
            val loading by placesViewModel.loading.collectAsStateWithLifecycle()
            // The query is screen state, not app state: it does not outlive the
            // visit, and holding it in the ViewModel only to mirror it here
            // would be two copies of one string.
            var query by rememberSaveable { mutableStateOf("") }
            PlacesScreen(
                saved = saved,
                results = results,
                query = query,
                loading = loading,
                onQueryChange = {
                    query = it
                    placesViewModel.setQuery(it)
                },
                onSave = placesViewModel::save,
                onSaveCoordinates = placesViewModel::saveCoordinates,
                onSelect = placesViewModel::select,
                onRemove = placesViewModel::remove,
                onDone = {
                    screen = Screen.HOME
                    // The place decides which coordinates every fetch uses, so
                    // coming back has to re-ask rather than keep showing the
                    // last place's weather under the new place's name.
                    homeViewModel.refresh()
                },
            )
        }

        Screen.ALERTS -> {
            BackHandler { screen = Screen.HOME }
            val rules by alertsViewModel.rules.collectAsStateWithLifecycle()
            val alertSettings by alertsViewModel.settings.collectAsStateWithLifecycle()
            AlertsScreen(
                rules = rules,
                settings = alertSettings,
                onAdd = alertsViewModel::add,
                onToggle = alertsViewModel::toggle,
                onDelete = alertsViewModel::delete,
                onSetQuietHours = alertsViewModel::setQuietHours,
                onSetQuietWindow = alertsViewModel::setQuietWindow,
                onSetSafetyNotifications = alertsViewModel::setSafetyNotifications,
                onSetPollCadence = alertsViewModel::setPollCadence,
                onDone = { screen = Screen.HOME },
            )
        }
    }
}

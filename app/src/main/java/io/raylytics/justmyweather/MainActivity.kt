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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.ui.alerts.AlertsScreen
import io.raylytics.justmyweather.ui.alerts.AlertsViewModel
import io.raylytics.justmyweather.ui.customize.CustomizeScreen
import io.raylytics.justmyweather.ui.customize.CustomizeViewModel
import io.raylytics.justmyweather.ui.home.HomeScreen
import io.raylytics.justmyweather.ui.home.HomeViewModel
import io.raylytics.justmyweather.ui.theme.JustMyWeatherTheme
import io.raylytics.justmyweather.ui.theme.ThemeViewModel
import io.raylytics.justmyweather.ui.theme.themeResolvesToDark
import io.raylytics.justmyweather.view.ThemeConfig

/** The screens this app has. A plain enum + state switch is all the navigation
 * a handful of destinations need — no nav library to learn or wire. */
private enum class Screen { HOME, CUSTOMIZE, ALERTS }

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as JustMyWeatherApp).container }

    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                HomeViewModel(
                    container.weatherRepository,
                    container.locationProvider,
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
                    // "Is there any reason to poll?" — a live rule OR safety
                    // alerts being on. Using only the rule list here would let
                    // a user with no rules switch safety alerts on and have the
                    // worker cancelled out from under them.
                    onRulesChanged = { rules ->
                        AlertWorker.sync(
                            applicationContext,
                            rules.any { it.enabled } || alertsViewModel.settings.value.safetyNotifications,
                            alertsViewModel.settings.value.pollMinutes,
                        )
                    },
                    onRuleActivated = { AlertWorker.runOnce(applicationContext) },
                    onCadenceChanged = { minutes ->
                        AlertWorker.sync(
                            applicationContext,
                            alertsViewModel.rules.value.any { it.enabled } ||
                                alertsViewModel.settings.value.safetyNotifications,
                            minutes,
                        )
                    },
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
    themeConfig: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    onEnterAlerts: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    when (screen) {
        Screen.HOME -> {
            val state by homeViewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRefresh = homeViewModel::refresh,
                onSetMode = homeViewModel::setMode,
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
                onMoveUp = customizeViewModel::moveUp,
                onMoveDown = customizeViewModel::moveDown,
                onSetDensity = customizeViewModel::setDensity,
                onSetDefaultMode = customizeViewModel::setDefaultMode,
                onSetDailyStyle = customizeViewModel::setDailyStyle,
                onSetDailyLayout = customizeViewModel::setDailyLayout,
                onSetHourlyLayout = customizeViewModel::setHourlyLayout,
                onSetAlertBannerPosition = customizeViewModel::setAlertBannerPosition,
                theme = themeConfig,
                onThemeChange = onThemeChange,
                gadgetbridgeEnabled = gadgetbridgeEnabled,
                onSetGadgetbridgeEnabled = customizeViewModel::setGadgetbridgeEnabled,
                onDone = { screen = Screen.HOME },
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

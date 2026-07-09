package io.raylytics.justmyweather

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
                )
            }
        }
    }

    private val customizeViewModel: CustomizeViewModel by viewModels {
        viewModelFactory {
            initializer { CustomizeViewModel(container.viewConfigRepository) }
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
                    onRulesChanged = { rules ->
                        AlertWorker.sync(
                            applicationContext,
                            rules.any { it.enabled },
                            alertsViewModel.settings.value.pollMinutes,
                        )
                    },
                    onRuleActivated = { AlertWorker.runOnce(applicationContext) },
                    onCadenceChanged = { minutes ->
                        AlertWorker.sync(
                            applicationContext,
                            alertsViewModel.rules.value.any { it.enabled },
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

        if (!container.locationProvider.hasPermission()) {
            requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        setContent {
            val themeConfig by themeViewModel.config.collectAsStateWithLifecycle()
            JustMyWeatherTheme(themeConfig) {
                // Surface Compose testTags as resource-ids so UI tests (Maestro)
                // can target controls that carry no stable text, like switches.
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
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
            CustomizeScreen(
                config = config,
                onToggle = customizeViewModel::toggle,
                onRelabel = customizeViewModel::relabel,
                onMoveUp = customizeViewModel::moveUp,
                onMoveDown = customizeViewModel::moveDown,
                onSetDensity = customizeViewModel::setDensity,
                theme = themeConfig,
                onThemeChange = onThemeChange,
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
                onSetPollCadence = alertsViewModel::setPollCadence,
                onDone = { screen = Screen.HOME },
            )
        }
    }
}

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.raylytics.justmyweather.ads.AdBanner
import io.raylytics.justmyweather.ads.AdPolicy
import io.raylytics.justmyweather.alerts.AlertWorker
import io.raylytics.justmyweather.support.SupportKind
import io.raylytics.justmyweather.support.bugDiagnostics
import io.raylytics.justmyweather.support.composeSupportMail
import io.raylytics.justmyweather.support.currentAppInfo
import io.raylytics.justmyweather.support.ideaContext
import io.raylytics.justmyweather.support.openStoreListing
import io.raylytics.justmyweather.support.recentLogLines
import io.raylytics.justmyweather.support.supportMail
import io.raylytics.justmyweather.ui.alerts.AlertsScreen
import io.raylytics.justmyweather.ui.alerts.AlertsViewModel
import io.raylytics.justmyweather.ui.customize.CustomizeScreen
import io.raylytics.justmyweather.ui.customize.CustomizeViewModel
import io.raylytics.justmyweather.ui.home.HomeScreen
import io.raylytics.justmyweather.ui.home.HomeUiState
import io.raylytics.justmyweather.ui.home.HomeViewModel
import io.raylytics.justmyweather.ui.home.SUN_TICK
import io.raylytics.justmyweather.ui.home.describe
import io.raylytics.justmyweather.ui.places.PlacesScreen
import io.raylytics.justmyweather.ui.places.PlacesViewModel
import io.raylytics.justmyweather.ui.settings.AppSettingsScreen
import io.raylytics.justmyweather.ui.settings.AppSettingsViewModel
import io.raylytics.justmyweather.ui.settings.RegionScreen
import io.raylytics.justmyweather.ui.settings.RegionViewModel
import io.raylytics.justmyweather.ui.support.SupportScreen
import io.raylytics.justmyweather.ui.theme.JustMyWeatherTheme
import io.raylytics.justmyweather.ui.theme.LocalConventions
import io.raylytics.justmyweather.ui.theme.ThemeViewModel
import io.raylytics.justmyweather.ui.theme.themeResolvesToDark
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.widget.WidgetRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** How long a bug report waits for the glance to report its state. */
private const val GLANCE_WAIT_MS = 2_000L

/** The screens this app has. A plain enum + state switch is all the navigation
 * a handful of destinations need — no nav library to learn or wire. */
private enum class Screen { HOME, CUSTOMIZE, APP_SETTINGS, REGION, ALERTS, PLACES, REPORT_BUG, SUBMIT_IDEA }

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
                    // The region refreshes from a place the user chose or a
                    // fix — never the fallback city (roborev 5424).
                    onPlaceResolved = { container.regionRepository.refresh(container.locationResolver.resolveKnown()) },
                )
            }
        }
    }

    private val customizeViewModel: CustomizeViewModel by viewModels {
        viewModelFactory {
            initializer {
                CustomizeViewModel(container.viewConfigRepository)
            }
        }
    }

    private val appSettingsViewModel: AppSettingsViewModel by viewModels {
        viewModelFactory {
            initializer { AppSettingsViewModel(container.gadgetbridgeSettingsRepository, container.removeAdsManager) }
        }
    }

    private val regionViewModel: RegionViewModel by viewModels {
        viewModelFactory {
            initializer {
                RegionViewModel(container.regionRepository) {
                    WidgetRefreshWorker.runOnce(applicationContext, afterCurrent = true)
                }
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
                    officialAlertsHere = {
                        container.weatherRepository.officialAlertsAvailable(container.locationResolver.resolve())
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

        // Once per launch: Google's consent SDK says whether ads may run here,
        // asking the user first where the law requires it (ads/AdsConsent).
        // The location prompt waits until that has settled, so a first launch
        // in Europe shows one dialog at a time rather than the permission
        // sheet stacked on top of the consent form (seen on the emulator).
        container.adsConsent.gather(this) {
            val alive = !isFinishing && !isDestroyed
            if (alive && !container.locationProvider.hasPermission()) {
                requestLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        setContent {
            val themeConfig by themeViewModel.config.collectAsStateWithLifecycle()
            // Starts true: the banner is withheld until the store's answer
            // is read, so an owner never sees it flash in.
            val adsRemoved by container.adsEntitlementRepository.adsRemoved.collectAsStateWithLifecycle(true)
            // The region decides how everything reads; whether a banner may
            // be shown at all is Google's consent SDK's call (ads/AdsConsent),
            // false until it has answered, so no ad loads before then.
            val conventions by container.regionRepository.conventions.collectAsStateWithLifecycle(Conventions.US)
            val canShowAds by container.adsConsent.canShowAds.collectAsStateWithLifecycle()
            val privacyOptionsRequired by container.adsConsent.privacyOptionsRequired.collectAsStateWithLifecycle()
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
                    CompositionLocalProvider(LocalConventions provides conventions) {
                        App(
                            homeViewModel = homeViewModel,
                            customizeViewModel = customizeViewModel,
                            appSettingsViewModel = appSettingsViewModel,
                            regionViewModel = regionViewModel,
                            alertsViewModel = alertsViewModel,
                            placesViewModel = placesViewModel,
                            themeConfig = themeConfig,
                            onThemeChange = themeViewModel::save,
                            onEnterAlerts = ::requestNotificationsIfNeeded,
                            loadBugReportState = ::bugReportState,
                            adsRemoved = adsRemoved,
                            canShowAds = canShowAds,
                            privacyOptionsRequired = privacyOptionsRequired,
                            onPrivacyChoices = { container.adsConsent.showPrivacyOptions(this@MainActivity) },
                            onBuyRemoveAds = { appSettingsViewModel.buyRemoveAds(this@MainActivity) },
                            onRateApp = { openStoreListing() },
                        )
                    }
                }
            }
        }
    }

    /**
     * What a bug report says about the app, read from the persisted stores and
     * not from any screen's StateFlow: those are shared WhileSubscribed and
     * report their defaults to a reader that arrives before a subscriber.
     * The glance state is the exception — it lives only in the ViewModel.
     * Its StateFlow starts at Loading, and first() on a StateFlow returns the
     * current value at once, so the wait is for a SETTLED state: first {}
     * subscribes, which starts the upstream. A glance still loading when the
     * wait ends is reported as exactly that — a stuck fetch is the kind of
     * thing a bug report is for — rather than blocking the report.
     */
    private suspend fun bugReportState(): List<Pair<String, String>> {
        val config = container.viewConfigRepository.config.first()
        val home =
            withTimeoutOrNull(GLANCE_WAIT_MS) {
                homeViewModel.state.first { it !is HomeUiState.Loading }
            }
        return listOf(
            "Glance" to (home?.describe() ?: "Loading (still, after ${GLANCE_WAIT_MS}ms)"),
            "Location permission" to container.locationProvider.hasPermission().toString(),
            "Visible modules" to config.visible.size.toString(),
            "Density" to config.density.name,
            "Theme" to container.themeConfigRepository.config.first().toString(),
            "Gadgetbridge" to container.gadgetbridgeSettingsRepository.enabled.first().toString(),
        )
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
    appSettingsViewModel: AppSettingsViewModel,
    regionViewModel: RegionViewModel,
    alertsViewModel: AlertsViewModel,
    placesViewModel: PlacesViewModel,
    themeConfig: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    onEnterAlerts: () -> Unit,
    adsRemoved: Boolean,
    /** Whether Google's consent SDK says ads may run (ads/AdsConsent). */
    canShowAds: Boolean,
    /** Whether a "Privacy choices" entry must be offered. */
    privacyOptionsRequired: Boolean,
    onPrivacyChoices: () -> Unit,
    /** Opens Play's purchase sheet; needs the Activity, so the host supplies it. */
    onBuyRemoveAds: () -> Unit,
    /** Opens the Play listing; the Activity owns the intent. */
    onRateApp: () -> Unit,
    /** The app state a bug report attaches, read fresh from the stores. */
    loadBugReportState: suspend () -> List<Pair<String, String>>,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    // The idea form opens from Customize and from App Settings, and returns
    // to whichever one opened it.
    var ideaFrom by rememberSaveable { mutableStateOf(Screen.CUSTOMIZE) }

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
            // The glance, and under it — only here, and only until Remove
            // Ads is bought — the one banner. The glance keeps the height;
            // the banner takes the few dp AdMob picks for this width and
            // sits above the gesture inset the outer padding leaves.
            Column(Modifier.fillMaxSize()) {
                HomeScreen(
                    state = state,
                    onRefresh = homeViewModel::refresh,
                    onSetMode = homeViewModel::setForecastMode,
                    onResizeModule = homeViewModel::resizeModule,
                    onPlaces = { screen = Screen.PLACES },
                    onMoveModule = homeViewModel::moveModule,
                    onAppSettings = { screen = Screen.APP_SETTINGS },
                    onCustomize = { screen = Screen.CUSTOMIZE },
                    onAlerts = {
                        onEnterAlerts()
                        alertsViewModel.refreshCoverage()
                        screen = Screen.ALERTS
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!adsRemoved && canShowAds) {
                    AdBanner(unitId = AdPolicy.bannerUnitId(BuildConfig.DEBUG, BuildConfig.ADMOB_BANNER_UNIT_ID))
                }
            }
        }

        Screen.CUSTOMIZE -> {
            // System back returns to the glance rather than exiting the app.
            BackHandler { screen = Screen.HOME }
            val config by customizeViewModel.config.collectAsStateWithLifecycle()
            CustomizeScreen(
                config = config,
                onToggle = customizeViewModel::toggle,
                onRelabel = customizeViewModel::relabel,
                onResize = customizeViewModel::resize,
                onMoveUp = customizeViewModel::moveUp,
                onMoveDown = customizeViewModel::moveDown,
                onSetDensity = customizeViewModel::setDensity,
                onSetTapForDetails = customizeViewModel::setTapForDetails,
                onSetTimesIn = customizeViewModel::setTimesIn,
                onSetDefaultForecastMode = customizeViewModel::setDefaultForecastMode,
                onSetDailyStyle = customizeViewModel::setDailyStyle,
                onSetHourlyHours = customizeViewModel::setHourlyHours,
                onSetDailyDays = customizeViewModel::setDailyDays,
                onSetSunDays = customizeViewModel::setSunDays,
                onToggleForecastElement = customizeViewModel::toggleForecastElement,
                onSetForecastTileLayout = customizeViewModel::setForecastTileLayout,
                onSetAlertBannerPosition = customizeViewModel::setAlertBannerPosition,
                theme = themeConfig,
                onThemeChange = onThemeChange,
                onSubmitIdea = {
                    ideaFrom = Screen.CUSTOMIZE
                    screen = Screen.SUBMIT_IDEA
                },
                onDone = { screen = Screen.HOME },
            )
        }

        Screen.APP_SETTINGS -> {
            BackHandler { screen = Screen.HOME }
            val gadgetbridgeEnabled by appSettingsViewModel.gadgetbridgeEnabled.collectAsStateWithLifecycle()
            val settingsAdsRemoved by appSettingsViewModel.adsRemoved.collectAsStateWithLifecycle()
            val removeAdsOffer by appSettingsViewModel.removeAdsOffer.collectAsStateWithLifecycle()
            val removeAdsStatus by appSettingsViewModel.removeAdsStatus.collectAsStateWithLifecycle()
            val inForce by regionViewModel.resolved.collectAsStateWithLifecycle()
            val reading = LocalConventions.current.units
            val regionSummary =
                listOfNotNull(inForce?.region?.name, "${reading.temperature.label}, ${reading.wind.label}")
                    .joinToString(" · ")
            AppSettingsScreen(
                gadgetbridgeEnabled = gadgetbridgeEnabled,
                onSetGadgetbridgeEnabled = appSettingsViewModel::setGadgetbridgeEnabled,
                adsRemoved = settingsAdsRemoved,
                canShowAds = canShowAds,
                privacyOptionsRequired = privacyOptionsRequired,
                onPrivacyChoices = onPrivacyChoices,
                regionSummary = regionSummary,
                onRegion = { screen = Screen.REGION },
                removeAdsPrice = removeAdsOffer?.formattedPrice,
                removeAdsStatus = removeAdsStatus,
                onBuyRemoveAds = onBuyRemoveAds,
                onRestorePurchases = appSettingsViewModel::restorePurchases,
                onReportBug = { screen = Screen.REPORT_BUG },
                onSubmitIdea = {
                    ideaFrom = Screen.APP_SETTINGS
                    screen = Screen.SUBMIT_IDEA
                },
                onRateApp = onRateApp,
                version = "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                onDone = { screen = Screen.HOME },
            )
        }

        // Each form returns to the page it was opened from.
        Screen.REPORT_BUG -> {
            BackHandler { screen = Screen.APP_SETTINGS }
            val context = LocalContext.current
            val app = remember { currentAppInfo() }
            // Built once per visit and off the main thread: the state is read
            // from the stores rather than from screen-scoped flows (which hold
            // their defaults until something subscribes — Customize's config
            // never has, on the way here), and logcat is a process spawn. The
            // form shows exactly what will be sent, so it waits for this
            // rather than showing a guess that changes under the user.
            val attached by produceState<String?>(null) {
                value =
                    withContext(Dispatchers.IO) {
                        bugDiagnostics(
                            app = app,
                            sourceScreen = "App Settings",
                            state = loadBugReportState(),
                            logs = recentLogLines(),
                            timestamp = Instant.now().toString(),
                        )
                    }
            }
            SupportScreen(
                kind = SupportKind.BUG,
                attached = attached,
                onSend = { message ->
                    val diagnostics = attached ?: return@SupportScreen false
                    context.composeSupportMail(supportMail(SupportKind.BUG, app, message, diagnostics))
                },
                onDone = { screen = Screen.APP_SETTINGS },
            )
        }

        Screen.SUBMIT_IDEA -> {
            BackHandler { screen = ideaFrom }
            val context = LocalContext.current
            val app = remember { currentAppInfo() }
            val sourceScreen = if (ideaFrom == Screen.APP_SETTINGS) "App Settings" else "Customize"
            val attached = remember { ideaContext(app, sourceScreen = sourceScreen) }
            SupportScreen(
                kind = SupportKind.IDEA,
                attached = attached,
                onSend = { message ->
                    context.composeSupportMail(supportMail(SupportKind.IDEA, app, message, attached))
                },
                onDone = { screen = ideaFrom },
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

        Screen.REGION -> {
            BackHandler { screen = Screen.APP_SETTINGS }
            val settings by regionViewModel.settings.collectAsStateWithLifecycle()
            val resolved by regionViewModel.resolved.collectAsStateWithLifecycle()
            val automatic by regionViewModel.automatic.collectAsStateWithLifecycle()
            RegionScreen(
                settings = settings,
                resolved = resolved,
                automatic = automatic,
                conventions = LocalConventions.current,
                onUpdate = regionViewModel::update,
                onDone = { screen = Screen.APP_SETTINGS },
            )
        }

        Screen.ALERTS -> {
            BackHandler { screen = Screen.HOME }
            val rules by alertsViewModel.rules.collectAsStateWithLifecycle()
            val alertSettings by alertsViewModel.settings.collectAsStateWithLifecycle()
            val officialAlertsHere by alertsViewModel.officialAlerts.collectAsStateWithLifecycle()
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
                officialAlertsHere = officialAlertsHere,
                onDone = { screen = Screen.HOME },
                events = alertsViewModel.events,
            )
        }
    }
}

package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.navigation.AppRoutes
import com.example.ui.navigation.TopLevelDestinations
import com.example.ui.screens.CompatibilityScreen
import com.example.ui.screens.ConnectedDevicesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HotspotConfigScreen
import com.example.ui.screens.QrCodeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TroubleshootingScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.screens.WifiInfoScreen
import com.example.ui.theme.WifiShareTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(WifiShareApp.container(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            WifiShareTheme(themeMode = uiState.settings.themeMode) {
                WifiShareRootApp(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironment()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiShareRootApp(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    LaunchedEffect(uiState.settingsLoaded, uiState.settings.onboardingCompleted) {
        if (uiState.settingsLoaded &&
            uiState.settings.onboardingCompleted &&
            currentRoute == AppRoutes.ROUTE_WELCOME
        ) {
            navController.navigate(AppRoutes.ROUTE_DASHBOARD) {
                popUpTo(AppRoutes.ROUTE_WELCOME) { inclusive = true }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onPermissionsUpdated()
        val deniedCount = results.values.count { !it }
        if (deniedCount == 0) {
            viewModel.showMessage("Wi-Fi permissions granted.")
        } else {
            viewModel.showMessage("Some permissions were declined. Grant Nearby Wi-Fi Devices for local hotspot sharing.")
        }
    }

    val requestCorePermissions: () -> Unit = {
        val required = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
        permissionLauncher.launch(required)
    }

    val requestLocationForSsid: () -> Unit = {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    val openSystemIntent: (Intent, String) -> Unit = { intent, fallbackMsg ->
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            viewModel.showMessage(fallbackMsg)
        }
    }

    val openWifiSettings: () -> Unit = {
        openSystemIntent(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            "Could not open Android Wi-Fi settings directly."
        )
    }

    val openLocationSettings: () -> Unit = {
        openSystemIntent(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            "Could not open Location settings."
        )
    }

    val openHotspotSettings: () -> Unit = {
        val tetherIntent = Intent().apply {
            setClassName("com.android.settings", "com.android.settings.TetherSettings")
        }
        val launched = runCatching {
            tetherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(tetherIntent)
        }.isSuccess
        if (!launched) {
            openSystemIntent(
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                "Open Settings -> Network & internet -> Hotspot & tethering manually."
            )
        }
    }

    val openAppSettings: () -> Unit = {
        val appIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        openSystemIntent(appIntent, "Could not open App Info settings.")
    }

    val isTopLevelRoute = TopLevelDestinations.any { it.route == currentRoute }

    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                if (isTopLevelRoute) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { navController.navigate(AppRoutes.ROUTE_QR_CODE) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_qr_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "Share via QR Code"
                                )
                            }
                            IconButton(
                                onClick = { navController.navigate(AppRoutes.ROUTE_HOTSPOT_CONFIG) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_config_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Hotspot Configuration"
                                )
                            }
                            IconButton(
                                onClick = { navController.navigate(AppRoutes.ROUTE_TROUBLESHOOTING) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_troubleshoot_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Build,
                                    contentDescription = "Troubleshooting"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            bottomBar = {
                if (isTopLevelRoute && !useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelRoute && useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.ROUTE_WELCOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppRoutes.ROUTE_WELCOME) {
                        WelcomeScreen(
                            permissions = uiState.permissions,
                            onRequestPermissions = requestCorePermissions,
                            onContinue = {
                                viewModel.completeWelcomeOnboarding()
                                navController.navigate(AppRoutes.ROUTE_DASHBOARD) {
                                    popUpTo(AppRoutes.ROUTE_WELCOME) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(AppRoutes.ROUTE_DASHBOARD) {
                        DashboardScreen(
                            uiState = uiState,
                            onToggleSharing = { viewModel.toggleSharing() },
                            onSelectEngineMode = { viewModel.selectEngineMode(it) },
                            onOpenQrCode = { navController.navigate(AppRoutes.ROUTE_QR_CODE) },
                            onOpenHotspotConfig = { navController.navigate(AppRoutes.ROUTE_HOTSPOT_CONFIG) },
                            onOpenWifiInfo = { navigateToTopLevel(AppRoutes.ROUTE_WIFI_INFO) },
                            onOpenDevices = { navigateToTopLevel(AppRoutes.ROUTE_CONNECTED_DEVICES) },
                            onOpenCompatibility = { navigateToTopLevel(AppRoutes.ROUTE_COMPATIBILITY) },
                            onOpenTroubleshooting = { navController.navigate(AppRoutes.ROUTE_TROUBLESHOOTING) },
                            onOpenSystemHotspotSettings = openHotspotSettings
                        )
                    }

                    composable(AppRoutes.ROUTE_WIFI_INFO) {
                        WifiInfoScreen(
                            uiState = uiState,
                            onRefresh = { viewModel.refreshEnvironment() },
                            onProbeConnectivity = { viewModel.probeUpstreamConnectivity() },
                            onRequestLocationForSsid = requestLocationForSsid,
                            onOpenLocationSettings = openLocationSettings,
                            onOpenWifiSettings = openWifiSettings
                        )
                    }

                    composable(AppRoutes.ROUTE_HOTSPOT_CONFIG) {
                        HotspotConfigScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onSelectEngineMode = { viewModel.selectEngineMode(it) },
                            onSaveConfig = { http, socks, suffix, pass ->
                                viewModel.updateHotspotConfig(http, socks, suffix, pass)
                            }
                        )
                    }

                    composable(AppRoutes.ROUTE_CONNECTED_DEVICES) {
                        ConnectedDevicesScreen(
                            uiState = uiState,
                            onRefreshClients = { viewModel.refreshEnvironment() },
                            onToggleBlockClient = { viewModel.toggleClientBlocked(it) }
                        )
                    }

                    composable(AppRoutes.ROUTE_QR_CODE) {
                        QrCodeScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onStartSharing = { viewModel.toggleSharing() },
                            onShowMessage = { viewModel.showMessage(it) }
                        )
                    }

                    composable(AppRoutes.ROUTE_COMPATIBILITY) {
                        CompatibilityScreen(
                            uiState = uiState,
                            onRefreshCheck = { viewModel.refreshCompatibility() },
                            onOpenTroubleshooting = { navController.navigate(AppRoutes.ROUTE_TROUBLESHOOTING) }
                        )
                    }

                    composable(AppRoutes.ROUTE_TROUBLESHOOTING) {
                        TroubleshootingScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onRunDiagnostics = { viewModel.runDiagnostics() },
                            onRequestPermissions = requestCorePermissions,
                            onOpenWifiSettings = openWifiSettings,
                            onOpenHotspotSettings = openHotspotSettings,
                            onOpenAppSettings = openAppSettings,
                            onStartSharing = {
                                viewModel.toggleSharing()
                                navigateToTopLevel(AppRoutes.ROUTE_DASHBOARD)
                            }
                        )
                    }

                    composable(AppRoutes.ROUTE_SETTINGS) {
                        SettingsScreen(
                            uiState = uiState,
                            onToggleWifiOnlySafety = { viewModel.setWifiOnlySafetyMode(it) },
                            onToggleAutoStopWifiLoss = { viewModel.setAutoStopOnWifiLoss(it) },
                            onToggleSocks5Relay = { viewModel.setEnableSocks5Relay(it) },
                            onToggleForegroundService = { viewModel.setUseForegroundService(it) },
                            onSelectThemeMode = { viewModel.setThemeMode(it) },
                            onOpenHotspotConfig = { navController.navigate(AppRoutes.ROUTE_HOTSPOT_CONFIG) },
                            onOpenTroubleshooting = { navController.navigate(AppRoutes.ROUTE_TROUBLESHOOTING) },
                            onOpenWelcomeTour = { navController.navigate(AppRoutes.ROUTE_WELCOME) },
                            onOpenAppSettings = openAppSettings
                        )
                    }
                }
            }
        }
    }
}

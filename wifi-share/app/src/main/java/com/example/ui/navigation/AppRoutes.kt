package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

object AppRoutes {
    const val ROUTE_WELCOME = "welcome"
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_WIFI_INFO = "wifi_info"
    const val ROUTE_HOTSPOT_CONFIG = "hotspot_config"
    const val ROUTE_CONNECTED_DEVICES = "connected_devices"
    const val ROUTE_QR_CODE = "qr_code"
    const val ROUTE_COMPATIBILITY = "compatibility"
    const val ROUTE_TROUBLESHOOTING = "troubleshooting"
    const val ROUTE_SETTINGS = "settings"
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val TopLevelDestinations = listOf(
    TopLevelDestination(
        route = AppRoutes.ROUTE_DASHBOARD,
        label = "Dashboard",
        selectedIcon = Icons.Filled.Router,
        unselectedIcon = Icons.Outlined.Router,
        testTag = "nav_dashboard"
    ),
    TopLevelDestination(
        route = AppRoutes.ROUTE_WIFI_INFO,
        label = "Wi-Fi",
        selectedIcon = Icons.Filled.Wifi,
        unselectedIcon = Icons.Outlined.Wifi,
        testTag = "nav_wifi_info"
    ),
    TopLevelDestination(
        route = AppRoutes.ROUTE_CONNECTED_DEVICES,
        label = "Devices",
        selectedIcon = Icons.Filled.Devices,
        unselectedIcon = Icons.Outlined.Devices,
        testTag = "nav_devices"
    ),
    TopLevelDestination(
        route = AppRoutes.ROUTE_COMPATIBILITY,
        label = "Check",
        selectedIcon = Icons.Filled.NetworkCheck,
        unselectedIcon = Icons.Outlined.NetworkCheck,
        testTag = "nav_compatibility"
    ),
    TopLevelDestination(
        route = AppRoutes.ROUTE_SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

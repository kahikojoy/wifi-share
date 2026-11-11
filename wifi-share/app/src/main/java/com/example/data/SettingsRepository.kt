package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SharingEngineMode
import com.example.model.WifiShareSettings
import com.example.ui.theme.AppThemeMode
import com.example.util.Formatters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wifiShareDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wifi_share_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val WIFI_ONLY_SAFETY = booleanPreferencesKey("wifi_only_safety_mode")
        val AUTO_STOP_WIFI_LOSS = booleanPreferencesKey("auto_stop_on_wifi_loss")
        val ENABLE_SOCKS5 = booleanPreferencesKey("enable_socks5_relay")
        val USE_FOREGROUND_SERVICE = booleanPreferencesKey("use_foreground_service")
        val PREFERRED_ENGINE = stringPreferencesKey("preferred_engine_mode")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val SOCKS_PORT = intPreferencesKey("socks_port")
        val P2P_SSID_SUFFIX = stringPreferencesKey("p2p_ssid_suffix")
        val P2P_PASSPHRASE = stringPreferencesKey("p2p_passphrase")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settingsFlow: Flow<WifiShareSettings> = context.wifiShareDataStore.data.map { prefs ->
        val modeName = prefs[Keys.PREFERRED_ENGINE]
        val engineMode = SharingEngineMode.entries.firstOrNull { it.name == modeName }
            ?: SharingEngineMode.LOCAL_HOTSPOT_RELAY

        val themeName = prefs[Keys.THEME_MODE]
        val themeMode = AppThemeMode.entries.firstOrNull { it.name == themeName }
            ?: AppThemeMode.DARK_NAVY

        WifiShareSettings(
            wifiOnlySafetyMode = prefs[Keys.WIFI_ONLY_SAFETY] ?: true,
            autoStopOnWifiLoss = prefs[Keys.AUTO_STOP_WIFI_LOSS] ?: true,
            enableSocks5Relay = prefs[Keys.ENABLE_SOCKS5] ?: false,
            useForegroundService = prefs[Keys.USE_FOREGROUND_SERVICE] ?: true,
            preferredEngineMode = engineMode,
            proxyPort = (prefs[Keys.PROXY_PORT] ?: 8080).coerceIn(1024, 65535),
            socksPort = (prefs[Keys.SOCKS_PORT] ?: 1080).coerceIn(1024, 65535),
            p2pSsidSuffix = Formatters.sanitizeP2pSsidSuffix(
                prefs[Keys.P2P_SSID_SUFFIX] ?: "WiFiShare"
            ),
            p2pPassphrase = prefs[Keys.P2P_PASSPHRASE]?.takeIf { it.length >= 8 }
                ?: "ShareNet2026",
            themeMode = themeMode,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun setWifiOnlySafetyMode(enabled: Boolean) {
        context.wifiShareDataStore.edit { it[Keys.WIFI_ONLY_SAFETY] = enabled }
    }

    suspend fun setAutoStopOnWifiLoss(enabled: Boolean) {
        context.wifiShareDataStore.edit { it[Keys.AUTO_STOP_WIFI_LOSS] = enabled }
    }

    suspend fun setEnableSocks5Relay(enabled: Boolean) {
        context.wifiShareDataStore.edit { it[Keys.ENABLE_SOCKS5] = enabled }
    }

    suspend fun setUseForegroundService(enabled: Boolean) {
        context.wifiShareDataStore.edit { it[Keys.USE_FOREGROUND_SERVICE] = enabled }
    }

    suspend fun setPreferredEngineMode(mode: SharingEngineMode) {
        context.wifiShareDataStore.edit { it[Keys.PREFERRED_ENGINE] = mode.name }
    }

    suspend fun updateHotspotConfig(
        proxyPort: Int,
        socksPort: Int,
        p2pSsidSuffix: String,
        p2pPassphrase: String
    ) {
        context.wifiShareDataStore.edit { prefs ->
            prefs[Keys.PROXY_PORT] = proxyPort.coerceIn(1024, 65535)
            prefs[Keys.SOCKS_PORT] = socksPort.coerceIn(1024, 65535)
            prefs[Keys.P2P_SSID_SUFFIX] = Formatters.sanitizeP2pSsidSuffix(p2pSsidSuffix)
            if (p2pPassphrase.length >= 8) {
                prefs[Keys.P2P_PASSPHRASE] = p2pPassphrase.take(63)
            }
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.wifiShareDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.wifiShareDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }
}

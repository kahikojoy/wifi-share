package com.example

import android.app.Application
import android.content.Context
import com.example.compatibility.CompatibilityEngine
import com.example.data.SettingsRepository
import com.example.network.HotspotController
import com.example.network.UpstreamWifiMonitor

class WifiShareContainer(context: Context) {
    private val appContext = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val compatibilityEngine = CompatibilityEngine(appContext)
    val upstreamWifiMonitor = UpstreamWifiMonitor(appContext)
    val hotspotController = HotspotController(appContext, upstreamWifiMonitor)
}

class WifiShareApp : Application() {

    lateinit var container: WifiShareContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = WifiShareContainer(this)
    }

    companion object {
        fun container(context: Context): WifiShareContainer {
            return (context.applicationContext as WifiShareApp).container
        }
    }
}

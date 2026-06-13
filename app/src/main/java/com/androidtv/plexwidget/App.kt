package com.androidtv.plexwidget

import android.app.Application
import com.androidtv.plexwidget.data.PlexStore
import com.androidtv.plexwidget.net.PlexClient

class App : Application() {

    lateinit var plexStore: PlexStore
        private set

    /** Configured PlexClient bound to this install's stable client identifier. */
    val plexClient: PlexClient by lazy {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0"
        val deviceName = android.os.Build.MODEL ?: "Android TV"
        PlexClient(plexStore.clientId, deviceName, version)
    }

    override fun onCreate() {
        super.onCreate()
        plexStore = PlexStore(this)
    }

    companion object {
        fun from(ctx: android.content.Context) = ctx.applicationContext as App
    }
}

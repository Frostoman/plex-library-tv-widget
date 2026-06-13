package com.androidtv.plexwidget.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms periodic sync after boot / when the launcher asks us to (re)initialize programs. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SyncWorker.schedule(context)
    }
}

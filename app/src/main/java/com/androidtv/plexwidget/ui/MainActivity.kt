package com.androidtv.plexwidget.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.androidtv.plexwidget.sync.SyncWorker

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, MainFragment())
                .commit()
        }
        // Make sure background refresh is scheduled.
        SyncWorker.schedule(this)
    }
}

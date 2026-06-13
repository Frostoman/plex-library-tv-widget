package com.androidtv.plexwidget.ui

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.androidtv.plexwidget.R
import com.androidtv.plexwidget.launch.PlexLauncher
import com.androidtv.plexwidget.model.PlexItem
import com.androidtv.plexwidget.sync.SyncWorker
import kotlinx.coroutines.launch

/**
 * Invisible trampoline for home-screen card clicks. The Google TV launcher rewrites
 * the package on a cross-package intent it fires directly, so the card launches this
 * (our own, always-launchable) activity instead. It resolves the item's Plex URI
 * (for shows this is a quick network lookup of the first unwatched episode) and
 * re-issues the intent from our process, then finishes. Translucent — no visible UI.
 *
 * The home-screen "refresh" tile also routes here: it kicks off a background sync and
 * returns immediately to the home screen.
 */
class OpenItemActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra(EXTRA_REFRESH, false)) {
            SyncWorker.runOnce(applicationContext)
            Toast.makeText(applicationContext, getString(R.string.refresh_started), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val ratingKey = intent.getStringExtra(EXTRA_RATING_KEY)
        if (ratingKey == null) { finish(); return }
        val item = PlexItem(
            ratingKey = ratingKey,
            title = "",
            year = null,
            type = intent.getStringExtra(EXTRA_TYPE) ?: "movie",
            thumb = null,
            slug = intent.getStringExtra(EXTRA_SLUG),
            lastViewedAt = null,
        )
        lifecycleScope.launch {
            val uri = PlexLauncher.playUri(this@OpenItemActivity, item)
            if (!PlexLauncher.launchUri(this@OpenItemActivity, uri)) {
                runCatching { startActivity(PlexLauncher.fallbackIntent(this@OpenItemActivity)) }
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_RATING_KEY = "rating_key"
        const val EXTRA_TYPE = "type"
        const val EXTRA_SLUG = "slug"
        const val EXTRA_REFRESH = "refresh"
    }
}

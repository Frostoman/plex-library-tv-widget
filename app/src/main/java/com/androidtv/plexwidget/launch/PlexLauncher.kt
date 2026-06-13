package com.androidtv.plexwidget.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.androidtv.plexwidget.App
import com.androidtv.plexwidget.model.PlexItem
import com.androidtv.plexwidget.ui.MainActivity
import com.androidtv.plexwidget.ui.OpenItemActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the official Plex app for a library item. Behaviour by type (all verified on
 * a real Android TV against the installed Plex app):
 *
 *  - MOVIE: a `plex://server://{machineId}/com.plexapp.plugins.library/library/metadata/{ratingKey}`
 *    intent (the exact form Plex's own home-screen channel cards use) starts playback
 *    immediately in Plex's player.
 *  - SHOW: we look up the show's **first unwatched episode** (`/allLeaves`, first with
 *    `viewCount == 0`) and play that episode with the same intent — so a click resumes
 *    the series where the user left off, skipping watched episodes. If every episode is
 *    watched (or lookup fails), we open the show's page via `watch.plex.tv/show/{slug}`.
 *
 * Home-screen cards can't carry these cross-package intents directly (the Google TV
 * launcher rewrites the target package); they launch our [OpenItemActivity] trampoline,
 * which resolves + re-issues the intent from our process.
 */
object PlexLauncher {

    const val PLEX_PACKAGE = "com.plexapp.android"

    fun isPlexInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PLEX_PACKAGE, 0); true
    }.getOrDefault(false)

    /** Direct-playback intent for a server item (movie or episode ratingKey). */
    private fun playbackUri(machineId: String, ratingKey: String): Uri = Uri.parse(
        "plex://server://$machineId/com.plexapp.plugins.library" +
            "/library/metadata/$ratingKey?playbackOrigin=AndroidTV%20Channel",
    )

    /** watch.plex.tv catalog page (used as a fallback / for shows with no unwatched episode). */
    private fun slugUri(item: PlexItem): Uri? {
        val slug = item.slug?.takeIf { it.isNotBlank() } ?: return null
        val kind = if (item.type == "show") "show" else "movie"
        return Uri.parse("https://watch.plex.tv/$kind/$slug")
    }

    /**
     * Resolve the Plex URI to open for an item. Movies resolve instantly; shows make a
     * network call to find the first unwatched episode, so call this off the main thread.
     */
    suspend fun playUri(context: Context, item: PlexItem): Uri? = withContext(Dispatchers.IO) {
        val store = App.from(context).plexStore
        val machineId = store.serverMachineId

        if (item.type != "show") {
            return@withContext if (machineId != null) playbackUri(machineId, item.ratingKey) else slugUri(item)
        }

        val base = store.serverBaseUri
        val token = store.serverToken
        if (machineId != null && base != null && token != null) {
            val episode = runCatching {
                App.from(context).plexClient.firstUnwatchedEpisode(base, token, item.ratingKey)
            }.getOrNull()
            if (episode != null) return@withContext playbackUri(machineId, episode)
        }
        slugUri(item) // all watched / lookup failed → open the show's page
    }

    /**
     * Fire a resolved Plex URI (scoped to the Plex package); on any failure fall back to
     * launching the Plex app. Must run on the main thread. Returns false only if Plex is
     * not installed.
     */
    fun launchUri(context: Context, uri: Uri?): Boolean {
        if (!isPlexInstalled(context)) return false
        if (uri != null) {
            val view = Intent(Intent.ACTION_VIEW, uri)
                .setPackage(PLEX_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(view); true }.getOrDefault(false)) return true
        }
        val launch = context.packageManager.getLaunchIntentForPackage(PLEX_PACKAGE) ?: return false
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }.getOrDefault(false)
    }

    /**
     * Intent to put on a home-screen preview program: targets our [OpenItemActivity]
     * trampoline, carrying the item identity so the trampoline can resolve + launch it.
     */
    fun homeCardIntent(context: Context, item: PlexItem): Intent =
        Intent(context, OpenItemActivity::class.java).apply {
            putExtra(OpenItemActivity.EXTRA_RATING_KEY, item.ratingKey)
            putExtra(OpenItemActivity.EXTRA_TYPE, item.type)
            putExtra(OpenItemActivity.EXTRA_SLUG, item.slug)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Intent for the home-screen "refresh" tile: triggers a background library sync. */
    fun refreshIntent(context: Context): Intent =
        Intent(context, OpenItemActivity::class.java).apply {
            putExtra(OpenItemActivity.EXTRA_REFRESH, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Fallback target so a card is never a dead end. */
    fun fallbackIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

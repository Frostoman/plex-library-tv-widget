package com.androidtv.plexwidget.model

/**
 * The two kinds of library this app surfaces, each as its own home-screen channel.
 * [serverType] matches Plex's library/item `type` field; [key] is our internal
 * identifier used for prefs, caches and channel bookkeeping.
 */
/** Ordering of titles in the widgets / channels. */
enum class SortOrder {
    /** Most recently watched first; never-watched titles after, alphabetically. */
    RECENT,
    /** Strictly A→Z by title. */
    ALPHABETICAL;

    fun comparator(): Comparator<PlexItem> = when (this) {
        RECENT -> compareByDescending<PlexItem> { it.lastViewedAt ?: 0L }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        ALPHABETICAL -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    }

    companion object {
        fun fromKey(name: String?): SortOrder = entries.firstOrNull { it.name == name } ?: RECENT
    }
}

enum class MediaKind(val key: String, val serverType: String) {
    MOVIE("movie", "movie"),
    SHOW("show", "show");

    companion object {
        fun fromKey(key: String?): MediaKind? = entries.firstOrNull { it.key == key }
    }
}

/** A single Plex Media Server connection candidate from plex.tv/resources. */
data class PlexConnection(
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
)

/** A Plex Media Server reachable by this account. */
data class PlexServer(
    val name: String,
    /** clientIdentifier — the server's machineIdentifier, used for deep links. */
    val machineId: String,
    /** Per-server access token (used for all {server}/... calls). */
    val accessToken: String,
    val owned: Boolean,
    val connections: List<PlexConnection>,
)

/** A library section on the server. */
data class PlexLibrary(
    val key: String,
    val type: String, // "movie", "show", "artist", "photo"
    val title: String,
    val agent: String, // e.g. tv.plex.agents.movie; com.plexapp.agents.none = "Other Videos"
)

/** A movie or show as shown on a card. */
data class PlexItem(
    /** ratingKey — the per-item id (also used for the poster cache filename). */
    val ratingKey: String,
    val title: String,
    val year: Int?,
    val type: String, // "movie" or "show"
    /** Relative poster path on the server, e.g. /library/metadata/123/thumb/170000. */
    val thumb: String?,
    /** Plex catalog slug (e.g. "dune-part-two") for watch.plex.tv deep links; null if unmatched. */
    val slug: String?,
    /** Epoch seconds of the last time this item was watched; null if never watched. */
    val lastViewedAt: Long?,
)

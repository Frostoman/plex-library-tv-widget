package com.androidtv.plexwidget.data

import android.content.Context
import com.androidtv.plexwidget.model.MediaKind
import com.androidtv.plexwidget.model.PlexItem
import com.androidtv.plexwidget.model.SortOrder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Persists the linked Plex account + chosen server + cached library items.
 * SharedPreferences-backed; poster JPEGs live as files under filesDir/posters/.
 */
class PlexStore(context: Context) {

    private val prefs = context.getSharedPreferences("plex", Context.MODE_PRIVATE)
    private val posterDir = File(context.filesDir, "posters").apply { mkdirs() }

    /** Stable per-install identifier sent as X-Plex-Client-Identifier. Created once. */
    val clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_CLIENT_ID, it).apply()
        }

    val isLinked: Boolean get() = accountToken != null && serverBaseUri != null

    var accountToken: String?
        get() = prefs.getString(KEY_ACCOUNT_TOKEN, null)
        set(v) = prefs.edit().putString(KEY_ACCOUNT_TOKEN, v).apply()

    var serverName: String?
        get() = prefs.getString(KEY_SERVER_NAME, null)
        set(v) = prefs.edit().putString(KEY_SERVER_NAME, v).apply()

    /** Server machineIdentifier — needed to build per-item deep links. */
    var serverMachineId: String?
        get() = prefs.getString(KEY_SERVER_MACHINE, null)
        set(v) = prefs.edit().putString(KEY_SERVER_MACHINE, v).apply()

    /** Currently-reachable server base URI, e.g. https://1-2-3-4.<hash>.plex.direct:32400. */
    var serverBaseUri: String?
        get() = prefs.getString(KEY_SERVER_URI, null)
        set(v) = prefs.edit().putString(KEY_SERVER_URI, v).apply()

    /** Per-server access token used for all {server}/... requests. */
    var serverToken: String?
        get() = prefs.getString(KEY_SERVER_TOKEN, null)
        set(v) = prefs.edit().putString(KEY_SERVER_TOKEN, v).apply()

    /** How titles are ordered in the widgets. Defaults to most-recently-watched. */
    var sortOrder: SortOrder
        get() = SortOrder.fromKey(prefs.getString(KEY_SORT, null))
        set(v) = prefs.edit().putString(KEY_SORT, v.name).apply()

    /** Epoch ms of the last time we launched Plex — used to pick a warm-up delay. */
    var lastPlexLaunchAt: Long
        get() = prefs.getLong(KEY_LAST_LAUNCH, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_LAUNCH, v).apply()

    // ---- cached items per kind ----

    fun saveItems(kind: MediaKind, items: List<PlexItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("rk", it.ratingKey)
                    .put("t", it.title)
                    .put("y", it.year ?: JSONObject.NULL)
                    .put("ty", it.type)
                    .put("th", it.thumb ?: JSONObject.NULL)
                    .put("sl", it.slug ?: JSONObject.NULL)
                    .put("lv", it.lastViewedAt ?: JSONObject.NULL),
            )
        }
        prefs.edit().putString(itemsKey(kind), arr.toString()).apply()
    }

    fun loadItems(kind: MediaKind): List<PlexItem> {
        val raw = prefs.getString(itemsKey(kind), null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PlexItem(
                ratingKey = o.getString("rk"),
                title = o.optString("t"),
                year = if (o.isNull("y")) null else o.optInt("y"),
                type = o.optString("ty"),
                thumb = if (o.isNull("th")) null else o.optString("th"),
                slug = if (o.isNull("sl")) null else o.optString("sl"),
                lastViewedAt = if (o.isNull("lv")) null else o.optLong("lv"),
            )
        }
    }

    fun posterFile(ratingKey: String): File = File(posterDir, "$ratingKey.jpg")

    // ---- per-kind visibility (which items appear on the home screen) ----
    // We store the HIDDEN ratingKeys; an empty set means everything is visible,
    // so items discovered in future syncs are shown by default.

    private fun hiddenIds(kind: MediaKind): MutableSet<String> {
        val raw = prefs.getString(hiddenKey(kind), null) ?: return mutableSetOf()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
        }.getOrDefault(mutableSetOf())
    }

    fun isVisible(kind: MediaKind, ratingKey: String): Boolean = ratingKey !in hiddenIds(kind)

    fun setVisible(kind: MediaKind, ratingKey: String, visible: Boolean) {
        val set = hiddenIds(kind)
        if (visible) set.remove(ratingKey) else set.add(ratingKey)
        val arr = JSONArray()
        set.forEach { arr.put(it) }
        prefs.edit().putString(hiddenKey(kind), arr.toString()).apply()
    }

    fun clear() {
        // Keep the client identifier so re-linking reuses the same authorized device.
        val id = prefs.getString(KEY_CLIENT_ID, null)
        prefs.edit().clear().apply()
        if (id != null) prefs.edit().putString(KEY_CLIENT_ID, id).apply()
        posterDir.listFiles()?.forEach { it.delete() }
    }

    private fun itemsKey(kind: MediaKind) = "items_${kind.key}"
    private fun hiddenKey(kind: MediaKind) = "hidden_${kind.key}"

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_ACCOUNT_TOKEN = "account_token"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_SERVER_MACHINE = "server_machine"
        const val KEY_SERVER_URI = "server_uri"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_SORT = "sort_order"
        const val KEY_LAST_LAUNCH = "last_plex_launch"
    }
}

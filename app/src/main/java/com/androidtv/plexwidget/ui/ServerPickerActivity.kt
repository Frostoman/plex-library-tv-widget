package com.androidtv.plexwidget.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.lifecycle.lifecycleScope
import com.androidtv.plexwidget.App
import com.androidtv.plexwidget.R
import com.androidtv.plexwidget.model.PlexServer
import com.androidtv.plexwidget.sync.SyncManager
import com.androidtv.plexwidget.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lets the user choose which Plex Media Server to use, when their account can reach
 * more than one. Shown automatically during linking (only if there's a choice) and
 * available any time from Settings. A single-server account is auto-selected with no
 * prompt. Picking a server stores it and runs a sync.
 */
class ServerPickerActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, ServerPickerFragment(), android.R.id.content)
        }
    }
}

class ServerPickerFragment : GuidedStepSupportFragment() {

    private val app by lazy { App.from(requireContext()) }
    private val appContext by lazy { requireContext().applicationContext }
    private var servers: List<PlexServer> = emptyList()

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(R.string.server_title),
            getString(R.string.server_loading),
            null,
            null,
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        // Populated after we fetch the server list.
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadServers()
    }

    private fun loadServers() {
        val token = app.plexStore.accountToken ?: run { activity?.finish(); return }
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { app.plexClient.resources(token) }.getOrDefault(emptyList())
            }
            servers = list
            when {
                list.isEmpty() -> setStatus(getString(R.string.link_no_server))
                list.size == 1 -> { setStatus(getString(R.string.link_success)); selectServer(list[0]) }
                else -> {
                    setStatus(getString(R.string.server_desc))
                    actions = list.mapIndexed { i, s -> serverAction(i, s) }
                }
            }
        }
    }

    private fun serverAction(index: Int, server: PlexServer): GuidedAction {
        val local = server.connections.any { it.local && !it.relay }
        val hint = getString(if (local) R.string.server_local else R.string.server_remote)
        return GuidedAction.Builder(requireContext())
            .id(index.toLong())
            .title(server.name)
            .description(hint)
            .build()
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        servers.getOrNull(action.id.toInt())?.let { selectServer(it) }
    }

    private fun selectServer(server: PlexServer) {
        setStatus(getString(R.string.link_success))
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val uri = app.plexClient.pickReachableConnection(server) ?: return@withContext false
                app.plexStore.apply {
                    serverName = server.name
                    serverMachineId = server.machineId
                    serverToken = server.accessToken
                    serverBaseUri = uri
                }
                SyncWorker.schedule(appContext)
                SyncManager(appContext).sync()
                true
            }
            if (ok) activity?.finish() else setStatus(getString(R.string.link_no_server))
        }
    }

    private fun setStatus(text: String) {
        guidanceStylist.descriptionView?.text = text
    }
}

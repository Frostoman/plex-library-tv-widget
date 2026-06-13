package com.androidtv.plexwidget.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.androidtv.plexwidget.App
import com.androidtv.plexwidget.R
import com.androidtv.plexwidget.databinding.ActivityLinkBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Links a Plex account using the keyboard-free plex.tv/link PIN flow:
 * show a code, poll until the user enters it at plex.tv/link, then discover the
 * server and run the first sync.
 */
class PlexLinkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLinkBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLinkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.retryButton.setOnClickListener { startLinking() }
        startLinking()
    }

    private fun startLinking() {
        val app = App.from(this)
        val client = app.plexClient
        setBusy(true)
        binding.retryButton.visibility = View.GONE
        binding.codeText.text = ""
        binding.statusText.text = ""

        lifecycleScope.launch {
            try {
                val pin = withContext(Dispatchers.IO) { client.createPin() }
                binding.codeText.text = pin.code
                binding.statusText.text = getString(R.string.link_waiting)

                // Poll for ~5 minutes (codes are valid ~30 min, but the user is here now).
                var token: String? = null
                for (attempt in 0 until MAX_POLLS) {
                    delay(POLL_MS)
                    token = withContext(Dispatchers.IO) { client.checkPin(pin.id) }
                    if (token != null) break
                }

                if (token == null) {
                    fail(getString(R.string.link_expired)); return@launch
                }

                app.plexStore.accountToken = token
                binding.statusText.text = getString(R.string.link_success)

                // Hand off to server selection (auto-selects when there's only one server).
                startActivity(android.content.Intent(this@PlexLinkActivity, ServerPickerActivity::class.java))
                finish()
            } catch (e: Exception) {
                fail(getString(R.string.link_failed, e.message ?: getString(R.string.error_network)))
            }
        }
    }

    private fun fail(message: String) {
        binding.statusText.text = message
        binding.retryButton.visibility = View.VISIBLE
        setBusy(false)
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private companion object {
        const val POLL_MS = 3000L
        const val MAX_POLLS = 100
    }
}

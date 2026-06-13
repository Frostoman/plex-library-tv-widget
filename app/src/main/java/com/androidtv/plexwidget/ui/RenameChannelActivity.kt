package com.androidtv.plexwidget.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.androidtv.plexwidget.R
import com.androidtv.plexwidget.databinding.ActivityRenameBinding
import com.androidtv.plexwidget.model.MediaKind
import com.androidtv.plexwidget.tv.TvChannelPublisher

/**
 * Lets the user set the display name of each home-screen channel (Movies / TV Shows).
 * Empty falls back to the default string. Stored in the same prefs the publisher reads.
 */
class RenameChannelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRenameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRenameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val publisher = TvChannelPublisher(this)
        val prefs = getSharedPreferences(TvChannelPublisher.PREFS, Context.MODE_PRIVATE)
        binding.moviesInput.setText(prefs.getString(publisher.channelNameKey(MediaKind.MOVIE), ""))
        binding.showsInput.setText(prefs.getString(publisher.channelNameKey(MediaKind.SHOW), ""))

        binding.saveButton.setOnClickListener {
            prefs.edit()
                .putString(publisher.channelNameKey(MediaKind.MOVIE), binding.moviesInput.text.toString().trim())
                .putString(publisher.channelNameKey(MediaKind.SHOW), binding.showsInput.text.toString().trim())
                .apply()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                publisher.applyChannelName(MediaKind.MOVIE)
                publisher.applyChannelName(MediaKind.SHOW)
            }
            Toast.makeText(this, getString(R.string.rename_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

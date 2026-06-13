package com.androidtv.plexwidget.ui

import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.androidtv.plexwidget.App
import com.androidtv.plexwidget.R
import com.androidtv.plexwidget.model.MediaKind
import com.androidtv.plexwidget.model.PlexItem
import com.androidtv.plexwidget.tv.TvChannelPublisher

/** Hosts the per-library item-selection checklist (kind passed via [EXTRA_KIND]). */
class SelectItemsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val kind = MediaKind.fromKey(intent.getStringExtra(EXTRA_KIND)) ?: MediaKind.MOVIE
            GuidedStepSupportFragment.addAsRoot(this, SelectItemsFragment.create(kind), android.R.id.content)
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
    }
}

/**
 * One checkbox per item. Checked = visible in the home-screen widget.
 * Action ids are list positions (ratingKeys aren't guaranteed numeric); toggles
 * are persisted immediately; the channel is republished when leaving the screen.
 */
class SelectItemsFragment : GuidedStepSupportFragment() {

    private val kind by lazy {
        MediaKind.fromKey(arguments?.getString(SelectItemsActivity.EXTRA_KIND)) ?: MediaKind.MOVIE
    }
    private val store by lazy { App.from(requireContext()).plexStore }
    private val items: List<PlexItem> by lazy {
        store.loadItems(kind).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val title = if (kind == MediaKind.SHOW) R.string.select_shows_title else R.string.select_movies_title
        return GuidanceStylist.Guidance(
            getString(title),
            getString(R.string.select_items_desc),
            null,
            null,
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        items.forEachIndexed { index, item ->
            actions.add(
                GuidedAction.Builder(requireContext())
                    .id(index.toLong())
                    .title(item.title)
                    .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                    .checked(store.isVisible(kind, item.ratingKey))
                    .build(),
            )
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val item = items.getOrNull(action.id.toInt()) ?: return
        store.setVisible(kind, item.ratingKey, action.isChecked)
    }

    override fun onStop() {
        super.onStop()
        // Republish this channel with the new selection using cached items (no network).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ctx = requireContext().applicationContext
            val s = store
            val k = kind
            Thread {
                runCatching { TvChannelPublisher(ctx).publish(k, s, s.loadItems(k)) }
            }.start()
        }
    }

    companion object {
        fun create(kind: MediaKind) = SelectItemsFragment().apply {
            arguments = Bundle().apply { putString(SelectItemsActivity.EXTRA_KIND, kind.key) }
        }
    }
}

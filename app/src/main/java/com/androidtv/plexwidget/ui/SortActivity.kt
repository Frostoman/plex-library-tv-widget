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
import com.androidtv.plexwidget.model.SortOrder
import com.androidtv.plexwidget.tv.TvChannelPublisher

/** Lets the user choose how titles are ordered in both widgets. */
class SortActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, SortFragment(), android.R.id.content)
        }
    }
}

class SortFragment : GuidedStepSupportFragment() {

    private val store by lazy { App.from(requireContext()).plexStore }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
        GuidanceStylist.Guidance(
            getString(R.string.sort_title),
            getString(R.string.sort_desc),
            null,
            null,
        )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val current = store.sortOrder
        actions.add(radio(SortOrder.RECENT, getString(R.string.sort_recent), current))
        actions.add(radio(SortOrder.ALPHABETICAL, getString(R.string.sort_alpha), current))
    }

    private fun radio(order: SortOrder, label: String, current: SortOrder) =
        GuidedAction.Builder(requireContext())
            .id(order.ordinal.toLong())
            .title(label)
            .checkSetId(RADIO_SET)
            .checked(order == current)
            .build()

    override fun onGuidedActionClicked(action: GuidedAction) {
        val order = SortOrder.entries.getOrNull(action.id.toInt()) ?: return
        if (order != store.sortOrder) {
            store.sortOrder = order
            resortAndRepublish(order)
        }
        finishGuidedStepSupportFragments()
    }

    /** Re-order cached items and republish both channels — no network needed. */
    private fun resortAndRepublish(order: SortOrder) {
        val ctx = requireContext().applicationContext
        val s = store
        Thread {
            for (kind in MediaKind.entries) {
                val sorted = s.loadItems(kind).sortedWith(order.comparator())
                s.saveItems(kind, sorted)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { TvChannelPublisher(ctx).publish(kind, s, sorted) }
                }
            }
        }.start()
    }

    private companion object {
        const val RADIO_SET = 1
    }
}

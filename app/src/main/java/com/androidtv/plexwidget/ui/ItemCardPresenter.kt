package com.androidtv.plexwidget.ui

import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.androidtv.plexwidget.R
import com.androidtv.plexwidget.data.PlexStore
import com.androidtv.plexwidget.model.PlexItem

/** Renders a movie/show as a poster card with its cached poster (or a placeholder). */
class ItemCardPresenter(private val store: PlexStore) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val media = item as PlexItem
        val card = viewHolder.view as ImageCardView
        card.titleText = media.title
        card.contentText = media.year?.toString() ?: ""

        val file = store.posterFile(media.ratingKey)
        val bmp = if (file.exists() && file.length() > 0) {
            runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        } else null

        if (bmp != null) {
            card.mainImageView.setImageBitmap(bmp)
        } else {
            card.mainImageView.setImageDrawable(
                ContextCompat.getDrawable(card.context, R.drawable.default_box_art),
            )
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ImageCardView).mainImage = null
    }

    private companion object {
        const val CARD_WIDTH = 213
        const val CARD_HEIGHT = 320
    }
}

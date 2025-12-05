package nl.giejay.android.tv.immich.card

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.leanback.widget.BaseCardView
import androidx.leanback.widget.ImageCardView
import com.bumptech.glide.Glide
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.shared.presenter.AbstractPresenter
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.GRID_COLUMN_COUNT
import timber.log.Timber

open class CardPresenter(context: Context, style: Int = R.style.DefaultCardTheme) :
    AbstractPresenter<ImageCardView, ICard>(ContextThemeWrapper(context, style)) {

    companion object {
        private const val VIDEO_ICON_TAG = "VIDEO_OVERLAY_ICON"
    }

    override fun onCreateView(): ImageCardView {
        val cardView = ImageCardView(context)

        // 1. LEER NÚMERO DE COLUMNAS PARA CALCULAR TAMAÑO
        val cols = try {
        PreferenceManager.get(GRID_COLUMN_COUNT)
        } catch (e: Exception) { 4 }

        // 2. ELEGIR DIMENSIONES SEGÚN COLUMNAS
        // Usamos las dimensiones que definimos en dimens.xml
        val (widthRes, heightRes) = when (cols) {
            3 -> Pair(R.dimen.card_width_3_cols, R.dimen.card_height_3_cols)
            5 -> Pair(R.dimen.card_width_5_cols, R.dimen.card_height_5_cols)
            else -> Pair(R.dimen.card_width_4_cols, R.dimen.card_height_4_cols)
        }

        val width = context.resources.getDimensionPixelSize(widthRes)
        val height = context.resources.getDimensionPixelSize(heightRes)
        cardView.setMainImageDimensions(width, height)

        // 3. ESTILO (REDONDEO)
        val radius = context.resources.getDimension(R.dimen.card_corner_radius)

        cardView.mainImageView!!.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        cardView.mainImageView!!.clipToOutline = true

        // Fondo transparente para que el redondeo se vea limpio sobre negro
        cardView.setBackgroundColor(Color.TRANSPARENT)

        return cardView
    }

    override fun onBindViewHolder(card: ICard, cardView: ImageCardView) {
        cardView.tag = card
        
        // 2. APLICAR ESPACIADO (GAP)
        val spacing = cardView.resources.getDimensionPixelSize(R.dimen.card_spacing)
        if (cardView.layoutParams is ViewGroup.MarginLayoutParams) {
            val params = cardView.layoutParams as ViewGroup.MarginLayoutParams
            params.setMargins(spacing, spacing, spacing, spacing)
            cardView.layoutParams = params
        }

        // --- LÓGICA EXISTENTE ---
        val showNames = try {
            PreferenceManager.get(nl.giejay.android.tv.immich.shared.prefs.SHOW_FILE_NAMES_GRID)
        } catch (e: Exception) {
            true
        }

        if (showNames) {
            cardView.cardType = BaseCardView.CARD_TYPE_INFO_UNDER
            cardView.titleText = card.title
            cardView.contentText = card.description
        } else {
            cardView.cardType = BaseCardView.CARD_TYPE_MAIN_ONLY
            cardView.titleText = null
            cardView.contentText = null
        }
        
        // Icono de Video
        if (card is Card && card.isVideo) {
            val playIcon = context.getDrawable(android.R.drawable.ic_media_play)?.mutate()
            playIcon?.setTint(Color.WHITE)
            
            if (playIcon != null) {
                val layerDrawable = LayerDrawable(arrayOf(playIcon))
                layerDrawable.setLayerGravity(0, Gravity.BOTTOM or Gravity.END)
                layerDrawable.setLayerInset(0, 0, 20, 20, 20) 
                cardView.mainImageView!!.foreground = layerDrawable
            }
        } else {
            cardView.mainImageView!!.foreground = null
        }
        
        loadImage(card, cardView)
        setSelected(cardView, card.selected)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        super.onUnbindViewHolder(viewHolder)
        if(context is Activity && context.isFinishing){
            return
        }
        try {
            val imgView = (viewHolder.view as ImageCardView).mainImageView!!
            Glide.with(context).clear(imgView)
            imgView.foreground = null
        } catch (e: IllegalArgumentException){
            Timber.e(e)
        }
    }

    open fun loadImage(card: ICard, cardView: ImageCardView) {
        val url = card.thumbnailUrl
        val imageView = cardView.mainImageView!!
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        if (!url.isNullOrBlank()) {
            if(url.startsWith("http")){
                Glide.with(context)
                    .asBitmap()
                    .load(url)
                    .into(imageView)
            } else {
                val resourceId = context.resources.getIdentifier(url, "drawable", context.packageName)
                if (resourceId != 0) imageView.setImageResource(resourceId)
            }
        } else {
            imageView.setImageDrawable(null)
        }
    }

    private fun setSelected(imageCardView: ImageCardView, selected: Boolean) {
        if(selected){
            // Al seleccionar, usamos el borde
            imageCardView.mainImageView!!.background = context.getDrawable(R.drawable.border)
        } else {
            imageCardView.mainImageView!!.background = null
        }
    }
}

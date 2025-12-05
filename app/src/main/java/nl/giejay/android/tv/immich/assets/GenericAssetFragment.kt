package nl.giejay.android.tv.immich.assets

import android.os.Bundle
import androidx.fragment.app.setFragmentResultListener
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.navigation.fragment.findNavController
import nl.giejay.android.tv.immich.album.AlbumDetailsFragmentDirections
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.home.HomeFragmentDirections
import nl.giejay.android.tv.immich.shared.fragment.VerticalCardGridFragment
import nl.giejay.android.tv.immich.shared.prefs.ALL_ASSETS_SORTING
import nl.giejay.android.tv.immich.shared.prefs.ContentType
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.EnumByTitlePref
import nl.giejay.android.tv.immich.shared.prefs.FILTER_CONTENT_TYPE
import nl.giejay.android.tv.immich.shared.prefs.GRID_COLUMN_COUNT
import nl.giejay.android.tv.immich.shared.prefs.MetaDataScreen
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SCREENSAVER_ANIMATE_ASSET_SLIDE
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ANIMATION_SPEED
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_GLIDE_TRANSFORMATION
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_INTERVAL
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_HEIGHT
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MAX_CUT_OFF_WIDTH
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_MERGE_PORTRAIT_PHOTOS
import nl.giejay.android.tv.immich.shared.prefs.SLIDER_ONLY_USE_THUMBNAILS
import nl.giejay.android.tv.immich.shared.util.toCard
import nl.giejay.android.tv.immich.shared.util.toSliderItems
import nl.giejay.mediaslider.util.LoadMore
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import timber.log.Timber

abstract class GenericAssetFragment : VerticalCardGridFragment<Asset>() {
    protected lateinit var currentFilter: ContentType
    protected lateinit var currentSort: PhotosOrder

    override fun onCreate(savedInstanceState: Bundle?) {
        val sortingKey = getSortingKey()
        val filterKey = getFilterKey()
        currentSort = PreferenceManager.get(sortingKey)
        currentFilter = PreferenceManager.get(filterKey)
        
        super.onCreate(savedInstanceState)
        
        val cols = try {
            PreferenceManager.get(GRID_COLUMN_COUNT)
        } catch (e: Exception) { 4 }
        
        (gridPresenter as? VerticalGridPresenter)?.numberOfColumns = cols
        
        PreferenceManager.subscribeMultiple(listOf(sortingKey, filterKey, GRID_COLUMN_COUNT)) { state ->
            if(state[sortingKey.key()] != currentSort || state[filterKey.key()] != currentFilter){
                clearState()
                currentSort = state[sortingKey.key()] as PhotosOrder
                currentFilter = state[filterKey.key()] as ContentType
                fetchInitialItems()
            }
        }

        // 4. FIX COMPLETO: ACTUALIZAR VISTA Y MEMORIA (CORREGIDO Y COMPILABLE)
        setFragmentResultListener("asset_favorite_changed") { _, bundle ->
            val assetId = bundle.getString("assetId")
            val isFavorite = bundle.getBoolean("isFavorite")
            
            if (assetId != null) {
                // A) ACTUALIZAR LA MEMORIA
                // Usamos una variable local para evitar errores de concurrencia de Kotlin (Smart cast error)
                val localAssets = assets 
                val assetIndex = localAssets.indexOfFirst { it.id == assetId }
                
                if (assetIndex != -1) {
                    val updatedAsset = localAssets[assetIndex].copy(isFavorite = isFavorite)
                    
                    // Ahora comprobamos la variable local, que es segura
                    if (localAssets is MutableList) {
                        localAssets[assetIndex] = updatedAsset
                    }
                }

                // B) ACTUALIZAR LA VISTA
                if (adapter != null && adapter.size() > 0) {
                    val count = adapter.size()
                    for (i in 0 until count) {
                        val item = adapter.get(i)
                        if (item is Card && item.id == assetId) {
                            item.isFavorite = isFavorite
                            (adapter as? ArrayObjectAdapter)?.replace(i, item)
                            Timber.d("GRID: Favorito actualizado para $assetId")
                            break 
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (adapter != null && adapter.size() > 0) {
            (adapter as? ArrayObjectAdapter)?.notifyArrayItemRangeChanged(0, adapter.size())
        }
    }

    override fun filterItems(items: List<Asset>): List<Asset> {
        return items.filter { currentFilter ==  ContentType.ALL || it.type.lowercase() == currentFilter.toString().lowercase() }
    }

    open fun getSortingKey(): EnumByTitlePref<PhotosOrder>{
        return ALL_ASSETS_SORTING
    }

    open fun getFilterKey(): EnumByTitlePref<ContentType>{
        return FILTER_CONTENT_TYPE
    }

    override fun sortItems(items: List<Asset>): List<Asset> {
        return items.sortedWith(currentSort.sort)
    }

    override fun onItemSelected(card: Card, indexOf: Int) {
        // no use case yet
    }

    open fun showMediaCount(): Boolean {
        return false
    }

    override fun openPopUpMenu() {
        findNavController().navigate(
            HomeFragmentDirections.actionGlobalToSettingsDialog("generic_asset_settings")
        )
    }

    override fun onItemClicked(card: Card) {
        val toSliderItems = assets.toSliderItems(keepOrder = true, mergePortrait = PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
        val loadMore: LoadMore = suspend {
            val moreAssets = loadMoreAssets()
            // also load the data in the overview
            setDataOnMain(moreAssets)
            moreAssets.toSliderItems(true, PreferenceManager.get(SLIDER_MERGE_PORTRAIT_PHOTOS))
        }

        findNavController().navigate(
            AlbumDetailsFragmentDirections.actionToPhotoSlider(
                MediaSliderConfiguration(
                    toSliderItems.indexOfFirst { it.ids().contains(card.id) },
                    PreferenceManager.get(SLIDER_INTERVAL),
                    PreferenceManager.get(SLIDER_ONLY_USE_THUMBNAILS),
                    isVideoSoundEnable = true,
                    toSliderItems,
                    loadMore,
                    { item -> manualUpdatePosition(this.assets.indexOfFirst { item.ids().contains(it.id) }) },
                    animationSpeedMillis = PreferenceManager.get(SLIDER_ANIMATION_SPEED),
                    maxCutOffHeight = PreferenceManager.get(SLIDER_MAX_CUT_OFF_HEIGHT),
                    maxCutOffWidth = PreferenceManager.get(SLIDER_MAX_CUT_OFF_WIDTH),
                    transformation = PreferenceManager.get(SLIDER_GLIDE_TRANSFORMATION),
                    debugEnabled = PreferenceManager.get(DEBUG_MODE),
                    enableSlideAnimation = PreferenceManager.get(SCREENSAVER_ANIMATE_ASSET_SLIDE),
                    gradiantOverlay = false,
                    metaDataConfig = PreferenceManager.getAllMetaData(MetaDataScreen.VIEWER)
                )
            )
        )
    }

    override fun getBackgroundPicture(it: Asset): String? {
        val showBackground = PreferenceManager.get(nl.giejay.android.tv.immich.shared.prefs.LOAD_BACKGROUND_IMAGE)
        if (!showBackground) return null
        return ApiUtil.getFileUrl(it.id, "IMAGE")
    }

    override fun createCard(a: Asset): Card {
        return a.toCard()
    }
}

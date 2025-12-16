package nl.giejay.android.tv.immich.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.Bucket
import nl.giejay.android.tv.immich.shared.prefs.PhotosOrder
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.shared.prefs.SHOW_ONLY_VIDEOS
import timber.log.Timber

class TimelineViewModel : ViewModel() {

    private val _buckets = MutableStateFlow<List<Bucket>>(emptyList())
    val buckets: StateFlow<List<Bucket>> = _buckets

    private val _selectedBucketId = MutableStateFlow<String?>(null)
    val selectedBucketId: StateFlow<String?> = _selectedBucketId

    /**
     * Assets originales sin filtrar, tal como vienen del servidor
     */
    private var rawAssets: List<Asset> = emptyList()

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingAssets = MutableStateFlow(false)
    val isLoadingAssets: StateFlow<Boolean> = _isLoadingAssets

    private var bucketsLoaded = false

    fun loadBuckets(apiClient: ApiClient, forceReload: Boolean = false) {
        if (bucketsLoaded && _buckets.value.isNotEmpty() && !forceReload) {
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.listBuckets("", PhotosOrder.NEWEST_OLDEST)
                }

                result.fold(
                    { error ->
                        Timber.e("Error loading buckets: $error")
                    },
                    { bucketList ->
                        _buckets.value = bucketList
                        bucketsLoaded = true

                        if (_selectedBucketId.value == null && bucketList.isNotEmpty()) {
                            selectBucket(bucketList.first().timeBucket, apiClient)
                        } else {
                            _selectedBucketId.value?.let {
                                loadAssetsForBucket(it, apiClient)
                            }
                        }
                    }
                )

            } catch (e: Exception) {
                Timber.e(e, "Exception loading buckets")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectBucket(bucketId: String, apiClient: ApiClient) {
        if (_selectedBucketId.value == bucketId && rawAssets.isNotEmpty()) {
            return
        }

        _selectedBucketId.value = bucketId
        loadAssetsForBucket(bucketId, apiClient)
    }

    private fun loadAssetsForBucket(bucketId: String, apiClient: ApiClient) {
        viewModelScope.launch {
            _isLoadingAssets.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.getAssetsForBucket(
                        "",
                        bucketId,
                        PhotosOrder.NEWEST_OLDEST
                    )
                }

                result.fold(
                    { error ->
                        Timber.e("Error loading assets: $error")
                    },
                    { assetList ->
                        rawAssets = assetList
                        applyFilterAndEmit()
                    }
                )

            } catch (e: Exception) {
                Timber.e(e, "Exception loading assets")
            } finally {
                _isLoadingAssets.value = false
            }
        }
    }

    /**
     * Aplica el filtro "Show only videos" sobre los assets actuales
     */
    private fun applyFilterAndEmit() {
        val showOnlyVideos = PreferenceManager.get(SHOW_ONLY_VIDEOS)

        _assets.value = if (showOnlyVideos) {
            rawAssets.filter { it.type.equals("VIDEO", ignoreCase = true) }
        } else {
            rawAssets
        }
    }

    /**
     * Útil si en el futuro quieres forzar el re-filtrado
     * (por ejemplo al volver de Settings)
     */
    fun refreshFilter() {
        applyFilterAndEmit()
    }

    fun forceReload(apiClient: ApiClient) {
        bucketsLoaded = false
        rawAssets = emptyList()
        _buckets.value = emptyList()
        _assets.value = emptyList()
        loadBuckets(apiClient, forceReload = true)
    }

    fun getSelectedBucket(): Bucket? {
        val selectedId = _selectedBucketId.value ?: return null
        return _buckets.value.find { it.timeBucket == selectedId }
    }
}


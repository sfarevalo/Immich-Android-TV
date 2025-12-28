package nl.giejay.android.tv.immich.api.model

data class UpdateAssetRequest(
    val isFavorite: Boolean
)

data class DeleteAssetsRequest(
    val force: Boolean = false,
    val ids: List<String>
)

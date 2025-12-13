package nl.giejay.android.tv.immich.shared.cache

/**
 * Cache global de estados de favoritos
 * Persiste cambios entre navegaciones de fragmentos
 */
object FavoriteCache {
    /**
     * Mapa de overrides manuales: assetId -> isFavorite
     * Se usa para sincronizar cambios inmediatos entre pantallas
     */
    val overrides = mutableMapOf<String, Boolean>()
    
    /**
     * Limpia el cache (útil al cerrar sesión o cambiar de usuario)
     */
    fun clear() {
        overrides.clear()
    }
}

package nl.giejay.android.tv.immich.slider

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.HOST_NAME
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.view.MediaSliderFragment
import nl.giejay.mediaslider.view.MediaSliderView
import timber.log.Timber

class ImmichMediaSlider : MediaSliderFragment() {

    private lateinit var apiClient: ApiClient
    private lateinit var config: MediaSliderConfiguration

    @SuppressLint("UnsafeOptInUsageError")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- FIX: CONEXIÓN CORRECTA DEL CABLE ---
        // Conectamos el evento de la Vista con tu lógica existente de API
        this.onAssetFavoriteChanged = { itemHolder, isFavorite ->
            // itemHolder es el SliderItemViewHolder que viene de la vista
            // Necesitamos pasarle a performToggleFavorite los datos exactos que pide
            
            if (view is MediaSliderView) {
                // Reutilizamos la lógica que ya tenías escrita abajo
                // Nota: isFavorite ya viene invertido desde la vista, pero performToggleFavorite
                // lo invierte de nuevo basándose en el objeto, así que primero actualizamos el objeto
                // para que la lógica de performToggleFavorite no lo invierta dos veces erróneamente.
                
                // Opción segura: Llamar a performToggleFavorite forzando el estado o dejando que él lo gestione.
                // Como performToggleFavorite hace "val newStatus = !sliderItem.isFavorite", 
                // y la vista YA cambió el visual, vamos a dejar que perform haga la llamada a API y confirme.
                
                // Simplemente pasamos los datos:
                val item = itemHolder.mainItem
                val id = itemHolder.ids().firstOrNull()
                
                if (id != null) {
                    // IMPORTANTE: En la vista ya cambiamos el booleano visualmente para que fuera rápido.
                    // Pero performToggleFavorite usa el valor actual del objeto para invertirlo (!isFavorite).
                    // Para que no se líe, le pasamos el objeto tal cual.
                    performToggleFavorite(id, item, view)
                }
            }
        }
        // ----------------------------------------

        Timber.i("Loading ${this.javaClass.simpleName}")

        val bundle = ImmichMediaSliderArgs.fromBundle(requireArguments())
        this.config = bundle.config

        if (config.items.isEmpty()) {
            Toast.makeText(requireContext(), "No items to play", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        setupApiClient()

        setDefaultExoFactory(
            DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("x-api-key" to PreferenceManager.get(API_KEY)))
        )

        loadMediaSliderView(config)

        setupKeyInterceptor(view)
    }

    private fun setupApiClient() {
        apiClient = ApiClient.getClient(
            ApiClientConfig(
                PreferenceManager.get(HOST_NAME),
                PreferenceManager.get(API_KEY),
                PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                PreferenceManager.get(DEBUG_MODE)
            )
        )
    }

    private fun setupKeyInterceptor(view: View) {
        if (view is MediaSliderView) {
            view.onLongPressCenterListener = {
                toggleFavoriteCurrentItem(view)
            }
        }
    }

    private fun toggleFavoriteCurrentItem(view: MediaSliderView) {
        val currentPosition = view.getCurrentIndex()
        
        if (currentPosition in config.items.indices) {
            val currentItemHolder = config.items[currentPosition]
            val assetId = currentItemHolder.ids().firstOrNull()
            
            if (assetId != null) {
                val item = currentItemHolder.mainItem 
                performToggleFavorite(assetId, item, view)
            }
        }
    }

    private fun performToggleFavorite(assetId: String, sliderItem: nl.giejay.mediaslider.model.SliderItem, view: MediaSliderView) {
        // Calculamos el nuevo estado deseado (invertir el actual)
        // OJO: Si vienes del click de la estrella, visualmente ya cambió, pero el objeto sliderItem
        // aún puede tener el valor viejo o nuevo dependiendo de si lo actualizaste en la View.
        // Asumimos que aquí manda la API.
        val newStatus = !sliderItem.isFavorite

        lifecycleScope.launch(Dispatchers.IO) {
            apiClient.toggleFavorite(assetId, newStatus).fold(
                { error -> 
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_SHORT).show()
                        // Opcional: Si falló, revertir el icono en la vista
                        // view.updateFavoriteIcon() 
                    }
                },
                { _ ->
                    // ÉXITO: Actualizamos todo
                    withContext(Dispatchers.Main) {
                        // 1. Actualizamos modelo local (fuente de verdad en memoria)
                        sliderItem.isFavorite = newStatus
                        
                        // 2. Refrescamos el icono visualmente (por si acaso no coincidía)
                        view.updateFavoriteIcon()
                        
                        // 3. Avisamos al usuario
                        val msg = if (newStatus) "❤️ Favorito" else "💔 No Favorito"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

                        // 4. AVISAMOS AL GRID (LA CLAVE DE TODO)
                        // Esto envía el dato a la pantalla anterior
                        setFragmentResult("asset_favorite_changed", bundleOf(
                            "assetId" to assetId,
                            "isFavorite" to newStatus
                        ))
                    }
                }
            )
        }
    }
}

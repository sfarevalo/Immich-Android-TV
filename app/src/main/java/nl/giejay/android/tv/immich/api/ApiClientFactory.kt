package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.util.UnsafeOkHttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

object ApiClientFactory {

    fun getClient(disableSsl: Boolean, apiKey: String, debugMode: Boolean): OkHttpClient {
        val apiKeyInterceptor = interceptor(apiKey)

        // 1. Crear el builder
        val builder = if (disableSsl)
            UnsafeOkHttpClient.unsafeOkHttpClient()
        else OkHttpClient.Builder()

        // 2. Añadir la API Key siempre
        builder.addInterceptor(apiKeyInterceptor)

        // 3. AÑADIR INTERCEPTOR DE LOGGING
        builder.addInterceptor(Interceptor { chain ->
            val request = chain.request()
            Timber.d("HTTP: ${request.method()} ${request}")
            val response = chain.proceed(request)
            Timber.d("HTTP: Response ${response.code()} for ${request.method()} ${request}")
            response
        })

        return builder.build()
    }

    private fun interceptor(apiKey: String): Interceptor = Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
            .addHeader("x-api-key", apiKey.trim())
            .build()
        chain.proceed(newRequest)
    }
}

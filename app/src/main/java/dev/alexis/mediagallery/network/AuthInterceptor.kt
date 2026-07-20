package dev.alexis.mediagallery.network

import dev.alexis.mediagallery.data.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adiciona "Authorization: Bearer <token>" em toda requisição feita
 * através deste cliente Retrofit -- equivalente ao authFetch() do site.
 *
 * Login e registro simplesmente não têm token ainda nesse momento,
 * então o header não é adicionado (o backend não deveria exigi-lo
 * nessas rotas mesmo).
 *
 * Importante: as thumbnails (/thumbs/...) são carregadas pelo Coil
 * SEM passar por este cliente autenticado, porque a rota é pública --
 * ver RetrofitClient.kt.
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getTokenSync()

        val request = if (token != null) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}

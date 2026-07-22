package dev.alexis.mediagallery.network

import dev.alexis.mediagallery.data.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds "Authorization: Bearer <token>" to every request made through
 * this Retrofit client.
 *
 * Login and registration simply don't have a token yet at this point,
 * so the header isn't added (the backend shouldn't require it on these
 * routes anyway).
 *
 * Important: thumbnails (/thumbs/...) are loaded by Coil WITHOUT going
 * through this authenticated client, because the route is public --
 * see RetrofitClient.kt. The actual image filename used to fetch the
 * thumb is a UUID that's only available after an authenticated request,
 * so that's not a problem in the current scope of testing.
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

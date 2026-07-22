package dev.alexis.mediagallery

import android.content.Context
import dev.alexis.mediagallery.data.TokenManager
import dev.alexis.mediagallery.network.ApiService
import dev.alexis.mediagallery.network.RetrofitClient

/**
 * Manual dependency container. A single instance created on
 * Application and injected "in hand" where needed -- without Hilt/Koin
 * for now, to keep the project simple at this stage.
 */
class AppContainer(context: Context) {
    val tokenManager: TokenManager = TokenManager(context.applicationContext)
    val apiService: ApiService by lazy { RetrofitClient.getInstance(tokenManager) }
}

package dev.alexis.wirelessgallery

import android.content.Context
import dev.alexis.wirelessgallery.data.TokenManager
import dev.alexis.wirelessgallery.network.ApiService
import dev.alexis.wirelessgallery.network.RetrofitClient

/**
 * Manual dependency container. A single instance created on
 * Application and injected "in hand" where needed -- without Hilt/Koin
 * for now, to keep the project simple at this stage.
 */
class AppContainer(context: Context) {
    val tokenManager: TokenManager = TokenManager(context.applicationContext)
    val apiService: ApiService by lazy { RetrofitClient.getInstance(tokenManager) }
}

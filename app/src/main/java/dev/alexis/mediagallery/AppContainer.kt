package dev.alexis.mediagallery

import android.content.Context
import dev.alexis.mediagallery.data.TokenManager
import dev.alexis.mediagallery.network.ApiService
import dev.alexis.mediagallery.network.RetrofitClient

/**
 * Container manual de dependências. Uma instância única criada na
 * Application e injetada "na mão" onde for preciso -- sem Hilt/Koin
 * por enquanto, pra manter o projeto simples nesta fase.
 */
class AppContainer(context: Context) {
    val tokenManager: TokenManager = TokenManager(context.applicationContext)
    val apiService: ApiService by lazy { RetrofitClient.getInstance(tokenManager) }
}

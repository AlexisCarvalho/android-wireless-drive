package dev.alexis.wirelessdrive

import android.content.Context
import dev.alexis.wirelessdrive.data.ServerConfigManager
import dev.alexis.wirelessdrive.data.SessionManager
import dev.alexis.wirelessdrive.data.TokenManager
import dev.alexis.wirelessdrive.network.ApiService
import dev.alexis.wirelessdrive.network.RetrofitClient

/**
 * Manual dependency container. A single instance created on
 * Application and injected "in hand" where needed -- without Hilt/Koin
 * for now, to keep the project simple at this stage.
 */
class AppContainer(context: Context) {
    val tokenManager: TokenManager = TokenManager(context.applicationContext)
    val serverConfigManager: ServerConfigManager = ServerConfigManager(context.applicationContext)
    val apiService: ApiService by lazy { RetrofitClient.getInstance(tokenManager) }
    val sessionManager: SessionManager by lazy { SessionManager(tokenManager) }
}
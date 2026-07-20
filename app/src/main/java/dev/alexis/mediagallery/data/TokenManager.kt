package dev.alexis.mediagallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

/**
 * Guarda o token em disco (DataStore) e mantém uma cópia em memória
 * para leitura síncrona -- necessária porque o AuthInterceptor do OkHttp
 * roda em código síncrono, não suspend.
 *
 * A leitura inicial via runBlocking acontece uma única vez, na criação
 * da instância (normalmente dentro de uma Application ou de um container
 * de DI simples). Não é chamada a cada requisição.
 */
class TokenManager(private val context: Context) {

    private val tokenKey = stringPreferencesKey("auth_token")

    @Volatile
    private var cachedToken: String? = runBlocking {
        context.dataStore.data.first()[tokenKey]
    }

    suspend fun saveToken(token: String) {
        cachedToken = token
        context.dataStore.edit { prefs -> prefs[tokenKey] = token }
    }

    suspend fun clearToken() {
        cachedToken = null
        context.dataStore.edit { prefs -> prefs.remove(tokenKey) }
    }

    /** Usado pelo AuthInterceptor, sempre chamado numa thread de rede, nunca na main. */
    fun getTokenSync(): String? = cachedToken

    fun isLoggedIn(): Boolean = cachedToken != null
}

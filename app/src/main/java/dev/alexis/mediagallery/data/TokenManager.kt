package dev.alexis.mediagallery.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

/**
 * Stores the token on disk (DataStore) and keeps an in-memory copy
 * for synchronous reads -- necessary because OkHttp's AuthInterceptor
 * runs in synchronous code, not suspendable.
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

    /** Used by the AuthInterceptor, always called on a network thread, never on the main thread. */
    fun getTokenSync(): String? = cachedToken

    fun isLoggedIn(): Boolean = cachedToken != null
}

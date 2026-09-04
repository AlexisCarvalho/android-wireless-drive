package dev.alexis.wirelessdrive.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dev.alexis.wirelessdrive.network.ApiConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.serverDataStore by preferencesDataStore(name = "saved_servers")

data class SavedServer(
    val name: String,
    val url: String
)

class ServerConfigManager(private val context: Context) {
    private val gson = Gson()
    private val serversKey = stringPreferencesKey("saved_servers_json")
    private val activeUrlKey = stringPreferencesKey("active_server_url")

    init {
        val savedActiveUrl = runBlocking {
            context.serverDataStore.data.first()[activeUrlKey]
        }
        if (!savedActiveUrl.isNullOrBlank()) {
            ApiConfig.baseUrl = savedActiveUrl
        }
    }

    suspend fun saveServer(name: String, url: String) {
        val normalizedName = name.trim()
        val normalizedUrl = normalizeUrl(url)
        if (normalizedName.isBlank() || normalizedUrl.isBlank()) return

        val existing = loadServersInternal()
        val withoutSameName = existing.filterNot { it.name == normalizedName }
        val updated = listOf(SavedServer(normalizedName, normalizedUrl)) + withoutSameName

        context.serverDataStore.edit { prefs ->
            prefs[serversKey] = gson.toJson(updated.take(6))
        }
    }

    suspend fun loadServers(): List<SavedServer> = loadServersInternal()

    suspend fun removeServer(name: String) {
        val existing = loadServersInternal()
        val updated = existing.filterNot { it.name == name }
        context.serverDataStore.edit { prefs ->
            prefs[serversKey] = gson.toJson(updated)
        }
    }

    suspend fun selectServer(server: SavedServer) {
        ApiConfig.baseUrl = server.url
        context.serverDataStore.edit { prefs ->
            prefs[activeUrlKey] = server.url
        }
    }

    suspend fun getActiveServerName(): String? {
        val activeUrl = context.serverDataStore.data.first()[activeUrlKey] ?: return null
        return loadServersInternal().firstOrNull { it.url == activeUrl }?.name
    }

    private suspend fun loadServersInternal(): List<SavedServer> {
        val rawJson = context.serverDataStore.data.first()[serversKey] ?: return emptyList()
        return try {
            val array = gson.fromJson(rawJson, Array<SavedServer>::class.java)
            array?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
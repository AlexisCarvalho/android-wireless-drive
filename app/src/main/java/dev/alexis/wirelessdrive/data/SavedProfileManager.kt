package dev.alexis.wirelessdrive.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

private val Context.profileDataStore by preferencesDataStore(name = "saved_profiles")

data class SavedProfile(
    val code: String,
    val password: String
)

class SavedProfileManager(private val context: Context) {
    private val gson = Gson()
    private val profilesKey = stringPreferencesKey("saved_profiles_json")

    suspend fun saveProfile(code: String, password: String) {
        val normalizedCode = code.trim()
        val normalizedPassword = password.trim()
        if (normalizedCode.isBlank() || normalizedPassword.isBlank()) return

        val existing = loadProfilesInternal()
        val withoutSameCode = existing.filterNot { it.code == normalizedCode }
        val updated = listOf(SavedProfile(normalizedCode, normalizedPassword)) + withoutSameCode

        context.profileDataStore.edit { prefs ->
            prefs[profilesKey] = gson.toJson(updated.take(4))
        }
    }

    suspend fun loadProfiles(): List<SavedProfile> = loadProfilesInternal()

    suspend fun clearProfiles() {
        context.profileDataStore.edit { prefs -> prefs.remove(profilesKey) }
    }

    suspend fun removeProfile(code: String) {
        val normalizedCode = code.trim()
        if (normalizedCode.isBlank()) return
        val existing = loadProfilesInternal()
        val updated = existing.filterNot { it.code == normalizedCode }
        context.profileDataStore.edit { prefs ->
            prefs[profilesKey] = gson.toJson(updated)
        }
    }

    private suspend fun loadProfilesInternal(): List<SavedProfile> {
        val rawJson = context.profileDataStore.data.first()[profilesKey] ?: return emptyList()
        return try {
            val array = gson.fromJson(rawJson, Array<SavedProfile>::class.java)
            array?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

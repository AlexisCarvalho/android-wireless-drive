package dev.alexis.wirelessdrive.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import dev.alexis.wirelessdrive.data.TokenManager

@UnstableApi
class AuthenticatingHttpDataSourceFactory(
    private val tokenManager: TokenManager
) : HttpDataSource.Factory {

    private val delegate = DefaultHttpDataSource.Factory()

    private var extraProperties: Map<String, String> = emptyMap()

    override fun createDataSource(): HttpDataSource {
        val token = tokenManager.getTokenSync()

        val properties = extraProperties + (
                token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
                )

        delegate.setDefaultRequestProperties(properties)
        return delegate.createDataSource()
    }

    override fun setDefaultRequestProperties(
        defaultRequestProperties: MutableMap<String, String>
    ): HttpDataSource.Factory {
        extraProperties = defaultRequestProperties.toMap()
        return this
    }
}
package dev.alexis.wirelessdrive.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import dev.alexis.wirelessdrive.network.ApiConfig
import dev.alexis.wirelessdrive.network.ApiService
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import java.io.IOException
import androidx.core.net.toUri

const val PLACEHOLDER_MEDIA_SCHEME = "wirelessdrive-media"

fun placeholderUriFor(mediaId: Int): Uri =
    "$PLACEHOLDER_MEDIA_SCHEME://$mediaId".toUri()

@UnstableApi
class LazyResolvingDataSource(
    private val apiService: ApiService,
    private val delegate: HttpDataSource
) : DataSource by delegate {

    override fun open(dataSpec: DataSpec): Long {
        val resolvedSpec = if (dataSpec.uri.scheme == PLACEHOLDER_MEDIA_SCHEME) {
            val mediaId = dataSpec.uri.host?.toIntOrNull()
                ?: dataSpec.uri.schemeSpecificPart?.trimStart(':', '/')?.toIntOrNull()
                ?: throw IOException("ID de mídia inválido: ${dataSpec.uri}")

            val resolvedUrl = runBlocking {
                try {
                    val response = apiService.getMediaStreamUrl(mediaId)

                    if (!response.isSuccessful) {
                        throw HttpException(response)
                    }

                    val endpoint = response.body()?.url?.takeIf { it.isNotBlank() }
                        ?: throw IOException("URL de stream vazia para a mídia $mediaId")

                    "${ApiConfig.baseUrl}$endpoint"
                } catch (e: HttpException) {
                    throw IOException("Falha ao resolver URL de stream para a mídia $mediaId", e)
                }
            }

            dataSpec.buildUpon().setUri(resolvedUrl.toUri()).build()
        } else {
            dataSpec
        }

        return delegate.open(resolvedSpec)
    }
}

@UnstableApi
class LazyResolvingDataSourceFactory(
    private val apiService: ApiService,
    private val authenticatingFactory: HttpDataSource.Factory
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        LazyResolvingDataSource(apiService, authenticatingFactory.createDataSource())
}
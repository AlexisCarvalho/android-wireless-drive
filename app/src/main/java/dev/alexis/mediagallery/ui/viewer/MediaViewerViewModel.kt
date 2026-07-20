package dev.alexis.mediagallery.ui.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alexis.mediagallery.data.Media
import dev.alexis.mediagallery.data.TokenManager
import dev.alexis.mediagallery.network.ApiConfig
import dev.alexis.mediagallery.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import androidx.core.net.toUri

// ByteArray num data class deixa o compilador avisar sobre equals()/hashCode()
// padrão (comparam por referência, não por conteúdo) -- inofensivo aqui,
// já que não comparamos instâncias de UiState entre si.
data class MediaViewerUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val media: Media? = null,
    val localVideoUri: Uri? = null,
    val localAudioUri: Uri? = null,
    val imageBytes: ByteArray? = null,
    val authToken: String? = null
)

class MediaViewerViewModel(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val cacheDir: File,
    private val mediaId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaViewerUiState())
    val uiState: StateFlow<MediaViewerUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null)
            }

            try {
                // Busca os detalhes da mídia
                val detailResponse = apiService.getMediaDetail(mediaId)
                val media = detailResponse.body()

                if (!detailResponse.isSuccessful || media == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Não foi possível carregar essa mídia"
                        )
                    }
                    return@launch
                }

                // Token JWT para o ExoPlayer
                val token = tokenManager.getTokenSync()

                when (media.type) {
                    "video" -> {
                        val streamUri = resolvePlaybackUri(media.id)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                media = media,
                                localVideoUri = streamUri,
                                authToken = token
                            )
                        }
                    }

                    "audio" -> {
                        val streamUri = resolvePlaybackUri(media.id)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                media = media,
                                localAudioUri = streamUri,
                                authToken = token
                            )
                        }
                    }

                    else -> {
                        // Para imagens ainda baixamos o conteúdo em memória
                        val fileResponse = apiService.getMediaFile(mediaId)
                        val body = fileResponse.body()

                        if (!fileResponse.isSuccessful || body == null) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Não foi possível baixar o arquivo"
                                )
                            }
                            return@launch
                        }

                        val bytes = withContext(Dispatchers.IO) {
                            body.bytes()
                        }

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                media = media,
                                imageBytes = bytes
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Erro ao carregar: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun resolvePlaybackUri(mediaId: Int): Uri {
        val streamResponse = runCatching {
            apiService.getMediaStreamUrl(mediaId)
        }.getOrNull()

        val endpoint = streamResponse
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?.url
            ?.takeIf { it.isNotBlank() }
            ?: "/api/media/$mediaId/file"

        return "${ApiConfig.BASE_URL}$endpoint".toUri()
    }

    private fun writeToCacheFile(body: ResponseBody, media: Media): File {
        val extension = media.filename
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?: "mp4"
        val file = File(cacheDir, "media_${media.id}.$extension")
        body.byteStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}

package dev.alexis.mediagallery.ui.viewer

import android.net.Uri
import androidx.core.net.toUri
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
import java.io.File

// ByteArray in a data class causes the compiler to warn about the default
// equals()/hashCode() (which compare by reference, not by content) -- harmless
// here, since we don't compare UiState instances with each other.
data class MediaViewerUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val media: Media? = null,
    val localVideoUri: Uri? = null,
    val localAudioUri: Uri? = null,
    val imageBytes: ByteArray? = null,
    val authToken: String? = null,
    val canGoToPrevious: Boolean = false,
    val canGoToNext: Boolean = false
)

class MediaViewerViewModel(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val cacheDir: File,
    private val mediaId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaViewerUiState())
    val uiState: StateFlow<MediaViewerUiState> = _uiState.asStateFlow()

    private var currentMediaId = mediaId
    private var availableMediaIds: List<Int> = emptyList()
    private var currentMediaIndex = -1
    private var currentMediaType = ""

    init {
        loadMedia()
    }

    fun loadMedia(targetMediaId: Int? = null) {
        viewModelScope.launch {
            val requestedMediaId = targetMediaId ?: currentMediaId
            currentMediaId = requestedMediaId

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    canGoToPrevious = false,
                    canGoToNext = false
                )
            }

            try {
                val detailResponse = apiService.getMediaDetail(requestedMediaId)
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

                val mediasResponse = apiService.getMedias()
                val medias = mediasResponse.body()?.medias.orEmpty()

                when (media.type) {
                    "video" -> {
                        currentMediaType = "video"
                    }

                    "audio" -> {
                        currentMediaType = "audio"
                    }
                }

                availableMediaIds = if (currentMediaType !== "") {
                    medias.filter { media -> media.type == currentMediaType }.map { it.id }

                } else {
                    medias.map { it.id }
                }
                currentMediaIndex = availableMediaIds.indexOf(requestedMediaId)

                val canGoToPrevious =
                    currentMediaIndex >= 0 && currentMediaIndex < availableMediaIds.lastIndex
                val canGoToNext = currentMediaIndex > 0

                val token = tokenManager.getTokenSync()

                when (media.type) {
                    "video" -> {
                        val streamUri = resolvePlaybackUri(media.id)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                media = media,
                                localVideoUri = streamUri,
                                authToken = token,
                                canGoToPrevious = canGoToPrevious,
                                canGoToNext = canGoToNext
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
                                authToken = token,
                                canGoToPrevious = canGoToPrevious,
                                canGoToNext = canGoToNext
                            )
                        }
                    }

                    else -> {
                        // The image is downloaded entirely into memory.
                        val fileResponse = apiService.getMediaFile(requestedMediaId)
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
                                imageBytes = bytes,
                                canGoToPrevious = canGoToPrevious,
                                canGoToNext = canGoToNext
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

    fun goToPreviousMedia() {
        if (currentMediaIndex < 0) return
        val previousMediaId = availableMediaIds.getOrNull(currentMediaIndex + 1)
        previousMediaId?.let { loadMedia(it) }
    }

    fun goToNextMedia() {
        if (currentMediaIndex < 0) return
        val nextMediaId = availableMediaIds.getOrNull(currentMediaIndex - 1)
        nextMediaId?.let { loadMedia(it) }
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
}

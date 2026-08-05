package dev.alexis.wirelessdrive.ui.viewer

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alexis.wirelessdrive.data.Media
import dev.alexis.wirelessdrive.data.SessionManager
import dev.alexis.wirelessdrive.data.TokenManager
import dev.alexis.wirelessdrive.network.ApiConfig
import dev.alexis.wirelessdrive.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
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
    val canGoToNext: Boolean = false,
    // Needed because the resolved URI can
    // be textually identical to the previous one, so UI code that keys off the
    // URI alone wouldn't notice anything changed.
    val playbackRefreshVersion: Int = 0
)

class MediaViewerViewModel(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val sessionManager: SessionManager,
    private val cacheDir: File,
    private val mediaId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaViewerUiState())
    val uiState: StateFlow<MediaViewerUiState> = _uiState.asStateFlow()

    private var currentMediaId = mediaId
    private var availableMediaIds: List<Int> = emptyList()
    private var currentMediaIndex = -1
    private var currentMediaType = ""
    private var isRefreshingPlaybackAuth = false
    private var cachedMedias: List<Media>? = null

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
                    if (sessionManager.handleAuthFailure(detailResponse.code())) {
                        return@launch
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Não foi possível carregar essa mídia"
                        )
                    }
                    return@launch
                }

                val medias = getMediaListCached() ?: return@launch

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
                        val streamUri = try {
                            resolvePlaybackUri(media.id)
                        } catch (e: HttpException) {
                            if (sessionManager.handleAuthFailure(e.code())) {
                                return@launch
                            }
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Não foi possível carregar o vídeo"
                                )
                            }
                            return@launch
                        }

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
                        val streamUri = try {
                            resolvePlaybackUri(media.id)
                        } catch (e: HttpException) {
                            if (sessionManager.handleAuthFailure(e.code())) {
                                return@launch
                            }
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "Não foi possível carregar o áudio"
                                )
                            }
                            return@launch
                        }

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
                            if (sessionManager.handleAuthFailure(fileResponse.code())) {
                                return@launch
                            }

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

    /**
     * Re-resolves the stream URL (and current auth token) for the media that's
     * currently playing, without touching `media`, the position in the
     * playlist, or the loading/error state.
     *
     * Call this when ExoPlayer fails to open a new HTTP connection with a
     * 401/403 -- which happens both when the access token has simply expired
     * mid-playback, and when the user seeks into a byte range that hasn't been
     * downloaded yet and the token expired in the meantime.
     */
    fun refreshPlaybackAuth() {
        if (isRefreshingPlaybackAuth) return
        val media = _uiState.value.media ?: return
        if (media.type != "video" && media.type != "audio") return

        isRefreshingPlaybackAuth = true
        viewModelScope.launch {
            try {
                if (!tokenManager.isLoggedIn()) {
                    return@launch
                }

                val streamUri = resolvePlaybackUri(media.id)
                val token = tokenManager.getTokenSync()

                _uiState.update {
                    it.copy(
                        authToken = token,
                        localVideoUri = if (media.type == "video") streamUri else it.localVideoUri,
                        localAudioUri = if (media.type == "audio") streamUri else it.localAudioUri,
                        playbackRefreshVersion = it.playbackRefreshVersion + 1
                    )
                }
            } catch (e: HttpException) {
                sessionManager.handleAuthFailure(e.code())
            } catch (_: Exception) {
                // Transient/network failure: best-effort, the next
                // 401/403 from ExoPlayer will trigger another attempt.
            } finally {
                isRefreshingPlaybackAuth = false
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

    /**
     * Returns the full media list, fetching it from the network only the
     * first time it's needed and reusing it afterwards. Returns null if the
     * request failed due to an expired session (already handled by
     * [SessionManager.handleAuthFailure] -- the caller should just
     * `return@launch`). Any other failure falls back to an empty list.
     */
    private suspend fun getMediaListCached(): List<Media>? {
        cachedMedias?.let { return it }

        val mediasResponse = apiService.getMedias()
        if (!mediasResponse.isSuccessful) {
            if (sessionManager.handleAuthFailure(mediasResponse.code())) {
                return null
            }
            return emptyList()
        }

        val medias = mediasResponse.body()?.medias.orEmpty()
        cachedMedias = medias
        return medias
    }

    /**
     * Forces the next call to [loadMedia] to fetch a fresh media list instead
     * of reusing the cached one.
     */
    fun invalidateMediaList() {
        cachedMedias = null
    }

    private suspend fun resolvePlaybackUri(mediaId: Int): Uri {
        val streamResponse = apiService.getMediaStreamUrl(mediaId)

        if (!streamResponse.isSuccessful) {
            throw HttpException(streamResponse)
        }

        val endpoint = streamResponse.body()?.url?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("URL de stream vazia")

        return "${ApiConfig.BASE_URL}$endpoint".toUri()
    }
}
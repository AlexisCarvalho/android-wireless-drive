package dev.alexis.mediagallery.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alexis.mediagallery.data.Media
import dev.alexis.mediagallery.network.ApiService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.app.Application
import dev.alexis.mediagallery.data.TokenManager
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.provider.MediaStore
import dev.alexis.mediagallery.data.GenerateThumbnailResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

data class GalleryUiState(
    val medias: List<Media> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val downloadingMedia: Map<Int, Float> = emptyMap(),
    val isGeneratingThumbnails: Boolean = false,
    val generatingThumbnail: Map<Int, Boolean> = emptyMap(),
    val generatedThumbnails: Int = 0,
    val totalThumbnailsToGenerate: Int = 0
)

class GalleryViewModel(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    private suspend fun handleAuthFailure(responseCode: Int): Boolean {
        return if (responseCode == 401 || responseCode == 403) {
            tokenManager.clearToken()
            _sessionExpired.emit(Unit)
            true
        } else {
            false
        }
    }

    private suspend fun generateThumbnailInternal(
        id: Int
    ): Result<GenerateThumbnailResponse> {

        return runCatching {

            _uiState.update {
                it.copy(
                    generatingThumbnail = it.generatingThumbnail + (id to true)
                )
            }

            val response = apiService.generateThumbnail(id)

            if (!response.isSuccessful) {
                if (handleAuthFailure(response.code())) {
                    throw CancellationException()
                }

                throw Exception("Erro ao gerar thumbnail")
            }

            response.body() ?: throw Exception("Resposta inválida")

        }.also {
            _uiState.update {
                it.copy(
                    generatingThumbnail = it.generatingThumbnail - id
                )
            }
        }
    }

    fun generateThumbnail(id: Int) {
        viewModelScope.launch {

            val result = generateThumbnailInternal(id)

            result.onSuccess { response ->

                _uiState.update { state ->

                    val index = state.medias.indexOfFirst { it.id == id }

                    if (index == -1) return@update state

                    val newList = state.medias.toMutableList()

                    newList[index] = newList[index].copy(
                        thumbnail = response.thumbnail
                    )

                    state.copy(medias = newList)
                }

            }.onFailure { e ->

                _uiState.update {
                    it.copy(
                        errorMessage = e.message
                    )
                }

            }
        }
    }

    fun generateMissingThumbnails() {
        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    isGeneratingThumbnails = true,
                    errorMessage = null
                )
            }

            try {

                val response = apiService.getMissingThumbnails()

                if (!response.isSuccessful) {
                    if (handleAuthFailure(response.code())) {
                        return@launch
                    }

                    _uiState.update {
                        it.copy(
                            isGeneratingThumbnails = false,
                            errorMessage = "Erro ao consultar thumbnails ausentes"
                        )
                    }
                    return@launch
                }

                val missingMedias = response.body()?.medias.orEmpty()

                if (missingMedias.isEmpty()) {
                    _uiState.update {
                        it.copy(isGeneratingThumbnails = false)
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        generatedThumbnails = 0,
                        totalThumbnailsToGenerate = missingMedias.size
                    )
                }

                val failures = AtomicInteger(0)

                coroutineScope {

                    missingMedias.map { media ->

                        async {

                            val result = generateThumbnailInternal(media.id)

                            result.onSuccess { response ->

                                _uiState.update { state ->

                                    val index = state.medias.indexOfFirst { it.id == media.id }

                                    if (index == -1) return@update state

                                    val newList = state.medias.toMutableList()

                                    newList[index] = newList[index].copy(
                                        thumbnail = response.thumbnail
                                    )

                                    state.copy(medias = newList)
                                }

                            }.onFailure {

                                failures.incrementAndGet()

                            }

                            _uiState.update {
                                it.copy(
                                    generatedThumbnails = it.generatedThumbnails + 1
                                )
                            }

                        }

                    }.awaitAll()

                }

                _uiState.update {
                    it.copy(
                        isGeneratingThumbnails = false,
                        generatedThumbnails = 0,
                        totalThumbnailsToGenerate = 0,
                        errorMessage = if (failures.get() > 0)
                            "Alguns thumbnails não puderam ser gerados"
                        else
                            null
                    )
                }

            } catch (e: Exception) {

                _uiState.update {
                    it.copy(
                        isGeneratingThumbnails = false,
                        errorMessage = "Erro ao gerar thumbnails: ${e.message.orEmpty()}"
                    )
                }

            }

        }
    }

    fun deleteThumbnail(id: Int) {

        viewModelScope.launch {

            try {

                val response = apiService.deleteThumbnail(id)

                if (response.isSuccessful) {
                    loadMedias()
                } else {
                    if (handleAuthFailure(response.code())) {
                        return@launch
                    }
                    _uiState.update {
                        it.copy(errorMessage = "Erro ao deletar thumbnail")
                    }
                }

            } catch (e: Exception) {

                _uiState.update {
                    it.copy(errorMessage = e.message)
                }

            }

        }

    }

    fun deleteMedia(id: Int) {

        viewModelScope.launch {

            try {

                val response = apiService.deleteMedia(id)

                if (response.isSuccessful) {
                    loadMedias()
                } else {
                    if (handleAuthFailure(response.code())) {
                        return@launch
                    }
                    _uiState.update {
                        it.copy(errorMessage = "Erro ao deletar mídia")
                    }
                }

            } catch (e: Exception) {

                _uiState.update {
                    it.copy(errorMessage = e.message)
                }

            }

        }

    }

    fun updateMedia(id: Int, title: String, description: String) {
        viewModelScope.launch {
            try {
                val response = apiService.updateMedia(
                    id = id,
                    request = dev.alexis.mediagallery.data.UpdateMediaRequest(
                        title = title,
                        description = description.ifBlank { null }
                    )
                )

                if (response.isSuccessful) {
                    loadMedias()
                } else {
                    if (handleAuthFailure(response.code())) {
                        return@launch
                    }
                    _uiState.update {
                        it.copy(errorMessage = "Erro ao editar mídia")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message)
                }
            }
        }
    }

    fun downloadMedia(media: Media) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        downloadingMedia = it.downloadingMedia + (media.id to 0f)
                    )
                }

                withContext(Dispatchers.IO) {
                    val response = apiService.getMediaFile(media.id)

                    if (!response.isSuccessful) {
                        if (handleAuthFailure(response.code())) {
                            return@withContext
                        }
                        throw Exception("Erro HTTP ${response.code()}")
                    }

                    val body = response.body()
                        ?: throw Exception("Arquivo vazio")

                    val extension = media.filename
                        ?.substringAfterLast('.', "")
                        ?.takeIf { it.isNotBlank() }

                    val fileName = buildString {
                        append("${media.id}_${media.title}")
                        extension?.let { append(".$it") }
                    }

                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, media.mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val resolver = application.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw Exception("Não foi possível criar o arquivo")

                    resolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)

                            val total = body.contentLength()
                            var downloaded = 0L

                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break

                                output.write(buffer, 0, read)

                                downloaded += read

                                if (total > 0) {
                                    _uiState.update {
                                        it.copy(
                                            downloadingMedia = it.downloadingMedia +
                                                    (media.id to downloaded.toFloat() / total)
                                        )
                                    }
                                }
                            }

                            output.flush()
                        }
                    } ?: throw Exception("Não foi possível abrir o arquivo para escrita")
                }

                _uiState.update {
                    it.copy(
                        downloadingMedia = it.downloadingMedia - media.id
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        downloadingMedia = it.downloadingMedia - media.id
                    )
                }
            }
        }
    }

    fun loadMedias() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = apiService.getMedias()
                if (response.isSuccessful) {
                    // CreatedAt vem em RFC3339 (ex: "2024-01-15T10:30:00Z"), formato
                    // do encoding/json do Go para time.Time -- ordenação lexicográfica
                    // de string já funciona corretamente nesse formato, sem precisar
                    // parsear pra Date antes.
                    val medias = response.body()?.medias.orEmpty()
                        .sortedByDescending { it.createdAt }
                    _uiState.update { it.copy(medias = medias, isLoading = false) }
                } else {
                    if (handleAuthFailure(response.code())) {
                        return@launch
                    }
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Erro ao carregar mídias")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Erro ao conectar: ${e.message}")
                }
            }
        }
    }
}
package dev.alexis.mediagallery.ui.gallery

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alexis.mediagallery.data.GenerateThumbnailResponse
import dev.alexis.mediagallery.data.Media
import dev.alexis.mediagallery.data.TokenManager
import dev.alexis.mediagallery.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tipo de operação em lote em andamento, usado para decidir qual ícone/
 * contador exibir na barra superior (ver GalleryScreen).
 */
enum class BatchOperationType {
    GENERATE_THUMBNAIL,
    DOWNLOAD,
    DELETE_THUMBNAIL,
    DELETE_MEDIA
}

/**
 * total == 0 é usado como estado "verificando" (ex: consultando quais
 * thumbnails estão faltando antes de saber quantos itens existem),
 * exibido como spinner indeterminado na UI.
 */
data class BatchProgress(
    val type: BatchOperationType,
    val total: Int,
    val completed: Int
)

data class GalleryUiState(
    val medias: List<Media> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val downloadingMedia: Map<Int, Float> = emptyMap(),
    val generatingThumbnail: Map<Int, Boolean> = emptyMap(),
    val selectedIds: Set<Int> = emptySet(),
    val batchProgress: BatchProgress? = null
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

    fun startSelection(id: Int) {
        _uiState.update { it.copy(selectedIds = setOf(id)) }
    }

    fun toggleSelection(id: Int) {
        _uiState.update { state ->
            val newSelection = if (id in state.selectedIds) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
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

    private suspend fun generateThumbnailsInternal(ids: List<Int>) {

        _uiState.update {
            it.copy(
                batchProgress = BatchProgress(
                    BatchOperationType.GENERATE_THUMBNAIL,
                    total = ids.size,
                    completed = 0
                ),
                errorMessage = null
            )
        }

        val failures = AtomicInteger(0)

        coroutineScope {

            ids.map { id ->

                async {

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

                    }.onFailure {
                        failures.incrementAndGet()
                    }

                    _uiState.update { state ->
                        val progress = state.batchProgress
                        state.copy(
                            batchProgress = progress?.copy(completed = progress.completed + 1)
                        )
                    }

                }

            }.awaitAll()

        }

        val idsSet = ids.toSet()

        _uiState.update { state ->
            state.copy(
                batchProgress = null,
                selectedIds = state.selectedIds - idsSet,
                errorMessage = if (failures.get() > 0) {
                    if (ids.size == 1)
                        "Não foi possível gerar o thumbnail"
                    else
                        "Alguns thumbnails não puderam ser gerados"
                } else null
            )
        }
    }

    fun generateThumbnails(ids: List<Int>) {
        if (ids.isEmpty()) return

        viewModelScope.launch {
            generateThumbnailsInternal(ids)
        }
    }

    fun generateMissingThumbnails() {
        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    batchProgress = BatchProgress(
                        BatchOperationType.GENERATE_THUMBNAIL,
                        total = 0,
                        completed = 0
                    ),
                    errorMessage = null
                )
            }

            try {

                val response = apiService.getMissingThumbnails()

                if (!response.isSuccessful) {
                    if (handleAuthFailure(response.code())) {
                        _uiState.update { it.copy(batchProgress = null) }
                        return@launch
                    }

                    _uiState.update {
                        it.copy(
                            batchProgress = null,
                            errorMessage = "Erro ao consultar thumbnails ausentes"
                        )
                    }
                    return@launch
                }

                val missingIds = response.body()?.medias.orEmpty().map { it.id }

                if (missingIds.isEmpty()) {
                    _uiState.update { it.copy(batchProgress = null) }
                    return@launch
                }

                generateThumbnailsInternal(missingIds)

            } catch (e: Exception) {

                _uiState.update {
                    it.copy(
                        batchProgress = null,
                        errorMessage = "Erro ao gerar thumbnails: ${e.message.orEmpty()}"
                    )
                }

            }

        }
    }

    private suspend fun deleteThumbnailInternal(id: Int): Boolean {
        return try {

            val response = apiService.deleteThumbnail(id)

            if (response.isSuccessful) {
                true
            } else {
                if (!handleAuthFailure(response.code())) {
                    _uiState.update {
                        it.copy(errorMessage = "Erro ao deletar thumbnail")
                    }
                }
                false
            }

        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = e.message)
            }
            false
        }
    }

    fun deleteThumbnail(id: Int) {
        viewModelScope.launch {
            if (deleteThumbnailInternal(id)) {
                loadMedias()
            }
        }
    }

    fun deleteThumbnails(ids: List<Int>) {
        if (ids.isEmpty()) return

        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    batchProgress = BatchProgress(
                        BatchOperationType.DELETE_THUMBNAIL,
                        total = ids.size,
                        completed = 0
                    ),
                    errorMessage = null
                )
            }

            val failures = AtomicInteger(0)

            coroutineScope {
                ids.map { id ->
                    async {
                        val success = deleteThumbnailInternal(id)
                        if (!success) failures.incrementAndGet()

                        _uiState.update { state ->
                            val progress = state.batchProgress
                            state.copy(
                                batchProgress = progress?.copy(completed = progress.completed + 1)
                            )
                        }
                    }
                }.awaitAll()
            }

            loadMediasInternal()

            val idsSet = ids.toSet()

            _uiState.update { state ->
                state.copy(
                    batchProgress = null,
                    selectedIds = state.selectedIds - idsSet,
                    errorMessage = state.errorMessage ?: if (failures.get() > 0) {
                        if (ids.size == 1)
                            "Não foi possível excluir o thumbnail"
                        else
                            "Alguns thumbnails não puderam ser excluídos"
                    } else null
                )
            }
        }
    }

    private suspend fun deleteMediaInternal(id: Int): Boolean {
        return try {

            val response = apiService.deleteMedia(id)

            if (response.isSuccessful) {
                true
            } else {
                if (!handleAuthFailure(response.code())) {
                    _uiState.update {
                        it.copy(errorMessage = "Erro ao deletar mídia")
                    }
                }
                false
            }

        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = e.message)
            }
            false
        }
    }

    fun deleteMedia(id: Int) {
        viewModelScope.launch {
            if (deleteMediaInternal(id)) {
                loadMedias()
            }
        }
    }

    fun deleteMedias(ids: List<Int>) {
        if (ids.isEmpty()) return

        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    batchProgress = BatchProgress(
                        BatchOperationType.DELETE_MEDIA,
                        total = ids.size,
                        completed = 0
                    ),
                    errorMessage = null
                )
            }

            val failures = AtomicInteger(0)

            coroutineScope {
                ids.map { id ->
                    async {
                        val success = deleteMediaInternal(id)
                        if (!success) failures.incrementAndGet()

                        _uiState.update { state ->
                            val progress = state.batchProgress
                            state.copy(
                                batchProgress = progress?.copy(completed = progress.completed + 1)
                            )
                        }
                    }
                }.awaitAll()
            }

            loadMediasInternal()

            val idsSet = ids.toSet()

            _uiState.update { state ->
                state.copy(
                    batchProgress = null,
                    selectedIds = state.selectedIds - idsSet,
                    errorMessage = state.errorMessage ?: if (failures.get() > 0) {
                        if (ids.size == 1)
                            "Não foi possível excluir a mídia"
                        else
                            "Algumas mídias não puderam ser excluídas"
                    } else null
                )
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

    private suspend fun downloadMediaInternal(media: Media): Boolean {
        return try {

            _uiState.update {
                it.copy(
                    downloadingMedia = it.downloadingMedia + (media.id to 0f)
                )
            }

            withContext(Dispatchers.IO) {
                val streamUrlResponse = apiService.getMediaStreamUrl(media.id, type = "download")

                if (!streamUrlResponse.isSuccessful) {
                    if (handleAuthFailure(streamUrlResponse.code())) {
                        throw CancellationException()
                    }
                    throw Exception("Erro HTTP ${streamUrlResponse.code()}")
                }

                val downloadUrl = streamUrlResponse.body()?.url
                    ?: throw Exception("URL de download inválida")

                val response = apiService.downloadFromUrl(downloadUrl)

                if (!response.isSuccessful) {
                    if (handleAuthFailure(response.code())) {
                        throw CancellationException()
                    }
                    throw Exception("Erro HTTP ${response.code()}")
                }

                val body = response.body()
                    ?: throw Exception("Arquivo vazio")

                val extension = media.filename
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotBlank() }

                val baseTitle = media.title
                    .replace(Regex("""\.[^.]+$"""), "")

                val fileName = buildString {
                    append("${media.id}_$baseTitle")
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

            true

        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update {
                it.copy(
                    downloadingMedia = it.downloadingMedia - media.id
                )
            }
            false
        }
    }

    fun downloadMedia(media: Media) {
        viewModelScope.launch {
            downloadMediaInternal(media)
        }
    }

    fun downloadMedias(medias: List<Media>) {
        if (medias.isEmpty()) return

        viewModelScope.launch {

            _uiState.update {
                it.copy(
                    batchProgress = BatchProgress(
                        BatchOperationType.DOWNLOAD,
                        total = medias.size,
                        completed = 0
                    ),
                    errorMessage = null
                )
            }

            var failures = 0

            for (media in medias) {

                val success = downloadMediaInternal(media)

                if (!success) failures++

                _uiState.update { state ->
                    val progress = state.batchProgress
                    state.copy(
                        batchProgress = progress?.copy(completed = progress.completed + 1)
                    )
                }
            }

            val idsSet = medias.map { it.id }.toSet()

            _uiState.update { state ->
                state.copy(
                    batchProgress = null,
                    selectedIds = state.selectedIds - idsSet,
                    errorMessage = if (failures > 0) {
                        if (medias.size == 1)
                            "Não foi possível baixar o arquivo"
                        else
                            "Alguns arquivos não puderam ser baixados"
                    } else null
                )
            }
        }
    }

    fun loadMedias() {
        viewModelScope.launch {
            loadMediasInternal()
        }
    }

    private suspend fun loadMediasInternal() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val response = apiService.getMedias()
            if (response.isSuccessful) {
                // CreatedAt comes in RFC3339 format (for example, "2024-01-15T10:30:00Z"),
                // the same format produced by Go's encoding/json for time.Time -- lexicographic
                // string sorting already works correctly in this format, without needing to
                // parse it into a Date first.
                val medias = response.body()?.medias.orEmpty()
                    .sortedByDescending { it.createdAt }
                _uiState.update { it.copy(medias = medias, isLoading = false) }
            } else {
                if (handleAuthFailure(response.code())) {
                    _uiState.update { it.copy(isLoading = false) }
                    return
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
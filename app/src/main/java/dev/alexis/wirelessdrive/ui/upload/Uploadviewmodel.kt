package dev.alexis.wirelessdrive.ui.upload

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dev.alexis.wirelessdrive.data.GenericResponse
import dev.alexis.wirelessdrive.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException

sealed class UploadStatus {
    data object Pending : UploadStatus()
    data class Uploading(val progress: Int = 0) : UploadStatus()
    data object Success : UploadStatus()
    data class Error(val message: String) : UploadStatus()
}

data class UploadItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val status: UploadStatus = UploadStatus.Pending
)

data class UploadUiState(
    val items: List<UploadItem> = emptyList(),
    val isUploading: Boolean = false,
    val allDone: Boolean = false
)

class InputStreamRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit = { _, _ -> }
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long {
        return contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: -1L
    }

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Não foi possível abrir o arquivo")

        input.use { stream ->
            val buffer = ByteArray(8192)
            var bytesWritten = 0L
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                bytesWritten += read
                onProgress(bytesWritten, total)
            }
        }
    }
}

class UploadViewModel(
    private val apiService: ApiService,
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    fun onFilesPicked(uris: List<Uri>) {
        val existing = _uiState.value.items.map { it.uri }.toSet()
        val newItems = uris.filterNot { it in existing }.map { resolveUploadItem(it) }
        _uiState.update { it.copy(items = it.items + newItems, allDone = false) }
    }

    fun removeItem(uri: Uri) {
        _uiState.update { it.copy(items = it.items.filterNot { item -> item.uri == uri }) }
    }

    fun uploadAll() {
        val state = _uiState.value
        if (state.items.isEmpty() || state.isUploading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, allDone = false) }

            for (item in state.items) {
                if (item.status is UploadStatus.Success) continue
                updateStatus(item.uri, UploadStatus.Uploading(0))
                val result = uploadSingle(item)
                updateStatus(item.uri, result)
            }

            _uiState.update { it.copy(isUploading = false, allDone = true) }
        }
    }

    private suspend fun uploadSingle(item: UploadItem): UploadStatus = withContext(Dispatchers.IO) {
        try {
            val mimeType = contentResolver.getType(item.uri) ?: "application/octet-stream"

            val title = item.displayName.substringBeforeLast('.', item.displayName)

            var lastPercent = -1
            val requestBody = InputStreamRequestBody(
                contentResolver = contentResolver,
                uri = item.uri,
                mediaType = mimeType.toMediaTypeOrNull()
            ) { bytesWritten, totalBytes ->
                if (totalBytes > 0) {
                    val percent = ((bytesWritten * 100) / totalBytes).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        updateStatus(item.uri, UploadStatus.Uploading(percent))
                    }
                }
            }

            val filePart = MultipartBody.Part.createFormData(
                "file",
                item.displayName,
                requestBody
            )

            val titlePart = title.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = apiService.uploadMedia(
                filePart,
                titlePart
            )

            if (response.isSuccessful) {
                UploadStatus.Success
            } else {
                val message = parseErrorMessage(response.errorBody()?.string())
                    ?: "Falha no upload"

                UploadStatus.Error(message)
            }

        } catch (e: Exception) {
            UploadStatus.Error(e.message ?: "Erro desconhecido")
        }
    }

    private fun updateStatus(uri: Uri, status: UploadStatus) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.uri == uri) it.copy(status = status) else it })
        }
    }

    private fun resolveUploadItem(uri: Uri): UploadItem {
        var name = uri.lastPathSegment ?: "arquivo"
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        return UploadItem(uri = uri, displayName = name, sizeBytes = size)
    }

    private fun parseErrorMessage(rawErrorBody: String?): String? {
        if (rawErrorBody.isNullOrBlank()) return null
        return try {
            Gson().fromJson(rawErrorBody, GenericResponse::class.java)?.error
        } catch (e: Exception) {
            null
        }
    }
}
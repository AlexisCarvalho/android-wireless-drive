package dev.alexis.mediagallery.ui.upload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun UploadScreen(
    viewModel: UploadViewModel,
    onUploadFinished: () -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> -> viewModel.onFilesPicked(uris) }

    // Mesmo comportamento do site: só volta sozinho quando TODOS deram certo.
    // Se algum falhou, fica na tela pra deixar reenviar.
    LaunchedEffect(uiState.allDone) {
        val allSucceeded = uiState.items.isNotEmpty() && uiState.items.all { it.status is UploadStatus.Success }
        if (uiState.allDone && allSucceeded) {
            delay(1200)
            onUploadFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Enviar arquivos", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { pickFilesLauncher.launch("*/*") },
            enabled = !uiState.isUploading
        ) {
            Text("Selecionar arquivos")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.items.isEmpty()) {
            Text(
                "Nenhum arquivo selecionado ainda",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.items, key = { it.uri }) { item ->
                    UploadItemRow(
                        item = item,
                        removable = !uiState.isUploading,
                        onRemove = { viewModel.removeItem(item.uri) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.uploadAll() },
                enabled = !uiState.isUploading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Enviar ${uiState.items.size} arquivo(s)")
                }
            }
        }
    }
}

@Composable
private fun UploadItemRow(item: UploadItem, removable: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.displayName, maxLines = 1)
            Text(
                text = "${"%.2f".format(item.sizeBytes / 1024.0 / 1024.0)} MB · ${statusLabel(item.status)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (removable) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remover")
            }
        }
    }
}

private fun statusLabel(status: UploadStatus): String = when (status) {
    UploadStatus.Pending -> "Aguardando"
    UploadStatus.Uploading -> "Enviando..."
    UploadStatus.Success -> "Enviado ✓"
    is UploadStatus.Error -> "Erro: ${status.message}"
}
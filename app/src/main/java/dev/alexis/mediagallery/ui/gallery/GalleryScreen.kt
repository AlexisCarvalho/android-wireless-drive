package dev.alexis.mediagallery.ui.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.alexis.mediagallery.data.Media
import dev.alexis.mediagallery.network.ApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onMediaClick: (Media) -> Unit,
    onUploadClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val focusRequester = remember {
        FocusRequester()
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showListView by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedMedia by remember {
        mutableStateOf<Media?>(null)
    }

    var isSearching by remember {
        mutableStateOf(false)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var showContextMenu by remember {
        mutableStateOf(false)
    }

    var showDeleteMediaDialog by remember {
        mutableStateOf(false)
    }

    var showDeleteThumbnailDialog by remember {
        mutableStateOf(false)
    }

    var showEditMediaDialog by remember {
        mutableStateOf(false)
    }

    var editingTitle by remember {
        mutableStateOf("")
    }
    var editingDescription by remember {
        mutableStateOf("")
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Dispara toda vez que esta composable entra em composição de novo --
    // inclusive ao voltar da tela de upload, então os itens recém-enviados
    // já aparecem sem precisar de um botão de refresh manual.
    LaunchedEffect(Unit) {
        viewModel.loadMedias()
    }

    LaunchedEffect(viewModel) {
        viewModel.sessionExpired.collect {
            onLogoutClick()
        }
    }

    val filteredMedias = remember(uiState.medias, searchText) {
        if (searchText.isBlank()) {
            uiState.medias
        } else {
            uiState.medias.filter {
                it.title.contains(searchText, ignoreCase = true) ||
                        it.filename.orEmpty().contains(searchText, ignoreCase = true) ||
                        it.description.orEmpty().contains(searchText, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {

                    if (showListView && isSearching) {

                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = { Text("Pesquisar...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )

                    } else {

                        Text("Mídias")

                    }

                },
                actions = {
                    if (showListView) {

                        IconButton(
                            onClick = {

                                if (isSearching) {
                                    isSearching = false
                                    searchText = ""
                                    keyboardController?.hide()
                                } else {
                                    isSearching = true
                                }

                            }
                        ) {
                            Icon(
                                imageVector =
                                    if (isSearching)
                                        Icons.Default.Close
                                    else
                                        Icons.Default.Search,
                                contentDescription = "Pesquisar",
                                tint = if (uiState.isGeneratingThumbnails) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary
                            )
                        }

                    } else {

                        IconButton(
                            onClick = { viewModel.generateMissingThumbnails() },
                            enabled = !uiState.isGeneratingThumbnails
                        ) {
                            if (uiState.isGeneratingThumbnails) {

                                val progress =
                                    uiState.generatedThumbnails.toFloat() /
                                            uiState.totalThumbnailsToGenerate.coerceAtLeast(1)

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(28.dp)
                                ) {

                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 2.dp
                                    )

                                    Text(
                                        text = "${uiState.totalThumbnailsToGenerate - uiState.generatedThumbnails}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 12.sp
                                    )
                                }

                            } else {

                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Gerar thumbnails",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                    }

                    IconButton(
                        onClick = {

                            showListView = !showListView

                            if (!showListView) {
                                //isSearching = false
                                //searchText = ""
                                keyboardController?.hide()
                            }

                        }
                    ) {
                        Icon(
                            imageVector = if (showListView) Icons.Filled.GridView else Icons.Filled.List,
                            contentDescription = if (showListView) "Modo grade" else "Modo lista"
                        )
                    }

                    TextButton(onClick = onLogoutClick) {
                        Text("Sair")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onUploadClick) {
                Icon(Icons.Filled.Add, contentDescription = "Enviar arquivos")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = uiState.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.loadMedias() }) { Text("Tentar novamente") }
                    }
                }

                uiState.medias.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sem mídias ainda")
                    }
                }

                else -> {
                    if (showListView) {
                        LazyColumn(
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(filteredMedias, key = { it.id }) { media ->
                                MediaListItem(
                                    media = media,
                                    downloadProgress = uiState.downloadingMedia[media.id],
                                    onClick = { onMediaClick(media) },
                                    onLongClick = {
                                        selectedMedia = media
                                        showContextMenu = true
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = PaddingValues(2.dp)
                        ) {
                            items(uiState.medias, key = { it.id }) { media ->
                                MediaGridItem(
                                    media = media,
                                    downloadProgress = uiState.downloadingMedia[media.id],
                                    isGeneratingThumbnail = uiState.generatingThumbnail[media.id],
                                    onClick = { onMediaClick(media) },
                                    onLongClick = {
                                        selectedMedia = media
                                        showContextMenu = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = {
                    showContextMenu = false
                }
            ) {

                DropdownMenuItem(
                    text = { Text("Baixar") },
                    onClick = {
                        selectedMedia?.let {
                            viewModel.downloadMedia(it)
                        }

                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("Gerar thumbnail") },
                    onClick = {
                        selectedMedia?.let {
                            viewModel.generateThumbnail(it.id)
                        }

                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("Editar título/descrição") },
                    onClick = {
                        selectedMedia?.let { media ->
                            editingTitle = media.title.orEmpty()
                            editingDescription = media.description.orEmpty()
                        }
                        showEditMediaDialog = true
                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("Deletar thumbnail") },
                    onClick = {
                        showDeleteThumbnailDialog = true
                        showContextMenu = false
                    }
                )

                DropdownMenuItem(
                    text = { Text("Deletar mídia") },
                    onClick = {
                        showDeleteMediaDialog = true
                        showContextMenu = false
                    }
                )
            }

            if (showEditMediaDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showEditMediaDialog = false
                    },
                    title = {
                        Text("Editar mídia")
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = editingTitle,
                                onValueChange = { editingTitle = it },
                                label = { Text("Título") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editingDescription,
                                onValueChange = { editingDescription = it },
                                label = { Text("Descrição") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedMedia?.let { media ->
                                    viewModel.updateMedia(
                                        id = media.id,
                                        title = editingTitle.trim(),
                                        description = editingDescription.trim()
                                    )
                                }
                                showEditMediaDialog = false
                            }
                        ) {
                            Text("Salvar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditMediaDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (showDeleteMediaDialog) {

                AlertDialog(
                    onDismissRequest = {
                        showDeleteMediaDialog = false
                    },

                    title = {
                        Text("Excluir mídia")
                    },

                    text = {
                        Text("Tem certeza que deseja excluir esta mídia?")
                    },

                    confirmButton = {

                        TextButton(
                            onClick = {

                                selectedMedia?.let {
                                    viewModel.deleteMedia(it.id)
                                }

                                showDeleteMediaDialog = false
                            }
                        ) {
                            Text("Excluir")
                        }

                    },

                    dismissButton = {

                        TextButton(
                            onClick = {
                                showDeleteMediaDialog = false
                            }
                        ) {
                            Text("Cancelar")
                        }

                    }

                )

            }
            if (showDeleteThumbnailDialog) {

                AlertDialog(
                    onDismissRequest = {
                        showDeleteThumbnailDialog = false
                    },

                    title = {
                        Text("Excluir thumbnail")
                    },

                    text = {
                        Text("Deseja remover o thumbnail desta mídia?")
                    },

                    confirmButton = {

                        TextButton(
                            onClick = {

                                selectedMedia?.let {
                                    viewModel.deleteThumbnail(it.id)
                                }

                                showDeleteThumbnailDialog = false
                            }
                        ) {
                            Text("Excluir")
                        }

                    },

                    dismissButton = {

                        TextButton(
                            onClick = {
                                showDeleteThumbnailDialog = false
                            }
                        ) {
                            Text("Cancelar")
                        }

                    }

                )

            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaListItem(
    media: Media,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconForType(media.type),
                contentDescription = media.type,
                tint = iconTintForType(media.type),
                modifier = Modifier.padding(end = 10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = media.title.ifBlank { media.filename.orEmpty() },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )

                downloadProgress?.let { progress ->
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (downloadProgress != null) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Baixando",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        downloadProgress?.let { progress ->
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridItem(
    media: Media,
    downloadProgress: Float?,
    isGeneratingThumbnail: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail ou ícone
        if (!media.thumbnail.isNullOrBlank() &&
            (media.type == "image" || media.type == "video")
        ) {
            AsyncImage(
                model = "${ApiConfig.BASE_URL}/thumbs/${media.thumbnail}",
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForType(media.type),
                    contentDescription = media.type,
                    tint = iconTintForType(media.type),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Overlay de download
        downloadProgress?.let { progress ->

            // Ícone de download
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Baixando",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Barra de progresso
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Overlay de geração de thumbnail
        if (isGeneratingThumbnail == true) {

            // Ícone
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Gerando thumbnail",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Barra indeterminada
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun iconForType(type: String): ImageVector = when (type.lowercase()) {
    "video" -> Icons.Filled.Videocam
    "audio" -> Icons.Filled.AudioFile
    "image" -> Icons.Filled.Image
    else -> Icons.Filled.InsertDriveFile
}

@Composable
private fun iconTintForType(type: String) = when (type.lowercase()) {
    "video" -> Color(0xFF915FF0)
    "audio" -> Color(0xFFD20A2E)
    "image" -> Color(0xFF109C84)
    else -> Color(0xFFFF5C00)
}
package dev.alexis.wirelessdrive.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import dev.alexis.wirelessdrive.data.Media
import dev.alexis.wirelessdrive.network.ApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onMediaClick: (Media) -> Unit,
    onUploadClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val isSelectionMode = uiState.selectedIds.isNotEmpty()
    val selectedMedias = uiState.medias.filter { it.id in uiState.selectedIds }

    val focusRequester = remember {
        FocusRequester()
    }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showListView by rememberSaveable {
        mutableStateOf(false)
    }

    var isSearching by remember {
        mutableStateOf(false)
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var showSelectionMenu by remember {
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

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMedias()
    }

    val filteredMedias = remember(uiState.medias, searchText) {
        if (searchText.isBlank()) {
            uiState.medias
        } else {
            uiState.medias.filter {
                it.title.contains(searchText, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {

                    when {
                        isSelectionMode -> {
                            val count = uiState.selectedIds.size
                            Text(if (count == 1) "1 selecionada" else "$count selecionadas")
                        }

                        showListView && isSearching -> {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                placeholder = { Text("Pesquisar...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }

                        else -> {
                            Text("Arquivos")
                        }
                    }

                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar seleção")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {

                        val batchProgress = uiState.batchProgress

                        if (batchProgress != null) {

                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BatchProgressIndicator(
                                    progress = batchProgress,
                                    icon = when (batchProgress.type) {
                                        BatchOperationType.DOWNLOAD -> Icons.Default.Download
                                        BatchOperationType.GENERATE_THUMBNAIL -> Icons.Default.Refresh
                                        BatchOperationType.DELETE_THUMBNAIL -> Icons.Default.ImageNotSupported
                                        BatchOperationType.DELETE_MEDIA -> Icons.Default.Delete
                                    }
                                )
                            }

                        } else {

                            IconButton(
                                onClick = { viewModel.downloadMedias(selectedMedias) }
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Baixar selecionadas"
                                )
                            }

                            IconButton(
                                onClick = { showDeleteMediaDialog = true }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Excluir arquivos")
                            }

                            Box {
                                IconButton(onClick = { showSelectionMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                                }

                                DropdownMenu(
                                    expanded = showSelectionMenu,
                                    onDismissRequest = { showSelectionMenu = false }
                                ) {

                                    DropdownMenuItem(
                                        text = { Text("Gerar thumbnail") },
                                        onClick = {
                                            viewModel.generateThumbnails(uiState.selectedIds.toList())
                                            showSelectionMenu = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Deletar thumbnail") },
                                        onClick = {
                                            showDeleteThumbnailDialog = true
                                            showSelectionMenu = false
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Editar título") },
                                        enabled = uiState.selectedIds.size == 1,
                                        onClick = {
                                            selectedMedias.singleOrNull()?.let { media ->
                                                editingTitle = media.title
                                            }
                                            showEditMediaDialog = true
                                            showSelectionMenu = false
                                        }
                                    )
                                }
                            }
                        }

                    } else {

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
                                    tint = if (uiState.batchProgress != null) MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.6f
                                    ) else MaterialTheme.colorScheme.primary
                                )
                            }

                        } else {

                            IconButton(
                                onClick = { viewModel.generateMissingThumbnails() },
                                enabled = uiState.batchProgress == null
                            ) {
                                if (uiState.batchProgress?.type !== null) {

                                    BatchProgressIndicator(
                                        progress = uiState.batchProgress!!,
                                        icon = Icons.Default.Refresh
                                    )

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
                }
            )
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(onClick = onUploadClick) {
                    Icon(Icons.Filled.Add, contentDescription = "Enviar arquivos")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState.isLoading && uiState.medias.isEmpty() -> {
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
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.loadMedias() }) { Text("Recarregar arquivos") }
                    }
                }

                uiState.medias.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Sem arquivos ainda")
                    }
                }

                else -> {
                    Column {
                        if (uiState.isRefreshing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (showListView) {
                            LazyColumn(
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                items(filteredMedias, key = { it.id }) { media ->
                                    MediaListItem(
                                        media = media,
                                        downloadProgress = uiState.downloadingMedia[media.id],
                                        isSelectionMode = isSelectionMode,
                                        isSelected = media.id in uiState.selectedIds,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(media.id)
                                            } else {
                                                onMediaClick(media)
                                            }
                                        },
                                        onLongClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(media.id)
                                            } else {
                                                viewModel.startSelection(media.id)
                                            }
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
                                        isSelectionMode = isSelectionMode,
                                        isSelected = media.id in uiState.selectedIds,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(media.id)
                                            } else {
                                                onMediaClick(media)
                                            }
                                        },
                                        onLongClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleSelection(media.id)
                                            } else {
                                                viewModel.startSelection(media.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showEditMediaDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showEditMediaDialog = false
                    },
                    title = {
                        Text("Editar arquivo")
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
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedMedias.singleOrNull()?.let { media ->
                                    viewModel.updateMedia(
                                        id = media.id,
                                        title = editingTitle.trim()
                                    )
                                }
                                showEditMediaDialog = false
                                viewModel.clearSelection()
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

                val count = selectedMedias.size

                AlertDialog(
                    onDismissRequest = {
                        showDeleteMediaDialog = false
                    },

                    title = {
                        Text(if (count == 1) "Excluir arquivo" else "Excluir arquivos")
                    },

                    text = {
                        Text(
                            if (count == 1)
                                "Tem certeza que deseja excluir este arquivo?"
                            else
                                "Tem certeza que deseja excluir estes $count arquivos?"
                        )
                    },

                    confirmButton = {

                        TextButton(
                            onClick = {
                                viewModel.deleteMedias(uiState.selectedIds.toList())
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

                val count = selectedMedias.size

                AlertDialog(
                    onDismissRequest = {
                        showDeleteThumbnailDialog = false
                    },

                    title = {
                        Text(if (count == 1) "Excluir thumbnail" else "Excluir thumbnails")
                    },

                    text = {
                        Text(
                            if (count == 1)
                                "Deseja remover o thumbnail deste arquivo?"
                            else
                                "Deseja remover o thumbnail destes $count arquivos?"
                        )
                    },

                    confirmButton = {

                        TextButton(
                            onClick = {
                                viewModel.deleteThumbnails(uiState.selectedIds.toList())
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

@Composable
private fun BatchProgressIndicator(
    progress: BatchProgress,
    icon: ImageVector
) {
    val fraction = if (progress.total > 0) {
        progress.completed.toFloat() / progress.total
    } else {
        0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(28.dp)
    ) {

        CircularProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 2.dp
        )

        if (progress.total > 0) {
            Text(
                text = "${progress.total - progress.completed}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaListItem(
    media: Media,
    downloadProgress: Float?,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selecionada" else "Não selecionada",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp)
                )
            } else {
                Icon(
                    imageVector = iconForType(media.type),
                    contentDescription = media.type,
                    tint = iconTintForType(media.type),
                    modifier = Modifier.padding(end = 10.dp)
                )
            }

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
    isSelectionMode: Boolean,
    isSelected: Boolean,
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

        downloadProgress?.let { progress ->

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

        if (isGeneratingThumbnail == true) {

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

        if (isSelectionMode) {

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selecionada" else "Não selecionada",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(18.dp)
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
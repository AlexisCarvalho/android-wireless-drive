package dev.alexis.mediagallery.ui.viewer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.Player
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val window = (context as? android.app.Activity)?.window
    remember(window) {
        window?.let { WindowInsetsControllerCompat(it, it.decorView) }
    }

    var showHeader by remember { mutableStateOf(false) }
    var controlsVersion by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(showHeader, controlsVersion) {

        if (!showHeader)
            return@LaunchedEffect

        delay(3000)

        showHeader = false

    }

    val videoPlayer =
        uiState.localVideoUri?.let {

            rememberVideoPlayer(
                uri = it,
                token = uiState.authToken
            )

        }

    val audioPlayer =
        uiState.localAudioUri?.let {

            rememberAudioPlayer(it)

        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {

                detectTapGestures {

                    showHeader = !showHeader

                    if (showHeader) {
                        controlsVersion++
                    }

                }

            }
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            uiState.errorMessage != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = uiState.errorMessage.orEmpty(), color = Color.White)
                    Button(onClick = { viewModel.loadMedia() }) { Text("Tentar novamente") }
                }
            }

            uiState.localVideoUri != null -> {
                VideoPlayer(
                    player = videoPlayer!!,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            }

            uiState.localAudioUri != null -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF915FF0),
                                    Color(0xFFD20A2E),
                                    Color(0xFF109C84)
                                )
                            )
                        )
                ) {

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = .08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = Color.White
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(24.dp)
                        )

                        Text(
                            text = uiState.media?.title.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                    }

                }

            }

            uiState.imageBytes != null -> {
                AsyncImage(
                    model = uiState.imageBytes,
                    contentDescription = uiState.media?.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                Text(
                    text = "Pré-visualização não disponível para este tipo de arquivo ainda",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        videoPlayer?.let {

            PlayerOverlay(
                player = it,
                title = uiState.media?.title.orEmpty(),
                visible = showHeader,
                onBackClick = onBackClick,
                onUserInteraction = {
                    controlsVersion++
                }
            )

        }

        if (uiState.imageBytes != null) {

            ImageOverlay(
                title = uiState.media?.title.orEmpty(),
                visible = showHeader,
                onBackClick = onBackClick
            )

        }

        audioPlayer?.let {

            PlayerOverlay(
                player = it,
                title = uiState.media?.title.orEmpty(),
                visible = showHeader,
                onBackClick = onBackClick,
                onUserInteraction = {
                    controlsVersion++
                }
            )

        }
    }
}

private fun formatTime(ms: Long): String {

    val totalSeconds = ms / 1000

    val minutes = totalSeconds / 60

    val seconds = totalSeconds % 60

    return "%02d:%02d".format(
        minutes,
        seconds
    )

}

@Composable
private fun ImageOverlay(
    title: String,
    visible: Boolean,
    onBackClick: () -> Unit
) {

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it }
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it }
            ) + fadeOut()
        ) {

            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .45f))
                    .padding(horizontal = 8.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = onBackClick
                ) {

                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )

                }

                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

            }
        }
    }
}

@Composable
private fun PlayerOverlay(
    player: ExoPlayer,
    title: String,
    visible: Boolean,
    onBackClick: () -> Unit,
    onUserInteraction: () -> Unit
) {

    var isPlaying by remember(player) {
        mutableStateOf(player.isPlaying)
    }

    var currentPosition by remember {
        mutableLongStateOf(0L)
    }

    var duration by remember {
        mutableLongStateOf(1L)
    }

    var sliderPosition by remember {
        mutableFloatStateOf(0f)
    }

    var isDragging by remember {
        mutableStateOf(false)
    }

    DisposableEffect(player) {

        val listener = object : Player.Listener {

            override fun onIsPlayingChanged(
                playing: Boolean
            ) {

                isPlaying = playing

            }

        }

        player.addListener(listener)

        onDispose {

            player.removeListener(listener)

        }

    }

    LaunchedEffect(player) {

        while (true) {

            duration = max(player.duration, 1L)

            if (!isDragging) {

                currentPosition = player.currentPosition

                sliderPosition = currentPosition.toFloat()

            }

            delay(250)

        }

    }

    Box(
        Modifier.fillMaxSize()
    ) {

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it }
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it }
            ) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = .45f))
                    .padding(horizontal = 8.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = onBackClick
                ) {

                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = Color.White
                    )

                }

                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

            }
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.Center),
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                IconButton(
                    onClick = {

                        onUserInteraction()

                        player.seekTo(
                            max(player.currentPosition - 5_000L, 0L)
                        )

                    }
                ) {

                    Icon(
                        imageVector = Icons.Default.Replay5,
                        contentDescription = "Voltar 5 segundos",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )

                }

                IconButton(
                    onClick = {

                        onUserInteraction()

                        if (player.isPlaying)
                            player.pause()
                        else
                            player.play()

                    }
                ) {

                    Icon(
                        imageVector =
                            if (isPlaying)
                                Icons.Default.PauseCircleFilled
                            else
                                Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )

                }

                IconButton(
                    onClick = {

                        onUserInteraction()

                        player.seekTo(
                            min(
                                player.currentPosition + 5_000L,
                                duration
                            )
                        )

                    }
                ) {

                    Icon(
                        imageVector = Icons.Default.Forward5,
                        contentDescription = "Avançar 5 segundos",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )

                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it }
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it }
            ) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = formatTime(sliderPosition.toLong()),
                        color = Color.White
                    )

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = formatTime(duration),
                        color = Color.White
                    )

                }

                Slider(

                    value = sliderPosition,

                    onValueChange = {

                        onUserInteraction()

                        isDragging = true

                        sliderPosition = it

                    },

                    onValueChangeFinished = {

                        onUserInteraction()

                        player.seekTo(
                            sliderPosition.toLong()
                        )

                        currentPosition = sliderPosition.toLong()

                        isDragging = false

                    },

                    valueRange = 0f..duration.toFloat()

                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {

            PlayerView(context).apply {

                this.player = player

                useController = false

            }

        }
    )

}

@Composable
private fun AudioPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {

            PlayerView(context).apply {

                this.player = player

                useController = false

            }

        }
    )

}

@OptIn(UnstableApi::class)
@Composable
private fun rememberVideoPlayer(
    uri: Uri,
    token: String?
): ExoPlayer {

    val context = LocalContext.current

    val dataSourceFactory = remember(token) {
        DefaultHttpDataSource.Factory().apply {
            token?.let {
                setDefaultRequestProperties(
                    mapOf(
                        "Authorization" to "Bearer $it"
                    )
                )
            }
        }
    }

    val player = remember(uri, token) {

        ExoPlayer.Builder(context)
            .build()
            .apply {

                val mediaSource =
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(
                            MediaItem.fromUri(uri)
                        )

                setMediaSource(mediaSource)

                prepare()

                playWhenReady = true
            }

    }

    DisposableEffect(player) {

        onDispose {
            player.release()
        }

    }

    return player
}

@Composable
private fun rememberAudioPlayer(
    uri: Uri
): ExoPlayer {

    val context = LocalContext.current

    val player = remember(uri) {

        ExoPlayer.Builder(context)
            .build()
            .apply {

                setMediaItem(
                    MediaItem.fromUri(uri)
                )

                prepare()

                playWhenReady = true

            }

    }

    DisposableEffect(player) {

        onDispose {
            player.release()
        }

    }

    return player
}
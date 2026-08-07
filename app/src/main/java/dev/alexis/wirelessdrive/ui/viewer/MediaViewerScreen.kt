package dev.alexis.wirelessdrive.ui.viewer

import android.content.ComponentName
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors
import dev.alexis.wirelessdrive.data.Media
import dev.alexis.wirelessdrive.playback.PlaybackService
import dev.alexis.wirelessdrive.playback.placeholderUriFor
import kotlinx.coroutines.delay
import kotlin.math.max
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
    var autoplayEnabled by remember { mutableStateOf(false) }
    var shuffleEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(showHeader, controlsVersion) {

        if (!showHeader)
            return@LaunchedEffect

        delay(3000)

        showHeader = false

    }

    val videoPlayer =
        uiState.localVideoUri?.let {

            rememberVideoPlayer(
                mediaKey = uiState.media?.id ?: -1,
                uri = it,
                token = uiState.authToken,
                refreshVersion = uiState.playbackRefreshVersion,
                onAuthError = { viewModel.refreshPlaybackAuth() }
            )

        }

    val audioQueueState =
        uiState.localAudioUri?.let {

            rememberAudioController(
                playlist = uiState.playlist,
                startMediaId = uiState.media?.id ?: -1,
                autoplayEnabled = autoplayEnabled,
                shuffleEnabled = shuffleEnabled,
                onCurrentMediaChanged = { id -> viewModel.onExternalMediaChanged(id) },
                onAuthError = { viewModel.refreshPlaybackAuth() }
            )

        }

    val audioPlayer = audioQueueState?.player

    val handleBackClick: () -> Unit = {
        audioPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        onBackClick()
    }

    BackHandler(onBack = handleBackClick)

    val effectiveCanGoToPrevious = if (shuffleEnabled) uiState.canShuffle else uiState.canGoToPrevious
    val effectiveCanGoToNext = if (shuffleEnabled) uiState.canShuffle else uiState.canGoToNext

    val handlePreviousClick: () -> Unit = {
        if (shuffleEnabled) viewModel.goToRandomMedia() else viewModel.goToPreviousMedia()
    }

    val handleNextClick: () -> Unit = {
        if (shuffleEnabled) viewModel.goToRandomMedia() else viewModel.goToNextMedia()
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
                onBackClick = handleBackClick,
                canGoToPrevious = effectiveCanGoToPrevious,
                canGoToNext = effectiveCanGoToNext,
                autoplayEnabled = autoplayEnabled,
                onToggleAutoplay = { autoplayEnabled = !autoplayEnabled },
                shuffleEnabled = shuffleEnabled,
                onToggleShuffle = { shuffleEnabled = !shuffleEnabled },
                onPreviousClick = handlePreviousClick,
                onNextClick = handleNextClick,
                onUserInteraction = {
                    controlsVersion++
                }
            )

        }

        audioPlayer?.let { player ->

            PlayerOverlay(
                player = player,
                title = uiState.media?.title.orEmpty(),
                visible = showHeader,
                onBackClick = handleBackClick,
                canGoToPrevious = audioQueueState?.hasPrevious ?: false,
                canGoToNext = audioQueueState?.hasNext ?: false,
                autoplayEnabled = autoplayEnabled,
                onToggleAutoplay = { autoplayEnabled = !autoplayEnabled },
                shuffleEnabled = shuffleEnabled,
                onToggleShuffle = { shuffleEnabled = !shuffleEnabled },
                onPreviousClick = { player.seekToPreviousMediaItem() },
                onNextClick = { player.seekToNextMediaItem() },
                onUserInteraction = {
                    controlsVersion++
                }
            )

        }

        if (uiState.imageBytes != null) {

            ImageOverlay(
                title = uiState.media?.title.orEmpty(),
                visible = showHeader,
                onBackClick = handleBackClick
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
    player: Player,
    title: String,
    visible: Boolean,
    onBackClick: () -> Unit,
    canGoToPrevious: Boolean,
    canGoToNext: Boolean,
    autoplayEnabled: Boolean,
    onToggleAutoplay: () -> Unit,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
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

    LaunchedEffect(player, autoplayEnabled, canGoToNext) {
        if (!autoplayEnabled || !canGoToNext) return@LaunchedEffect

        while (true) {
            if (player.playbackState == Player.STATE_ENDED) {
                onUserInteraction()
                onNextClick()
                break
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
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                IconButton(
                    onClick = {
                        onUserInteraction()
                        onToggleAutoplay()
                    }
                ) {
                    Icon(
                        imageVector = if (autoplayEnabled) Icons.Default.RepeatOn else Icons.Default.Repeat,
                        contentDescription = null,
                        tint = if (autoplayEnabled) Color(0xFF7CFFB2) else Color.White
                    )
                }

                IconButton(
                    onClick = {
                        onUserInteraction()
                        onToggleShuffle()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Reprodução aleatória",
                        tint = if (shuffleEnabled) Color(0xFF7CFFB2) else Color.White
                    )
                }

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
                        onPreviousClick()
                    },
                    enabled = canGoToPrevious
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowLeft,
                        contentDescription = "Mídia anterior",
                        tint = if (canGoToPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp)
                    )
                }

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

                IconButton(
                    onClick = {
                        onUserInteraction()
                        onNextClick()
                    },
                    enabled = canGoToNext
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardDoubleArrowRight,
                        contentDescription = "Próxima mídia",
                        tint = if (canGoToNext) Color.White else Color.White.copy(alpha = 0.35f),
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

/**
 * True if this error was caused by ExoPlayer failing to open a new HTTP
 * connection with a 401 or 403 -- e.g. the access token expired mid-playback,
 * or the user seeked into a byte range that requires a fresh connection and
 * the token had already expired by then. Also covers a 401/403 from
 * resolving the stream URL itself (see LazyResolvingDataSource), which
 * surfaces as a retrofit2.HttpException instead.
 */
private fun isAuthError(error: PlaybackException): Boolean {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException &&
            (cause.responseCode == 401 || cause.responseCode == 403)
        ) {
            return true
        }
        if (cause is retrofit2.HttpException &&
            (cause.code() == 401 || cause.code() == 403)
        ) {
            return true
        }
        cause = cause.cause
    }
    return false
}

@OptIn(UnstableApi::class)
@Composable
private fun rememberVideoPlayer(
    mediaKey: Int,
    uri: Uri,
    token: String?,
    refreshVersion: Int,
    onAuthError: () -> Unit
): ExoPlayer {

    val context = LocalContext.current

    val player = remember(mediaKey) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(player, uri, token, refreshVersion) {

        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            token?.let {
                setDefaultRequestProperties(
                    mapOf(
                        "Authorization" to "Bearer $it"
                    )
                )
            }
        }

        val resumePosition = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.playWhenReady

        val mediaSource =
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(
                    MediaItem.fromUri(uri)
                )

        player.setMediaSource(mediaSource, resumePosition)
        player.prepare()
        player.playWhenReady = wasPlaying

    }

    DisposableEffect(player, onAuthError) {

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isAuthError(error)) {
                    onAuthError()
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }

    }

    DisposableEffect(mediaKey) {

        onDispose {
            player.release()
        }

    }

    return player
}

/**
 * Small snapshot of what the audio queue can currently do, since Compose
 * has no way to know when ExoPlayer's own hasNextMediaItem()/
 * hasPreviousMediaItem() change unless something explicitly observes it.
 */
private data class AudioQueueState(
    val player: Player?,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

@OptIn(UnstableApi::class)
@Composable
private fun rememberAudioController(
    playlist: List<Media>,
    startMediaId: Int,
    autoplayEnabled: Boolean,
    shuffleEnabled: Boolean,
    onCurrentMediaChanged: (Int) -> Unit,
    onAuthError: () -> Unit
): AudioQueueState {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var hasInitializedQueue by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var hasPrevious by remember { mutableStateOf(false) }

    val latestAutoplayEnabled by rememberUpdatedState(autoplayEnabled)
    val latestOnCurrentMediaChanged by rememberUpdatedState(onCurrentMediaChanged)

    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            { controller = controllerFuture.get() },
            MoreExecutors.directExecutor()
        )

        onDispose {
            MediaController.releaseFuture(controllerFuture)
            controller = null
        }
    }

    LaunchedEffect(controller, playlist) {
        val player = controller ?: return@LaunchedEffect
        if (hasInitializedQueue || playlist.isEmpty()) return@LaunchedEffect

        val startIndex = playlist.indexOfFirst { it.id == startMediaId }.coerceAtLeast(0)

        val items = playlist.map { media ->
            MediaItem.Builder()
                .setMediaId(media.id.toString())
                .setUri(placeholderUriFor(media.id))
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(media.title).build()
                )
                .build()
        }

        player.setMediaItems(items, startIndex, 0L)
        player.shuffleModeEnabled = shuffleEnabled
        player.prepare()
        player.playWhenReady = true

        hasInitializedQueue = true
        hasNext = player.hasNextMediaItem()
        hasPrevious = player.hasPreviousMediaItem()
    }

    LaunchedEffect(controller, shuffleEnabled) {
        val player = controller ?: return@LaunchedEffect
        player.shuffleModeEnabled = shuffleEnabled
        hasNext = player.hasNextMediaItem()
        hasPrevious = player.hasPreviousMediaItem()
    }

    DisposableEffect(controller) {
        val player = controller

        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isAuthError(error)) {
                    onAuthError()
                    player?.prepare()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !latestAutoplayEnabled) {
                    player?.pause()
                }

                hasNext = player?.hasNextMediaItem() ?: false
                hasPrevious = player?.hasPreviousMediaItem() ?: false

                mediaItem?.mediaId?.toIntOrNull()?.let(latestOnCurrentMediaChanged)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                hasNext = player?.hasNextMediaItem() ?: false
                hasPrevious = player?.hasPreviousMediaItem() ?: false
            }
        }

        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    return AudioQueueState(
        player = controller,
        hasNext = hasNext,
        hasPrevious = hasPrevious
    )
}
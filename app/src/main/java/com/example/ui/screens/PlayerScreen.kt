package com.example.ui.screens

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.local.LocalMediaItem
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(items: List<LocalMediaItem>, isPaused: Boolean = false) {
    var currentIndex by remember { mutableStateOf(0) }
    val currentItem = items[currentIndex]
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = currentItem) { item ->
            val localFile = item.localPath?.let { java.io.File(it) }
            val mediaSource = if (localFile != null && localFile.exists()) item.localPath else item.url
            
            if (item.tipo == "video") {
                VideoPlayer(
                    url = mediaSource,
                    isPaused = isPaused,
                    onFinished = {
                        currentIndex = (currentIndex + 1) % items.size
                    }
                )
            } else {
                ImagePlayer(
                    url = mediaSource,
                    duration = item.duracao,
                    isPaused = isPaused,
                    onFinished = {
                        currentIndex = (currentIndex + 1) % items.size
                    }
                )
            }
        }
    }
}

@Composable
fun ImagePlayer(url: String, duration: Int, isPaused: Boolean, onFinished: () -> Unit) {
    var remainingTime by remember(url) { mutableLongStateOf(duration * 1000L) }
    
    LaunchedEffect(url, isPaused) {
        if (!isPaused) {
            val startTime = System.currentTimeMillis()
            while (remainingTime > 0) {
                delay(500)
                if (!isPaused) {
                    val elapsed = System.currentTimeMillis() - startTime
                    // We don't want to double count, but delay(500) is approximate
                    // Better approach:
                }
            }
        }
    }
    // Let's use a simpler approach for ImagePlayer pause
    val lastIsPaused = remember { mutableStateOf(isPaused) }
    
    LaunchedEffect(url, isPaused) {
        if (!isPaused) {
            val startTime = System.currentTimeMillis()
            val initialRemaining = remainingTime
            while (remainingTime > 0 && !isPaused) {
                delay(100)
                remainingTime = initialRemaining - (System.currentTimeMillis() - startTime)
            }
            if (remainingTime <= 0) {
                onFinished()
            }
        }
    }
    
    AsyncImage(
        model = if (url.startsWith("/")) java.io.File(url) else url,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(url: String, isPaused: Boolean, onFinished: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(isPaused) {
        exoPlayer.playWhenReady = !isPaused
    }

    DisposableEffect(url) {
        val uri = if (url.startsWith("/")) Uri.fromFile(java.io.File(url)) else Uri.parse(url)
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onFinished()
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
        }
    }

    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

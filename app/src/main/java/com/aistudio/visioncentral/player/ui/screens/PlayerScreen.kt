package com.aistudio.visioncentral.player.ui.screens

import android.net.Uri
import android.util.Log
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
import com.aistudio.visioncentral.player.data.local.LocalMediaItem
import kotlinx.coroutines.delay

import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.aspectRatio
import com.aistudio.visioncentral.player.data.local.DeviceConfig

import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.tween

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(items: List<LocalMediaItem>, config: DeviceConfig?, isPaused: Boolean = false) {
    Log.d("VisionCentral", "[CONFIG APLICADA NA UI]")
    Log.d("VisionCentral", "  - Orientacao: ${config?.orientacao}")
    Log.d("VisionCentral", "  - Rotacao: ${config?.rotacao}")
    Log.d("VisionCentral", "  - Proporcao: ${config?.proporcao}")
    Log.d("VisionCentral", "  - Modo Exibicao: ${config?.modoExibicao}")
    Log.d("VisionCentral", "  - Zoom: ${config?.zoom}")
    Log.d("VisionCentral", "  - Brilho: ${config?.brilho}")
    Log.d("VisionCentral", "  - Contraste: ${config?.contraste}")
    Log.d("VisionCentral", "  - Saturacao: ${config?.saturacao}")
    Log.d("VisionCentral", "  - Volume: ${config?.volume}")
    Log.d("VisionCentral", "  - Tempo Transicao: ${config?.tempoTransicao}")

    var currentIndex by remember(items) { mutableIntStateOf(0) }
    val currentItem = items.getOrNull(currentIndex) ?: return
    
    val rotation = config?.rotacao?.toFloatOrNull() ?: 0f
    val zoom = (config?.zoom ?: 100) / 100f
    val transitionTime = config?.tempoTransicao ?: 500
    
    // Color filters
    val brightness = (config?.brilho ?: 100) / 100f
    val contrast = (config?.contraste ?: 100) / 100f
    val saturation = (config?.saturacao ?: 100) / 100f
    
    val colorMatrix = remember(brightness, contrast, saturation) {
        val matrix = ColorMatrix()
        
        // Contrast & Brightness
        // R' = R * contrast + (brightness - 1)
        val brightnessOffset = (brightness - 1.0f) * 255f
        
        // For ColorMatrix in Compose, values is a FloatArray of 20 elements
        val values = matrix.values
        // Row 0: R
        values[0] = contrast
        values[4] = brightnessOffset
        // Row 1: G
        values[6] = contrast
        values[9] = brightnessOffset
        // Row 2: B
        values[12] = contrast
        values[14] = brightnessOffset
        // Row 3: A
        values[18] = 1f
        
        // Saturation
        val satMatrix = ColorMatrix().apply { setToSaturation(saturation) }
        matrix.timesAssign(satMatrix)
        
        matrix
    }

    val scaleString = config?.modoExibicao ?: "Fit"
    val contentScale = when (scaleString) {
        "Fit", "Contain" -> ContentScale.Fit
        "Fill" -> ContentScale.FillBounds
        "Cover", "Crop" -> ContentScale.Crop
        "Inside" -> ContentScale.Inside
        "None" -> ContentScale.None
        else -> ContentScale.Fit
    }
    
    val resizeMode = when (scaleString) {
        "Fit", "Contain" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        "Fill" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
        "Cover", "Crop" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    val aspectRatio = when (config?.proporcao) {
        "16:9" -> 16f / 9f
        "4:3" -> 4f / 3f
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        "21:9" -> 21f / 9f
        else -> null
    }

    val finalAspectRatio = remember(aspectRatio, rotation) {
        if (rotation == 90f || rotation == 270f) {
            aspectRatio?.let { 1f / it }
        } else {
            aspectRatio
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .rotate(rotation)
            .scale(zoom)
            .then(
                if (finalAspectRatio != null) Modifier.aspectRatio(finalAspectRatio) else Modifier
            )
            .graphicsLayer {
                renderEffect = null // This is for newer APIs, but we can use ColorFilter in children
            }
    ) {
        Crossfade(
            targetState = currentItem,
            animationSpec = tween(durationMillis = transitionTime)
        ) { item ->
            val localFile = item.localPath?.let { java.io.File(it) }
            val mediaSource = if (localFile != null && localFile.exists()) item.localPath else item.url
            
            Log.d("VisionCentral", "--- Início da Reprodução ---")
            Log.d("VisionCentral", "Mídia Atual: id=${item.id}, nome=${item.nome}, tipo=${item.tipo}, origem=${item.origem}")
            Log.d("VisionCentral", "Fonte de Mídia (Path ou URL): $mediaSource")
            
            when {
                item.origem == "url" -> {
                    Log.d("VisionCentral", "Renderer selecionado: WebPlayer (WebView)")
                    // Para origem URL, SEMPRE usar a URL original (item.url), ignorando qualquer path local
                    WebPlayer(
                        url = item.url,
                        duration = item.duracao,
                        isPaused = isPaused,
                        onFinished = {
                            currentIndex = (currentIndex + 1) % items.size
                        }
                    )
                }
                item.tipo == "video" -> {
                    Log.d("VisionCentral", "Renderer selecionado: VideoPlayer (ExoPlayer)")
                    VideoPlayer(
                        url = mediaSource ?: "",
                        isPaused = isPaused,
                        contentScale = contentScale,
                        resizeMode = resizeMode,
                        volume = (config?.volume ?: 100) / 100f,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        onFinished = {
                            currentIndex = (currentIndex + 1) % items.size
                        }
                    )
                }
                else -> {
                    Log.d("VisionCentral", "Renderer selecionado: ImagePlayer (AsyncImage)")
                    ImagePlayer(
                        url = mediaSource ?: "",
                        duration = item.duracao,
                        isPaused = isPaused,
                        contentScale = contentScale,
                        colorFilter = ColorFilter.colorMatrix(colorMatrix),
                        onFinished = {
                            currentIndex = (currentIndex + 1) % items.size
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WebPlayer(
    url: String,
    duration: Int,
    isPaused: Boolean,
    onFinished: () -> Unit
) {
    Log.d("VisionCentral", "WebPlayer: Carregando URL: $url")
    var remainingTime by remember(url) { mutableLongStateOf(duration * 1000L) }
    
    LaunchedEffect(url, isPaused) {
        if (!isPaused) {
            val startTime = System.currentTimeMillis()
            val initialRemaining = remainingTime
            while (remainingTime > 0 && !isPaused) {
                delay(200) // Slightly longer delay for CPU efficiency
                remainingTime = initialRemaining - (System.currentTimeMillis() - startTime)
            }
            if (remainingTime <= 0) {
                Log.d("VisionCentral", "WebPlayer: Tempo esgotado para $url")
                onFinished()
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                Log.d("VisionCentral", "WebPlayer: WebView criada")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("VisionCentral", "WebPlayer: Página carregada: $url")
                    }
                }
                
                // Prevent focus and keyboard for digital signage
                isFocusable = false
                isFocusableInTouchMode = false
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowContentAccess = true
                    allowFileAccess = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                }
                // Habilita cookies
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                Log.d("VisionCentral", "WebPlayer: Decidindo como carregar conteúdo")
                Log.d("VisionCentral", "WebPlayer: String enviada: $url")
                
                val lowerUrl = url.lowercase().trim()
                when {
                    lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://") -> {
                        Log.d("VisionCentral", "WebPlayer: Chamando webView.loadUrl() com HTTP(S)")
                        webView.loadUrl(url)
                    }
                    lowerUrl.contains("<!doctype html>") || lowerUrl.contains("<html") || lowerUrl.contains("<body") -> {
                        Log.d("VisionCentral", "WebPlayer: Detectado HTML - Chamando loadDataWithBaseURL()")
                        webView.loadDataWithBaseURL(null, url, "text/html", "utf-8", null)
                    }
                    else -> {
                        Log.d("VisionCentral", "WebPlayer: Formato desconhecido - Tentando loadUrl() como fallback")
                        webView.loadUrl(url)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        onRelease = { webView ->
            Log.d("VisionCentral", "WebPlayer: WebView liberada")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            // webView.clearCache(true) // Removido para evitar erro de chromium: No such file or directory
            webView.destroy()
        }
    )
}

@Composable
fun ImagePlayer(
    url: String, 
    duration: Int, 
    isPaused: Boolean, 
    contentScale: ContentScale, 
    colorFilter: ColorFilter? = null,
    onFinished: () -> Unit
) {
    var remainingTime by remember(url) { mutableLongStateOf(duration * 1000L) }
    
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
        contentScale = contentScale,
        colorFilter = colorFilter
    )
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String, 
    isPaused: Boolean, 
    contentScale: ContentScale, 
    resizeMode: Int, 
    volume: Float = 1.0f,
    colorFilter: ColorFilter? = null,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(isPaused) {
        exoPlayer.playWhenReady = !isPaused
    }

    LaunchedEffect(volume) {
        exoPlayer.volume = volume
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

    // Note: Applying ColorFilter to AndroidView (PlayerView) is tricky.
    // Usually PlayerView doesn't support ColorFilter directly on the surface.
    // A better way is using a overlay with blend modes, but that's complex.
    // For now, we'll apply it via Modifier.graphicsLayer if possible or just accept it works mostly for images.
    
    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = exoPlayer
                useController = false
                this.resizeMode = resizeMode
            }
        },
        update = {
            it.resizeMode = resizeMode
        },
        modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                // Some effects can be applied here, but ColorMatrix is easier on AsyncImage
            }
    )
}

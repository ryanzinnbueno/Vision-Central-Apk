package com.aistudio.visioncentral.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.aistudio.visioncentral.player.ui.theme.MyApplicationTheme

import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.visioncentral.player.ui.screens.*
import com.aistudio.visioncentral.player.ui.viewmodel.PlayerViewModel
import com.aistudio.visioncentral.player.ui.viewmodel.UiState

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d("VisionCentral", "Aplicativo iniciando - MainActivity.onCreate")
    
    // Hide system bars and keep screen on
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    
    enableEdgeToEdge()
    WindowInsetsControllerCompat(window, window.decorView).let { controller ->
      controller.hide(WindowInsetsCompat.Type.systemBars())
      controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    setContent {
      MyApplicationTheme {
        PermissionsWrapper {
          val viewModel: PlayerViewModel = viewModel()
          val state by viewModel.uiState.collectAsState()
          val isSyncing by viewModel.isSyncing.collectAsState()
          val config by viewModel.deviceConfig.collectAsState()
          val isDownloading by viewModel.isDownloading.collectAsState()
          val downloadProgress by viewModel.downloadProgress.collectAsState()
          val storageError by viewModel.storageError.collectAsState()
          
          var showUnlinkDialog by remember { mutableStateOf(false) }
          var showTechnicalPanel by remember { mutableStateOf(false) }
          
          val snackbarHostState = remember { SnackbarHostState() }
          val scope = rememberCoroutineScope()

          // Handle Orientation
          LaunchedEffect(config?.orientacao) {
              config?.let {
                  val orientacaoStr = it.orientacao ?: "Horizontal"
                  Log.d("VisionCentral", "Aplicando orientação: $orientacaoStr")
                  
                  val targetOrientation = when (orientacaoStr.lowercase()) {
                      "vertical", "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                      "horizontal", "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                      "reverse_vertical", "reverse_portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                      "reverse_horizontal", "reverse_landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                      "sensor" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
                      else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                  }
                  
                  if (requestedOrientation != targetOrientation) {
                      Log.d("VisionCentral", "Mudando requestedOrientation para: $targetOrientation")
                      requestedOrientation = targetOrientation
                  } else {
                      Log.d("VisionCentral", "Orientação já está correta: $targetOrientation")
                  }
              }
          }

          Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color(0xFF050508)
          ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
              Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF050508)
              ) {
                when (val s = state) {
                  is UiState.Splash -> SplashScreen()
                  is UiState.Activation -> ActivationScreen(s.error) { token ->
                    viewModel.activate(token)
                  }
                  is UiState.Syncing -> SyncingScreen(s.message)
                  is UiState.Playing -> PlayerScreen(s.items, config = config, isPaused = showTechnicalPanel)
                  is UiState.Error -> ErrorScreen(s.message) { viewModel.checkStatus() }
                  is UiState.TechnicalPanel -> {
                    // This state is handled by showTechnicalPanel variable for better overlay
                  }
                }
              }

              // Watermark Logo visible on top
              WatermarkLogo(
                isSyncing = isSyncing,
                onTechnicalPanelRequested = { showTechnicalPanel = true },
                onSyncingWarning = {
                  scope.launch {
                    snackbarHostState.showSnackbar("Aguarde a conclusão da sincronização para desvincular.")
                  }
                }
              )

              if (showTechnicalPanel && config != null) {
                TechnicalPanelDialog(
                  config = config!!,
                  isDownloading = isDownloading,
                  downloadProgress = downloadProgress,
                  storageError = storageError,
                  onSyncNow = {
                    showTechnicalPanel = false
                    viewModel.syncNow()
                  },
                  onUnlink = {
                    showTechnicalPanel = false
                    showUnlinkDialog = true
                  },
                  onDismiss = { showTechnicalPanel = false }
                )
              }

              if (showUnlinkDialog) {
                UnlinkDialog(
                  onConfirm = {
                    showUnlinkDialog = false
                    viewModel.unlink()
                  },
                  onDismiss = { showUnlinkDialog = false }
                )
              }
            }
          }
        }
      }
    }
  }
}

// Remove the Greeting composables

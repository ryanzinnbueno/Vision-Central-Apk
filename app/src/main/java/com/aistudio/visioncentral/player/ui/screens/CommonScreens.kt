package com.aistudio.visioncentral.player.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import com.aistudio.visioncentral.player.R

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.vision_central_logo_watermark_1783967060496),
                contentDescription = "Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Vision Central",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Player Profissional",
                fontSize = 18.sp,
                color = Color(0xFF22D3EE),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF22D3EE),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sincronizando conteúdos...",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ActivationScreen(error: String? = null, onActivate: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { 
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0D0D12))
                .padding(48.dp)
        ) {
            Text(
                text = "TOKEN DE ATIVAÇÃO",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    letterSpacing = 4.sp
                ),
                placeholder = {
                    Text(
                        "VC-XXXXXX", 
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.DarkGray,
                        fontSize = 32.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.DarkGray,
                    cursorColor = Color(0xFF3B82F6)
                ),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        if (token.isNotBlank()) onActivate(token)
                    }
                )
            )
            
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error, color = Color(0xFFF87171), fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Button(
                onClick = { 
                    if (token.isNotBlank()) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onActivate(token) 
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "ATIVAR",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Insira o código gerado no painel administrativo Vision Central.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SyncingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.vision_central_logo_watermark_1783967060496),
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = Color(0xFF22D3EE),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Vision Central Player",
                fontSize = 12.sp,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Erro", fontSize = 24.sp, color = Color.Red)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = message, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            // No TV usually we'd have a button, but user said "sem botões".
            // However, on error screen we might need one for recovery if polling fails.
            Text(text = "Tentando novamente automaticamente...", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun WatermarkLogo(
    isSyncing: Boolean,
    onTechnicalPanelRequested: () -> Unit,
    onSyncingWarning: () -> Unit
) {
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            onClick = {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 3000) {
                    clickCount++
                } else {
                    clickCount = 1
                }
                lastClickTime = currentTime

                if (clickCount >= 3) {
                    clickCount = 0
                    onTechnicalPanelRequested()
                }
            },
            modifier = Modifier
                .padding(24.dp)
                .size(56.dp)
                .onFocusChanged { isFocused = it.isFocused },
            color = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent,
            shape = RoundedCornerShape(28.dp),
            border = if (isFocused) BorderStroke(2.dp, Color(0xFF22D3EE).copy(alpha = 0.5f)) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.vision_central_logo_watermark_1783967060496),
                    contentDescription = "Vision Central Logo",
                    modifier = Modifier
                        .size(40.dp)
                        .alpha(if (isFocused) 0.8f else 0.25f)
                )
            }
        }
    }
}

@Composable
fun TechnicalPanelDialog(
    config: com.aistudio.visioncentral.player.data.local.DeviceConfig,
    isDownloading: Boolean,
    downloadProgress: Map<String, Float>,
    storageError: String?,
    onSyncNow: () -> Unit,
    onTogglePlaybackMode: (Boolean) -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }

    var focusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        if (!focusRequested) {
            try {
                firstItemFocusRequester.requestFocus()
                focusRequested = true
            } catch (e: Exception) {
                // Ignore if still not attached
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(28.dp)),
            color = Color(0xFF0D0D12),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (Fixed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vision_central_logo_watermark_1783967060496),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Painel Técnico", 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White,
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Text("X", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp)
                ) {
                    item {
                        Text(
                            text = "Informações do Dispositivo",
                            color = Color(0xFF22D3EE),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow("ID:", config.deviceId)
                        InfoRow("Cliente:", config.tvName ?: "Não vinculado")
                        InfoRow("Orientação:", config.orientacao)
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (isDownloading || downloadProgress.isNotEmpty() || storageError != null) {
                        item {
                            Text(
                                text = "Status de Download",
                                color = Color(0xFF22D3EE),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (storageError != null) {
                                Text(text = storageError, color = Color(0xFFF87171), fontSize = 13.sp)
                            } else {
                                val totalProgress = if (downloadProgress.isEmpty()) 0f else downloadProgress.values.average().toFloat()
                                LinearProgressIndicator(
                                    progress = { totalProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF22D3EE),
                                    trackColor = Color.DarkGray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isDownloading) "Baixando ${downloadProgress.size} mídias... ${(totalProgress * 100).toInt()}%" else "Downloads concluídos",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    item {
                        HorizontalDivider(color = Color.DarkGray)
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    item {
                        TvOptionButton(
                            "Sincronizar agora", 
                            onClick = onSyncNow,
                            modifier = Modifier.focusRequester(firstItemFocusRequester)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        if (!config.modoReproducaoAtivo) {
                            TvOptionButton(
                                "Ativar Reprodução",
                                onClick = { onTogglePlaybackMode(true) },
                                backgroundColor = Color(0xFF22D3EE),
                                contentColor = Color.Black
                            )
                        } else {
                            TvSwitchRow(
                                label = "Modo Reprodução",
                                checked = config.modoReproducaoAtivo,
                                onCheckedChange = onTogglePlaybackMode
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        TvOptionButton(
                            "Desvincular dispositivo",
                            color = Color(0xFFF87171),
                            onClick = onUnlink
                        )
                        // Extra bottom padding to ensure accessibility on all screens
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
                
                // Footer (Fixed)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    TvOptionButton(
                        "Fechar",
                        onClick = onDismiss,
                        modifier = Modifier.width(120.dp),
                        backgroundColor = Color.DarkGray.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvOptionButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        color = if (isFocused) Color.White.copy(alpha = 0.15f) else backgroundColor,
        contentColor = contentColor ?: if (isFocused) Color(0xFF22D3EE) else color,
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) BorderStroke(2.dp, Color(0xFF22D3EE)) else null
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text, 
                fontSize = 16.sp, 
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TvSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        color = if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) BorderStroke(2.dp, Color(0xFF22D3EE)) else null
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label, 
                color = if (isFocused) Color.White else Color.Gray,
                fontSize = 16.sp
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF22D3EE),
                    checkedTrackColor = Color(0xFF22D3EE).copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.width(100.dp))
        Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StoppedScreen(onOpenTechnicalPanel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050508)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.vision_central_logo_watermark_1783967060496),
                contentDescription = null,
                modifier = Modifier.size(100.dp).alpha(0.5f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Reprodução Interrompida",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "O modo de reprodução está desativado.",
                fontSize = 16.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onOpenTechnicalPanel,
                modifier = Modifier.height(56.dp).width(250.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Abrir Painel Técnico", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UnlinkDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0D12),
        titleContentColor = Color.White,
        textContentColor = Color(0xFF94A3B8),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Desvincular dispositivo",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Tem certeza que deseja remover este dispositivo da conta atual?\n\nEsta ação apagará apenas os dados armazenados neste dispositivo.",
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
            ) {
                Text("Desvincular", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
            ) {
                Text("Cancelar")
            }
        }
    )
}

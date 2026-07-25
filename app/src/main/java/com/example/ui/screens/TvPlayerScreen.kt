package com.example.ui.screens

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.model.Channel
import com.example.ui.viewmodel.EpgUiState
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun disableSSLCertificateChecking() {
    try {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<java.security.cert.X509Certificate>, authType: String) {}
        })

        val sc = javax.net.ssl.SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
        javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        Log.d("SSL_BYPASS", "Globally bypassed SSL verification for maximum compatibility")
    } catch (e: Exception) {
        Log.e("SSL_BYPASS", "Failed to setup trust-all SSL manager", e)
    }
}

private fun buildMediaItem(streamUrl: String, liveConfiguration: MediaItem.LiveConfiguration): MediaItem {
    val builder = MediaItem.Builder()
        .setUri(streamUrl)
        .setLiveConfiguration(liveConfiguration)
    
    val urlLower = streamUrl.lowercase()
    if (urlLower.contains("m3u8")) {
        builder.setMimeType("application/x-mpegURL")
    } else if (urlLower.contains(".ts") || urlLower.contains("/ts/")) {
        builder.setMimeType("video/mp2t")
    }
    return builder.build()
}

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    channel: Channel,
    allChannels: List<Channel>,
    epgState: EpgUiState,
    onChannelSelected: (Channel) -> Unit,
    onClosePlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showOverlay by remember { mutableStateOf(true) }

    // Retry and recovery states for stability
    var retryCount by remember { mutableStateOf(0) }
    val maxRetries = 8
    var isReconnecting by remember { mutableStateOf(false) }
    var connectionStatusText by remember { mutableStateOf("") }

    // Remote keypad number tuner states
    val focusRequester = remember { FocusRequester() }
    var digitBuffer by remember { mutableStateOf("") }
    var tempMessage by remember { mutableStateOf<String?>(null) }

    val channelIndex = allChannels.indexOfFirst { it.id == channel.id }
    val channelNumber = if (channelIndex != -1) channelIndex + 1 else 1

    // Fetch active & upcoming scheduling details from the EPG Argentina source
    val currentAndNext = remember(channel, epgState) {
        if (epgState is EpgUiState.Success) {
            val programs = getProgramsForChannel(channel, epgState.channelPrograms)
            getCurrentAndNextProgram(programs)
        } else {
            Pair(null, null)
        }
    }
    val currentProg = currentAndNext.first
    val nextProg = currentAndNext.second

    // Capture standard remote Back key to navigate back to channel listings
    BackHandler {
        onClosePlayer()
    }

    // Auto-focus container when player opens & bypass SSL verification errors
    LaunchedEffect(Unit) {
        disableSSLCertificateChecking()
        focusRequester.requestFocus()
    }

    // Auto-hide cinematic overlay HUD info after 3.5 seconds
    LaunchedEffect(channel.id) {
        showOverlay = true
        delay(3500)
        showOverlay = false
    }

    // Debounced channel sintonización by numeric remote typing
    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isNotEmpty()) {
            tempMessage = null
            delay(1500) // 1.5 seconds wait interval
            val typedNum = digitBuffer.toIntOrNull()
            if (typedNum != null) {
                val targetIdx = typedNum - 1
                if (targetIdx in allChannels.indices) {
                    onChannelSelected(allChannels[targetIdx])
                } else {
                    tempMessage = "Canal $typedNum no válido (Total: ${allChannels.size})"
                    delay(2000)
                    tempMessage = null
                }
            }
            digitBuffer = ""
        }
    }

    val liveConfiguration = remember {
        MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(15000) // Forces player to lag 15s behind the live edge to pre-buffer content
            .setMinPlaybackSpeed(0.95f)
            .setMaxPlaybackSpeed(1.05f)
            .build()
    }

    // Configura un factory de red personalizado con Timeouts optimizados y User-Agent de navegador moderno
    // Esto previene bloqueos 403 (un problema súper común en IPTV) y recupera la conexión mucho más rápido ante microcortes.
    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000) // 15 segundos max de espera de conexión
            .setReadTimeoutMs(15000)    // 15 segundos max de lectura del buffer
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
    }

    val mediaSourceFactory = remember {
        DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)
    }

    // El control de carga de ExoPlayer adaptado específicamente para soportar transmisiones en vivo de IPTV:
    // Cargando 10 segundos inicialmente (según lo pedido por el usuario) y manteniendo un colchón dinámico estable.
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        15000, // minBufferMs: 15s (permite que fluya el stream sin exigir más de lo que el servidor en vivo tiene disponible)
                        45000, // maxBufferMs: 45s (colchón protector grande por si se congela el stream)
                        10000, // bufferForPlaybackMs: espera a que cargue 10 segundos antes de comenzar la reproducción para evitar cortes!
                        10000  // bufferForPlaybackAfterRebufferMs: espera 10s después de un microcorte para retomar con estabilidad máxima
                    )
                    .build()
            )
            .build().apply {
                playWhenReady = true
            }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Reconexión sofisticada con enfriamiento de socket de 1.5s
    // Esto evita que los servidores de IPTV bloqueen las peticiones concurrentes por overload de sesiones.
    val triggerReconnect: () -> Unit = {
        isReconnecting = true
        isBuffering = true
        val currentAttempt = retryCount + 1
        connectionStatusText = "Optimizando señal... Re-conectando ($currentAttempt/$maxRetries)"
        Log.d("IPTV_PLAYER", "Attempting automatic connection-slot delay recovery $currentAttempt of $maxRetries")
        
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        
        scope.launch {
            delay(1500) // 1.5s delay to release active socket on server side cleanly
            val mediaItem = buildMediaItem(channel.streamUrl, liveConfiguration)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Monitoring loop for freeze and buffering timeout detection
    LaunchedEffect(channel.streamUrl, retryCount) {
        var lastPosition = -1L
        var freezeDurationSec = 0
        var consecutiveBufferingSec = 0
        var cleanPlaybackSec = 0
        
        while (true) {
            delay(1000)
            
            if (errorMessage != null) {
                // If we've reached max retry limits and given up, stop evaluating in background
                continue
            }
            
            // 1. Detect buffering for too long
            if (exoPlayer.playbackState == Player.STATE_BUFFERING) {
                consecutiveBufferingSec++
                if (consecutiveBufferingSec >= 5) { // Faster recovery timeout (5 seconds instead of 8)
                    Log.d("IPTV_PLAYER", "Buffering timeout detected ($consecutiveBufferingSec s) on attempt $retryCount")
                    consecutiveBufferingSec = 0
                    if (retryCount < maxRetries) {
                        retryCount++
                        triggerReconnect()
                    } else {
                        isBuffering = false
                        isReconnecting = false
                        errorMessage = "Canal no disponible (Tiempo de espera de red superado)."
                    }
                }
            } else {
                consecutiveBufferingSec = 0
            }
            
            // 2. Playback freeze detector
            if (exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_READY) {
                val currentPos = exoPlayer.currentPosition
                if (currentPos == lastPosition) {
                    freezeDurationSec++
                    if (freezeDurationSec >= 4) { // Reconnect faster if stream freezes for 4 seconds
                        Log.d("IPTV_PLAYER", "Playback freeze detected at $currentPos on attempt $retryCount")
                        freezeDurationSec = 0
                        if (retryCount < maxRetries) {
                            retryCount++
                            triggerReconnect()
                        } else {
                            isBuffering = false
                            isReconnecting = false
                            errorMessage = "Canal no disponible (Transmisión congelada)."
                        }
                    }
                } else {
                    freezeDurationSec = 0
                    cleanPlaybackSec++
                    if (cleanPlaybackSec >= 5) {
                        if (retryCount > 0) {
                            Log.d("IPTV_PLAYER", "Smooth dynamic stream playback verified. Resetting retry counters.")
                            retryCount = 0
                            isReconnecting = false
                            connectionStatusText = ""
                        }
                    }
                }
                lastPosition = currentPos
            } else {
                freezeDurationSec = 0
                cleanPlaybackSec = 0
            }
        }
    }

    // Bind life cycle logic and error observers to stream
    DisposableEffect(channel.streamUrl) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("IPTV_PLAYER", "ExoPlayer error occurred: ${error.message}", error)
                
                // Keep last frame or show recovering status instead of closing immediately
                if (retryCount < maxRetries) {
                    retryCount++
                    scope.launch {
                        delay(2000) // Small wait before retry to avoid connection storms
                        triggerReconnect()
                    }
                } else {
                    isBuffering = false
                    isReconnecting = false
                    errorMessage = "Canal no disponible (Error en la transmisión: ${error.errorCodeName})."
                }
            }
        }
        
        exoPlayer.addListener(listener)
        
        // Reset state values for a pristine channel change
        isBuffering = true
        isReconnecting = false
        connectionStatusText = ""
        errorMessage = null
        retryCount = 0
        
        val mediaItem = buildMediaItem(channel.streamUrl, liveConfiguration)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerViewRef?.player = null
            playerViewRef = null
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                            val currentIndex = allChannels.indexOfFirst { it.id == channel.id }
                            if (currentIndex != -1 && allChannels.isNotEmpty()) {
                                val nextIndex = (currentIndex + 1) % allChannels.size
                                onChannelSelected(allChannels[nextIndex])
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            val currentIndex = allChannels.indexOfFirst { it.id == channel.id }
                            if (currentIndex != -1 && allChannels.isNotEmpty()) {
                                val prevIndex = if (currentIndex - 1 < 0) allChannels.size - 1 else currentIndex - 1
                                onChannelSelected(allChannels[prevIndex])
                            }
                            true
                        }
                        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                            if (errorMessage != null) {
                                // Manual retry with Remote OK/ENTER
                                errorMessage = null
                                retryCount = 0
                                isBuffering = true
                                isReconnecting = false
                                val mediaItem = buildMediaItem(channel.streamUrl, liveConfiguration)
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                                exoPlayer.play()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEvent.KEYCODE_0 -> { digitBuffer += "0"; true }
                        KeyEvent.KEYCODE_1 -> { digitBuffer += "1"; true }
                        KeyEvent.KEYCODE_2 -> { digitBuffer += "2"; true }
                        KeyEvent.KEYCODE_3 -> { digitBuffer += "3"; true }
                        KeyEvent.KEYCODE_4 -> { digitBuffer += "4"; true }
                        KeyEvent.KEYCODE_5 -> { digitBuffer += "5"; true }
                        KeyEvent.KEYCODE_6 -> { digitBuffer += "6"; true }
                        KeyEvent.KEYCODE_7 -> { digitBuffer += "7"; true }
                        KeyEvent.KEYCODE_8 -> { digitBuffer += "8"; true }
                        KeyEvent.KEYCODE_9 -> { digitBuffer += "9"; true }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Native PlayerView rendered using Jetpack view-interop
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Custom cinema controls overlays
                    keepScreenOn = true // Corrects screen timeout sleep while stream is active
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
            },
            onRelease = { playerView ->
                playerView.player = null
                playerViewRef = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading stream loader & Reconectando status Overlay (Discrete layout preserving last frame)
        if ((isBuffering || isReconnecting) && errorMessage == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isReconnecting) Color.Black.copy(alpha = 0.45f) else Color.Transparent)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isReconnecting) connectionStatusText else "Cargando más para evitar menos cortes...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isReconnecting) "Auto-estabilizando señal (Reduciendo cortes)" else "Cargando búfer de estabilidad (10s) • Canal $channelNumber: ${channel.name}",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Custom stream parsing Error Dialog UI (Solid fallback screen when link is fallen)
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "📡",
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Canal no disponible",
                        color = Color.Red,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$errorMessage",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    Row {
                        Button(
                            onClick = onClosePlayer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray
                            ),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text("Regresar (Back)", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                // Manual retry click
                                errorMessage = null
                                retryCount = 0
                                isBuffering = true
                                isReconnecting = false
                                val mediaItem = buildMediaItem(channel.streamUrl, liveConfiguration)
                                exoPlayer.setMediaItem(mediaItem)
                                exoPlayer.prepare()
                                exoPlayer.play()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Reintentar (OK)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Custom details HUD overlay containing Channel Number, Name, Logo and Current Programming/Epg Show
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large styled channel index badge (Requirements 2)
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "CH $channelNumber",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .size(56.dp)
                            .padding(end = 12.dp)
                    )
                }
                Column {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "EN VIVO • ${channel.category}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Beautifully align current program on right side matching standard high-fidelity TV guides
                Column(
                    modifier = Modifier
                        .widthIn(max = 450.dp)
                        .padding(start = 16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (currentProg != null) {
                        Text(
                            text = "En reproducción: [${currentProg.displayStart} - ${currentProg.displayEnd}]",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentProg.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentProg.description.isNotEmpty()) {
                            Text(
                                text = currentProg.description,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (nextProg != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Siguiente [${nextProg.displayStart}]: ${nextProg.title}",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "Programación en Vivo",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sintonizando en Directo",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = "Sin información de guía disponible",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        // Remote numeric tuning UI overlay (Requirement 3)
        if (digitBuffer.isNotEmpty() || tempMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.90f), shape = RoundedCornerShape(12.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (tempMessage != null) "❌" else "🔢",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = tempMessage ?: "Sintonizando canal...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                    if (tempMessage == null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = digitBuffer,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Navigation visual helper
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(
                text = "Flechas ARRIBA/ABAJO o CH+/CH- para pasar canales • Marcar número para sintonizar",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.75f), shape = MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

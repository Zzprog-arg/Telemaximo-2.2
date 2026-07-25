package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.UserAccount
import coil.compose.AsyncImage
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.model.Channel
import com.example.ui.viewmodel.ChannelCategory
import com.example.ui.viewmodel.EpgProgram
import com.example.ui.viewmodel.EpgUiState
import com.example.ui.viewmodel.IPTVUiState
import java.util.Date

enum class DashboardSection {
    Inicio, Guia, Canales, CanalesOp2
}

@Composable
fun HomeScreen(
    uiState: IPTVUiState,
    epgState: EpgUiState,
    currentUser: UserAccount?,
    onChannelSelected: (Channel) -> Unit,
    onReloadData: () -> Unit,
    onLogout: () -> Unit
) {
    var activeSection by remember { mutableStateOf(DashboardSection.Inicio) }
    var isSidebarOpen by remember { mutableStateOf(true) }
    val sidebarFocusRequester = remember { FocusRequester() }
    var showAccountDialog by remember { mutableStateOf(false) }

    // Smoothly animate sidebar collapse and expand transitions.
    // Changing the collapse target from 0.dp to 72.dp prevents deallocating focus nodes,
    // which completely eliminates focus recursion crash loops on Android TV boxes.
    val sidebarWidth by animateDpAsState(
        targetValue = if (isSidebarOpen) 230.dp else 72.dp,
        label = "sidebar_width"
    )

    // Pull focus to active section in sidebar when sidebar opens
    LaunchedEffect(isSidebarOpen) {
        if (isSidebarOpen) {
            try {
                sidebarFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request might fail on initial pass, gracefully ignore
            }
        }
    }

    // Intercept back actions: return to "Inicio" if inside non-Inicio sections
    BackHandler(enabled = activeSection != DashboardSection.Inicio) {
        activeSection = DashboardSection.Inicio
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D12))
    ) {
        // --- 1. SMOOTH COLLAPSIBLE LEFT NAVIGATION SIDEBAR PANEL ---
        Box(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(Color(0xFF13151D))
                .clipToBounds()
                .onFocusChanged { focusState ->
                    // Auto-open sidebar when it receives focus, auto-close when focus leaves it
                    if (focusState.hasFocus) {
                        isSidebarOpen = true
                    } else {
                        isSidebarOpen = false
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .requiredWidth(230.dp)
                    .fillMaxHeight()
                    .padding(vertical = 24.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Elegant Brand Logo Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, bottom = 32.dp)
                ) {
                    Text(
                        text = "📺",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "IPTV Player",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sidebar navigation buttons with requested dynamic focus mapping
                SidebarItem(
                    label = "Inicio",
                    icon = "🏠",
                    isSelected = activeSection == DashboardSection.Inicio,
                    onSelect = { activeSection = DashboardSection.Inicio },
                    modifier = if (activeSection == DashboardSection.Inicio) Modifier.focusRequester(sidebarFocusRequester) else Modifier
                )
                SidebarItem(
                    label = "Guía de Canales",
                    icon = "📅",
                    isSelected = activeSection == DashboardSection.Guia,
                    onSelect = { activeSection = DashboardSection.Guia },
                    modifier = if (activeSection == DashboardSection.Guia) Modifier.focusRequester(sidebarFocusRequester) else Modifier
                )
                SidebarItem(
                    label = "Canales",
                    icon = "📁",
                    isSelected = activeSection == DashboardSection.Canales,
                    onSelect = { activeSection = DashboardSection.Canales },
                    modifier = if (activeSection == DashboardSection.Canales) Modifier.focusRequester(sidebarFocusRequester) else Modifier
                )
                SidebarItem(
                    label = "Canales OP2",
                    icon = "⚙️",
                    isSelected = activeSection == DashboardSection.CanalesOp2,
                    onSelect = { activeSection = DashboardSection.CanalesOp2 },
                    modifier = if (activeSection == DashboardSection.CanalesOp2) Modifier.focusRequester(sidebarFocusRequester) else Modifier
                )
                SidebarItem(
                    label = "Mi cuenta",
                    icon = "👤",
                    isSelected = false,
                    onSelect = { showAccountDialog = true }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Background Reload indicators
                var isSyncFocused by remember { mutableStateOf(false) }
                IconButton(
                    onClick = onReloadData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isSyncFocused = it.isFocused }
                        .background(
                            if (isSyncFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recargar Señal",
                            color = if (isSyncFocused) Color.White else Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 2. ACTIVE VIEWPORT PANE (RIGHT HAND PANEL) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFF08090C))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopStart
        ) {
            when (uiState) {
                is IPTVUiState.Loading -> {
                    // Full immersive TV loading layout as requested (Requirement 7)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(32.dp)
                                .widthIn(max = 450.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_telemaximo_banner),
                                contentDescription = "TelemaXimo Logo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .padding(bottom = 16.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "TelemaXimo TV",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = uiState.message,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            LinearProgressIndicator(
                                progress = uiState.progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(Color(0xFF1B1C24), shape = RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFF1B1C24)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${(uiState.progress * 100).toInt()}%",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                is IPTVUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color(0xFF1E1E24), shape = RoundedCornerShape(16.dp))
                                .padding(32.dp)
                                .widthIn(max = 500.dp)
                        ) {
                            Text("⚠️", style = MaterialTheme.typography.displayMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Fallo de Carga Directa",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                              )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.message,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            var isRetryFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = onReloadData,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                border = BorderStroke(2.dp, if (isRetryFocused) Color.White else Color.Transparent),
                                modifier = Modifier.onFocusChanged { isRetryFocused = it.isFocused }
                            ) {
                                Text("Reintentar Conexión", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                is IPTVUiState.Success -> {
                    // Render only the currently active section
                    when (activeSection) {
                        DashboardSection.Inicio -> {
                            InicioView(
                                recommended = uiState.recommendedChannels,
                                eventChannels = uiState.eventChannels,
                                onChannelSelected = onChannelSelected
                            )
                        }
                        DashboardSection.Guia -> {
                            GuiaView(
                                channels = uiState.allChannels,
                                epgState = epgState,
                                onChannelSelected = onChannelSelected
                            )
                        }
                        DashboardSection.Canales -> {
                            CategoriasView(
                                categories = uiState.categories,
                                onChannelSelected = onChannelSelected
                            )
                        }
                        DashboardSection.CanalesOp2 -> {
                            CanalesOp2View(
                                channels = uiState.op2Channels,
                                onChannelSelected = onChannelSelected
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("👤", fontSize = 28.sp)
                    Text(
                        text = "Mi Cuenta TelemaXimo",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Logged in User Card
                    Surface(
                        color = Color(0xFF1B1E28),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Usuario:", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                Text(currentUser?.username ?: "Invitado", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Contraseña:", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                Text(currentUser?.password ?: "••••••••", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Estado:", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF4CAF50), shape = RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Premium Activo", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dispositivos:", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                Text("1 / ${currentUser?.maxDevices ?: 3} (Este TV)", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Simulated subscriptions & packs "para aparentar"
                    Text(
                        text = "Packs Contratados",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Surface(
                        color = Color(0xFF13151D),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val packs = listOf(
                                "⚡ TelemaXimo Full HD",
                                "⚽ Pack Fútbol Premium (AFA)",
                                "🍿 Pack Cine Star & HBO Premium",
                                "🏆 Señales en Vivo 4K",
                                "🔮 Canales de Eventos Especiales OP2"
                            )
                            packs.forEach { pack ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text("✔️", fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                                    Text(
                                        text = pack,
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                var isLogoutFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        showAccountDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    border = BorderStroke(2.dp, if (isLogoutFocused) Color.White else Color.Transparent),
                    modifier = Modifier.onFocusChanged { isLogoutFocused = it.isFocused }
                ) {
                    Text("Cerrar Sesión", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                var isCloseFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = { showAccountDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    border = BorderStroke(2.dp, if (isCloseFocused) Color.White else Color.Transparent),
                    modifier = Modifier.onFocusChanged { isCloseFocused = it.isFocused }
                ) {
                    Text("Volver", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF13151D),
            textContentColor = Color.White,
            titleContentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// --- COMPOSE SUB-VIEWS FOR TV SECTIONS ---

@Composable
fun SidebarItem(
    label: String,
    icon: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onSelect,
        color = when {
            isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> Color(0xFF202330)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                text = label,
                color = if (isFocused || isSelected) Color.White else Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * 4. Screen section: INICIO (Showing Events / fallback cards)
 */
@Composable
fun InicioView(
    recommended: List<Channel>,
    eventChannels: List<Channel>,
    onChannelSelected: (Channel) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Inicio",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Transmisiones especiales, eventos destacados y canales recomendados",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Announcements / Dynamic Event Banners Row (Requirement 4)
        item {
            Column {
                Text(
                    text = if (eventChannels.isNotEmpty()) "Eventos Especiales en Vivo" else "Bienvenidos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (eventChannels.isNotEmpty()) {
                        // Display the events fetched dynamically from M3U playlist group categories
                        for (event in eventChannels.take(3)) {
                            AdEventCard(
                                channel = event,
                                onPlay = { onChannelSelected(event) }
                            )
                        }
                    } else {
                        // Default Fallback promotion cards (Requirement 4)
                        AdBannerCard(
                            title = "Bienvenido",
                            badge = "SISTEMA",
                            description = "Disfrutá tus canales de aire, deportes y noticias preferidas.",
                            gradientStart = Color(0xFF1E143A),
                            gradientEnd = Color(0xFF432C8C)
                        )
                        AdBannerCard(
                            title = "Disfrutá tus canales",
                            badge = "VIVO",
                            description = "Utilizá el control remoto para pasar canales con Flechas o CH+/CH-.",
                            gradientStart = Color(0xFF331414),
                            gradientEnd = Color(0xFF8C2C2C)
                        )
                        AdBannerCard(
                            title = "Sin eventos disponibles",
                            badge = "AGENDA",
                            description = "Vuelve a consultar pronto para sintonizar señales especiales temporarias.",
                            gradientStart = Color(0xFF1C1D24),
                            gradientEnd = Color(0xFF3B3E4F)
                        )
                    }
                }
            }
        }

        // Recommended Channels list: First 5 channels constraints
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Recomendados para Ti",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (recommended.isEmpty()) {
                    Text(
                        "No se encontraron canales para recomendar.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recommended) { channel ->
                            ChannelCard(
                                channel = channel,
                                onChannelSelected = onChannelSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. Screen section: GUÍA (XML parsed program list)
 */
@Composable
fun GuiaView(
    channels: List<Channel>,
    epgState: EpgUiState,
    onChannelSelected: (Channel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Guía de Programación TV (EPG)",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "Horarios y programas del día cargados desde EPG Argentina",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when (epgState) {
            is EpgUiState.Loading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Procesando XML de EPG Argentina (Esto toma unos segundos)...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is EpgUiState.Error -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📡", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No se pudo sincronizar de open-epg.com",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            epgState.message,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            is EpgUiState.Success -> {
                val epgMap = epgState.channelPrograms
                
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    items(channels) { channel ->
                        EpgChannelRow(
                            channel = channel,
                            programs = getProgramsForChannel(channel, epgMap),
                            onPlay = { onChannelSelected(channel) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5. Screen section: CANALES (All Categories horizontally)
 */
@Composable
fun CategoriasView(
    categories: List<ChannelCategory>,
    onChannelSelected: (Channel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 32.dp)) {
                Text(
                    text = "Categorías de Televisión",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Navegue libremente entre las filas usando su control remoto",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(categories) { category ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 32.dp, bottom = 10.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(category.channels) { channel ->
                        ChannelCard(
                            channel = channel,
                            onChannelSelected = onChannelSelected
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5. Screen section: CANALES OP2 (*op2 matching)
 */
@Composable
fun CanalesOp2View(
    channels: List<Channel>,
    onChannelSelected: (Channel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Señales OP2",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "Transmisiones alternativas o señales específicas (OP2) de la lista de reproducción",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚙️",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Sin Canales OP2 en la lista actual",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyVerticalGridTV(
                channels = channels,
                onChannelSelected = onChannelSelected
            )
        }
    }
}

@Composable
fun LazyVerticalGridTV(
    channels: List<Channel>,
    onChannelSelected: (Channel) -> Unit
) {
    // Elegant TV flow supporting LazyColumn rows of 4 cards
    val rows = channels.windowed(size = 4, step = 4, partialWindows = true)
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        items(rows) { rowChannels ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (channel in rowChannels) {
                    ChannelCard(
                        channel = channel,
                        onChannelSelected = onChannelSelected
                    )
                }
            }
        }
    }
}

// --- CORE UI DECORATOR CARD AND BANNER ELEMENTS ---

@Composable
fun AdBannerCard(
    title: String,
    badge: String,
    description: String,
    gradientStart: Color,
    gradientEnd: Color
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.00f,
        label = "banner_scale"
    )

    Card(
        modifier = Modifier
            .width(260.dp)
            .height(130.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { /* decorative active state */ },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.2.dp, if (isFocused) Color.White else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = listOf(gradientStart, gradientEnd)))
                .padding(14.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 4. Beautiful dynamic Event Banner representing events fetched from M3U (Requirement 4)
 */
@Composable
fun AdEventCard(
    channel: Channel,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.00f,
        label = "event_scale"
    )

    Card(
        modifier = Modifier
            .width(260.dp)
            .height(130.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onPlay() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.5.dp, if (isFocused) Color.White else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF8E0505),
                            Color(0xFF330202)
                        )
                    )
                )
        ) {
            // High fidelity logo layout background overlay
            if (channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.22f,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = channel.groupTitle.uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "🔴 EVENTO",
                        color = Color.Red,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }

                Column {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Presioná OK para ver ahora",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 3. High Performance Optimization constraints
 */
@Composable
fun ChannelCard(
    channel: Channel,
    onChannelSelected: (Channel) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    // Dynamic focus scaling and border transforms matching TV systems
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .width(170.dp)
            .height(105.dp)
            .scale(scale)
            .onFocusChanged { state ->
                isFocused = state.isFocused
            }
            .clickable {
                onChannelSelected(channel)
            },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 3.5.dp else 1.2.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color(0xFF282C35)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF232A3B) else Color(0xFF14171E),
            contentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!isError && channel.logoUrl.isNotEmpty()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    onError = {
                        isError = true
                    }
                )
                
                // Bottom shadow banner overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 40f
                            )
                        )
                )

                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            } else {
                // TV Emoji Placeholder fallback Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF232630),
                                    Color(0xFF16181F)
                                )
                            )
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📺",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Custom Row representing Epg Channel information
 */
@Composable
fun EpgChannelRow(
    channel: Channel,
    programs: List<EpgProgram>,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val pair = remember(programs) { getCurrentAndNextProgram(programs) }
    val currentProg = pair.first
    val nextProg = pair.second

    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color(0xFF1E2435) else Color(0xFF14161F),
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color(0xFF232631)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Logo / Placeholder representation
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 40.dp)
                    .background(Color(0xFF0C0D12), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Text("📺", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Channel name
            Column(
                modifier = Modifier.width(150.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.category,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Current playing program
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (currentProg != null) {
                    val progress = remember(currentProg) { getProgressPercentage(currentProg) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "[${currentProg.displayStart} - ${currentProg.displayEnd}]",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = currentProg.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (currentProg.description.isNotEmpty()) {
                        Text(
                            text = currentProg.description,
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Progress slider bar percentage
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray
                    )

                } else {
                    Text(
                        text = "Programación en vivo",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sintonice para ver la transmisión en directo",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Next Program Preview on right side
            Column(
                modifier = Modifier.width(200.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (nextProg != null) {
                    Text(
                        text = "Siguiente: ${nextProg.displayStart}",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = nextProg.title,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "Sin próximo evento",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Quick live indicator badge
            Text(
                text = "EN VIVO",
                color = Color.Red,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// --- CALCULATION AND LOOKUP UTILITIES ---

fun getProgramsForChannel(channel: Channel, epgMap: Map<String, List<EpgProgram>>): List<EpgProgram> {
    val cleanName = channel.name.lowercase().trim()
    
    // Fuzzy matching sequence
    val exactMatch = epgMap[cleanName]
    if (exactMatch != null) return exactMatch

    // Regex check if similar sequence exists inside
    for ((epgKey, progs) in epgMap) {
        if (cleanName.contains(epgKey) || epgKey.contains(cleanName)) {
            return progs
        }
    }
    
    // Strip FHD, HD, | tags to improve matchmaking index
    val strippedName = cleanName
        .replace("fhd", "")
        .replace("hd", "")
        .replace("|", "")
        .replace("ar", "")
        .replace("argentina", "")
        .replace("noticias", "")
        .trim()
        
    if (strippedName.length > 2) {
        for ((epgKey, progs) in epgMap) {
            if (epgKey.contains(strippedName) || strippedName.contains(epgKey)) {
                return progs
            }
        }
    }

    return emptyList()
}

fun getCurrentAndNextProgram(programs: List<EpgProgram>): Pair<EpgProgram?, EpgProgram?> {
    val now = Date()
    var current: EpgProgram? = null
    var next: EpgProgram? = null
    
    for (i in programs.indices) {
        val prog = programs[i]
        val start = prog.startDateRaw ?: continue
        val stop = prog.stopDateRaw ?: continue
        
        if (now.time >= start.time && now.time <= stop.time) {
            current = prog
            if (i + 1 < programs.size) {
                next = programs[i + 1]
            }
            break
        }
    }
    
    // Fallback: search closest program
    if (current == null && programs.isNotEmpty()) {
        for (prog in programs) {
            val start = prog.startDateRaw ?: continue
            if (start.time > now.time) {
                next = prog
                break
            }
        }
    }
    
    return Pair(current, next)
}

fun getProgressPercentage(prog: EpgProgram): Float {
    val start = prog.startDateRaw?.time ?: return 0f
    val stop = prog.stopDateRaw?.time ?: return 0f
    val now = Date().time
    
    if (now <= start) return 0f
    if (now >= stop) return 1f
    
    val total = stop - start
    val elapsed = now - start
    return elapsed.toFloat() / total.toFloat()
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.TvPlayerScreen
import com.example.ui.screens.LoginScreen
import com.example.db.UserAccount
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.IPTVUiState
import com.example.ui.viewmodel.IPTVViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: IPTVViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val context = this
                val optimizedImageLoader = remember {
                    ImageLoader.Builder(context)
                        .memoryCache {
                            MemoryCache.Builder(context)
                                .maxSizePercent(0.20)
                                .build()
                        }
                        .diskCache {
                            DiskCache.Builder()
                                .directory(context.cacheDir.resolve("logo_cache"))
                                .maxSizeBytes(20 * 1024 * 1024)
                                .build()
                        }
                        .crossfade(true)
                        .build()
                }

                CompositionLocalProvider(LocalImageLoader provides optimizedImageLoader) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF0C0D12)
                    ) {
                        val state by viewModel.uiState.collectAsState()
                        val epgState by viewModel.epgState.collectAsState()
                        val playingChannel by viewModel.playingChannel.collectAsState()

                        val activeChannel = playingChannel
                        
                        var currentUser by remember { mutableStateOf<UserAccount?>(null) }

                        if (currentUser == null) {
                            LoginScreen(
                                onLoginSuccess = { user ->
                                    currentUser = user
                                }
                            )
                        } else if (currentUser?.isAdmin == true) {
                            AdminDashboardScreen(
                                onLogout = {
                                    val prefs = context.getSharedPreferences("iptv_session_prefs", android.content.Context.MODE_PRIVATE)
                                    prefs.edit().clear().apply()
                                    currentUser = null
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                HomeScreen(
                                    uiState = state,
                                    epgState = epgState,
                                    currentUser = currentUser,
                                    onChannelSelected = { channel -> viewModel.selectChannel(channel) },
                                    onReloadData = { viewModel.loadData(forceRefresh = true) },
                                    onLogout = {
                                        val prefs = context.getSharedPreferences("iptv_session_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().clear().apply()
                                        currentUser = null
                                    }
                                )

                                if (activeChannel != null) {
                                    val allChannels = (state as? IPTVUiState.Success)?.allChannels ?: emptyList()
                                    TvPlayerScreen(
                                        channel = activeChannel,
                                        allChannels = allChannels,
                                        epgState = epgState,
                                        onChannelSelected = { viewModel.selectChannel(it) },
                                        onClosePlayer = { viewModel.selectChannel(null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

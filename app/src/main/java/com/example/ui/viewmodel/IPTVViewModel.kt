package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.db.DatabaseService
import com.example.model.Channel
import com.example.parser.M3uParser
import com.example.parser.EpgParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Date

data class ChannelCategory(
    val name: String,
    val channels: List<Channel>
)

data class EpgProgram(
    val title: String,
    val description: String,
    val startTime: String,
    val stopTime: String,
    val displayStart: String,
    val displayEnd: String,
    val startDateRaw: Date?,
    val stopDateRaw: Date?
)

sealed interface IPTVUiState {
    data class Loading(val progress: Float, val message: String) : IPTVUiState
    data class Success(
        val categories: List<ChannelCategory>,
        val allChannels: List<Channel>,
        val recommendedChannels: List<Channel>,
        val op2Channels: List<Channel>,
        val eventChannels: List<Channel>
    ) : IPTVUiState
    data class Error(val message: String) : IPTVUiState
}

sealed interface EpgUiState {
    object Loading : EpgUiState
    data class Success(
        val channelPrograms: Map<String, List<EpgProgram>> // keys: channel names & channel ids lowercased
    ) : EpgUiState
    data class Error(val message: String) : EpgUiState
}

class IPTVViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<IPTVUiState>(IPTVUiState.Loading(0.0f, "Preparando sistema..."))
    val uiState: StateFlow<IPTVUiState> = _uiState.asStateFlow()

    private val _epgState = MutableStateFlow<EpgUiState>(EpgUiState.Loading)
    val epgState: StateFlow<EpgUiState> = _epgState.asStateFlow()

    private val _playingChannel = MutableStateFlow<Channel?>(null)
    val playingChannel: StateFlow<Channel?> = _playingChannel.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Required fixed URLs requested by user
    private val m3uSourceUrl = "https://raw.githubusercontent.com/Zzprog-arg/uwu.m3u/fd5dba6c9f6d8cfcccf345aa5c22b71071bee47f/lista2.m3u"
    private val epgSourceUrl = "https://www.open-epg.com/files/argentina3.xml"

    init {
        loadData(forceRefresh = false)
    }

    /**
     * Entry point to load M3U and EPG TV Guide. Runs automatically on startup.
     */
    fun loadData(forceRefresh: Boolean = false) {
        loadPlaylist(forceRefresh)
        loadEpg()
    }

    /**
     * Downloads and parses modern remote IPTV playlist automatically
     */
    fun loadPlaylist(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val playlistPrefs = context.getSharedPreferences("iptv_playlist_prefs", android.content.Context.MODE_PRIVATE)
            val cachedContent = playlistPrefs.getString("cached_m3u_content", "") ?: ""

            if (!forceRefresh && cachedContent.isNotBlank()) {
                _uiState.value = IPTVUiState.Loading(0.20f, "Cargando lista guardada...")
                try {
                    _uiState.value = IPTVUiState.Loading(0.40f, "Parseando canales...")
                    var parsedClean = M3uParser.parse(cachedContent)
                    if (parsedClean.isEmpty()) {
                        parsedClean = M3uParser.parse(M3uParser.DEFAULT_M3U_LIST)
                    }
                    _uiState.value = IPTVUiState.Loading(0.80f, "Preparando categorías...")
                    processParsedChannels(parsedClean)
                    return@launch
                } catch (e: Exception) {
                    Log.e("IPTV_VIEWMODEL", "Error parsing cached content, falling back to download", e)
                }
            }

            _uiState.value = IPTVUiState.Loading(0.05f, "Descargando lista...")
            try {
                // Sync dynamic settings from database
                DatabaseService.loadSettings(context)
                val dynamicM3uUrl = DatabaseService.getM3uUrl(context)
                Log.d("IPTV_VIEWMODEL", "Fetching dynamic M3U playlist from: $dynamicM3uUrl")

                val request = Request.Builder().url(dynamicM3uUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Respuesta fallida del servidor HTTP: ${response.code}")
                    
                    _uiState.value = IPTVUiState.Loading(0.20f, "Cargando canales...")
                    val bodyString = response.body?.string() ?: throw IOException("Lista M3U vacía")
                    
                    // Save downloaded content to local cache
                    playlistPrefs.edit().putString("cached_m3u_content", bodyString).apply()

                    _uiState.value = IPTVUiState.Loading(0.40f, "Parseando canales...")
                    var parsedClean = M3uParser.parse(bodyString)
                    
                    if (parsedClean.isEmpty()) {
                        // Fallback to offline defaults
                        parsedClean = M3uParser.parse(M3uParser.DEFAULT_M3U_LIST)
                    }

                    _uiState.value = IPTVUiState.Loading(0.60f, "Optimizando logos...")
                    
                    // Preload logos background asynchronously so UI scrolling of lists is perfectly fluid
                    val imageLoader = context.imageLoader
                    val channelsWithLogos = parsedClean.filter { it.logoUrl.isNotEmpty() }.take(45)
                    for (ch in channelsWithLogos) {
                        val imgRequest = ImageRequest.Builder(context)
                            .data(ch.logoUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(imgRequest)
                    }

                    _uiState.value = IPTVUiState.Loading(0.75f, "Preparando categorías...")
                    delay(200) // Small cinematic delays to make loading screen visually readable
                    
                    _uiState.value = IPTVUiState.Loading(0.90f, "Preparando eventos...")
                    delay(200)

                    _uiState.value = IPTVUiState.Loading(1.00f, "Listo")
                    delay(300)

                    processParsedChannels(parsedClean)
                }
            } catch (e: Exception) {
                // Return fallback assets if user has internet issues
                try {
                    val fallbackList = M3uParser.parse(M3uParser.DEFAULT_M3U_LIST)
                    processParsedChannels(fallbackList)
                } catch (ex: Exception) {
                    _uiState.value = IPTVUiState.Error("Fallo al conectar: ${e.localizedMessage ?: "Verifique su red"}")
                }
            }
        }
    }

    private fun processParsedChannels(channels: List<Channel>) {
        // Filter out OP2 channels from the main list of channels used for categories
        val nonOp2Channels = channels.filterNot { channel ->
            val gt = channel.groupTitle.trim().lowercase()
            gt.endsWith("op2") || gt.contains("op2")
        }
        val categories = groupAndSortChannels(nonOp2Channels)
        
        // 4. Recommendation constraint: first 5 channels of the loaded M3U list
        val recommended = nonOp2Channels.take(5)

        // 1. OP2 Filter constraint: group-title contains or ends with "OP2" (case-insensitive)
        val op2List = channels.filter { channel ->
            val gt = channel.groupTitle.trim().lowercase()
            gt.endsWith("op2") || gt.contains("op2")
        }

        // 4. Event Filter constraint: group-title starts with "evento" (case-insensitive)
        val eventList = channels.filter { channel ->
            channel.groupTitle.trim().lowercase().startsWith("evento")
        }

        _uiState.value = IPTVUiState.Success(
            categories = categories,
            allChannels = channels,
            recommendedChannels = recommended,
            op2Channels = op2List,
            eventChannels = eventList
        )
    }

    /**
     * Automatically downloads the Open-EPG TV guide feed asynchronously
     */
    fun loadEpg() {
        viewModelScope.launch(Dispatchers.IO) {
            _epgState.value = EpgUiState.Loading
            try {
                val request = Request.Builder().url(epgSourceUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Fallo HTTP al descargar EPG XML: ${response.code}")
                    val bodyStream = response.body?.byteStream() ?: throw IOException("Cuerpo EPG vacío")
                    
                    val parsedEpgMap = EpgParser.parse(bodyStream)
                    _epgState.value = EpgUiState.Success(parsedEpgMap)
                }
            } catch (e: Exception) {
                _epgState.value = EpgUiState.Error("No se pudo descargar la programación de la guía: ${e.localizedMessage ?: "Fallo de conexión"}")
            }
        }
    }

    fun selectChannel(channel: Channel?) {
        _playingChannel.value = channel
    }

    /**
     * Groups parsed flat channels and sorts them matching native TV guides
     */
    private fun groupAndSortChannels(channels: List<Channel>): List<ChannelCategory> {
        val groupedMap = channels.groupBy { it.category }
        val sortedCategories = mutableListOf<ChannelCategory>()
        
        // Sort all parsed categories alphabetically
        val sortedKeys = groupedMap.keys.sorted()
        for (catName in sortedKeys) {
            val list = groupedMap[catName]
            if (list != null && list.isNotEmpty()) {
                sortedCategories.add(ChannelCategory(catName, list))
            }
        }
        return sortedCategories
    }
}

package com.example.karaoke.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaoke.data.KaraokeRepository
import com.example.karaoke.data.prefs.SettingsStore
import com.example.karaoke.data.remote.dto.QueueItem
import com.example.karaoke.data.remote.dto.SongItem
import com.example.karaoke.playback.PlaybackEngine
import com.example.karaoke.playback.PlaybackState
import com.example.karaoke.playback.PrepareProgress
import com.example.karaoke.ui.navigation.DrawerTab
import com.example.karaoke.ui.navigation.QueueAction
import com.example.karaoke.ui.navigation.QueueInteractionMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val connectionOk: Boolean = false,
    val connectionError: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val drawerOpen: Boolean = false,
    val drawerTab: DrawerTab = DrawerTab.Library,
    val overlayVisible: Boolean = true,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val prepareProgress: PrepareProgress? = null,
    val toastMessage: String? = null,
    // Library tab
    val libraryQuery: String = "",
    val librarySongs: List<SongItem> = emptyList(),
    val libraryPage: Int = 1,
    val libraryLoading: Boolean = false,
    val libraryHasMore: Boolean = true,
    // Queue tab
    val queueFocusIndex: Int = 0,
    val queueMode: QueueInteractionMode = QueueInteractionMode.Browse,
    val queueAction: QueueAction = QueueAction.Top,
    // Settings tab
    val settingsUrl: String = "",
    val settingsTesting: Boolean = false,
    val settingsError: String? = null,
)

class PlayerViewModel(
    private val repository: KaraokeRepository,
    private val settings: SettingsStore,
    val playbackEngine: PlaybackEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState(settingsUrl = settings.server))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var sseJob: Job? = null
    private var overlayJob: Job? = null

    init {
        playbackEngine.onPlaybackEnded = { onSongEnded() }
        playbackEngine.onStartedPlaying = { songId ->
            repository.markSinging(songId)
            repository.sendCommand(1, "3")
            refreshQueue()
        }
        viewModelScope.launch {
            playbackEngine.state.collect { state ->
                _uiState.update { it.copy(playbackState = state) }
                if (state is PlaybackState.Playing) showOverlayTemporarily()
            }
        }
        viewModelScope.launch {
            playbackEngine.prepareProgress.collect { progress ->
                _uiState.update { it.copy(prepareProgress = progress) }
            }
        }
        viewModelScope.launch {
            repository.queue.collect { queue ->
                _uiState.update { it.copy(queue = queue) }
            }
        }
        startSession()
    }

    fun startSession() {
        repository.configureFromSettings()
        viewModelScope.launch {
            val probe = repository.probe(settings.server)
            _uiState.update {
                it.copy(
                    connectionOk = probe.isSuccess,
                    connectionError = probe.isFailure,
                    settingsUrl = settings.server,
                )
            }
            if (probe.isSuccess) {
                refreshQueue()
                val queue = repository.queue.value
                if (queue.isNotEmpty()) {
                    playbackEngine.prepareQueueHead(queue, autoPlay = false)
                }
                startSse()
            }
        }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun toggleDrawer(open: Boolean? = null) {
        _uiState.update { state ->
            val newOpen = open ?: !state.drawerOpen
            if (newOpen && state.librarySongs.isEmpty()) {
                loadLibrary(reset = true)
            }
            state.copy(
                drawerOpen = newOpen,
                drawerTab = if (newOpen && !state.drawerOpen) DrawerTab.Library else state.drawerTab,
                queueMode = QueueInteractionMode.Browse,
            )
        }
    }

    fun setDrawerTab(tab: DrawerTab) {
        _uiState.update { it.copy(drawerTab = tab, queueMode = QueueInteractionMode.Browse) }
        if (tab == DrawerTab.Library && _uiState.value.librarySongs.isEmpty()) {
            loadLibrary(reset = true)
        }
    }

    fun togglePlayPause() {
        val wasPlaying = playbackEngine.isPlaying()
        playbackEngine.togglePlayPause()
        if (wasPlaying) {
            viewModelScope.launch { repository.sendCommand(1, "4") }
        } else {
            val queue = _uiState.value.queue
            if (queue.isNotEmpty() && playbackEngine.currentSongId() != queue.first().id) {
                viewModelScope.launch {
                    playbackEngine.prepareQueueHead(queue, autoPlay = true)
                }
            }
        }
        showOverlayTemporarily()
    }

    fun handleKey(keyCode: Int): Boolean {
        val state = _uiState.value
        return when {
            state.drawerOpen -> handleDrawerKey(keyCode, state)
            else -> handlePlayerKey(keyCode, state)
        }
    }

    private fun handlePlayerKey(keyCode: Int, state: PlayerUiState): Boolean = when (keyCode) {
        android.view.KeyEvent.KEYCODE_MENU -> {
            toggleDrawer(true)
            true
        }
        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        android.view.KeyEvent.KEYCODE_ENTER,
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> {
            if (state.queue.isNotEmpty() || playbackEngine.isPlaying()) {
                togglePlayPause()
            } else {
                toggleDrawer(true)
            }
            true
        }
        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
            viewModelScope.launch { skipToNext() }
            true
        }
        android.view.KeyEvent.KEYCODE_BACK,
        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
        -> true // swallow
        else -> false
    }

    private fun handleDrawerKey(keyCode: Int, state: PlayerUiState): Boolean = when (state.drawerTab) {
        DrawerTab.Library -> handleLibraryKey(keyCode, state)
        DrawerTab.Queue -> handleQueueKey(keyCode, state)
        DrawerTab.Settings -> handleSettingsKey(keyCode, state)
    }

    private fun handleLibraryKey(keyCode: Int, state: PlayerUiState): Boolean = when (keyCode) {
        android.view.KeyEvent.KEYCODE_BACK -> {
            toggleDrawer(false)
            true
        }
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
            cycleTab(forward = true)
            true
        }
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
            cycleTab(forward = false)
            true
        }
        else -> false
    }

    private fun handleSettingsKey(keyCode: Int, state: PlayerUiState): Boolean = when (keyCode) {
        android.view.KeyEvent.KEYCODE_BACK -> {
            toggleDrawer(false)
            true
        }
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
            cycleTab(forward = true)
            true
        }
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
            cycleTab(forward = false)
            true
        }
        else -> false
    }

    private fun handleQueueKey(keyCode: Int, state: PlayerUiState): Boolean {
        if (state.queue.isEmpty()) {
            return when (keyCode) {
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                -> {
                    toggleDrawer(false)
                    true
                }
                else -> false
            }
        }
        return when (state.queueMode) {
            QueueInteractionMode.Browse -> when (keyCode) {
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                -> {
                    toggleDrawer(false)
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    _uiState.update {
                        it.copy(queueFocusIndex = (it.queueFocusIndex - 1).coerceAtLeast(0))
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    _uiState.update {
                        it.copy(
                            queueFocusIndex = (it.queueFocusIndex + 1)
                                .coerceAtMost(it.queue.size - 1),
                        )
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    cycleTab(forward = true)
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                -> {
                    val item = state.queue.getOrNull(state.queueFocusIndex) ?: return false
                    if (item.isPlaying()) return true
                    _uiState.update {
                        it.copy(
                            queueMode = QueueInteractionMode.Action,
                            queueAction = QueueAction.Top,
                        )
                    }
                    true
                }
                else -> false
            }
            QueueInteractionMode.Action -> when (keyCode) {
                android.view.KeyEvent.KEYCODE_BACK -> {
                    _uiState.update { it.copy(queueMode = QueueInteractionMode.Browse) }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    _uiState.update { it.copy(queueMode = QueueInteractionMode.Browse) }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    _uiState.update {
                        it.copy(
                            queueAction = if (it.queueAction == QueueAction.Top) {
                                QueueAction.Remove
                            } else {
                                QueueAction.Top
                            },
                        )
                    }
                    true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                -> {
                    val item = state.queue.getOrNull(state.queueFocusIndex) ?: return false
                    viewModelScope.launch {
                        when (state.queueAction) {
                            QueueAction.Top -> repository.setTop(item.id)
                            QueueAction.Remove -> repository.remove(item.id)
                        }
                        refreshQueue()
                        _uiState.update {
                            it.copy(queueMode = QueueInteractionMode.Browse)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun cycleTab(forward: Boolean) {
        _uiState.update { state ->
            val tabs = DrawerTab.entries
            val idx = tabs.indexOf(state.drawerTab)
            val next = if (forward) (idx + 1) % tabs.size else (idx - 1 + tabs.size) % tabs.size
            state.copy(drawerTab = tabs[next], queueMode = QueueInteractionMode.Browse)
        }
    }

    fun updateLibraryQuery(q: String) {
        _uiState.update { it.copy(libraryQuery = q) }
        loadLibrary(reset = true)
    }

    fun loadLibrary(reset: Boolean) {
        viewModelScope.launch {
            val page = if (reset) 1 else _uiState.value.libraryPage + 1
            if (!reset && !_uiState.value.libraryHasMore) return@launch
            _uiState.update { it.copy(libraryLoading = true) }
            val result = repository.loadLibrary(page, _uiState.value.libraryQuery)
            result.fold(
                onSuccess = { songs ->
                    _uiState.update { state ->
                        state.copy(
                            librarySongs = if (reset) songs else state.librarySongs + songs,
                            libraryPage = page,
                            libraryHasMore = songs.isNotEmpty(),
                            libraryLoading = false,
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(libraryLoading = false) }
                    showToast(it.message ?: "加载曲库失败")
                },
            )
        }
    }

    fun enqueueSong(songId: Int) {
        viewModelScope.launch {
            repository.enqueue(songId).fold(
                onSuccess = { showToast("已点歌") },
                onFailure = { showToast(it.message ?: "点歌失败") },
            )
        }
    }

    fun updateSettingsUrl(url: String) {
        _uiState.update { it.copy(settingsUrl = url, settingsError = null) }
    }

    fun testConnection() {
        val url = _uiState.value.settingsUrl.trim().trimEnd('/')
        viewModelScope.launch {
            _uiState.update { it.copy(settingsTesting = true, settingsError = null) }
            val result = repository.probe(url)
            _uiState.update {
                it.copy(
                    settingsTesting = false,
                    settingsError = result.exceptionOrNull()?.message,
                    connectionOk = result.isSuccess,
                    connectionError = result.isFailure,
                )
            }
        }
    }

    fun saveAndReconnect() {
        val url = _uiState.value.settingsUrl.trim().trimEnd('/')
        viewModelScope.launch {
            _uiState.update { it.copy(settingsTesting = true) }
            val result = repository.probe(url)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        settingsTesting = false,
                        settingsError = result.exceptionOrNull()?.message,
                    )
                }
                return@launch
            }
            repository.reconnect(url)
            playbackEngine.cancelPrepareWait()
            refreshQueue()
            val queue = repository.queue.value
            playbackEngine.prepareQueueHead(queue, autoPlay = false)
            startSse()
            _uiState.update {
                it.copy(
                    settingsTesting = false,
                    connectionOk = true,
                    connectionError = false,
                    settingsError = null,
                )
            }
            showToast("已保存并重连")
        }
    }

    fun openSettingsFromError() {
        toggleDrawer(true)
        setDrawerTab(DrawerTab.Settings)
    }

    private suspend fun refreshQueue() {
        repository.refreshQueue()
    }

    private suspend fun onSongEnded() {
        val queue = _uiState.value.queue
        if (queue.isNotEmpty()) {
            repository.markFinished(queue.first().id)
        }
        refreshQueue()
        val newQueue = repository.queue.value
        if (newQueue.isEmpty()) {
            _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
        } else {
            playbackEngine.prepareQueueHead(newQueue, autoPlay = true, forceReload = true)
        }
    }

    private suspend fun skipToNext() {
        val queue = _uiState.value.queue
        if (queue.isNotEmpty()) repository.markFinished(queue.first().id)
        refreshQueue()
        val newQueue = repository.queue.value
        playbackEngine.prepareQueueHead(newQueue, autoPlay = true, forceReload = true)
    }

    private fun startSse() {
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            repository.sseEvents().collect { msg ->
                handleSse(msg.code, msg.data)
            }
        }
    }

    private fun handleSse(code: Int, data: String) {
        when (code) {
            1 -> when (data) {
                "0" -> playbackEngine.pause()
                "1" -> {
                    if (!playbackEngine.isPlaying()) playbackEngine.play()
                }
                "5" -> viewModelScope.launch {
                    val queue = repository.refreshQueue().getOrDefault(emptyList())
                    playbackEngine.prepareQueueHead(queue, autoPlay = true)
                }
            }
            2 -> playbackEngine.reSing()
            3 -> viewModelScope.launch { skipToNext() }
            4 -> playbackEngine.setVocalsMuted(data != "0")
            5 -> {
                val vol = data.toFloatOrNull() ?: return
                settings.vocalsVolume = vol
                playbackEngine.updateVolumes(vol, settings.accompanimentVolume)
            }
            6 -> {
                val vol = data.toFloatOrNull() ?: return
                settings.accompanimentVolume = vol
                playbackEngine.updateVolumes(settings.vocalsVolume, vol)
            }
            7 -> playbackEngine.playSoundEffect(data)
            8 -> viewModelScope.launch { onQueueChanged() }
            9 -> {
                val readyId = data.toIntOrNull() ?: return
                playbackEngine.signalPrepareReady(readyId)
                viewModelScope.launch {
                    val queue = repository.refreshQueue().getOrDefault(repository.queue.value)
                    if (queue.isNotEmpty() && queue.first().id == readyId) {
                        playbackEngine.prepareQueueHead(
                            queue,
                            autoPlay = playbackEngine.pendingAutoPlay || playbackEngine.isPlaying(),
                            forceReload = true,
                        )
                    }
                }
            }
        }
    }

    private suspend fun onQueueChanged() {
        val wasPlaying = playbackEngine.isPlaying()
        val prevHeadId = _uiState.value.queue.firstOrNull()?.id
        refreshQueue()
        val queue = repository.queue.value
        if (queue.isEmpty()) {
            if (!wasPlaying) {
                playbackEngine.prepareQueueHead(emptyList(), autoPlay = false)
            }
            return
        }
        val newHead = queue.first()
        val headChanged = prevHeadId != newHead.id
        if (!wasPlaying && (headChanged || playbackEngine.currentSongId() != newHead.id)) {
            playbackEngine.prepareQueueHead(queue, autoPlay = false, forceReload = headChanged)
        }
    }

    private fun showOverlayTemporarily() {
        _uiState.update { it.copy(overlayVisible = true) }
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            delay(5000)
            if (playbackEngine.isPlaying()) {
                _uiState.update { it.copy(overlayVisible = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnectSse()
        playbackEngine.release()
    }
}

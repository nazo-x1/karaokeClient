package com.example.karaoke.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaoke.data.KaraokeRepository
import com.example.karaoke.data.ServerUrlNormalizer
import com.example.karaoke.data.prefs.SettingsStore
import com.example.karaoke.data.remote.dto.QueueItem
import com.example.karaoke.data.remote.dto.PrepareStatus
import com.example.karaoke.data.remote.dto.SongItem
import com.example.karaoke.playback.PlaybackEngine
import com.example.karaoke.playback.PlaybackState
import com.example.karaoke.playback.PrepareProgress
import com.example.karaoke.ui.UiMessenger
import com.example.karaoke.ui.navigation.DrawerFocusZone
import com.example.karaoke.ui.navigation.DrawerTab
import com.example.karaoke.ui.navigation.LibraryFocus
import com.example.karaoke.ui.navigation.RandomFocus
import com.example.karaoke.ui.navigation.QueueAction
import com.example.karaoke.ui.navigation.QueueInteractionMode
import com.example.karaoke.ui.navigation.SettingsFocus
import com.example.karaoke.ui.components.isPrepareActive
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
    val drawerFocusZone: DrawerFocusZone = DrawerFocusZone.Content,
    val overlayVisible: Boolean = true,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val prepareProgress: PrepareProgress? = null,
    // Library tab
    val libraryQuery: String = "",
    val librarySongs: List<SongItem> = emptyList(),
    val libraryPage: Int = 1,
    val libraryLoading: Boolean = false,
    val libraryHasMore: Boolean = true,
    val libraryFocusIndex: Int = 0,
    // Random tab
    val randomSongs: List<SongItem> = emptyList(),
    val randomLoading: Boolean = false,
    val randomFocusIndex: Int = 0,
    // Prepare tracking (点歌后资源准备)
    val prepareTracks: List<PrepareTrack> = emptyList(),
    // Queue tab
    val queueFocusIndex: Int = 0,
    val queueMode: QueueInteractionMode = QueueInteractionMode.Browse,
    val queueAction: QueueAction = QueueAction.Top,
    // Settings tab
    val settingsUrl: String = "",
    val settingsTesting: Boolean = false,
    val settingsError: String? = null,
    val settingsFocusIndex: Int = 0,
)

data class PrepareTrack(
    val songId: Int,
    val displayName: String,
    val status: PrepareStatus,
)

class PlayerViewModel(
    private val repository: KaraokeRepository,
    private val settings: SettingsStore,
    val playbackEngine: PlaybackEngine,
    private val uiMessenger: UiMessenger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState(settingsUrl = settings.server))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var sseJob: Job? = null
    private var overlayJob: Job? = null
    private var preparePollJob: Job? = null
    private var libraryPool: List<SongItem> = emptyList()
    private var randomSeed: Int = 0

    companion object {
        private const val RANDOM_PICK_COUNT = 12
        private const val LIBRARY_POOL_MAX_PAGES = 5
    }

    init {
        playbackEngine.onPlaybackEnded = {
            viewModelScope.launch {
                runCatching { onSongEnded() }.onFailure {
                    uiMessenger.show(it.message ?: "切歌失败")
                }
            }
        }
        playbackEngine.onStartedPlaying = { songId ->
            viewModelScope.launch {
                runCatching {
                    repository.markSinging(songId)
                    repository.sendCommand(1, "3")
                    refreshQueue()
                }.onFailure {
                    uiMessenger.show(it.message ?: "同步播放状态失败")
                }
            }
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
        startPreparePolling()
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

    fun toggleDrawer(open: Boolean? = null) {
        _uiState.update { state ->
            val newOpen = open ?: !state.drawerOpen
            if (newOpen && state.librarySongs.isEmpty()) {
                loadLibrary(reset = true)
            }
            state.copy(
                drawerOpen = newOpen,
                drawerTab = if (newOpen && !state.drawerOpen) DrawerTab.Library else state.drawerTab,
                drawerFocusZone = DrawerFocusZone.Content,
                libraryFocusIndex = 0,
                randomFocusIndex = 0,
                settingsFocusIndex = 0,
                queueFocusIndex = 0,
                queueMode = QueueInteractionMode.Browse,
            )
        }
    }

    fun setDrawerTab(tab: DrawerTab) {
        _uiState.update {
            it.copy(
                drawerTab = tab,
                queueMode = QueueInteractionMode.Browse,
                drawerFocusZone = DrawerFocusZone.Content,
                libraryFocusIndex = 0,
                randomFocusIndex = 0,
                settingsFocusIndex = 0,
                queueFocusIndex = 0,
            )
        }
        if (tab == DrawerTab.Library && _uiState.value.librarySongs.isEmpty()) {
            loadLibrary(reset = true)
        }
        if (tab == DrawerTab.Random) {
            loadRandomTab()
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

    private fun handleDrawerKey(keyCode: Int, state: PlayerUiState): Boolean {
        if (state.drawerTab == DrawerTab.Queue &&
            state.queueMode == QueueInteractionMode.Action &&
            state.queue.isNotEmpty()
        ) {
            return handleQueueActionKey(keyCode, state)
        }
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_BACK -> {
                toggleDrawer(false)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                cycleTab(forward = false)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cycleTab(forward = true)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                moveDrawerFocusUp(state)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveDrawerFocusDown(state)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            -> {
                activateDrawerFocus(state)
                true
            }
            else -> false
        }
    }

    private fun handleQueueActionKey(keyCode: Int, state: PlayerUiState): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_BACK,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT,
            -> {
                cancelQueueAction()
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
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER,
            -> {
                val item = state.queue.getOrNull(state.queueFocusIndex) ?: return false
                if (item.isPlaying()) return true
                executeQueueAction(state.queueAction, item.id)
                true
            }
            else -> false
        }
    }

    private fun moveDrawerFocusUp(state: PlayerUiState) {
        when (state.drawerFocusZone) {
            DrawerFocusZone.Tabs -> Unit
            DrawerFocusZone.Content -> when (state.drawerTab) {
                DrawerTab.Library -> {
                    if (state.libraryFocusIndex <= 0) {
                        _uiState.update { it.copy(drawerFocusZone = DrawerFocusZone.Tabs) }
                    } else {
                        _uiState.update { it.copy(libraryFocusIndex = state.libraryFocusIndex - 1) }
                    }
                }
                DrawerTab.Random -> {
                    if (state.randomFocusIndex <= 0) {
                        _uiState.update { it.copy(drawerFocusZone = DrawerFocusZone.Tabs) }
                    } else {
                        _uiState.update { it.copy(randomFocusIndex = state.randomFocusIndex - 1) }
                    }
                }
                DrawerTab.Queue -> {
                    if (state.queue.isEmpty()) {
                        _uiState.update { it.copy(drawerFocusZone = DrawerFocusZone.Tabs) }
                    } else if (state.queueFocusIndex <= 0) {
                        _uiState.update { it.copy(drawerFocusZone = DrawerFocusZone.Tabs) }
                    } else {
                        _uiState.update { it.copy(queueFocusIndex = state.queueFocusIndex - 1) }
                    }
                }
                DrawerTab.Settings -> {
                    if (state.settingsFocusIndex <= 0) {
                        _uiState.update { it.copy(drawerFocusZone = DrawerFocusZone.Tabs) }
                    } else {
                        _uiState.update { it.copy(settingsFocusIndex = state.settingsFocusIndex - 1) }
                    }
                }
            }
        }
    }

    private fun moveDrawerFocusDown(state: PlayerUiState) {
        when (state.drawerFocusZone) {
            DrawerFocusZone.Tabs -> _uiState.update { it.copy(drawerFocusZone = DrawerFocusZone.Content) }
            DrawerFocusZone.Content -> when (state.drawerTab) {
                DrawerTab.Library -> {
                    val max = libraryMaxIndex(state)
                    if (state.libraryFocusIndex < max) {
                        _uiState.update { it.copy(libraryFocusIndex = state.libraryFocusIndex + 1) }
                    }
                }
                DrawerTab.Random -> {
                    val max = randomMaxIndex(state)
                    if (state.randomFocusIndex < max) {
                        _uiState.update { it.copy(randomFocusIndex = state.randomFocusIndex + 1) }
                    }
                }
                DrawerTab.Queue -> {
                    if (state.queue.isEmpty()) return
                    val max = state.queue.size - 1
                    if (state.queueFocusIndex < max) {
                        _uiState.update { it.copy(queueFocusIndex = state.queueFocusIndex + 1) }
                    }
                }
                DrawerTab.Settings -> {
                    if (state.settingsFocusIndex < SettingsFocus.SAVE) {
                        _uiState.update { it.copy(settingsFocusIndex = state.settingsFocusIndex + 1) }
                    }
                }
            }
        }
    }

    private fun activateDrawerFocus(state: PlayerUiState) {
        when (state.drawerFocusZone) {
            DrawerFocusZone.Tabs -> _uiState.update { it.copy(drawerFocusZone = DrawerFocusZone.Content) }
            DrawerFocusZone.Content -> when (state.drawerTab) {
                DrawerTab.Library -> when (state.libraryFocusIndex) {
                    LibraryFocus.SEARCH -> Unit
                    else -> {
                        val songCount = state.librarySongs.size
                        if (state.libraryHasMore &&
                            state.libraryFocusIndex == LibraryFocus.loadMore(songCount)
                        ) {
                            loadLibrary(reset = false)
                        } else {
                            val songIndex = state.libraryFocusIndex - 1
                            state.librarySongs.getOrNull(songIndex)?.let { enqueueSong(it.id, it.display_name) }
                        }
                    }
                }
                DrawerTab.Random -> when (state.randomFocusIndex) {
                    RandomFocus.REFRESH -> refreshRandom()
                    else -> {
                        val songIndex = state.randomFocusIndex - 1
                        state.randomSongs.getOrNull(songIndex)?.let { enqueueSong(it.id, it.display_name) }
                    }
                }
                DrawerTab.Queue -> {
                    val item = state.queue.getOrNull(state.queueFocusIndex) ?: return
                    if (item.isPlaying()) return
                    _uiState.update {
                        it.copy(
                            queueMode = QueueInteractionMode.Action,
                            queueAction = QueueAction.Top,
                        )
                    }
                }
                DrawerTab.Settings -> when (state.settingsFocusIndex) {
                    SettingsFocus.TEST -> testConnection()
                    SettingsFocus.SAVE -> saveAndReconnect()
                    else -> Unit
                }
            }
        }
    }

    private fun libraryMaxIndex(state: PlayerUiState): Int {
        val count = LibraryFocus.itemCount(
            songCount = state.librarySongs.size,
            hasMore = state.libraryHasMore && !state.libraryLoading,
        )
        return (count - 1).coerceAtLeast(0)
    }

    private fun randomMaxIndex(state: PlayerUiState): Int {
        val count = RandomFocus.itemCount(state.randomSongs.size)
        return (count - 1).coerceAtLeast(0)
    }

    private fun cycleTab(forward: Boolean) {
        _uiState.update { state ->
            val tabs = DrawerTab.entries
            val idx = tabs.indexOf(state.drawerTab)
            val next = if (forward) (idx + 1) % tabs.size else (idx - 1 + tabs.size) % tabs.size
            state.copy(
                drawerTab = tabs[next],
                queueMode = QueueInteractionMode.Browse,
                drawerFocusZone = DrawerFocusZone.Content,
                libraryFocusIndex = 0,
                randomFocusIndex = 0,
                settingsFocusIndex = 0,
                queueFocusIndex = 0,
            )
        }
        if (_uiState.value.drawerTab == DrawerTab.Library && _uiState.value.librarySongs.isEmpty()) {
            loadLibrary(reset = true)
        }
        if (_uiState.value.drawerTab == DrawerTab.Random) {
            loadRandomTab()
        }
    }

    fun refreshRandom() {
        randomSeed += 1
        recomputeRandomSongs()
    }

    fun loadRandomTab() {
        viewModelScope.launch {
            _uiState.update { it.copy(randomLoading = true) }
            ensureLibraryPool()
            recomputeRandomSongs()
            _uiState.update { it.copy(randomLoading = false) }
        }
    }

    private suspend fun ensureLibraryPool() {
        if (libraryPool.isNotEmpty()) return
        val merged = mutableListOf<SongItem>()
        var page = 1
        repeat(LIBRARY_POOL_MAX_PAGES) {
            val songs = repository.loadLibrary(page, "").getOrElse { emptyList() }
            if (songs.isEmpty()) return@repeat
            merged.addAll(songs)
            page += 1
        }
        libraryPool = merged
    }

    private fun recomputeRandomSongs() {
        val pool = libraryPool.filter { it.can_queue }.shuffled(java.util.Random(randomSeed.toLong()))
        _uiState.update {
            it.copy(
                randomSongs = pool.take(RANDOM_PICK_COUNT),
                randomFocusIndex = it.randomFocusIndex.coerceAtMost(
                    (RandomFocus.itemCount(pool.take(RANDOM_PICK_COUNT).size) - 1).coerceAtLeast(0),
                ),
            )
        }
    }

    fun cancelQueueAction() {
        _uiState.update { it.copy(queueMode = QueueInteractionMode.Browse) }
    }

    /** 触摸：点击播放区域（播放/暂停或打开菜单）。 */
    fun onPlayerTap() {
        if (_uiState.value.drawerOpen) return
        val state = _uiState.value
        if (state.queue.isNotEmpty() || playbackEngine.isPlaying()) {
            togglePlayPause()
        } else {
            toggleDrawer(true)
        }
    }

    /** 触摸：点击已点列表行。 */
    fun onQueueRowTapped(index: Int) {
        val state = _uiState.value
        val item = state.queue.getOrNull(index) ?: return
        when (state.queueMode) {
            QueueInteractionMode.Browse -> {
                _uiState.update { it.copy(queueFocusIndex = index) }
                if (item.isPlaying()) return
                _uiState.update {
                    it.copy(
                        queueMode = QueueInteractionMode.Action,
                        queueAction = QueueAction.Top,
                    )
                }
            }
            QueueInteractionMode.Action -> {
                if (index == state.queueFocusIndex) {
                    cancelQueueAction()
                } else {
                    _uiState.update { it.copy(queueFocusIndex = index) }
                    if (!item.isPlaying()) {
                        _uiState.update {
                            it.copy(queueMode = QueueInteractionMode.Action, queueAction = QueueAction.Top)
                        }
                    } else {
                        cancelQueueAction()
                    }
                }
            }
        }
    }

    /** 触摸：点击置顶/移除按钮。 */
    fun onQueueActionTapped(action: QueueAction) {
        val state = _uiState.value
        if (state.queueMode != QueueInteractionMode.Action) return
        val item = state.queue.getOrNull(state.queueFocusIndex) ?: return
        if (item.isPlaying()) return
        executeQueueAction(action, item.id)
    }

    private fun executeQueueAction(action: QueueAction, songId: Int) {
        viewModelScope.launch {
            when (action) {
                QueueAction.Top -> repository.setTop(songId)
                QueueAction.Remove -> repository.remove(songId)
            }
            refreshQueue()
            _uiState.update { it.copy(queueMode = QueueInteractionMode.Browse) }
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
                        val merged = if (reset) songs else state.librarySongs + songs
                        val next = state.copy(
                            librarySongs = merged,
                            libraryPage = page,
                            libraryHasMore = songs.isNotEmpty(),
                            libraryLoading = false,
                        )
                        next.copy(
                            libraryFocusIndex = next.libraryFocusIndex
                                .coerceAtMost(libraryMaxIndex(next)),
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(libraryLoading = false) }
                    uiMessenger.show(it.message ?: "加载曲库失败")
                },
            )
        }
    }

    fun enqueueSong(songId: Int, displayName: String = "") {
        viewModelScope.launch {
            repository.enqueue(songId).fold(
                onSuccess = { response ->
                    handleEnqueueResponse(songId, displayName, response)
                },
                onFailure = { uiMessenger.show(it.message ?: "点歌失败") },
            )
        }
    }

    private fun handleEnqueueResponse(
        songId: Int,
        displayName: String,
        response: com.example.karaoke.data.remote.dto.EnqueueResponse,
    ) {
        val name = displayName.ifBlank { songNameOrDefault(songId) }
        val prepare = response.prepare
        if (response.needsPrepare && prepare != null) {
            trackPrepare(songId, name, prepare)
            uiMessenger.show(response.message.ifBlank { "已加入准备队列，完成后可点歌" })
            return
        }
        if (prepare != null && isPrepareActive(prepare)) {
            trackPrepare(songId, name, prepare)
        }
        uiMessenger.show(response.message)
        if (response.success) {
            viewModelScope.launch { refreshQueue() }
        }
    }

    private fun songNameOrDefault(songId: Int): String {
        val state = _uiState.value
        return state.librarySongs.find { it.id == songId }?.display_name
            ?: state.randomSongs.find { it.id == songId }?.display_name
            ?: "歌曲 #$songId"
    }

    private fun trackPrepare(songId: Int, displayName: String, initial: PrepareStatus) {
        _uiState.update { state ->
            val tracks = state.prepareTracks
                .filterNot { it.songId == songId }
                .plus(PrepareTrack(songId, displayName, initial))
            state.copy(prepareTracks = tracks)
        }
    }

    private fun refreshPrepareTrack(songId: Int) {
        viewModelScope.launch {
            repository.fetchPrepareStatus(songId).onSuccess { status ->
                if (status == null) return@onSuccess
                _uiState.update { state ->
                    val existing = state.prepareTracks.find { it.songId == songId } ?: return@update state
                    val updated = existing.copy(status = status)
                    val tracks = if (status.ready || status.status in setOf("failed", "not_needed")) {
                        state.prepareTracks.filterNot { it.songId == songId }
                    } else {
                        state.prepareTracks.map { if (it.songId == songId) updated else it }
                    }
                    state.copy(prepareTracks = tracks)
                }
                if (status.ready) {
                    uiMessenger.show("播放资源已就绪，可再次点歌")
                }
            }
        }
    }

    private fun startPreparePolling() {
        preparePollJob?.cancel()
        preparePollJob = viewModelScope.launch {
            while (true) {
                delay(1500)
                val active = _uiState.value.prepareTracks.filter { isPrepareActive(it.status) }
                if (active.isEmpty()) continue
                active.forEach { track -> refreshPrepareTrack(track.songId) }
            }
        }
    }

    fun updateSettingsUrl(url: String) {
        _uiState.update { it.copy(settingsUrl = url, settingsError = null) }
    }

    fun testConnection() {
        val normalized = ServerUrlNormalizer.normalize(_uiState.value.settingsUrl)
        if (normalized.isNullOrBlank()) {
            _uiState.update { it.copy(settingsError = "地址格式无效") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(settingsTesting = true, settingsError = null) }
            val result = repository.probe(normalized)
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
        val normalized = ServerUrlNormalizer.normalize(_uiState.value.settingsUrl)
        if (normalized.isNullOrBlank()) {
            _uiState.update { it.copy(settingsError = "地址格式无效") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(settingsTesting = true) }
            val result = repository.probe(normalized)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        settingsTesting = false,
                        settingsError = result.exceptionOrNull()?.message,
                    )
                }
                return@launch
            }
            repository.reconnect(normalized)
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
            uiMessenger.show("已保存并重连")
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
                refreshPrepareTrack(readyId)
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

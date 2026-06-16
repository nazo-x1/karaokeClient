package com.example.karaoke.data

import com.example.karaoke.data.prefs.SettingsStore
import com.example.karaoke.data.remote.KaraokeApi
import com.example.karaoke.data.remote.SseClient
import com.example.karaoke.data.remote.dto.EnqueueResponse
import com.example.karaoke.data.remote.dto.PrepareStatus
import com.example.karaoke.data.remote.dto.QueueItem
import com.example.karaoke.data.remote.dto.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class KaraokeRepository(
    private val api: KaraokeApi,
    private val sseClient: SseClient,
    private val settings: SettingsStore,
) {
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    val serverUrl: String get() = settings.server

    fun configureFromSettings() {
        if (settings.hasServer) {
            api.setBaseUrl(settings.server)
        }
    }

    fun saveServer(url: String) {
        val normalized = ServerUrlNormalizer.normalize(url) ?: url.trim().trimEnd('/')
        settings.server = normalized
        api.setBaseUrl(normalized)
    }

    suspend fun probe(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = ServerUrlNormalizer.normalize(url)
            ?: return@withContext Result.failure(Exception("地址格式无效，示例：http://192.168.1.20:15233"))
        api.setBaseUrl(normalized)
        api.probeConnection()
    }

    fun reconnect(url: String) {
        sseClient.disconnect()
        saveServer(url)
    }

    suspend fun refreshQueue(): Result<List<QueueItem>> = withContext(Dispatchers.IO) {
        val result = api.fetchQueue()
        result.onSuccess { _queue.value = it }
        result
    }

    suspend fun loadLibrary(page: Int, q: String): Result<List<SongItem>> =
        withContext(Dispatchers.IO) { api.fetchLibrary(page, q) }

    suspend fun enqueue(songId: Int): Result<EnqueueResponse> =
        withContext(Dispatchers.IO) { api.enqueue(songId) }

    suspend fun setTop(songId: Int): Result<Unit> =
        withContext(Dispatchers.IO) { api.setTop(songId) }

    suspend fun remove(songId: Int): Result<Unit> =
        withContext(Dispatchers.IO) { api.removeFromQueue(songId) }

    suspend fun fetchPlaybackProfile(songId: Int): Result<PlaybackData> =
        withContext(Dispatchers.IO) { api.fetchPlaybackProfile(songId) }

    suspend fun fetchPrepareStatus(songId: Int): Result<PrepareStatus?> =
        withContext(Dispatchers.IO) { api.fetchPrepareStatus(songId) }

    suspend fun ensureReady(songId: Int): Result<PrepareStatus?> =
        withContext(Dispatchers.IO) { api.postEnsureReady(songId) }

    suspend fun markSinging(songId: Int): Result<Unit> =
        withContext(Dispatchers.IO) { api.markSinging(songId) }

    suspend fun markFinished(songId: Int): Result<Unit> =
        withContext(Dispatchers.IO) { api.markFinished(songId) }

    suspend fun skipUnready(songId: Int): Result<Unit> =
        withContext(Dispatchers.IO) { api.skipUnready(songId) }

    suspend fun sendCommand(code: Int, data: String): Result<Unit> =
        withContext(Dispatchers.IO) { api.sendCommand(code, data) }

    fun streamUrl(songId: Int, kind: String): String = api.streamUrl(songId, kind)

    fun sseEvents() = sseClient.connect()

    fun disconnectSse() = sseClient.disconnect()
}

package com.example.karaoke.data

import com.example.karaoke.data.prefs.SettingsStore
import com.example.karaoke.data.remote.KaraokeApi
import com.example.karaoke.data.remote.SseClient
import com.example.karaoke.data.remote.dto.PlaybackData
import com.example.karaoke.data.remote.dto.PrepareStatus
import com.example.karaoke.data.remote.dto.QueueItem
import com.example.karaoke.data.remote.dto.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        settings.server = url
        api.setBaseUrl(url)
    }

    suspend fun probe(url: String): Result<Unit> {
        api.setBaseUrl(url)
        return api.probeConnection()
    }

    fun reconnect(url: String) {
        sseClient.disconnect()
        saveServer(url)
    }

    suspend fun refreshQueue(): Result<List<QueueItem>> {
        val result = api.fetchQueue()
        result.onSuccess { _queue.value = it }
        return result
    }

    suspend fun loadLibrary(page: Int, q: String): Result<List<SongItem>> =
        api.fetchLibrary(page, q)

    suspend fun enqueue(songId: Int): Result<Unit> = api.enqueue(songId)

    suspend fun setTop(songId: Int): Result<Unit> = api.setTop(songId)

    suspend fun remove(songId: Int): Result<Unit> = api.removeFromQueue(songId)

    suspend fun fetchPlaybackProfile(songId: Int): Result<PlaybackData> =
        api.fetchPlaybackProfile(songId)

    suspend fun fetchPrepareStatus(songId: Int): Result<PrepareStatus?> =
        api.fetchPrepareStatus(songId)

    suspend fun ensureReady(songId: Int): Result<PrepareStatus?> =
        api.postEnsureReady(songId)

    suspend fun markSinging(songId: Int): Result<Unit> = api.markSinging(songId)

    suspend fun markFinished(songId: Int): Result<Unit> = api.markFinished(songId)

    suspend fun skipUnready(songId: Int): Result<Unit> = api.skipUnready(songId)

    suspend fun sendCommand(code: Int, data: String): Result<Unit> =
        api.sendCommand(code, data)

    fun streamUrl(songId: Int, kind: String): String = api.streamUrl(songId, kind)

    fun sseEvents() = sseClient.connect()

    fun disconnectSse() = sseClient.disconnect()
}

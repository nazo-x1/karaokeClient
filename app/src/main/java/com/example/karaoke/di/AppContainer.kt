package com.example.karaoke.di

import android.content.Context
import com.example.karaoke.data.KaraokeRepository
import com.example.karaoke.data.prefs.SettingsStore
import com.example.karaoke.data.remote.KaraokeApi
import com.example.karaoke.data.remote.SseClient
import com.example.karaoke.playback.PlaybackEngine
import com.google.gson.Gson

class AppContainer(context: Context) {
    val settings = SettingsStore(context.applicationContext)
    private val gson = Gson()
    private val client = KaraokeApi.createDefaultClient()
    val api = KaraokeApi(client, gson)
    val sseClient = SseClient(client, gson, api)
    val repository = KaraokeRepository(api, sseClient, settings)

    lateinit var playbackEngine: PlaybackEngine
        private set

    fun initPlayback(onToast: (String) -> Unit) {
        playbackEngine = PlaybackEngine(
            context.applicationContext,
            repository,
            onToast,
        )
        playbackEngine.init(settings.vocalsVolume, settings.accompanimentVolume)
        repository.configureFromSettings()
    }
}

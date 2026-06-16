package com.example.karaoke.di

import android.content.Context
import com.example.karaoke.data.KaraokeRepository
import com.example.karaoke.data.prefs.SettingsStore
import com.example.karaoke.data.remote.KaraokeApi
import com.example.karaoke.data.remote.SseClient
import com.example.karaoke.playback.PlaybackEngine
import com.example.karaoke.ui.UiMessenger
import com.google.gson.Gson

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val settings = SettingsStore(appContext)
    val uiMessenger = UiMessenger()
    private val gson = Gson()
    private val client = KaraokeApi.createDefaultClient()
    val api = KaraokeApi(client, gson)
    val sseClient = SseClient(client, gson, api)
    val repository = KaraokeRepository(api, sseClient, settings)

    lateinit var playbackEngine: PlaybackEngine
        private set

    fun initPlayback() {
        playbackEngine = PlaybackEngine(
            appContext,
            repository,
            uiMessenger::show,
        )
        playbackEngine.init(settings.vocalsVolume, settings.accompanimentVolume)
        repository.configureFromSettings()
    }
}

package com.example.karaoke.playback

import com.example.karaoke.data.remote.dto.PrepareStatus
import com.example.karaoke.data.remote.dto.QueueItem

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Preparing(
        val songId: Int,
        val displayName: String,
        val status: PrepareStatus?,
    ) : PlaybackState

    data class Ready(val songId: Int, val displayName: String) : PlaybackState
    data class Playing(val songId: Int, val displayName: String) : PlaybackState
    data class Error(val message: String) : PlaybackState
}

data class PrepareProgress(
    val songId: Int,
    val displayName: String,
    val status: PrepareStatus?,
)

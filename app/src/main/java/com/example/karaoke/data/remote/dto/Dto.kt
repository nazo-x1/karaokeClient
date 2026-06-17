package com.example.karaoke.data.remote.dto

data class ApiEnvelope(
    val code: Int,
    val msg: String? = null,
)

data class ApiResult<T>(
    val code: Int,
    val msg: String? = null,
    val data: T? = null,
    val total: Int? = null,
    val page: Int? = null,
    val totalPage: Int? = null,
)

data class QueueItem(
    val id: Int,
    val name: String,
    val state: String,
    val times: Int = 0,
    val is_top: Int = 0,
    val playback_mode: String? = null,
) {
    fun isPlaying(): Boolean = state == "playing"
    fun isPending(): Boolean = state == "pending"
}

data class SongItem(
    val id: Int,
    val display_name: String,
    val source_origin: String? = null,
    val playback_mode: String? = null,
    val can_queue: Boolean = true,
    val prepare: PrepareStatus? = null,
)

data class PrepareStatus(
    val song_id: Int? = null,
    val ready: Boolean = false,
    val status: String? = null,
    val message: String? = null,
    val progress: Double? = null,
    val error: String? = null,
    val phase: String? = null,
)

data class EnqueueResult(
    val playback_mode: String? = null,
    val prepare: PrepareStatus? = null,
)

data class EnqueueResponse(
    val success: Boolean,
    val message: String,
    val prepare: PrepareStatus? = null,
    val needsPrepare: Boolean = false,
)

data class PlaybackData(
    val id: Int,
    val display_name: String,
    val mode: String,
    val can_queue: Boolean,
    val ready_to_stream: Boolean = true,
    val prepare: PrepareStatus? = null,
)

data class SseMessage(
    val code: Int,
    val data: String,
)

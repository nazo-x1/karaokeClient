package com.example.karaoke.data.remote

import com.example.karaoke.data.remote.dto.ApiResult
import com.example.karaoke.data.remote.dto.PlaybackData
import com.example.karaoke.data.remote.dto.PrepareStatus
import com.example.karaoke.data.remote.dto.QueueItem
import com.example.karaoke.data.remote.dto.SongItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class KaraokeApi(
    private val client: OkHttpClient,
    private val gson: Gson,
) {
    var baseUrl: String = ""
        private set

    fun setBaseUrl(url: String) {
        baseUrl = url.trim().trimEnd('/')
    }

    private fun apiV1(path: String): String = "$baseUrl/api/v1$path"

    private inline fun <reified T> parse(body: String): ApiResult<T>? =
        gson.fromJson(body, object : TypeToken<ApiResult<T>>() {}.type)

    private fun <T> execute(request: Request, parser: (String) -> T?): Result<T> = try {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: return Result.failure(Exception("空响应"))
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}"))
            }
            val parsed = parser(bodyStr) ?: return Result.failure(Exception("解析失败"))
            Result.success(parsed)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private inline fun <reified T> apiGet(path: String): Result<T> {
        val request = Request.Builder().url(apiV1(path)).build()
        return execute(request) { body ->
            val res = parse<T>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "请求失败")
            res.data
        }
    }

    fun probeConnection(): Result<Unit> {
        val request = Request.Builder()
            .url(apiV1("/library/songs?page=1&q="))
            .build()
        return execute(request) { body ->
            val res = parse<List<SongItem>>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "连接失败")
            Unit
        }
    }

    fun fetchQueue(): Result<List<QueueItem>> = apiGet("/queue")

    fun fetchLibrary(page: Int, q: String): Result<List<SongItem>> {
        val request = Request.Builder()
            .url(apiV1("/library/songs?page=$page&q=${q.encode()}"))
            .build()
        return execute(request) { body ->
            val res = parse<List<SongItem>>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "加载曲库失败")
            res.data ?: emptyList()
        }
    }

    fun enqueue(songId: Int): Result<Unit> = postEmpty("/queue/songs/$songId")

    fun setTop(songId: Int): Result<Unit> = postEmpty("/queue/songs/$songId/top")

    fun removeFromQueue(songId: Int): Result<Unit> {
        val request = Request.Builder()
            .url(apiV1("/queue/songs/$songId"))
            .delete()
            .build()
        return execute(request) { body ->
            val res = gson.fromJson(body, ApiResult::class.java)
            if (res.code != 0) throw Exception(res.msg ?: "移除失败")
            Unit
        }
    }

    fun fetchPlaybackProfile(songId: Int): Result<PlaybackData> = apiGet("/playback/songs/$songId")

    fun fetchPrepareStatus(songId: Int): Result<PrepareStatus?> {
        val request = Request.Builder()
            .url(apiV1("/playback/songs/$songId/prepare-status"))
            .build()
        return execute(request) { body ->
            val res = parse<PrepareStatus>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "获取准备状态失败")
            res.data
        }
    }

    fun postEnsureReady(songId: Int): Result<PrepareStatus?> {
        val request = Request.Builder()
            .url(apiV1("/playback/songs/$songId/ensure-ready"))
            .post(byteArrayOf().toRequestBody(null))
            .build()
        return execute(request) { body ->
            val res = parse<PrepareStatus>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "触发准备失败")
            res.data
        }
    }

    fun markSinging(songId: Int): Result<Unit> = postEmpty("/playback/session/singing/$songId")

    fun markFinished(songId: Int): Result<Unit> = postEmpty("/playback/session/finished/$songId")

    fun skipUnready(songId: Int): Result<Unit> = postEmpty("/playback/session/skip-unready/$songId")

    fun sendCommand(code: Int, data: String): Result<Unit> {
        val payload = gson.toJson(mapOf("code" to code, "data" to data))
        val request = Request.Builder()
            .url(apiV1("/events/command"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return execute(request) { Unit }
    }

    fun streamUrl(songId: Int, kind: String): String =
        apiV1("/playback/stream/$songId/$kind")

    fun eventsUrl(): String = apiV1("/events")

    private fun postEmpty(path: String): Result<Unit> {
        val request = Request.Builder()
            .url(apiV1(path))
            .post(byteArrayOf().toRequestBody(null))
            .build()
        return execute(request) { body ->
            val res = gson.fromJson(body, ApiResult::class.java)
            if (res.code != 0) throw Exception(res.msg ?: "请求失败")
            Unit
        }
    }

    private fun String.encode(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        fun createDefaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

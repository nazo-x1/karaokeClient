package com.example.karaoke.data.remote

import com.example.karaoke.data.remote.dto.ApiEnvelope
import com.example.karaoke.data.remote.dto.ApiResult
import com.example.karaoke.data.remote.dto.LibraryPage
import com.example.karaoke.data.remote.dto.EnqueueResponse
import com.example.karaoke.data.remote.dto.EnqueueResult
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
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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

    private fun parseEnvelope(body: String): ApiEnvelope? =
        gson.fromJson(body, ApiEnvelope::class.java)

    private fun <T> execute(request: Request, parser: (String) -> T?): Result<T> = try {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: return Result.failure(Exception("服务器无响应"))
            if (!response.isSuccessful) {
                return Result.failure(
                    Exception(mapHttpError(response.code, bodyStr)),
                )
            }
            val parsed = parser(bodyStr) ?: return Result.failure(Exception("无法解析服务器响应"))
            Result.success(parsed)
        }
    } catch (e: Exception) {
        Result.failure(Exception(mapNetworkError(e)))
    }

    fun probeConnection(): Result<Unit> {
        val request = Request.Builder()
            .url(apiV1("/library/songs?page=1&q="))
            .get()
            .build()
        return execute(request) { body ->
            val trimmed = body.trimStart()
            if (trimmed.startsWith("<")) {
                throw Exception("返回的是网页而非 API，请只填服务器根地址（如 http://IP:端口）")
            }
            val res = parseEnvelope(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg?.ifBlank { null } ?: "连接失败")
            Unit
        }
    }

    fun fetchQueue(): Result<List<QueueItem>> = apiGet("/queue")

    fun fetchLibrary(page: Int, q: String, pageSize: Int = LIBRARY_PAGE_SIZE): Result<LibraryPage> {
        val request = Request.Builder()
            .url(apiV1("/library/songs?page=$page&q=${q.encode()}&page_size=$pageSize"))
            .build()
        return execute(request) { body ->
            val res = parse<List<SongItem>>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "加载曲库失败")
            val songs = res.data ?: emptyList()
            val totalPage = res.totalPage ?: 0
            val currentPage = res.page ?: page
            val hasMore = when {
                totalPage > 0 -> currentPage < totalPage
                else -> songs.size >= pageSize
            }
            LibraryPage(songs, hasMore)
        }
    }

    fun enqueue(songId: Int): Result<EnqueueResponse> {
        val request = Request.Builder()
            .url(apiV1("/queue/songs/$songId"))
            .post(byteArrayOf().toRequestBody(null))
            .build()
        return execute(request) { body ->
            val res = parse<EnqueueResult>(body) ?: return@execute null
            val prepare = res.data?.prepare
            when {
                res.code == 0 -> EnqueueResponse(
                    success = true,
                    message = userMessage(res.msg, "已点歌"),
                    prepare = prepare,
                )
                prepare != null && !prepare.ready -> EnqueueResponse(
                    success = false,
                    message = userMessage(res.msg, "播放资源准备中，请稍候…"),
                    prepare = prepare.copy(song_id = prepare.song_id ?: songId),
                    needsPrepare = true,
                )
                else -> throw Exception(res.msg ?: "点歌失败")
            }
        }
    }

    fun setTop(songId: Int): Result<Unit> = postEmpty("/queue/songs/$songId/top")

    fun removeFromQueue(songId: Int): Result<Unit> {
        val request = Request.Builder()
            .url(apiV1("/queue/songs/$songId"))
            .delete()
            .build()
        return execute(request) { body ->
            val res = gson.fromJson(body, ApiEnvelope::class.java)
            if (res.code != 0) throw Exception(res.msg ?: "移除失败")
            Unit
        }
    }

    fun fetchPlaybackProfile(songId: Int): Result<PlaybackData> = apiGet("/playback/songs/$songId")

    fun fetchPrepareStatus(songId: Int): Result<PrepareStatus?> {
        val request = Request.Builder()
            .url(apiV1("/playback/songs/$songId/prepare"))
            .build()
        return execute(request) { body ->
            val res = parse<PrepareStatus>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "获取准备状态失败")
            res.data
        }
    }

    fun schedulePrepare(songId: Int): Result<PrepareStatus?> {
        val request = Request.Builder()
            .url(apiV1("/playback/songs/$songId/prepare"))
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

    private inline fun <reified T> apiGet(path: String): Result<T> {
        val request = Request.Builder().url(apiV1(path)).build()
        return execute(request) { body ->
            val res = parse<T>(body) ?: return@execute null
            if (res.code != 0) throw Exception(res.msg ?: "请求失败")
            res.data ?: throw Exception("服务器未返回数据")
        }
    }

    private fun postEmpty(path: String): Result<Unit> {
        val request = Request.Builder()
            .url(apiV1(path))
            .post(byteArrayOf().toRequestBody(null))
            .build()
        return execute(request) { body ->
            val res = gson.fromJson(body, ApiEnvelope::class.java)
            if (res.code != 0) throw Exception(res.msg ?: "请求失败")
            Unit
        }
    }

    private fun String.encode(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun userMessage(msg: String?, fallback: String): String {
        val text = msg?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() && !it.equals("Success!", ignoreCase = true) } ?: fallback
    }

    private fun mapHttpError(code: Int, body: String): String = when (code) {
        404 -> "未找到 API（/api/v1），请确认后端版本与地址是否正确"
        401, 403 -> "服务器拒绝访问（HTTP $code）"
        else -> {
            val envelope = runCatching { parseEnvelope(body) }.getOrNull()
            envelope?.msg?.takeIf { it.isNotBlank() }
                ?: "HTTP $code"
        }
    }

    private fun mapNetworkError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "无法解析主机，请检查地址是否正确"
            e is ConnectException -> "无法连接服务器，请确认 IP、端口与网络"
            e is SocketTimeoutException -> "连接超时，请确认服务器可达"
            msg.contains("NetworkOnMainThread", ignoreCase = true) ->
                "网络请求异常，请更新客户端版本"
            msg.contains("Cleartext", ignoreCase = true) ->
                "明文 HTTP 被系统拦截，请使用 http:// 开头地址"
            msg.isNotBlank() -> msg
            else -> "连接失败"
        }
    }

    companion object {
        const val LIBRARY_PAGE_SIZE = 40

        fun createDefaultClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

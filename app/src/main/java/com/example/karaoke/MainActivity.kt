package com.example.karaoke

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var videoPlayer: ExoPlayer
    private lateinit var vocalsPlayer: ExoPlayer
    private lateinit var accPlayer: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var tvPlayingText: TextView

    private lateinit var soundPool: SoundPool
    private val soundEffectMap = mutableMapOf<String, Int>()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var sseEventSource: EventSource? = null

    private var serverIp = ""
    private var vocalsVolume = 1.0f
    private var accVolume = 1.0f

    private var singsList = mutableListOf<Song>()
    private var playbackMode = MODE_ENHANCED
    private var currentSongId = -1

    private var isVideoReady = false
    private var isVocalsReady = false
    private var isAccReady = false
    private var pendingAutoPlay = false

    private val syncHandler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            if (playbackMode == MODE_ENHANCED &&
                videoPlayer.isPlaying && vocalsPlayer.isPlaying
            ) {
                val videoPos = videoPlayer.currentPosition
                val audioPos = vocalsPlayer.currentPosition
                val diff = videoPos - audioPos

                when {
                    diff > 80 -> {
                        vocalsPlayer.setPlaybackSpeed(1.03f)
                        accPlayer.setPlaybackSpeed(1.03f)
                    }
                    diff < -80 -> {
                        vocalsPlayer.setPlaybackSpeed(0.97f)
                        accPlayer.setPlaybackSpeed(0.97f)
                    }
                    else -> {
                        if (abs(diff) < 30) {
                            vocalsPlayer.setPlaybackSpeed(1.0f)
                            accPlayer.setPlaybackSpeed(1.0f)
                        }
                    }
                }

                if (abs(diff) > 1000) {
                    vocalsPlayer.seekTo(videoPos)
                    accPlayer.seekTo(videoPos)
                }
            }
            syncHandler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)
        tvPlayingText = findViewById(R.id.tv_playing_text)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        initSoundEffects()

        val prefs = getSharedPreferences("KTV_PREFS", Context.MODE_PRIVATE)
        serverIp = prefs.getString("server", "http://192.168.1.20:15233")
            ?: "http://192.168.1.20:15233"
        vocalsVolume = prefs.getFloat("vocalsVolume", 1.0f)
        accVolume = prefs.getFloat("accompanimentVolume", 1.0f)

        showIpConfigDialog()
    }

    private fun apiV1(path: String): String = "$serverIp/api/v1$path"

    private fun streamUrl(songId: Int, kind: String): String {
        return apiV1("/playback/stream/$songId/$kind")
    }

    private fun fetchPrepareStatus(songId: Int): PrepareStatus? {
        val request = Request.Builder().url(apiV1("/playback/songs/$songId/prepare-status")).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val res = gson.fromJson(bodyStr, PrepareStatusResponse::class.java)
                if (res.code == 0) res.data else null
            }
        } catch (e: Exception) {
            Log.e("KTV_DEBUG", "fetchPrepareStatus 出错", e)
            null
        }
    }

    private fun postEnsureReady(songId: Int): PrepareStatus? {
        val request = Request.Builder()
            .url(apiV1("/playback/songs/$songId/ensure-ready"))
            .post(byteArrayOf().toRequestBody(null))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val res = gson.fromJson(bodyStr, PrepareStatusResponse::class.java)
                if (res.code == 0) res.data else res.data
            }
        } catch (e: Exception) {
            Log.e("KTV_DEBUG", "postEnsureReady 出错", e)
            null
        }
    }

    private fun waitUntilPlaybackReady(songId: Int, timeoutMs: Long = 3_600_000L): Boolean {
        var prep = postEnsureReady(songId) ?: return false
        if (prep.ready) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (prep.ready) return true
            if (prep.status == "failed") {
                showToast(prep.error ?: "播放资源准备失败")
                return false
            }
            try {
                Thread.sleep(1500)
            } catch (_: InterruptedException) {
                return false
            }
            prep = fetchPrepareStatus(songId) ?: prep
        }
        showToast("等待播放资源超时")
        return false
    }

    private fun fetchPlaybackProfile(songId: Int): PlaybackData? {
        val request = Request.Builder().url(apiV1("/playback/songs/$songId")).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val res = gson.fromJson(bodyStr, PlaybackResponse::class.java)
                if (res.code == 0) res.data else null
            }
        } catch (e: Exception) {
            Log.e("KTV_DEBUG", "fetchPlaybackProfile 出错", e)
            null
        }
    }

    private fun initSoundEffects() {
        val audioAttributes = PlatformAudioAttributes.Builder()
            .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
            .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()
        try {
            soundEffectMap["daxiao"] = soundPool.load(this, R.raw.daxiao, 1)
            soundEffectMap["guzhang"] = soundPool.load(this, R.raw.guzhang, 1)
            soundEffectMap["huanhu"] = soundPool.load(this, R.raw.huanhu, 1)
            soundEffectMap["xixu"] = soundPool.load(this, R.raw.xixu, 1)
        } catch (e: Exception) {
            Log.e("KTV_DEBUG", "加载音效失败: ${e.message}")
        }
    }

    private fun playUserInterruption(flag: String) {
        soundEffectMap[flag]?.let { soundId ->
            soundPool.play(soundId, 0.9f, 0.9f, 1, 0, 1.0f)
        }
    }

    private fun showIpConfigDialog() {
        val editText = EditText(this).apply {
            setText(serverIp)
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        AlertDialog.Builder(this)
            .setTitle("设置服务器 IP")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("保存并进入") { _, _ ->
                serverIp = editText.text.toString().trim()
                if (serverIp.endsWith("/")) {
                    serverIp = serverIp.substring(0, serverIp.length - 1)
                }
                getSharedPreferences("KTV_PREFS", Context.MODE_PRIVATE)
                    .edit().putString("server", serverIp).apply()

                initPlayers()
                startSSE()
                Thread { prepareCurrentSong(autoPlay = false) }.start()
            }.show()
    }

    private fun getSingListPure() {
        val request = Request.Builder().url(apiV1("/queue")).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val res = gson.fromJson(bodyStr, ApiResponse::class.java)
                    if (res.code == 0) {
                        singsList.clear()
                        singsList.addAll(res.data)
                    } else {
                        showToast(res.msg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KTV_DEBUG", "getSingListPure 出错", e)
        }
    }

    private fun showTipsPure() {
        runOnUiThread {
            var playinText = "暂未开始播放"
            if (singsList.isNotEmpty()) {
                if (singsList[0].isPlaying()) {
                    playinText = "当前播放：" + singsList[0].name
                    playinText += if (singsList.size > 1) {
                        "，下一首：" + singsList[1].name
                    } else {
                        "，暂无下一首歌曲"
                    }
                } else {
                    playinText += "，下一首：" + singsList[0].name
                }
            } else {
                playinText += "，暂无下一首歌曲"
            }
            tvPlayingText.text = playinText
        }
    }

    private fun setSingedPure() {
        if (singsList.isEmpty()) return
        val request = Request.Builder()
            .url(apiV1("/playback/session/finished/${singsList[0].id}"))
            .post(byteArrayOf().toRequestBody(null))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setSingingPure() {
        if (singsList.isEmpty()) return
        val request = Request.Builder()
            .url(apiV1("/playback/session/singing/${singsList[0].id}"))
            .post(byteArrayOf().toRequestBody(null))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendMessagePure(code: Int, data: String) {
        val payload = gson.toJson(mapOf("code" to code, "data" to data))
        val request = Request.Builder()
            .url(apiV1("/events/command"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetReadyFlags() {
        isVideoReady = false
        isVocalsReady = false
        isAccReady = false
    }

    private fun isPreparedForSong(songId: Int): Boolean {
        if (songId != currentSongId) return false
        if (!isVideoReady) return false
        if (playbackMode == MODE_PLAIN) return true
        return isVocalsReady && isAccReady
    }

    /** 加载队首歌曲；同一首歌已缓冲时仅恢复播放，不重新拉流。 */
    private fun prepareCurrentSong(autoPlay: Boolean, forceReload: Boolean = false) {
        getSingListPure()
        if (singsList.isEmpty()) return

        val song = singsList[0]
        if (!forceReload && isPreparedForSong(song.id)) {
            runOnUiThread {
                pendingAutoPlay = autoPlay
                if (autoPlay) startPlayback()
            }
            showTipsPure()
            return
        }

        val profile = fetchPlaybackProfile(song.id)
        if (profile == null) {
            showToast("无法获取播放配置")
            return
        }
        if (!profile.can_queue) {
            showToast("当前歌曲不可播放")
            return
        }
        if (!profile.ready_to_stream) {
            showToast("正在准备播放资源…")
            if (!waitUntilPlaybackReady(song.id)) {
                return
            }
        }

        playbackMode = if (profile.mode == MODE_ENHANCED) MODE_ENHANCED else MODE_PLAIN
        currentSongId = song.id
        val videoUrl = streamUrl(song.id, "video")

        runOnUiThread {
            pendingAutoPlay = autoPlay
            resetReadyFlags()

            videoPlayer.pause()
            vocalsPlayer.pause()
            accPlayer.pause()

            if (playbackMode == MODE_PLAIN) {
                videoPlayer.volume = 1.0f
                isVocalsReady = true
                isAccReady = true
                videoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
                videoPlayer.prepare()
            } else {
                videoPlayer.volume = 0f
                videoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
                vocalsPlayer.setMediaItem(MediaItem.fromUri(streamUrl(song.id, "vocals")))
                accPlayer.setMediaItem(MediaItem.fromUri(streamUrl(song.id, "accompaniment")))
                videoPlayer.prepare()
                vocalsPlayer.prepare()
                accPlayer.prepare()
            }
        }
        showTipsPure()
    }

    private fun startPlayback() {
        val audioReady = if (playbackMode == MODE_PLAIN) {
            true
        } else {
            isVocalsReady && isAccReady
        }
        if (!isVideoReady || !audioReady) {
            pendingAutoPlay = true
            return
        }

        pendingAutoPlay = false
        val pos = videoPlayer.currentPosition
        if (playbackMode == MODE_ENHANCED) {
            vocalsPlayer.seekTo(pos)
            accPlayer.seekTo(pos)
            vocalsPlayer.playWhenReady = true
            accPlayer.playWhenReady = true
        }
        videoPlayer.playWhenReady = true
    }

    private fun pausePlayback() {
        pendingAutoPlay = false
        videoPlayer.playWhenReady = false
        if (playbackMode == MODE_ENHANCED) {
            vocalsPlayer.playWhenReady = false
            accPlayer.playWhenReady = false
        }
    }

    private fun togglePlayPause() {
        if (videoPlayer.isPlaying) {
            pausePlayback()
            Thread { sendMessagePure(1, "4") }.start()
        } else if (isPreparedForSong(currentSongId)) {
            startPlayback()
        } else if (singsList.isNotEmpty()) {
            prepareCurrentSong(autoPlay = true, forceReload = false)
        }
    }

    private fun firstPlayPure() {
        if (singsList.isEmpty()) {
            getSingListPure()
        }
        if (singsList.isEmpty()) return
        prepareCurrentSong(autoPlay = true, forceReload = false)
    }

    private fun nextSongPure() {
        if (singsList.isNotEmpty()) {
            setSingedPure()
        }
        getSingListPure()
        if (singsList.isEmpty()) {
            runOnUiThread {
                currentSongId = -1
                tvPlayingText.text = "当前没有待播放的歌曲，快去点歌吧 ~"
                videoPlayer.stop()
                vocalsPlayer.stop()
                accPlayer.stop()
                resetReadyFlags()
            }
            return
        }
        prepareCurrentSong(autoPlay = true, forceReload = true)
    }

    private fun reSingPure() {
        runOnUiThread {
            videoPlayer.seekTo(0)
            if (playbackMode == MODE_ENHANCED) {
                vocalsPlayer.seekTo(0)
                accPlayer.seekTo(0)
            }
            startPlayback()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayers() {
        val mediaAudio = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val tvLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .build()

        videoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(tvLoadControl)
            .build()
            .apply {
                volume = 0f
                setAudioAttributes(mediaAudio, true)
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
        vocalsPlayer = ExoPlayer.Builder(this).build().apply {
            volume = vocalsVolume
            setAudioAttributes(mediaAudio, true)
        }
        accPlayer = ExoPlayer.Builder(this).build().apply {
            volume = accVolume
            setAudioAttributes(mediaAudio, true)
        }

        playerView.player = videoPlayer
        playerView.setKeepContentOnPlayerReset(true)

        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("KTV_DEBUG", "播放错误: ${error.errorCodeName}", error)
                showToast("播放失败: ${error.errorCodeName}")
            }
        }

        videoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        isVideoReady = true
                        if (pendingAutoPlay) startPlayback()
                    }
                    Player.STATE_ENDED -> {
                        isVideoReady = false
                        if (playbackMode == MODE_ENHANCED) {
                            isVocalsReady = false
                            isAccReady = false
                        }
                        Thread { nextSongPure() }.start()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (playbackMode == MODE_ENHANCED) {
                        if (!vocalsPlayer.isPlaying) vocalsPlayer.playWhenReady = true
                        if (!accPlayer.isPlaying) accPlayer.playWhenReady = true
                    }
                    Thread {
                        setSingingPure()
                        sendMessagePure(1, "3")
                        getSingListPure()
                        showTipsPure()
                    }.start()
                } else if (videoPlayer.playbackState != Player.STATE_ENDED) {
                    if (playbackMode == MODE_ENHANCED) {
                        vocalsPlayer.playWhenReady = false
                        accPlayer.playWhenReady = false
                    }
                }
            }
        })
        videoPlayer.addListener(errorListener)

        vocalsPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isVocalsReady = true
                    if (pendingAutoPlay) startPlayback()
                }
            }
        })
        vocalsPlayer.addListener(errorListener)

        accPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isAccReady = true
                    if (pendingAutoPlay) startPlayback()
                }
            }
        })
        accPlayer.addListener(errorListener)

        syncHandler.post(syncRunnable)
    }

    private fun startSSE() {
        val request = Request.Builder().url(apiV1("/events")).build()
        sseEventSource = EventSources.createFactory(client).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    try {
                        val msg = gson.fromJson(data, SseMessage::class.java)
                        handleSseMessage(msg)
                    } catch (e: Exception) {
                        Log.e("SSE", "JSON 解析失败", e)
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    Log.e("SSE", "SSE 连接断开", t)
                }
            }
        )
    }

    private fun handleSseMessage(msg: SseMessage) {
        when (msg.code) {
            1 -> {
                when (msg.data) {
                    "0" -> runOnUiThread { pausePlayback() }
                    "1" -> runOnUiThread {
                        if (videoPlayer.isPlaying) return@runOnUiThread
                        if (isPreparedForSong(currentSongId)) {
                            startPlayback()
                        } else {
                            Thread { prepareCurrentSong(autoPlay = true) }.start()
                        }
                    }
                    "5" -> Thread { firstPlayPure() }.start()
                }
            }
            2 -> reSingPure()
            3 -> Thread { nextSongPure() }.start()
            4 -> {
                if (playbackMode != MODE_ENHANCED) return
                runOnUiThread {
                    if (msg.data == "0") vocalsPlayer.volume = vocalsVolume
                    if (msg.data == "1") vocalsPlayer.volume = 0f
                }
            }
            5 -> {
                if (playbackMode != MODE_ENHANCED) return
                vocalsVolume = msg.data.toFloat()
                runOnUiThread { vocalsPlayer.volume = vocalsVolume }
                getSharedPreferences("KTV_PREFS", Context.MODE_PRIVATE)
                    .edit().putFloat("vocalsVolume", vocalsVolume).apply()
            }
            6 -> {
                if (playbackMode != MODE_ENHANCED) return
                accVolume = msg.data.toFloat()
                runOnUiThread { accPlayer.volume = accVolume }
                getSharedPreferences("KTV_PREFS", Context.MODE_PRIVATE)
                    .edit().putFloat("accompanimentVolume", accVolume).apply()
            }
            7 -> runOnUiThread { playUserInterruption(msg.data) }
            8 -> {
                Thread {
                    getSingListPure()
                    showTipsPure()
                }.start()
            }
            9 -> {
                Thread {
                    if (singsList.isNotEmpty() && singsList[0].id.toString() == msg.data) {
                        prepareCurrentSong(autoPlay = pendingAutoPlay || videoPlayer.isPlaying)
                    }
                }.start()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                runOnUiThread { togglePlayPause() }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                Thread { nextSongPure() }.start()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showToast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        sseEventSource?.cancel()
        syncHandler.removeCallbacks(syncRunnable)
        videoPlayer.release()
        vocalsPlayer.release()
        accPlayer.release()
        soundPool.release()
    }

    companion object {
        private const val MODE_PLAIN = "plain"
        private const val MODE_ENHANCED = "enhanced"
    }
}

data class ApiResponse(val code: Int, val msg: String, val data: List<Song>)
data class Song(val id: Int, val name: String, val state: String) {
    fun isPlaying(): Boolean = state == "playing"
    fun isPending(): Boolean = state == "pending"
}
data class SseMessage(val code: Int, val data: String)
data class PlaybackResponse(val code: Int, val msg: String, val data: PlaybackData?)
data class PlaybackData(
    val id: Int,
    val display_name: String,
    val mode: String,
    val can_queue: Boolean,
    val ready_to_stream: Boolean = true,
    val playback_source: String? = null,
)
data class PrepareStatusResponse(val code: Int, val msg: String, val data: PrepareStatus?)
data class PrepareStatus(
    val song_id: Int,
    val status: String,
    val ready: Boolean,
    val error: String? = null,
)

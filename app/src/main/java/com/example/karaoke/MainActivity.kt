package com.example.karaoke

import android.content.Context
import android.media.AudioAttributes
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.gson.Gson
import okhttp3.*
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
        .build()
    private val gson = Gson()
    private var sseEventSource: EventSource? = null

    private var serverIp = ""
    private var vocalsVolume = 1.0f
    private var accVolume = 1.0f

    private var singsList = mutableListOf<Song>()
    private var playbackMode = MODE_ENHANCED

    private var isVideoReady = false
    private var isVocalsReady = false
    private var isAccReady = false
    private var isPlayIntent = false

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

    private fun streamUrl(songId: Int, kind: String): String {
        return "$serverIp/song/stream/$songId/$kind"
    }

    private fun fetchPlaybackProfile(songId: Int): PlaybackData? {
        val request = Request.Builder().url("$serverIp/song/$songId/playback").build()
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
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
                Thread { loadSingPure(flag = false) }.start()
            }.show()
    }

    private fun getSingListPure() {
        val request = Request.Builder().url("$serverIp/song/singHistory/pendingAll").build()
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
                if (singsList[0].is_sing == -1) {
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
        val request = Request.Builder().url("$serverIp/song/setSinged/${singsList[0].id}").build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setSingingPure() {
        if (singsList.isEmpty()) return
        val request = Request.Builder().url("$serverIp/song/setSinging/${singsList[0].id}").build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendMessagePure(code: Int, data: String) {
        val request = Request.Builder().url("$serverIp/song/send/event?code=$code&data=$data").build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSingPure(flag: Boolean) {
        getSingListPure()
        if (singsList.isEmpty()) return

        val song = singsList[0]
        val profile = fetchPlaybackProfile(song.id)
        if (profile == null) {
            showToast("无法获取播放配置")
            return
        }
        if (!profile.can_queue) {
            showToast("当前歌曲不可播放")
            return
        }

        playbackMode = if (profile.mode == MODE_PLAIN) MODE_PLAIN else MODE_ENHANCED
        val videoUrl = streamUrl(song.id, "video")

        runOnUiThread {
            isVideoReady = false
            isVocalsReady = false
            isAccReady = false
            isPlayIntent = flag

            videoPlayer.stop()
            vocalsPlayer.stop()
            accPlayer.stop()

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

    private fun tryPlayPure() {
        val audioReady = if (playbackMode == MODE_PLAIN) {
            true
        } else {
            isVocalsReady && isAccReady
        }
        if (isVideoReady && audioReady) {
            isPlayIntent = false
            if (playbackMode == MODE_ENHANCED) {
                val pos = videoPlayer.currentPosition
                vocalsPlayer.seekTo(pos)
                accPlayer.seekTo(pos)
                vocalsPlayer.play()
                accPlayer.play()
            }
            videoPlayer.play()
        }
    }

    private fun firstPlayPure() {
        if (singsList.isNotEmpty()) {
            loadSingPure(flag = true)
        }
    }

    private fun nextSongPure() {
        if (singsList.isNotEmpty()) {
            setSingedPure()
        }
        getSingListPure()
        if (singsList.isEmpty()) {
            runOnUiThread {
                tvPlayingText.text = "当前没有待播放的歌曲，快去点歌吧 ~"
                videoPlayer.stop()
                vocalsPlayer.stop()
                accPlayer.stop()
            }
            return
        }
        loadSingPure(flag = true)
    }

    private fun reSingPure() {
        runOnUiThread {
            videoPlayer.seekTo(0)
            videoPlayer.play()
            if (playbackMode == MODE_ENHANCED) {
                vocalsPlayer.seekTo(0)
                accPlayer.seekTo(0)
                vocalsPlayer.play()
                accPlayer.play()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayers() {
        val tvLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10000, 30000, 1500, 2000)
            .build()
        videoPlayer = ExoPlayer.Builder(this).setLoadControl(tvLoadControl).build().apply { volume = 0f }
        vocalsPlayer = ExoPlayer.Builder(this).build().apply { volume = vocalsVolume }
        accPlayer = ExoPlayer.Builder(this).build().apply { volume = accVolume }

        playerView.player = videoPlayer

        videoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isVideoReady = true
                    if (isPlayIntent) tryPlayPure()
                } else if (state == Player.STATE_ENDED) {
                    isVideoReady = false
                    if (playbackMode == MODE_ENHANCED) {
                        isVocalsReady = false
                        isAccReady = false
                    }
                    Thread { nextSongPure() }.start()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (playbackMode == MODE_ENHANCED) {
                        if (!vocalsPlayer.isPlaying) vocalsPlayer.play()
                        if (!accPlayer.isPlaying) accPlayer.play()
                    }
                    Thread {
                        setSingingPure()
                        sendMessagePure(1, "3")
                        getSingListPure()
                        showTipsPure()
                    }.start()
                } else {
                    if (videoPlayer.playbackState != Player.STATE_ENDED) {
                        if (playbackMode == MODE_ENHANCED) {
                            if (vocalsPlayer.isPlaying) vocalsPlayer.pause()
                            if (accPlayer.isPlaying) accPlayer.pause()
                        }
                        Thread { sendMessagePure(1, "4") }.start()
                    }
                }
            }
        })

        vocalsPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isVocalsReady = true
                    if (isPlayIntent) tryPlayPure()
                }
            }
        })

        accPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isAccReady = true
                    if (isPlayIntent) tryPlayPure()
                }
            }
        })

        syncHandler.post(syncRunnable)
    }

    private fun startSSE() {
        val request = Request.Builder().url("$serverIp/song/events").build()
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
                if (msg.data == "0") runOnUiThread {
                    if (videoPlayer.isPlaying) {
                        videoPlayer.pause()
                    } else {
                        Thread { sendMessagePure(1, "4") }.start()
                    }
                }
                if (msg.data == "1") {
                    runOnUiThread {
                        isPlayIntent = true
                        if (!isVideoReady) {
                            Thread { loadSingPure(flag = true) }.start()
                        } else {
                            tryPlayPure()
                        }
                    }
                }
                if (msg.data == "5") {
                    Thread { firstPlayPure() }.start()
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
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (videoPlayer.isPlaying) {
                    videoPlayer.pause()
                } else {
                    isPlayIntent = true
                    if (!isVideoReady) {
                        Thread { loadSingPure(flag = true) }.start()
                    } else {
                        tryPlayPure()
                    }
                }
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
data class Song(val id: Int, val name: String, val is_sing: Int)
data class SseMessage(val code: Int, val data: String)
data class PlaybackResponse(val code: Int, val msg: String, val data: PlaybackData?)
data class PlaybackData(
    val id: Int,
    val display_name: String,
    val mode: String,
    val can_queue: Boolean,
    val playback_source: String? = null,
)

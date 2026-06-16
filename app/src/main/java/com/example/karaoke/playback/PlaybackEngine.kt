package com.example.karaoke.playback

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.karaoke.R
import com.example.karaoke.data.KaraokeRepository
import com.example.karaoke.data.remote.dto.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlaybackEngine(
    private val context: Context,
    private val repository: KaraokeRepository,
    private val onToast: (String) -> Unit,
) {
    lateinit var videoPlayer: ExoPlayer
        private set
    private lateinit var vocalsPlayer: ExoPlayer
    private lateinit var accPlayer: ExoPlayer

    private var soundPool: SoundPool? = null
    private val soundEffectMap = mutableMapOf<String, Int>()

    private var playbackMode = MODE_ENHANCED
    private var currentSongId = -1
    private var vocalsVolume = 1.0f
    private var accVolume = 1.0f

    private var isVideoReady = false
    private var isVocalsReady = false
    private var isAccReady = false
    var pendingAutoPlay = false
        private set
    var prepareWaitSongId = -1
        private set

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var onPlaybackEnded: (suspend () -> Unit)? = null
    var onStartedPlaying: (suspend (Int) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            if (playbackMode == MODE_ENHANCED &&
                ::videoPlayer.isInitialized &&
                videoPlayer.isPlaying && vocalsPlayer.isPlaying
            ) {
                val diff = videoPlayer.currentPosition - vocalsPlayer.currentPosition
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
                    val pos = videoPlayer.currentPosition
                    vocalsPlayer.seekTo(pos)
                    accPlayer.seekTo(pos)
                }
            }
            mainHandler.postDelayed(this, 250)
        }
    }

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _prepareProgress = MutableStateFlow<PrepareProgress?>(null)
    val prepareProgress: StateFlow<PrepareProgress?> = _prepareProgress.asStateFlow()

    fun init(vocalsVol: Float, accVol: Float) {
        vocalsVolume = vocalsVol
        accVolume = accVol
        initSoundEffects()
        initPlayers()
    }

    fun release() {
        prepareWaitSongId = -1
        mainHandler.removeCallbacks(syncRunnable)
        if (::videoPlayer.isInitialized) videoPlayer.release()
        if (::vocalsPlayer.isInitialized) vocalsPlayer.release()
        if (::accPlayer.isInitialized) accPlayer.release()
        soundPool?.release()
        soundPool = null
    }

    fun isPlaying(): Boolean = ::videoPlayer.isInitialized && videoPlayer.isPlaying

    fun currentSongId(): Int = currentSongId

    fun togglePlayPause() {
        if (!::videoPlayer.isInitialized) return
        if (videoPlayer.isPlaying) pause() else play()
    }

    fun play() = startPlayback()

    fun pause() = pausePlayback()

    fun reSing() {
        if (!::videoPlayer.isInitialized) return
        videoPlayer.seekTo(0)
        if (playbackMode == MODE_ENHANCED) {
            vocalsPlayer.seekTo(0)
            accPlayer.seekTo(0)
        }
        startPlayback()
    }

    fun playSoundEffect(flag: String) = playUserInterruption(flag)

    fun updateVolumes(vocals: Float, acc: Float) {
        vocalsVolume = vocals
        accVolume = acc
        if (::vocalsPlayer.isInitialized) vocalsPlayer.volume = vocals
        if (::accPlayer.isInitialized) accPlayer.volume = acc
    }

    fun setVocalsMuted(muted: Boolean) {
        if (::vocalsPlayer.isInitialized) {
            vocalsPlayer.volume = if (muted) 0f else vocalsVolume
        }
    }

    fun getVocalsVolume(): Float = vocalsVolume
    fun getAccVolume(): Float = accVolume

    suspend fun prepareQueueHead(
        queue: List<QueueItem>,
        autoPlay: Boolean,
        forceReload: Boolean = false,
    ) {
        if (queue.isEmpty()) {
            withContext(Dispatchers.Main) {
                currentSongId = -1
                stopAll()
                _state.value = PlaybackState.Idle
            }
            return
        }
        val song = queue.first()
        if (!forceReload && isPreparedForSong(song.id)) {
            withContext(Dispatchers.Main) {
                pendingAutoPlay = autoPlay
                if (autoPlay) startPlayback()
                updatePlayingState(song.id, song.name)
            }
            return
        }

        val profile = withContext(Dispatchers.IO) {
            repository.fetchPlaybackProfile(song.id).getOrNull()
        }
        if (profile == null) {
            onToast("无法获取播放配置")
            return
        }
        if (!profile.can_queue) {
            onToast("无法播放该歌曲")
            withContext(Dispatchers.IO) { repository.skipUnready(song.id) }
            prepareQueueHead(repository.refreshQueue().getOrDefault(emptyList()), autoPlay, true)
            return
        }

        var activeProfile = profile
        if (!activeProfile.ready_to_stream) {
            if (!waitUntilStreamReady(song.id, song.name)) {
                withContext(Dispatchers.IO) { repository.skipUnready(song.id) }
                prepareQueueHead(repository.refreshQueue().getOrDefault(emptyList()), autoPlay, true)
                return
            }
            activeProfile = withContext(Dispatchers.IO) {
                repository.fetchPlaybackProfile(song.id).getOrNull()
            } ?: activeProfile
            if (!activeProfile.ready_to_stream) {
                onToast("播放资源仍未就绪")
                withContext(Dispatchers.IO) { repository.skipUnready(song.id) }
                prepareQueueHead(repository.refreshQueue().getOrDefault(emptyList()), autoPlay, true)
                return
            }
        }

        playbackMode = if (activeProfile.mode == MODE_ENHANCED) MODE_ENHANCED else MODE_PLAIN
        currentSongId = song.id
        val videoUrl = repository.streamUrl(song.id, "video")

        withContext(Dispatchers.Main) {
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
                vocalsPlayer.setMediaItem(MediaItem.fromUri(repository.streamUrl(song.id, "vocals")))
                accPlayer.setMediaItem(MediaItem.fromUri(repository.streamUrl(song.id, "accompaniment")))
                videoPlayer.prepare()
                vocalsPlayer.prepare()
                accPlayer.prepare()
            }
            _state.value = PlaybackState.Ready(song.id, song.name)
        }
    }

    fun cancelPrepareWait() {
        prepareWaitSongId = -1
        _prepareProgress.value = null
    }

    fun signalPrepareReady(songId: Int) {
        if (prepareWaitSongId == songId) prepareWaitSongId = -1
    }

    private suspend fun waitUntilStreamReady(songId: Int, displayName: String): Boolean {
        prepareWaitSongId = songId
        withContext(Dispatchers.IO) { repository.ensureReady(songId) }
        val deadline = System.currentTimeMillis() + 3_600_000L
        while (System.currentTimeMillis() < deadline) {
            if (prepareWaitSongId != songId) return false
            val status = withContext(Dispatchers.IO) {
                repository.fetchPrepareStatus(songId).getOrNull()
            }
            if (status?.ready == true) {
                prepareWaitSongId = -1
                _prepareProgress.value = null
                return true
            }
            if (status?.status == "failed") {
                prepareWaitSongId = -1
                _prepareProgress.value = null
                onToast(status.error ?: "播放资源准备失败")
                return false
            }
            _prepareProgress.value = PrepareProgress(songId, displayName, status)
            _state.value = PlaybackState.Preparing(songId, displayName, status)
            delay(1500)
        }
        prepareWaitSongId = -1
        _prepareProgress.value = null
        onToast("等待播放资源超时")
        return false
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
            soundPool?.let { pool ->
                soundEffectMap["daxiao"] = pool.load(context, R.raw.daxiao, 1)
                soundEffectMap["guzhang"] = pool.load(context, R.raw.guzhang, 1)
                soundEffectMap["huanhu"] = pool.load(context, R.raw.huanhu, 1)
                soundEffectMap["xixu"] = pool.load(context, R.raw.xixu, 1)
            }
        } catch (e: Exception) {
            Log.e("KTV", "加载音效失败: ${e.message}")
        }
    }

    private fun playUserInterruption(flag: String) {
        soundEffectMap[flag]?.let { soundId ->
            soundPool?.play(soundId, 0.9f, 0.9f, 1, 0, 1.0f)
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

    private fun startPlayback() {
        if (!::videoPlayer.isInitialized) return
        val audioReady = playbackMode == MODE_PLAIN || (isVocalsReady && isAccReady)
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
        if (!::videoPlayer.isInitialized) return
        pendingAutoPlay = false
        videoPlayer.playWhenReady = false
        if (playbackMode == MODE_ENHANCED) {
            vocalsPlayer.playWhenReady = false
            accPlayer.playWhenReady = false
        }
    }

    private fun stopAll() {
        if (!::videoPlayer.isInitialized) return
        videoPlayer.stop()
        vocalsPlayer.stop()
        accPlayer.stop()
        resetReadyFlags()
    }

    private fun updatePlayingState(songId: Int, name: String) {
        _state.value = if (isPlaying()) {
            PlaybackState.Playing(songId, name)
        } else {
            PlaybackState.Ready(songId, name)
        }
    }

    private fun initPlayers() {
        val mediaAudio = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val tvLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        videoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(tvLoadControl)
            .build()
            .apply {
                volume = 0f
                setAudioAttributes(mediaAudio, true)
                setWakeMode(C.WAKE_MODE_NETWORK)
            }
        vocalsPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
            volume = vocalsVolume
            setAudioAttributes(mediaAudio, true)
        }
        accPlayer = ExoPlayer.Builder(context, renderersFactory).build().apply {
            volume = accVolume
            setAudioAttributes(mediaAudio, true)
        }

        val errorListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("KTV", "播放错误: ${error.errorCodeName}", error)
                onToast("播放失败: ${error.errorCodeName}")
                _state.value = PlaybackState.Error(error.errorCodeName)
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
                        val ended = onPlaybackEnded
                        if (ended != null) {
                            engineScope.launch(Dispatchers.IO) { ended() }
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    if (playbackMode == MODE_ENHANCED) {
                        if (!vocalsPlayer.isPlaying) vocalsPlayer.playWhenReady = true
                        if (!accPlayer.isPlaying) accPlayer.playWhenReady = true
                    }
                    val callback = onStartedPlaying
                    if (callback != null) {
                        engineScope.launch(Dispatchers.IO) { callback(currentSongId) }
                    }
                    _state.value = PlaybackState.Playing(currentSongId, "")
                } else if (videoPlayer.playbackState != Player.STATE_ENDED && currentSongId > 0) {
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

        mainHandler.post(syncRunnable)
    }

    companion object {
        const val MODE_PLAIN = "plain"
        const val MODE_ENHANCED = "enhanced"
    }
}

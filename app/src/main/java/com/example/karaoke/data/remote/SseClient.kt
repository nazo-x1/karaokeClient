package com.example.karaoke.data.remote

import android.util.Log
import com.example.karaoke.data.remote.dto.SseMessage
import com.google.gson.Gson
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class SseClient(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val api: KaraokeApi,
) {
    private var eventSource: EventSource? = null
    private var stopped = false

    fun connect(): Flow<SseMessage> = callbackFlow {
        stopped = false
        connectInternal(0L) { msg ->
            trySend(msg)
        }
        awaitClose {
            stopped = true
            eventSource?.cancel()
            eventSource = null
        }
    }

    fun disconnect() {
        stopped = true
        eventSource?.cancel()
        eventSource = null
    }

    private fun connectInternal(delayMs: Long, onMessage: (SseMessage) -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (stopped) return@postDelayed
            eventSource?.cancel()
            val request = okhttp3.Request.Builder().url(api.eventsUrl()).build()
            eventSource = EventSources.createFactory(client).newEventSource(
                request,
                object : EventSourceListener() {
                    override fun onOpen(eventSource: EventSource, response: Response) {
                        Log.i("SSE", "已连接")
                    }

                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        try {
                            onMessage(gson.fromJson(data, SseMessage::class.java))
                        } catch (e: Exception) {
                            Log.e("SSE", "JSON 解析失败", e)
                        }
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        Log.e("SSE", "SSE 连接断开，5s 后重连", t)
                        if (!stopped) connectInternal(5000L, onMessage)
                    }

                    override fun onClosed(eventSource: EventSource) {
                        if (!stopped) connectInternal(5000L, onMessage)
                    }
                },
            )
        }, delayMs)
    }
}

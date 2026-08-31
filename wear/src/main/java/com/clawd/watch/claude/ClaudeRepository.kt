package com.clawd.watch.claude

import android.util.Log
import com.clawd.watch.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * PUSH model, identical to the TV's: the Mac streams Claude's state over Server-Sent Events and the
 * watch holds one long-lived connection, so a state change lands on the wrist in well under a
 * second with no polling. On disconnect it keeps showing the last state greyed out and reconnects.
 *
 * The watch must be on the same Wi-Fi as the Mac. Wear OS parks the Wi-Fi radio aggressively when
 * the screen is off, which is exactly why MainActivity keeps the screen on: an open SSE connection
 * with a 2s heartbeat is what holds the link up.
 */
class ClaudeRepository(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val eventsUrl: String = run {
        val raw = BuildConfig.CLAUDE_STATUS_URL.trim().trimEnd('/')
        val base = if (raw.endsWith("/status")) raw.removeSuffix("/status") else raw
        if (base.isBlank()) "" else "$base/events"
    }

    // Finite read timeout: serve.py heartbeats every ~2s, so silence means the link is dead (Mac
    // asleep, Wi-Fi dropped, NAT dropped the flow without a FIN). 10s -> we notice and reconnect
    // instead of sitting forever on a stale "online".
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun stream(): Flow<WatchState> = channelFlow {
        if (eventsUrl.isBlank()) {
            trySend(WatchState(ui = null, online = false))
            awaitClose { }
            return@channelFlow
        }
        val factory = EventSources.createFactory(client)
        var last: ClaudeUi? = null

        while (isActive) {
            val ended = CompletableDeferred<Unit>()
            val listener = object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val ui = json.decodeFromString(ClaudeStateDto.serializer(), data).toUi()
                        last = ui
                        trySend(WatchState(ui, online = true))
                    } catch (e: Exception) {
                        Log.d(TAG, "bad event: ${e.message}")
                    }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    Log.d(TAG, "SSE failure: ${t?.message}")
                    trySend(WatchState(last, online = false))
                    if (!ended.isCompleted) ended.complete(Unit)
                }

                override fun onClosed(eventSource: EventSource) {
                    if (!ended.isCompleted) ended.complete(Unit)
                }
            }
            val es = factory.newEventSource(Request.Builder().url(eventsUrl).build(), listener)
            try {
                ended.await()
            } finally {
                es.cancel()
            }
            if (isActive) delay(1500) // reconnect backoff
        }
        awaitClose { }
    }

    private companion object { const val TAG = "ClawdWear" }
}

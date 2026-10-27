package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Event real-time dari backend. */
sealed interface ServerEvent {
    data object Connected : ServerEvent
    data object Disconnected : ServerEvent

    /** type = "scan_input" | "scan_output" */
    data class ScanChanged(val type: String, val qrId: String, val area: String?) : ServerEvent

    /** Data diubah langsung dari DBeaver (event "db_change") -> muat ulang. */
    data object DataChanged : ServerEvent
}

/**
 * Koneksi real-time ke backend lewat Server-Sent Events: GET /api/events (server.js).
 * Backend mengirim event "scan_input", "scan_output", "db_change", "lead_time_lewat", dan "ping".
 * Bila koneksi putus, otomatis mencoba lagi tiap 3 detik.
 */
object ScanEvents {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)   // stream terbuka terus
        .build()

    fun events(): Flow<ServerEvent> = callbackFlow {
        var current: Call? = null

        val job = launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url(ApiConfig.API_URL + "events")
                        .header("Accept", "text/event-stream")
                        .build()
                    val call = client.newCall(request).also { current = it }
                    call.execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        trySend(ServerEvent.Connected)
                        val source = resp.body?.source() ?: error("Body kosong")
                        var eventName = "message"
                        val data = StringBuilder()
                        while (isActive) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> {
                                    if (data.isNotEmpty()) parse(eventName, data.toString())?.let { trySend(it) }
                                    eventName = "message"
                                    data.clear()
                                }
                                line.startsWith(":") -> Unit // komentar / keep-alive
                                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    if (data.isNotEmpty()) data.append('\n')
                                    data.append(line.removePrefix("data:").trim())
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // putus / server mati -> coba lagi
                }
                trySend(ServerEvent.Disconnected)
                delay(3_000)
            }
        }

        awaitClose {
            current?.cancel()
            job.cancel()
        }
    }

    private fun parse(event: String, json: String): ServerEvent? {
        if (event == "db_change") return ServerEvent.DataChanged
        if (event != "scan_input" && event != "scan_output") return null
        return runCatching {
            val o = JSONObject(json)
            ServerEvent.ScanChanged(
                type = event,
                qrId = o.getString("qr_id"),
                area = if (o.has("area") && !o.isNull("area")) o.getString("area") else null,
            )
        }.getOrNull()
    }
}

package com.ustadmobile.door.sse

import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.DoorTag
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

actual class DoorEventSource actual constructor(
    repoConfig: RepositoryConfig,
    var url: String,
    var listener: DoorEventListener,
    private val retry: Int,
) {

    private val logPrefix: String
        get() = "[DoorEventSource@$this - $url]"

    @Volatile
    private lateinit var eventSource: EventSource

    private val okHttpClient: OkHttpClient

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    @Volatile
    private var isClosed: Boolean = false

    private val eventSourceListener = object:  EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            listener.onMessage(DoorServerSentEvent.parse(data))
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if(!isClosed) {
                val err = (t as? Exception) ?: IOException("other event source error")
                listener.onError(err)
                scope.launch {
                    Napier.e("$logPrefix error: $err . Attempting to reconnect after ${retry}ms")
                    delay(retry.toLong())
                    connectToEventSource()
                }
            }
        }

        override fun onOpen(eventSource: EventSource, response: Response) {
            listener.onOpen()
        }
    }

    init {
        okHttpClient = repoConfig.okHttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .build()
        connectToEventSource()
    }

    private fun connectToEventSource() {
        val request = Request.Builder().url(url)
            .build()
        eventSource = EventSources.createFactory(okHttpClient)
            .newEventSource(request, eventSourceListener)
    }

    actual fun close() {
        isClosed = true
        scope.cancel()
        eventSource.cancel()
        Napier.d("$logPrefix close", tag = DoorTag.LOG_TAG)
    }


    companion object {
        const val CONNECT_TIMEOUT = 10000L

        const val READ_TIMEOUT = (60 * 60 * 1000L) // 1 hour
    }

}
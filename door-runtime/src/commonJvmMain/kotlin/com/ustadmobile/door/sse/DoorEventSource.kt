package com.ustadmobile.door.sse

import com.ustadmobile.door.ext.doorIdentityHashCode
import io.github.aakira.napier.Napier
import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.DoorTag
import kotlinx.atomicfu.atomic
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

actual class DoorEventSource actual constructor(
    repoConfig: RepositoryConfig,
    var url: String,
    var listener: DoorEventListener,
    private val retry: Int,
) {

    private val logPrefix: String
        get() = "[DoorEventSource@${this.doorIdentityHashCode}]"

    private lateinit var eventSource: EventSource

    private val okHttpClient: OkHttpClient

    private val retryJob = atomic(null as Job?)

    private val eventSourceListener = object:  EventSourceListener() {
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            listener.onMessage(DoorServerSentEvent(id ?: "", type ?: "", data))
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            val err = (t as? Exception) ?: IOException("other event source error")
            listener.onError(err)
            retryJob.value = GlobalScope.launch {
                Napier.e("$logPrefix error: $err . Attempting to reconnect after ${retry}ms")
                delay(retry.toLong())
                retryJob.value = null
                connectToEventSource()
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
        retryJob.value?.cancel()
        eventSource.cancel()
        Napier.d("$logPrefix close", tag = DoorTag.LOG_TAG)
    }


    companion object {
        const val CONNECT_TIMEOUT = 10000L

        const val READ_TIMEOUT = (60 * 60 * 1000L) // 1 hour
    }

}
package com.ustadmobile.door.sse

import com.ustadmobile.door.RepositoryConfig
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.doorIdentityHashCode
import io.github.aakira.napier.Napier
import org.w3c.dom.EventSource

/**
 * This is a simple implementation wrapper for a Server Sent Event Source. It is similar to
 * EventSource in Javascript. The URL should be the server URL sending events. The listener
 * will be called when the stream is opened, on error, and when an event is received.
 */
actual class DoorEventSource actual constructor(
    val repoConfig: RepositoryConfig,
    var url: String,
    var listener: DoorEventListener,
    retry: Int
) {

    private val logPrefix: String
        get() = "[DoorEventSource@${this.doorIdentityHashCode}]"

    private val eventSource: EventSource = EventSource(url)

    init {
        eventSource.onmessage = { event ->
            listener.onMessage(
                DoorServerSentEvent(event.lastEventId, event.origin, event.data.toString())
            )
        }

        eventSource.onerror = {
            listener.onError(Exception("Error occurred on ${it.target.toString()}"))
        }

        eventSource.onopen = {
            listener.onOpen()
        }
    }

    actual fun close() {
        eventSource.close()
        Napier.d("$logPrefix close", tag = DoorTag.LOG_TAG)
    }
}
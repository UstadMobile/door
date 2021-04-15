package com.ustadmobile.door.sse

import com.ustadmobile.door.RepositoryConfig

/**
 * This is a simple implementation wrapper for a Server Sent Event Source. It is similar to
 * EventSource in Javascript. The URL should be the server URL sending events. The listener
 * will be called when the stream is opened, on error, and when an event is received.
 */
actual class DoorEventSource actual constructor(val repoConfig: RepositoryConfig, var url: String, var listener: DoorEventListener) {
    actual fun close() {
    }
}
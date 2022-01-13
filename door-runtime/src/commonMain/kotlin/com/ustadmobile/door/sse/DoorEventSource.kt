package com.ustadmobile.door.sse

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.RepositoryConfig

/**
 * This is a simple implementation wrapper for a Server Sent Event Source. It is similar to
 * EventSource in Javascript. The URL should be the server URL sending events. The listener
 * will be called when the stream is opened, on error, and when an event is received.
 */
expect class DoorEventSource(
    repoConfig: RepositoryConfig,
    url: String,
    listener: DoorEventListener,
    retry: Int = 2000
) {

    fun close()

}
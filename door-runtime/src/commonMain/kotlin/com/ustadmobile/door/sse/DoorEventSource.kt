package com.ustadmobile.door.sse

/**
 * This is a simple implementation wrapper for a Server Sent Event Source. It is similar to
 * EventSource in Javascript. The URL should be the server URL sending events. The listener
 * will be called when the stream is opened, on error, and when an event is received.
 */
expect class DoorEventSource(url: String, listener: DoorEventListener) {


    fun close()

}
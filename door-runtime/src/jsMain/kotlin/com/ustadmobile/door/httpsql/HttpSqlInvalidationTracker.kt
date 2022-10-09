package com.ustadmobile.door.httpsql

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.room.InvalidationTracker
import com.ustadmobile.door.room.InvalidationTrackerAsyncInit
import io.github.aakira.napier.Napier
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CompletableDeferred
import org.w3c.dom.EventSource

/**
 * Invalidation Tracker for use with HttpSql. Relies on a remote server sent events (SSE) endpoint.
 */
class HttpSqlInvalidationTracker (
    endpointUrl: String,
) : InvalidationTracker(), Closeable, InvalidationTrackerAsyncInit {

    private val eventSource: EventSource

    private val readyCompletableDeferred = CompletableDeferred<Boolean>()

    init {
        console.log("HttpSqlInvalidationTracker: connecting to: $endpointUrl/invalidations\n ")
        eventSource = EventSource("$endpointUrl/invalidations")
        eventSource.onopen = {
            Napier.d("HttpSql: Invalidation Event Source: open", tag = DoorTag.LOG_TAG)
            readyCompletableDeferred.complete(true)
        }

        eventSource.onerror = {
            Napier.e("HttpSql: Invalidation Event Source: Error: $it")
        }

        eventSource.onmessage = {
            val tablesInvalidated: List<String> = it.data.toString().split(",")
            Napier.d("HttpSql: Sending invalidations for ${tablesInvalidated.joinToString(){"'$it'"}}")
            fireChanges(tablesInvalidated.toSet())
        }
    }

    override suspend fun init(connection: Connection) {
        readyCompletableDeferred.await()
    }

    override fun close() {
        eventSource.close()
    }

}
package com.ustadmobile.door.room

import com.ustadmobile.door.jdbc.Connection

/**
 * This interface is implemented by any InvalidationTracker that requires Asynchronous initialization (e.g. that cannot
 * be done using the constructor).
 */
interface InvalidationTrackerAsyncInit {

    suspend fun init(connection: Connection)

}
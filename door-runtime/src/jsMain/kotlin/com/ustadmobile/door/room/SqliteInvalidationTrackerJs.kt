package com.ustadmobile.door.room

import com.ustadmobile.door.jdbc.Connection

internal class SqliteInvalidationTrackerJs(
    tableNames: Array<String>
): SqliteInvalidationTracker(tableNames, setupTriggersBeforeConnection = false), InvalidationTrackerAsyncInit{

    override suspend fun init(connection: Connection) {
        setupTriggersAsync(connection, temporary = false)
    }
}
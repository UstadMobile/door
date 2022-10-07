package com.ustadmobile.door.util

import com.ustadmobile.door.jdbc.Connection

/**
 * Interface that can be implemented by an InvalidationTracker to listen for when the database has been built (e.g. all
 * tables have been created etc). This could be used as a time to setup triggers etc. This is run after upgrades but
 * before callbacks
 */
interface InvalidationTrackerDbBuiltListener {

    suspend fun onDatabaseBuilt(connection: Connection)

}
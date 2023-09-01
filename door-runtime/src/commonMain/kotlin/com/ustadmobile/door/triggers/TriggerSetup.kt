package com.ustadmobile.door.triggers

import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.ext.DoorDatabaseMetadata

/**
 * This function will create the triggers (as per the @Trigger annotation on a replicate entity) and ReceiveView (if
 * the remoteInsertStrategy is INSERT_INTO_VIEW).
 *
 * This must be called when the database is first created (e.g. by the DatabaseBuilder)  and must also be called after
 * any migration.
 */
expect fun DoorSqlDatabase.setupTriggers(
    dbMetadata: DoorDatabaseMetadata<*>
)

package com.ustadmobile.door.triggers

import com.ustadmobile.door.DoorDatabaseCallbackSync
import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.ext.DoorDatabaseMetadata

/**
 * The TriggerSetupCallback creates the triggers (as per the @Trigger annotation) and the ReceiveView (if required).
 * This is used by the DatabaseBuilder
 */
internal class TriggerSetupCallback(
    private val dbMetadata: DoorDatabaseMetadata<*>
): DoorDatabaseCallbackSync {
    override fun onCreate(db: DoorSqlDatabase) {
        db.setupTriggers(dbMetadata)
    }

    override fun onOpen(db: DoorSqlDatabase) {

    }
}
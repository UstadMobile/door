package com.ustadmobile.door.triggers

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.dbType

actual fun DoorSqlDatabase.setupTriggers(
    dbMetadata: DoorDatabaseMetadata<*>
) {
    when(dbType()) {
        DoorDbType.SQLITE -> {
            setupTriggersSqlite(dbMetadata)
        }
        DoorDbType.POSTGRES -> {
            setupTriggersPostgres(dbMetadata)
        }
    }
}

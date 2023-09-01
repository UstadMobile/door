package com.ustadmobile.door.triggers

import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.replication.ReplicationEntityMetaData

fun DoorSqlDatabase.createReceiveView(entity: ReplicationEntityMetaData) {
    execSQL("""
                CREATE VIEW ${entity.receiveViewName} AS 
                       SELECT ${entity.entityTableName}.*, 0 AS fromNodeId
                         FROM ${entity.entityTableName}
            """)
}
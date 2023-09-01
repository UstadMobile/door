package com.ustadmobile.door.triggers

import com.ustadmobile.door.DoorSqlDatabase
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.ext.DoorDatabaseMetadata


fun DoorSqlDatabase.setupTriggersSqlite(
    dbMetadata: DoorDatabaseMetadata<*>
) {
    dbMetadata.replicateEntities.values.forEach { entity ->
        if(entity.remoteInsertStrategy == ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_VIEW) {
            createReceiveView(entity)
        }

        entity.triggers.forEach {trigger ->
            val tableOrViewName = if(trigger.on == Trigger.On.ENTITY) {
                entity.entityTableName
            }else {
                entity.receiveViewName
            }

            trigger.events.forEach { event ->
                val postfix = event.sqlKeyWord.lowercase().substring(0, 3)
                execSQL("""
                CREATE TRIGGER ${Trigger.NAME_PREFIX}${trigger.name}_$postfix 
                ${trigger.order.sqlStr} $event ON $tableOrViewName
                FOR EACH ROW ${if(trigger.conditionSql != "") " WHEN (${trigger.conditionSql}) " else ""}
                BEGIN
                    ${trigger.sqlStatements.joinToString(separator = ";")};
                END
            """.trimIndent())
            }

        }
    }
}
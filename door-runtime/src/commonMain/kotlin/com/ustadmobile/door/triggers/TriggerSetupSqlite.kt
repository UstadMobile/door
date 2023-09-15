package com.ustadmobile.door.triggers

import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.ext.DoorDatabaseMetadata

/**
 * Generate of all the SQL statements required to add the triggers and receive view as per the DoorDatabaseMetadata
 */
fun DoorDatabaseMetadata<*>.createSqliteTriggerSetupStatementList(): List<String> {
    return buildList {
        replicateEntities.values.forEach { entity ->
            if(entity.remoteInsertStrategy == ReplicateEntity.RemoteInsertStrategy.INSERT_INTO_RECEIVE_VIEW) {
                add(entity.createReceiveViewSql)
            }

            entity.triggers.forEach {trigger ->
                val tableOrViewName = if(trigger.on == Trigger.On.ENTITY) {
                    entity.entityTableName
                }else {
                    entity.receiveViewName
                }

                trigger.events.forEach { event ->
                    val postfix = event.sqlKeyWord.lowercase().substring(0, 3)
                    add("""
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
}
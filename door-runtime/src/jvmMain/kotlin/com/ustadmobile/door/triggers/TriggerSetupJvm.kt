package com.ustadmobile.door.triggers

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.mapRows

fun DoorDatabaseMetadata<*>.createTriggerSetupStatementList(dbType: Int) :List<String> {
    return if(dbType ==DoorDbType.SQLITE)
        createSqliteTriggerAndReceiveViewSetupStatementList()
    else
        createPostgresTriggerSetupStatementList()
}


/**
 * Drop all triggers that were created by Door (including triggers created by the Trigger annotation, and for Postgres,
 * the triggers that are used by PostgresChangeTracker). Also drop all ReceiveViews that are used for replication.
 *
 * This is done before migration (to avoid errors when a migration drops table e.g. a table would not be dropped if there
 * were still triggers associated with it).
 */
suspend fun Connection.dropDoorTriggersAndReceiveViews() {
    when(dbType()) {
        DoorDbType.SQLITE -> {
            dropDoorTriggersAndReceiveViewsSqlite()
        }

        DoorDbType.POSTGRES -> {
            createStatement().use { stmt ->
                //Drop any door-generated triggers
                val doorTriggerAndTableNames = stmt.executeQuery("""
                    SELECT trigger_name, event_object_table
                      FROM information_schema.triggers
                     WHERE trigger_name LIKE '${Trigger.NAME_PREFIX}%'
                """).use {  results ->
                    results.mapRows {resultSet ->
                        resultSet.getString(1) to resultSet.getString(2)
                    }
                }

                doorTriggerAndTableNames.forEach {
                    stmt.addBatch("DROP TRIGGER ${it.first} ON ${it.second}")
                }
                stmt.executeBatch()

                //Drop any door-generated functions
                val doorFunctionNames = stmt.executeQuery(""""
                    SELECT routine_name
                      FROM information_schema.routines
                     WHERE routine_type = 'FUNCTION'
                       AND routine_schema = 'public'
                       AND routine_name LIKE '${Trigger.NAME_PREFIX}%'
                """).use { results ->
                    results.mapRows { resultSet ->
                        resultSet.getString(1)
                    }
                }

                doorFunctionNames.forEach {
                    stmt.addBatch("DROP FUNCTION $it")
                }
                stmt.executeBatch()

                //Drop all ReceiveViews
                val doorReceiveViewNames = stmt.executeQuery("""
                    SELECT table_name 
                      FROM information_schema.views
                     WHERE lower(table_name) LIKE '%${DoorConstants.RECEIVE_VIEW_SUFFIX.lowercase()}'
                """).use { results ->
                    results.mapRows { resultSet ->
                        resultSet.getString(1)
                    }
                }

                doorReceiveViewNames.forEach {
                    stmt.addBatch("DROP VIEW $it")
                }
                stmt.executeBatch()
            }
        }
    }
}

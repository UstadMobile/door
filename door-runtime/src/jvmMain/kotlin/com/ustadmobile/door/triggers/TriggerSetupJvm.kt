package com.ustadmobile.door.triggers

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.ext.DoorDatabaseMetadata
import com.ustadmobile.door.ext.dbType
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsync
import com.ustadmobile.door.jdbc.ext.mapRows
import com.ustadmobile.door.triggers.TriggerConstants.SQLITE_SELECT_TRIGGER_NAMES
import com.ustadmobile.door.triggers.TriggerConstants.SQLITE_SELECT_VIEW_NAMES

fun DoorDatabaseMetadata<*>.createTriggerSetupStatementList(dbType: Int) :List<String> {
    return if(dbType ==DoorDbType.SQLITE)
        createSqliteTriggerSetupStatementList()
    else
        createPostgresTriggerSetupStatementList()
}

internal suspend fun Connection.getSqliteDoorTriggerNames(): List<String> {
    return prepareStatement(SQLITE_SELECT_TRIGGER_NAMES).use { preparedStmt ->
        preparedStmt.setString(1, "${Trigger.NAME_PREFIX}%")
        preparedStmt.executeQueryAsyncKmp().use { results ->
            results.mapRows { resultSet ->
                resultSet.getString(1)
            }
        }
    }
}

internal suspend fun Connection.getSqliteDoorReceiveViewNames(): List<String> {
    return prepareStatement(SQLITE_SELECT_VIEW_NAMES).use { preparedStatement ->
        preparedStatement.setString(1, "%${DoorConstants.RECEIVE_VIEW_SUFFIX}")
        preparedStatement.executeQueryAsyncKmp().use { results ->
            results.mapRows { resultSet ->
                resultSet.getString(1)
            }
        }
    }
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
            val triggerNames = getSqliteDoorTriggerNames()

            createStatement().use { stmt ->
                triggerNames.forEach { triggerName ->
                    stmt.executeUpdateAsync("DROP TRIGGER $triggerName")
                }
            }

            val receiveViewNames = getSqliteDoorReceiveViewNames()

            createStatement().use {
                receiveViewNames.forEach { receiveViewName ->
                    it.executeUpdateAsync("DROP VIEW $receiveViewName")
                }
            }
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

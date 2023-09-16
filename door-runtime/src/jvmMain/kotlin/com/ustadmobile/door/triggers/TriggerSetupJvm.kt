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
 *
 */
suspend fun Connection.dropDoorTriggers() {
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
    }
}
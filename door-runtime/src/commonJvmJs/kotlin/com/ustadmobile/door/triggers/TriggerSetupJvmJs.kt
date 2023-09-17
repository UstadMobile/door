package com.ustadmobile.door.triggers

import com.ustadmobile.door.annotation.Trigger
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.ext.*


internal suspend fun Connection.getSqliteDoorTriggerNames(): List<String> {
    return prepareStatement(TriggerConstants.SQLITE_SELECT_TRIGGER_NAMES).useStatementAsync { preparedStmt ->
        preparedStmt.setString(1, "${Trigger.NAME_PREFIX}%")
        preparedStmt.executeQueryAsyncKmp().useResults { results ->
            results.mapRows { resultSet ->
                resultSet.getString(1)
            }
        }
    }.mapNotNull { it }
}

internal suspend fun Connection.getSqliteDoorReceiveViewNames(): List<String> {
    return prepareStatement(TriggerConstants.SQLITE_SELECT_VIEW_NAMES).useStatementAsync { preparedStatement ->
        preparedStatement.setString(1, "%${com.ustadmobile.door.DoorConstants.RECEIVE_VIEW_SUFFIX}")
        preparedStatement.executeQueryAsyncKmp().useResults { results ->
            results.mapRows { resultSet ->
                resultSet.getString(1)
            }
        }
    }.mapNotNull { it }
}

internal suspend fun Connection.dropDoorTriggersAndReceiveViewsSqlite() {
    val triggerNames = getSqliteDoorTriggerNames()

    createStatement().useStatementAsync { stmt ->
        triggerNames.forEach { triggerName ->
            stmt.executeUpdateAsync("DROP TRIGGER $triggerName")
        }
    }

    val receiveViewNames = getSqliteDoorReceiveViewNames()

    createStatement().useStatementAsync {
        receiveViewNames.forEach { receiveViewName ->
            it.executeUpdateAsync("DROP VIEW $receiveViewName")
        }
    }
}

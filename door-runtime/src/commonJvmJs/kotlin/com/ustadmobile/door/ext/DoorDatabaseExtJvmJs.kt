package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.jdbc.*

actual suspend fun <R> DoorDatabase.prepareAndUseStatementAsync(
    stmtConfig: PreparedStatementConfig,
    block: suspend (PreparedStatement) -> R
) : R {
    var connection: Connection? = null
    var stmt: PreparedStatement? = null
    try {
        connection = openConnection()
        stmt = connection.prepareStatement(stmtConfig)

        return block(stmt)
    }finally {
        stmt?.close()
        connection?.close()
    }
}

actual fun <R> DoorDatabase.prepareAndUseStatement(
    stmtConfig: PreparedStatementConfig,
    block: (PreparedStatement) -> R
) : R {
    var connection: Connection? = null
    var stmt: PreparedStatement? = null
    try {
        connection = openConnection()
        stmt = connection.prepareStatement(stmtConfig)
        return block(stmt)
    }finally {
        stmt?.close()
        connection?.close()
    }
}

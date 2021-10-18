package com.ustadmobile.door.ext

import com.ustadmobile.door.*
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

actual val DoorDatabase.sourceDatabase: DoorDatabase?
    get() {
        return when {
            (this is DoorDatabaseJdbc && isInTransaction) -> this.doorJdbcSourceDatabase
            (this is DoorDatabaseJdbc && !isInTransaction) -> null
            (this is DoorDatabaseRepository) -> this.db
            (this is DoorDatabaseReplicateWrapper) -> this.realDatabase
            else -> throw IllegalStateException("SourceDatabase : Not a recognized implementation: ${this::class.qualifiedName}")
        }
    }


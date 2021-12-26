package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.util.NodeIdAuthCache
import io.github.aakira.napier.Napier

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
    }catch(e: Exception) {
        Napier.e("prepareAndUseStatement: Exception running SQL: '${stmtConfig.sql}'", e, tag = DoorTag.LOG_TAG)
        throw e
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
    }catch(e: Exception) {
        Napier.e("prepareAndUseStatement: Exception running SQL: '${stmtConfig.sql}'", e, tag = DoorTag.LOG_TAG)
        throw e
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
            else -> throw IllegalStateException("SourceDatabase : Not a recognized implementation: ${this::class}")
        }
    }

actual val DoorDatabase.doorPrimaryKeyManager: DoorPrimaryKeyManager
    get() = (rootDatabase as DoorDatabaseJdbc).realPrimaryKeyManager

actual fun DoorDatabase.handleTablesChanged(changeTableNames: List<String>) {
    handleTableChangedInternal(changeTableNames)
}

actual val DoorDatabase.replicationNotificationDispatcher: ReplicationNotificationDispatcher
    get() = if(this is DoorDatabaseJdbc) {
        this.realReplicationNotificationDispatcher
    }else {
        this.rootDatabase.replicationNotificationDispatcher
    }

actual fun DoorDatabase.addInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
    addChangeListener(changeListenerRequest)
}

actual fun DoorDatabase.removeInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
    removeChangeListener(changeListenerRequest)
}

actual val DoorDatabase.nodeIdAuthCache: NodeIdAuthCache
    get() = if(this is DoorDatabaseJdbc) {
        this.realNodeIdAuthCache
    }else {
        this.rootDatabase.nodeIdAuthCache
    }
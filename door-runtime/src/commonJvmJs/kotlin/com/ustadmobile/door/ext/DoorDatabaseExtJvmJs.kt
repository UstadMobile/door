package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier

actual suspend fun <R> DoorDatabase.prepareAndUseStatementAsync(
    stmtConfig: PreparedStatementConfig,
    block: suspend (PreparedStatement) -> R
) : R {
    try {
        return (this.rootDatabase as RoomJdbcImpl).jdbcImplHelper.useConnectionAsync { connection ->
            connection.prepareStatement(this, stmtConfig).useStatementAsync { stmt: PreparedStatement ->
                stmt.setQueryTimeout((rootDatabase as DoorDatabaseJdbc).jdbcQueryTimeout)
                val blockStartTime = systemTimeInMillis()
                return@useConnectionAsync block(stmt).also {
                    val blockTime = systemTimeInMillis() - blockStartTime
                    if(blockTime > 1000)
                        Napier.w("WARNING $this query ${stmtConfig.sql} took ${blockTime}ms")
                }
            }
        }
    }catch(e: Exception) {
        Napier.e("prepareAndUseStatement: Exception running SQL: '${stmtConfig.sqlToUse(this.dbType())}' on DB $this", e, tag = DoorTag.LOG_TAG)
        throw e
    }
}

actual fun <R> DoorDatabase.prepareAndUseStatement(
    stmtConfig: PreparedStatementConfig,
    block: (PreparedStatement) -> R
) : R {
    try {
        return (this.rootDatabase as RoomJdbcImpl).jdbcImplHelper.useConnection { connection ->
            connection.prepareStatement(this, stmtConfig).useStatement { stmt: PreparedStatement ->
                stmt.setQueryTimeout((rootDatabase as DoorDatabaseJdbc).jdbcQueryTimeout)
                val blockStartTime = systemTimeInMillis()
                return@useConnection block(stmt).also {
                    val blockTime = systemTimeInMillis() - blockStartTime
                    if(blockTime > 1000)
                        Napier.w("WARNING $this query ${stmtConfig.sql} took ${blockTime}ms")
                }
            }
        }
    }catch(e: Exception) {
        Napier.e("prepareAndUseStatement: Exception running SQL: '${stmtConfig.sqlToUse(this.dbType())}' on DB $this", e, tag = DoorTag.LOG_TAG)
        throw e
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

actual val DoorDatabase.replicationNotificationDispatcher: ReplicationNotificationDispatcher
    get() = if(this is DoorDatabaseJdbc) {
        this.realReplicationNotificationDispatcher
    }else {
        this.rootDatabase.replicationNotificationDispatcher
    }

actual fun DoorDatabase.addInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
    asCommon().addChangeListener(changeListenerRequest)
}

actual fun DoorDatabase.removeInvalidationListener(changeListenerRequest: ChangeListenerRequest) {
    asCommon().removeChangeListener(changeListenerRequest)
}

actual val DoorDatabase.nodeIdAuthCache: NodeIdAuthCache
    get() = if(this is DoorDatabaseJdbc) {
        this.realNodeIdAuthCache
    }else {
        this.rootDatabase.nodeIdAuthCache
    }

actual fun DoorDatabase.addIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    val rootDb = this.rootDatabase as DoorDatabaseJdbc
    rootDb.realIncomingReplicationListenerHelper.addIncomingReplicationListener(incomingReplicationListener)
}

actual fun DoorDatabase.removeIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    val rootDb = this.rootDatabase as DoorDatabaseJdbc
    rootDb.realIncomingReplicationListenerHelper.removeIncomingReplicationListener(incomingReplicationListener)
}

actual val DoorDatabase.incomingReplicationListenerHelper: IncomingReplicationListenerHelper
    get() = (this.rootDatabase as DoorDatabaseJdbc).realIncomingReplicationListenerHelper

actual val DoorDatabase.rootTransactionDatabase: DoorDatabase
    get() {
        var db = this
        while(db !is DoorDatabaseJdbc) {
            db = db.sourceDatabase
                ?: throw IllegalStateException("rootTransactionDatabase: cannot find DoorDatabaseJdbc through sourceDatabase")
        }

        return db
    }

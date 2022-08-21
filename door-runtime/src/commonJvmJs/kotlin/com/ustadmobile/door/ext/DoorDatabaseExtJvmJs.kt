package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.jdbc.ext.useStatement
import com.ustadmobile.door.jdbc.ext.useStatementAsync
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier

actual suspend fun <R> RoomDatabase.prepareAndUseStatementAsync(
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

actual fun <R> RoomDatabase.prepareAndUseStatement(
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

actual val RoomDatabase.sourceDatabase: RoomDatabase?
    get() {
        return when {
            (this is DoorDatabaseJdbc && !isInTransaction) -> null
            (this is DoorDatabaseRepository) -> this.db
            (this is DoorDatabaseReplicateWrapper) -> this.realDatabase
            else -> throw IllegalStateException("SourceDatabase : Not a recognized implementation: ${this::class}")
        }
    }

actual val RoomDatabase.doorPrimaryKeyManager: DoorPrimaryKeyManager
    get() = (rootDatabase as DoorDatabaseJdbc).realPrimaryKeyManager

actual val RoomDatabase.replicationNotificationDispatcher: ReplicationNotificationDispatcher
    get() = if(this is DoorDatabaseJdbc) {
        this.realReplicationNotificationDispatcher
    }else {
        this.rootDatabase.replicationNotificationDispatcher
    }

actual val RoomDatabase.nodeIdAuthCache: NodeIdAuthCache
    get() = if(this is DoorDatabaseJdbc) {
        this.realNodeIdAuthCache
    }else {
        this.rootDatabase.nodeIdAuthCache
    }

actual fun RoomDatabase.addIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    val rootDb = this.rootDatabase as DoorDatabaseJdbc
    rootDb.realIncomingReplicationListenerHelper.addIncomingReplicationListener(incomingReplicationListener)
}

actual fun RoomDatabase.removeIncomingReplicationListener(incomingReplicationListener: IncomingReplicationListener) {
    val rootDb = this.rootDatabase as DoorDatabaseJdbc
    rootDb.realIncomingReplicationListenerHelper.removeIncomingReplicationListener(incomingReplicationListener)
}

actual val RoomDatabase.incomingReplicationListenerHelper: IncomingReplicationListenerHelper
    get() = (this.rootDatabase as DoorDatabaseJdbc).realIncomingReplicationListenerHelper

actual val RoomDatabase.rootTransactionDatabase: RoomDatabase
    get() {
        var db = this
        while(db !is DoorDatabaseJdbc) {
            db = db.sourceDatabase
                ?: throw IllegalStateException("rootTransactionDatabase: cannot find DoorDatabaseJdbc through sourceDatabase")
        }

        return db
    }

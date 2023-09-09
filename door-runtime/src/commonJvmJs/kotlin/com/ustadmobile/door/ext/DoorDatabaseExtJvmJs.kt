package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.*
import com.ustadmobile.door.jdbc.ext.useStatement
import com.ustadmobile.door.jdbc.ext.useStatementAsync
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
        Napier.e("prepareAndUseStatementAsync: Exception running SQL: '${stmtConfig.sqlToUse(this.dbType())}' on DB $this allocated = $stmtAllocated", e, tag = DoorTag.LOG_TAG)
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
            this is DoorDatabaseJdbc -> null
            (this is DoorDatabaseRepository) -> this.db
            (this is DoorDatabaseWrapper<*>) -> this.realDatabase
            else -> throw IllegalStateException("SourceDatabase : Not a recognized implementation: ${this::class}")
        }
    }

actual val RoomDatabase.doorPrimaryKeyManager: DoorPrimaryKeyManager
    get() = (rootDatabase as DoorDatabaseJdbc).realPrimaryKeyManager

actual val RoomDatabase.nodeIdAuthCache: NodeIdAuthCache
    get() = if(this is DoorDatabaseJdbc) {
        this.realNodeIdAuthCache
    }else {
        this.rootDatabase.nodeIdAuthCache
    }


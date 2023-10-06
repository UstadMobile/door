package com.ustadmobile.door.ext

import com.ustadmobile.door.*
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ext.useStatement
import com.ustadmobile.door.jdbc.ext.useStatementAsync
import com.ustadmobile.door.log.e
import com.ustadmobile.door.log.v
import com.ustadmobile.door.log.w
import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.room.RoomJdbcImpl
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

actual suspend fun <R> RoomDatabase.prepareAndUseStatementAsync(
    stmtConfig: PreparedStatementConfig,
    block: suspend (PreparedStatement) -> R
) : R {
    val jdbcImpl = (this.rootDatabase as RoomJdbcImpl)
    val logger = jdbcImpl.jdbcImplHelper.logger
    try {
        return jdbcImpl.jdbcImplHelper.useConnectionAsync(
            readOnly = stmtConfig.readOnly
        ) { connection ->
            logger.v { "[prepareAndUseStatementAsync - ${jdbcImpl.jdbcImplHelper.dbName}] - prepare \"${stmtConfig.sql}\"" }
            connection.prepareStatement(this, stmtConfig).useStatementAsync { stmt: PreparedStatement ->
                stmt.setQueryTimeout((rootDatabase as DoorDatabaseJdbc).jdbcQueryTimeout)
                val blockStartTime = systemTimeInMillis()
                return@useConnectionAsync block(stmt).also {
                    val blockTime = systemTimeInMillis() - blockStartTime
                    if(blockTime > 1000)
                        logger.w("[prepareAndUseStatementAsync - ${jdbcImpl.jdbcImplHelper.dbName}] query ${stmtConfig.sql} took ${blockTime}ms")
                }
            }
        }
    }catch(e: Exception) {
        if(e !is CancellationException) {
            logger.e("[prepareAndUseStatementAsync - ${jdbcImpl.jdbcImplHelper.dbName}] - Exception running ${stmtConfig.sql}", e)
        }
        throw e
    }
}

actual fun <R> RoomDatabase.prepareAndUseStatement(
    stmtConfig: PreparedStatementConfig,
    block: (PreparedStatement) -> R
) : R {
    try {
        return (this.rootDatabase as RoomJdbcImpl).jdbcImplHelper.useConnection(
            readOnly = stmtConfig.readOnly
        ) { connection ->
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


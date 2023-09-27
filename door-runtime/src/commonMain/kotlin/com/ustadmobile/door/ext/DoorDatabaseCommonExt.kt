package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.SyncNodeIdCallback
import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.room.RoomDatabase
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * If this DoorDatabase represents a repository, then run the given block on the repository first
 * with a timeout. If the operation times out (e.g. due to network issues), then the operation will
 * be retried on the database. This can be useful to lookup values from the repo if possible with a
 * fallback to using the local database if this takes too long.
 *
 * If this DoorDatabase instance represents a database itself, then the operation will run
 * immediately on the database (without timeout).
 *
 * Example:
 * val entity = myRepo.withRepoTimeout(5000) {
 *     it.someDao.someQuery()
 * }
 *
 * @param timeMillis the timeout (in milliseconds)
 * @param block a function block that represents the function to run. The supplied parameter must be
 * used as the database for the fallback to running from the
 */
@Suppress("UNCHECKED_CAST", "unused")
suspend fun <T : RoomDatabase, R> T.onRepoWithFallbackToDb(timeMillis: Long, block: suspend (T) -> R) : R {
    return if(this is DoorDatabaseRepository) {
        try {
            withTimeout(timeMillis) {
                block(this@onRepoWithFallbackToDb)
            }
        }catch(e: TimeoutCancellationException) {
            block(this.db as T)
        }
    }else {
        block(this)
    }
}

/**
 * Runs a block of code on the database first (e.g. to immediately fetch the local value), and then
 * on the repository (e.g. to get an update if it is available).
 *
 * The function will first be invoked with the database and null as parameters. It will then be
 * invoked with the repository and the value returned from it's previous run on the database as
 * parameters.
 *
 * If a timeout occurs then the return value will be the value from invocation on the database
 */
@Suppress("UNCHECKED_CAST")
suspend fun <T: RoomDatabase, R> T.onDbThenRepoWithTimeout(timeMillis: Long, block: suspend (doorDb: T, lastResult: R?) -> R) : R{
    return if(this is DoorDatabaseRepository) {
        val dbResult = block(this.db as T, null)
        try {
            withTimeout(timeMillis) {
                block(this@onDbThenRepoWithTimeout, dbResult)
            }
        }catch(e: TimeoutCancellationException) {
            dbResult
        }
    }else {
        block(this, null)
    }
}


/**
 * Where the receiver is a repository, this will provide a pair of the original database object
 * and the repository. If the receiver is not a database, and IllegalArgumentException will be
 * thrown
 *
 * @return Pair of the database and repository
 * @throws IllegalArgumentException if the receiver is not actually the repository
 */
@Suppress("UNCHECKED_CAST", "unused")
fun <T: RoomDatabase> T.requireDbAndRepo(): Pair<T, T> {
    val repo = this as? DoorDatabaseRepository
        ?: throw IllegalStateException("Must use repo for addFileToContainer")
    val db = repo.db as T
    return Pair(db, this)
}

/**
 * Shorthand extension function for use in tests where clearAllTables is needed
 * to reset between test runs.
 *
 * After clearing all the table data, the sync tables (e.g. TableSyncStatus,
 * The nodeId on SyncNode, etc) need to be setup again.
 */
@Suppress("unused")
fun <T:RoomDatabase> T.clearAllTablesAndResetNodeId(nodeId: Long) : T {
    clearAllTables()
    execSqlBatch(*SyncNodeIdCallback(nodeId)
        .initSyncNodeSync(forceReset = true).toTypedArray())
    return this
}

/**
 * Suspended wrapper that will prepare a Statement, execute a code block, and return the code block result
 */
suspend fun <R> RoomDatabase.prepareAndUseStatementAsync(
    sql: String,
    readOnly: Boolean = false,
    block: suspend (PreparedStatement) -> R,
) = prepareAndUseStatementAsync(PreparedStatementConfig(sql, readOnly = readOnly), block)

/**
 * Suspended wrapper that will prepare a Statement, execute a code block, and return the code block result
 */
fun <R> RoomDatabase.prepareAndUseStatement(
    sql: String,
    readOnly: Boolean = false,
    block: (PreparedStatement) -> R
) = prepareAndUseStatement(PreparedStatementConfig(sql, readOnly = readOnly), block)

/**
 * Get the real, one and only, root database. This will get out of any wrappers, transactions, etc.
 */
val RoomDatabase.rootDatabase: RoomDatabase
    get() {
        var db = this
        while (true) {
            db = db.sourceDatabase ?: break
        }

        return db
    }

val RoomDatabase.arraySupported: Boolean
    get() = dbType() == DoorDbType.POSTGRES

inline fun <T: RoomDatabase> T.use(
    block: (T) -> Unit
) {
    try {
        block(this)
    }finally {
        close()
    }
}

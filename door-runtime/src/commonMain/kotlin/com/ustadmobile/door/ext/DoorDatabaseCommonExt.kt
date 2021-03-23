package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.SyncableDoorDatabase
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Extension property that will be true if this database is both syncable and the primary (eg. server)
 * instance, false otherwise
 */
val DoorDatabase.syncableAndPrimary: Boolean
        get() = (this as? SyncableDoorDatabase)?.master ?: false

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
@Suppress("UNCHECKED_CAST")
suspend fun <T : DoorDatabase, R> T.onRepoWithFallbackToDb(timeMillis: Long, block: suspend (T) -> R) : R {
    if(this is DoorDatabaseRepository) {
        try {
            return withTimeout(timeMillis) {
                block(this@onRepoWithFallbackToDb)
            }
        }catch(e: TimeoutCancellationException) {
            return block(this.db as T)
        }
    }else {
        return block(this)
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
suspend fun <T: DoorDatabase, R> T.onDbThenRepoWithTimeout(timeMillis: Long, block: suspend (doorDb: T, lastResult: R?) -> R) : R{
    if(this is DoorDatabaseRepository) {
        val dbResult = block(this.db as T, null)
        try {
            return withTimeout(timeMillis) {
                block(this@onDbThenRepoWithTimeout, dbResult)
            }
        }catch(e: TimeoutCancellationException) {
            return dbResult
        }
    }else {
        return block(this, null)
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
@Suppress("UNCHECKED_CAST")
fun <T: DoorDatabase> T.requireDbAndRepo(): Pair<T, T> {
    val repo = this as? DoorDatabaseRepository
        ?: throw IllegalStateException("Must use repo for addFileToContainer")
    val db = repo.db as T
    return Pair(db, this)
}

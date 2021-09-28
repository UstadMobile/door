package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorSqlDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
actual fun DoorDatabase.dbType(): Int {
    return DoorDbType.SQLITE
}

actual fun DoorDatabase.dbSchemaVersion(): Int = this.dbVersion

/**
 * Run a transaction within a suspend coroutine context. Not really implemented at the moment.
 */
actual suspend fun <T: DoorDatabase, R> T.withDoorTransactionAsync(dbKClass: KClass<out T>, block: suspend (T) -> R) : R {
    return block(this)
}

actual fun <T: DoorDatabase, R> T.withDoorTransaction(dbKClass: KClass<T>, block: (T) -> R) : R {
    return block(this)
}


actual fun DoorSqlDatabase.dbType(): Int {
    return DoorDbType.SQLITE
}

/**
 * Multiplatform wrapper function that will execute raw SQL statements in a
 * batch.
 *
 * Does not return any results. Will throw an exception in the event of
 * malformed SQL.
 *
 * The name deliberately lower cases sql to avoid name clashes
 */
actual fun DoorDatabase.execSqlBatch(vararg sqlStatements: String) {
    val connection = dataSource.getConnection()
    connection.setAutoCommit(false)
    sqlStatements.forEach {
        GlobalScope.launch {
            val statement = connection.prepareStatement(it)
            statement.executeUpdateAsync()
            statement.close()
        }
    }
    connection.commit()
    connection.setAutoCommit(true)
    connection.close()
}

actual fun <T : DoorDatabase> KClass<T>.doorDatabaseMetadata(): DoorDatabaseMetadata<T> {
    TODO("Not yet implemented")
}
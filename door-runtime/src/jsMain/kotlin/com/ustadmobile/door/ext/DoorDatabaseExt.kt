package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorSqlDatabase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Worker
import wrappers.IndexedDb
import wrappers.SQLiteDatasourceJs
import kotlin.js.json
import kotlin.reflect.KClass

/**
 * Get the database type that is running on the given database (DoorDbType.SQLITE Or DoorDbType.POSTGRES)
 */
actual fun DoorDatabase.dbType(): Int {
    return DoorDbType.SQLITE
}

actual fun DoorDatabase.dbSchemaVersion(): Int = this.dbVersion

/**
 * Run a transaction within a suspend coroutine context.
 */
actual suspend inline fun <T : DoorDatabase, R> T.doorWithTransaction(crossinline block: suspend (T) -> R): R {
    return block(this)
}

actual fun DoorSqlDatabase.dbType(): Int {
    return DoorDbType.SQLITE
}

/**
 * Init work that will only really be done for the real database implementation class (not the repo, syncreadonlywrapper, etc)
 */
fun DoorDatabase.init(dbName: String, webWorkerPath: String) {
    //dataSource = SQLiteDatasourceJs(dbName, Worker(webWorkerPath))
//    GlobalScope.launch {
//        val exists = IndexedDb.checkIfExists(dbName)
//        if(exists){
//            dataSource.loadDbFromIndexedDb()
//        }else{
//            createAllTables()
//        }
//        initCompletable.complete(true)
//    }
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
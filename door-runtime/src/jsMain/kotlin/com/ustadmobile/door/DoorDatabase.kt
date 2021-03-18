package com.ustadmobile.door

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Runnable
import wrappers.SqliteWorkerManager

actual abstract class DoorDatabase actual constructor() {

    internal lateinit var workerManager: SqliteWorkerManager

    val initCompletable = CompletableDeferred<Boolean>()

    fun openConnection() {

    }

    private suspend fun awaitReady() {
        initCompletable.await()
    }

    open suspend fun createAllTables() {
        //Generated code will actually run this
    }

    suspend fun doSomeQuery(sql: String) {
        awaitReady()

        //do the query
    }


    actual abstract fun clearAllTables()

    actual open fun runInTransaction(runnable: Runnable) {
    }

}
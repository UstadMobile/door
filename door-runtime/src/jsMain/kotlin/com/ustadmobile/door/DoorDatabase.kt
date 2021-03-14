package com.ustadmobile.door

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Runnable
import org.w3c.dom.Worker

actual abstract class DoorDatabase actual constructor() {

    internal lateinit var worker: Worker

    lateinit var dbName: String

    val initCompletable = CompletableDeferred<Boolean>()

    fun openConnection() {

    }

    suspend fun awaitReady() {
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
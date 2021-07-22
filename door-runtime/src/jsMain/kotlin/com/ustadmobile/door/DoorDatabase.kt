package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import wrappers.SQLiteDatasourceJs

actual abstract class DoorDatabase actual constructor(): DoorDatabaseCommon() {

    override val jdbcDbType: Int
        get() = DoorDbType.SQLITE

    override val jdbcArraySupported: Boolean
        get() = false

    internal lateinit var webWorkerPath: String

    val initCompletable = CompletableDeferred<Boolean>()

    private suspend fun awaitReady() {
        initCompletable.await()
    }

    actual abstract fun clearAllTables()

    actual override fun runInTransaction(runnable: Runnable) {
        super.runInTransaction(runnable)
    }

    protected fun setupFromDataSource() {
        TODO("Implement on JS by converting its builder to being async")
    }
}
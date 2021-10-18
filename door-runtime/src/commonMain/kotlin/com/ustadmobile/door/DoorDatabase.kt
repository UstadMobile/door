package com.ustadmobile.door

import kotlinx.coroutines.Runnable


expect abstract class DoorDatabase {

    abstract fun clearAllTables()

    open fun runInTransaction(runnable: Runnable)

}
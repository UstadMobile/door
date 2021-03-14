package com.ustadmobile.door

import kotlinx.coroutines.Runnable

actual abstract class DoorDatabase actual constructor() {
    actual abstract fun clearAllTables()
    actual open fun runInTransaction(runnable: Runnable) {
    }

}
package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

suspend fun DoorDatabase.waitUntil(timeout: Long, tableNames: List<String>, checker: () -> Boolean) {
    val completableDeferred = CompletableDeferred<Boolean>()
    val changeListener = DoorDatabase.ChangeListenerRequest(tableNames) {
        if(checker())
            completableDeferred.complete(true)
    }

    addChangeListener(changeListener)
    changeListener.onChange(tableNames)
    withTimeoutOrNull(timeout) { completableDeferred.await() }

    removeChangeListener(changeListener)
}
package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.door.DoorObserver
import com.ustadmobile.door.doorMainDispatcher
import kotlinx.coroutines.*

suspend fun <T> DoorLiveData<T>.waitUntilWithTimeout(timeout: Long, checker: (T) -> Boolean) : T {
    val completable = CompletableDeferred<T>()

    val observer = DoorObserver<T> {
        if(checker(it))
            completable.complete(it)
    }

    withContext(doorMainDispatcher()) {
        observeForever(observer)
    }

    try {
        return withTimeout(timeout) {
            completable.await()
        }
    }finally {
        withContext(doorMainDispatcher()) {
            removeObserver(observer)
        }
    }
}

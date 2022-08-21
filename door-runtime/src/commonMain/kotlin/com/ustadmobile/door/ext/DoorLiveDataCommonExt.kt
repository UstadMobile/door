package com.ustadmobile.door.ext

import com.ustadmobile.door.lifecycle.LiveData
import com.ustadmobile.door.lifecycle.Observer
import com.ustadmobile.door.doorMainDispatcher
import kotlinx.coroutines.*

suspend fun <T> LiveData<T>.waitUntilWithTimeout(timeout: Long, checker: (T) -> Boolean) : T {
    val completable = CompletableDeferred<T>()

    val observer = Observer<T> {
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

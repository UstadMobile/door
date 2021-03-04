package com.ustadmobile.door

import kotlinx.coroutines.CompletableDeferred

fun <T: Any?> DoorLiveData<T>.observe(lifecycleOwner: DoorLifecycleOwner, observer: (T?) -> Unit) {
    this.observe(lifecycleOwner, object : DoorObserver<T?> {
        override fun onChanged(t: T?) {
            observer.invoke(t)
        }
    })
}

class ObserverFnWrapper<T>(val observerFn: (T) -> Unit): DoorObserver<T> {
    override fun onChanged(t: T) {
        observerFn.invoke(t)
    }
}

/**
 * Suspend function that will wait for the first onChanged call to the observerm and then returns
 * the value.
 */
suspend fun <T> DoorLiveData<T>.getFirstValue(): T {
    val completableDeferred = CompletableDeferred<T>()

    val tmpObserver = object: DoorObserver<T> {
        override fun onChanged(t: T) {
            completableDeferred.complete(t)
        }
    }

    this.observeForever(tmpObserver)
    completableDeferred.await()
    this.removeObserver(tmpObserver)
    return completableDeferred.getCompleted()
}

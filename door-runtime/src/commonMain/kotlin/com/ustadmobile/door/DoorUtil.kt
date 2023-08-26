package com.ustadmobile.door

import com.ustadmobile.door.lifecycle.Observer
import kotlinx.coroutines.CompletableDeferred

//fun <T: Any?> LiveData<T>.observe(lifecycleOwner: LifecycleOwner, observer: (T?) -> Unit) {
//    this.observe(lifecycleOwner, object : Observer<T?> {
//        override fun onChanged(t: T?) {
//            observer.invoke(t)
//        }
//    })
//}

class ObserverFnWrapper<T>(val observerFn: (T) -> Unit): Observer<T> {
    override fun onChanged(t: T) {
        observerFn.invoke(t)
    }
}

///**
// * Suspend function that will wait for the first onChanged call to the observerm and then returns
// * the value.
// */
//suspend fun <T> LiveData<T>.getFirstValue(): T {
//    val completableDeferred = CompletableDeferred<T>()
//
//    val tmpObserver = object: Observer<T> {
//        override fun onChanged(t: T) {
//            completableDeferred.complete(t)
//        }
//    }
//
//    this.observeForever(tmpObserver)
//    completableDeferred.await()
//    this.removeObserver(tmpObserver)
//    return completableDeferred.getCompleted()
//}

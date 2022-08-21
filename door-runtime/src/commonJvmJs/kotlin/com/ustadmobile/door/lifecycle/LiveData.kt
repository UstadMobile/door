package com.ustadmobile.door.lifecycle

import com.ustadmobile.door.ext.concurrentSafeListOf
import kotlinx.atomicfu.atomic

actual abstract class LiveData<T> {

    private val value = atomic<T?>(null)

    private val activeObservers = concurrentSafeListOf<Observer<in T>>()

    private var active: Boolean = false

    private var initialValueLoaded: Boolean = false

    private val lifecycleObservers = mutableMapOf<Observer<in T>, Pair<LifecycleOwner, LifecycleObserver>>()

    actual constructor()

    constructor(value: T) {
        this.value.value = value
        initialValueLoaded = true
    }

    /**
     * When observe is called, this internal class is used to observe a given LifecycleOwner so that we can track if it
     * is active or not
     */
    inner class InnerLifecycleObserver(val observer: Observer<in T>): DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            addActiveObserver(observer)
        }

        override fun onStop(owner: LifecycleOwner) {
            removeActiveObserver(observer)
        }
    }

    private fun addActiveObserver(observer: Observer<in T>) {
        activeObservers.add(observer)

        //call onactive... that should then post the value out...
        if(!active) {
            active = true
            onActive()
        }

        if(initialValueLoaded) {
            if(initialValueLoaded && value.value == null) {
                //If initial value has been set as loaded, but it is null, the type arg must be nullable. The only
                //option here is to use an unchecked cast.
                @Suppress("UNCHECKED_CAST")
                val nullableObserver = observer as Observer<T?>
                nullableObserver.onChanged(null)
            }else {
                value.value?.let { observer.onChanged(it) }
            }
        }
    }

    private fun removeActiveObserver(observer: Observer<in T>) {
        if(activeObservers.remove(observer) && activeObservers.isEmpty()) {
            onInactive()
        }
    }

    actual open fun observe(lifecycleOwner: LifecycleOwner, observer: Observer<in T>) {
        if(lifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            addActiveObserver(observer)
        }

        val lifecycleObserver = InnerLifecycleObserver(observer)
        lifecycleObservers[observer] = Pair(lifecycleOwner, lifecycleObserver)
        lifecycleOwner.getLifecycle().addObserver(lifecycleObserver)
    }

    actual open fun observeForever(observer: Observer<in T>) {
        addActiveObserver(observer)
    }

    actual open fun removeObserver(observer: Observer<in T>) {
        removeActiveObserver(observer)
        val observedLifecycle = lifecycleObservers[observer]
        observedLifecycle?.first?.getLifecycle()?.removeObserver(observedLifecycle.second)
    }



    actual open fun getValue(): T?  = value.value

    protected actual open fun onActive() {

    }

    protected actual open fun onInactive() {

    }

    protected actual open fun postValue(value: T) {
        this.value.value = value
        initialValueLoaded = true
        activeObservers.forEach { it.onChanged(value) }
    }

    actual open fun hasActiveObservers(): Boolean = activeObservers.isNotEmpty()

}
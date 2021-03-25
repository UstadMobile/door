package com.ustadmobile.door

actual abstract class DoorLiveData<T> {

    private val valueRef = mutableMapOf<String,T>()

    private val activeObservers = mutableListOf<DoorObserver<in T>>()

    private var active: Boolean = false

    private var initialValueLoaded: Boolean = false

    private val lifecycleObservers = mutableMapOf<DoorObserver<in T>, Pair<DoorLifecycleOwner, LifecycleObserver>>()

    actual constructor()

    constructor(value: T) {
        this.valueRef[VALUE_REF] = value
        initialValueLoaded = true
    }

    inner class LifecycleObserver(val observer: DoorObserver<in T>): DoorLifecycleObserver() {

        override fun onStart(owner: DoorLifecycleOwner) {
            addActiveObserver(observer)
        }

        override fun onStop(owner: DoorLifecycleOwner) {
            removeActiveObserver(observer)
        }
    }

    private fun addActiveObserver(observer: DoorObserver<in T>) {
        activeObservers.add(observer)

        //call onactive... that should then post the value out...
        if(!active) {
            active = true
            onActive()
        }

        if(initialValueLoaded) {
            valueRef[VALUE_REF]?.let { observer.onChanged(it) }
        }
    }

    private fun removeActiveObserver(observer: DoorObserver<in T>) {
        if(activeObservers.remove(observer) && activeObservers.isEmpty()) {
            onInactive()
        }
    }

    actual open fun observe(lifecycleOwner: DoorLifecycleOwner, observer: DoorObserver<in T>) {
        if(lifecycleOwner.currentState >= DoorLifecycleObserver.STARTED) {
            addActiveObserver(observer)
        }

        val lifecycleObserver = LifecycleObserver(observer)
        lifecycleObservers[observer] = Pair(lifecycleOwner, lifecycleObserver)
        lifecycleOwner.addObserver(lifecycleObserver)
    }

    actual open fun observeForever(observer: DoorObserver<in T>) {
        addActiveObserver(observer)
    }

    actual open fun removeObserver(observer: DoorObserver<in T>) {
        removeActiveObserver(observer)
        val observedLifecycle = lifecycleObservers[observer]
        if(observedLifecycle!= null) {
            observedLifecycle.first.removeObserver(observedLifecycle.second)
        }
    }



    actual open fun getValue(): T?  = valueRef[VALUE_REF]

    protected open fun onActive() {

    }

    protected open fun onInactive() {

    }

    protected fun postValue(value: T) {
        valueRef[VALUE_REF] = value
        initialValueLoaded = true
        activeObservers.forEach { it.onChanged(value) }
    }

    actual open fun hasActiveObservers(): Boolean = activeObservers.isNotEmpty()

    companion object {
        const val VALUE_REF = "value_ref"
    }
}
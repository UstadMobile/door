package com.ustadmobile.door.lifecycle

expect abstract class LiveData<T: Any?>() {

    open fun observe(lifecycleOwner: LifecycleOwner, observer: Observer<in T>)

    open fun observeForever(observer: Observer<in T>)

    open fun removeObserver(observer: Observer<in T>)

    open fun getValue(): T?

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    protected open fun postValue(value: T)

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    protected open fun setValue(value: T)

    open fun hasActiveObservers(): Boolean

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    protected open fun onActive()

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    protected open fun onInactive()
}

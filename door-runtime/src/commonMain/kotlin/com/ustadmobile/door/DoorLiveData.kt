package com.ustadmobile.door



expect abstract class DoorLiveData<T>() {

    open fun observe(lifecycleOwner: DoorLifecycleOwner, observer: DoorObserver<in T>)

    open fun observeForever(observer: DoorObserver<in T>)

    open fun removeObserver(observer: DoorObserver<in T>)

    open fun getValue(): T?

    open fun hasActiveObservers(): Boolean

}
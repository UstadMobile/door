package com.ustadmobile.door

actual abstract class DoorLiveData<T> actual constructor() {
    actual open fun observe(
        lifecycleOwner: DoorLifecycleOwner,
        observer: DoorObserver<in T>
    ) {
    }

    actual open fun observeForever(observer: DoorObserver<in T>) {
    }

    actual open fun removeObserver(observer: DoorObserver<in T>) {
    }

    actual open fun getValue(): T? {
        TODO("Not yet implemented")
    }

    actual open fun hasActiveObservers(): Boolean {
        TODO("Not yet implemented")
    }

}
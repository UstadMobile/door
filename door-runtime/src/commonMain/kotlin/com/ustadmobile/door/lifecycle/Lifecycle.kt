package com.ustadmobile.door.lifecycle

expect abstract class Lifecycle {

    abstract fun addObserver(observer: LifecycleObserver)

    abstract fun removeObserver(observer: LifecycleObserver)

}
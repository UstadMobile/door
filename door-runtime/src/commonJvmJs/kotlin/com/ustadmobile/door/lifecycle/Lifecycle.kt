package com.ustadmobile.door.lifecycle

actual abstract class Lifecycle {

    abstract val realCurrentDoorState: DoorState

    actual abstract fun addObserver(observer: LifecycleObserver)

    actual abstract fun removeObserver(observer: LifecycleObserver)

}
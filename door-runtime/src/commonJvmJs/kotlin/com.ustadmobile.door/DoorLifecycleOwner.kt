package com.ustadmobile.door

actual interface DoorLifecycleOwner {

    val currentState: Int

    fun addObserver(observer: DoorLifecycleObserver)

    fun removeObserver(observer: DoorLifecycleObserver)

}
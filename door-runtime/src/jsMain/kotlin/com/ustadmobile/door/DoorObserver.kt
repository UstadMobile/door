package com.ustadmobile.door

actual interface DoorObserver<T> {
    actual fun onChanged(t: T)

}
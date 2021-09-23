package com.ustadmobile.door

actual fun interface DoorObserver<T> {

    actual fun onChanged(t: T)

}
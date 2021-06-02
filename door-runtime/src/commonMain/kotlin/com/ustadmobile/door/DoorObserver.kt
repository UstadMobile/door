package com.ustadmobile.door

expect fun interface DoorObserver<T> {

    fun onChanged(t: T)

}

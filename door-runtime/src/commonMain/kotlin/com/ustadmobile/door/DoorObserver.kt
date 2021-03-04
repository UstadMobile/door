package com.ustadmobile.door

expect interface DoorObserver<T> {

    fun onChanged(t: T)

}

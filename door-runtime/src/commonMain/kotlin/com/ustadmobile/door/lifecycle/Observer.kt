package com.ustadmobile.door.lifecycle

expect fun interface Observer<T> {

    fun onChanged(value: T)

}
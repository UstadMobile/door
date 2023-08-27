package com.ustadmobile.door.lifecycle

actual fun interface Observer<T> {

    actual fun onChanged(value: T)

}
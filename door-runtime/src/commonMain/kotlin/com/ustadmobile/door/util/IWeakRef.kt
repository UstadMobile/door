package com.ustadmobile.door.util

/**
 * Basic multiplatform WeakReference wrapper. Implemented by WeakReference on JVM,
 */
interface IWeakRef<T: Any> {

    fun get(): T?

}
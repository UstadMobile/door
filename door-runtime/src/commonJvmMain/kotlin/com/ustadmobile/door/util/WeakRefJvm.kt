package com.ustadmobile.door.util

import java.lang.ref.WeakReference

class WeakRefJvm<T: Any>(target: T): IWeakRef<T> {

    private val weakRef = WeakReference(target)

    override fun get() = weakRef.get()

}
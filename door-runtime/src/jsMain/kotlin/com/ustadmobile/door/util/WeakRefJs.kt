package com.ustadmobile.door.util

import js.core.WeakRef

class WeakRefJs<T: Any>(target: T) : IWeakRef<T> {

    private val weakRef =  WeakRef(target)

    override fun get() = weakRef.deref()

}
package com.ustadmobile.door.util

import js.memory.WeakRef

class WeakRefJs<T: Any>(target: T) : IWeakRef<T> {

    private val weakRef =  WeakRef(target)

    override fun get() = weakRef.deref()

}
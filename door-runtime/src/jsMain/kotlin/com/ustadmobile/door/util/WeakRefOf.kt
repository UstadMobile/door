package com.ustadmobile.door.util

actual fun <T: Any> weakRefOf(target: T): IWeakRef<T> = WeakRefJs(target)


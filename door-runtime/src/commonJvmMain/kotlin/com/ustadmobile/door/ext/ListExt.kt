package com.ustadmobile.door.ext

import java.util.concurrent.CopyOnWriteArrayList

actual fun <T> concurrentSafeListOf(vararg items: T) = CopyOnWriteArrayList<T>(items) as MutableList<T>

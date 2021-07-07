package com.ustadmobile.door.ext

/**
 * Expect/actual to provide a mutable list that is safe for concurrent use cases. On JS this is
 * simply a normal list (as Javascript is single threaded). On Android/JVM this is CopyOnWriteArrayList
 */
actual fun <T> concurrentSafeListOf(vararg items: T): MutableList<T> {
    return mutableListOf<T>().also {
        it.addAll(items)
    }
}
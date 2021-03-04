package com.ustadmobile.door.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

actual fun <T> threadSafeListOf(vararg items: T): MutableList<T> = CopyOnWriteArrayList(items)

actual fun <K, V> threadSafeMapOf(vararg  items: Pair<K, V>): MutableMap<K, V> {
    return ConcurrentHashMap<K, V>().also {
        it.putAll(items.toMap())
    }
}
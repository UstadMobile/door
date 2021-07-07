package com.ustadmobile.door.util

actual fun <T> threadSafeListOf(vararg items: T): MutableList<T> {
    return mutableListOf<T>().also {
        it.addAll(items)
    }
}

actual fun <K, V> threadSafeMapOf(vararg items: Pair<K, V>): MutableMap<K, V> {
    return mutableMapOf<K, V>().also {
        it.putAll(items)
    }
}
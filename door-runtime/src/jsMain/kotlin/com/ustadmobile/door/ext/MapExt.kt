package com.ustadmobile.door.ext

/**
 * Create a concurrent safe map. On JVM/Android this is a ConcurrentHashMap
 */
actual fun <K, V> concurrentSafeMapOf(vararg pairs: Pair<K, V>): MutableMap<K, V> {
    return mutableMapOf<K, V>().also {
        it.putAll(pairs)
    }
}
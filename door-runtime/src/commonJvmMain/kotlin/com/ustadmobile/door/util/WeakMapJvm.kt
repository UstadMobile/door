package com.ustadmobile.door.util

import java.util.*

/**
 * Simple wrapper for IWeakMap for JVM and Android using the WeakHashMap
 */
class WeakMapJvm<K: Any, V>: IWeakMap<K, V> {

    private val weakMap = Collections.synchronizedMap(WeakHashMap<K, V>())

    override fun get(key: K): V? {
        return weakMap[key]
    }

    override fun set(key: K, value: V) {
        weakMap[key] = value
    }

    override fun remove(key: K): V? {
        return weakMap.remove(key)
    }

    override fun containsKey(key: K): Boolean {
        return weakMap.containsKey(key)
    }
}
package com.ustadmobile.door.util

/**
 * Interface for a minimal WeakMap that can be supported using the underlying platforms. Javascript WeakMap does not
 * allow iteration on keys so containsKey, size, etc will not work.
 */
interface IWeakMap<K: Any, V> {

    operator fun get(key: K): V?

    operator fun set(key: K, value: V)

    fun remove(key: K): V?

    fun containsKey(key: K): Boolean

}
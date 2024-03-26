package com.ustadmobile.door.util

import js.collections.WeakMap

/**
 * Implementation of IWeakMap for Javascript using the Javascript native WeakMap.
 *
 * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WeakMap
 */
class WeakMapJs<K: Any, V> : IWeakMap<K, V> {

    private val mWeakMap = WeakMap<K, V>(emptyArray())

    override fun get(key: K): V? {
        return mWeakMap[key]
    }

    override fun set(key: K, value: V) {
        mWeakMap[key] = value
    }

    override fun remove(key: K): V? {
        val prevVal = get(key)
        mWeakMap.delete(key)
        return prevVal
    }

    override fun containsKey(key: K): Boolean {
        return mWeakMap.has(key)
    }
}
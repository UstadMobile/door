package com.ustadmobile.door.ext

actual fun <K, V> concurrentSafeMapOf(vararg items: Pair<K, V>) : MutableMap<K, V> {
    return mutableMapOf<K, V>().also {
        it.putAll(items)
    }
}
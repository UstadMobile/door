package com.ustadmobile.door.ext

import java.util.concurrent.ConcurrentHashMap

actual fun <K, V> concurrentSafeMapOf(vararg items: Pair<K, V>): MutableMap<K, V> = ConcurrentHashMap<K, V>(items.size).apply {
    putAll(items)
}

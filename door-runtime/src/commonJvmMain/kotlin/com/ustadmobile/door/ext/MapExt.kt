package com.ustadmobile.door.ext
import java.util.concurrent.ConcurrentHashMap

actual fun <K, V> concurrentSafeMapOf(vararg pairs: Pair<K, V>) : MutableMap<K, V> = ConcurrentHashMap<K, V>(pairs.size).apply {
    putAll(pairs)
}

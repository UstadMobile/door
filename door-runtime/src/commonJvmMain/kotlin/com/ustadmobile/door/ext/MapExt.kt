package com.ustadmobile.door.ext

import com.ustadmobile.door.util.IWeakMap
import com.ustadmobile.door.util.WeakMapJvm
import java.util.concurrent.ConcurrentHashMap

actual fun <K, V> concurrentSafeMapOf(vararg items: Pair<K, V>): MutableMap<K, V> = ConcurrentHashMap<K, V>(items.size).apply {
    putAll(items)
}

actual fun <K: Any, V> weakMapOf(): IWeakMap<K, V> {
    return WeakMapJvm()
}

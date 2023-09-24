package com.ustadmobile.door.ext

import com.ustadmobile.door.util.IWeakMap
import com.ustadmobile.door.util.WeakMapJs

actual fun <K, V> concurrentSafeMapOf(vararg items: Pair<K, V>) : MutableMap<K, V> {
    return mutableMapOf<K, V>().also {
        it.putAll(items)
    }
}

actual fun <K: Any, V> weakMapOf(): IWeakMap<K, V> {
    return WeakMapJs()
}

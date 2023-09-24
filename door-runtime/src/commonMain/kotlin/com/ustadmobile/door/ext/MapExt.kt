package com.ustadmobile.door.ext

import com.ustadmobile.door.util.IWeakMap

expect fun <K, V> concurrentSafeMapOf(vararg items: Pair<K, V>): MutableMap<K, V>

expect fun <K: Any, V> weakMapOf(): IWeakMap<K, V>

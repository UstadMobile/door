package com.ustadmobile.door.ext

expect fun <K, V> concurrentSafeMapOf(vararg items: Pair<K, V>): MutableMap<K, V>

expect fun <K, V> weakMapOf(): MutableMap<K, V>

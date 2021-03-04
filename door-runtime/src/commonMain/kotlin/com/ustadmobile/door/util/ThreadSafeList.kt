package com.ustadmobile.door.util

expect fun <T> threadSafeListOf(vararg items: T): MutableList<T>

expect fun <K, V> threadSafeMapOf(vararg  items: Pair<K, V>): MutableMap<K, V>

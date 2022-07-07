package com.ustadmobile.door.ext

import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

actual fun <T> concurrentSafeListOf(vararg items: T) = CopyOnWriteArrayList<T>(items) as MutableList<T>

actual fun <T> mutableLinkedListOf(vararg  items: T) = LinkedList<T>(items.toList()) as MutableList<T>

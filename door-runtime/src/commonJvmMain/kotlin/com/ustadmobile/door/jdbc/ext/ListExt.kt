package com.ustadmobile.door.jdbc.ext

import java.util.*

actual fun <T> mutableLinkedListOf(vararg  items: T) = LinkedList<T>(items.toList()) as MutableList<T>

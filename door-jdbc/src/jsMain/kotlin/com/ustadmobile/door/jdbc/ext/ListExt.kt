package com.ustadmobile.door.jdbc.ext

actual fun <T> mutableLinkedListOf(vararg  items: T): MutableList<T> = mutableListOf()

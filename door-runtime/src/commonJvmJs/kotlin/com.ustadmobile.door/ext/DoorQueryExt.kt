package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorQuery

private fun Any.isArray() = this is Array<*> ||
        this is ByteArray ||
        this is ShortArray ||
        this is IntArray ||
        this is LongArray ||
        this is FloatArray ||
        this is DoubleArray

/**
 * Check if the given raw query has any list or array parameters.
 */
@Suppress("unused") //This is used generated code
fun DoorQuery.hasListOrArrayParams() = values?.asList()?.any { it is List<*> || (it?.isArray() ?: false) } ?: false


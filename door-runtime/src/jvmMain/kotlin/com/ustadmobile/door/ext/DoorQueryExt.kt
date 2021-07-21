package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorQuery

/**
 * Check if the given raw query has any list or array parameters.
 */
@Suppress("unused") //This is used generated code
fun DoorQuery.hasListOrArrayParams() = values?.asList()?.any { it is List<*> || (it?.javaClass?.isArray ?: false) } ?: false


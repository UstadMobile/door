package com.ustadmobile.door

internal expect class JdbcArrayProxy actual constructor(
    typeName: String,
    objects: kotlin.Array<out Any?>
): JdbcArrayProxyCommon

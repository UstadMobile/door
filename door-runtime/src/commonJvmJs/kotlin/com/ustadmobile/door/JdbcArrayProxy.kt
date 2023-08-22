package com.ustadmobile.door

internal expect class JdbcArrayProxy(
    typeName: String,
    objects: kotlin.Array<out Any?>
): JdbcArrayProxyCommon

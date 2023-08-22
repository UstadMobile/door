package com.ustadmobile.door

expect class JdbcArrayProxy(
    typeName: String,
    objects: kotlin.Array<out Any?>
): JdbcArrayProxyCommon

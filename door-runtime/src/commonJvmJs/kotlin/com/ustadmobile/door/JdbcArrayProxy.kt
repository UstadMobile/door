package com.ustadmobile.door

@Suppress("RemoveRedundantQualifierName") //Should show what is a normal system array vs. what is a JDBC array
internal expect class JdbcArrayProxy(
    typeName: String,
    objects: kotlin.Array<out Any?>
): JdbcArrayProxyCommon

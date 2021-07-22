package com.ustadmobile.door

internal actual class JdbcArrayProxy actual constructor(
    typeName: String,
    objects: kotlin.Array<out Any?>
): JdbcArrayProxyCommon(typeName, objects) {

    override fun free() {

    }

}
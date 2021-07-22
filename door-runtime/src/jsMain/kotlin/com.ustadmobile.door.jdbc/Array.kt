package com.ustadmobile.door.jdbc

actual interface Array {

    actual fun getBaseTypeName(): String

    actual fun getBaseType(): Int

    actual fun getArray(): Any

    actual fun free()

}

package com.ustadmobile.door.jdbc

expect interface Array {

    fun getBaseTypeName(): String

    fun getBaseType(): Int

    fun getArray(): Any

    fun free()

}
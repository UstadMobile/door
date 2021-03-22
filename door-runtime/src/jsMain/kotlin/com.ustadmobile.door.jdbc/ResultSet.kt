package com.ustadmobile.door.jdbc

actual interface ResultSet {

    actual fun next(): Boolean

    actual fun getString(columnName: String): String?

    actual fun close()
}
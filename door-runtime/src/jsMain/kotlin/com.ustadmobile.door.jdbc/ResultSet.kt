package com.ustadmobile.door.jdbc

actual interface ResultSet {

    actual fun next(): Boolean

    actual fun getString(columnIndex: Int): String?

    actual fun close()
}
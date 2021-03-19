package com.ustadmobile.door.jdbc

actual interface ResultSet {

    actual fun next(): Boolean

    actual fun getString(index: Int): String

    actual fun close()
}
package com.ustadmobile.door.jdbc

expect interface ResultSet {

    fun next(): Boolean

    fun getString(columnIndex: Int): String?

    fun close()
}
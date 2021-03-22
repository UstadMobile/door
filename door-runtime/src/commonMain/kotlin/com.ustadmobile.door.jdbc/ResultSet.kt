package com.ustadmobile.door.jdbc

expect interface ResultSet {

    fun next(): Boolean

    fun getString(columnName: String): String?

    fun close()
}
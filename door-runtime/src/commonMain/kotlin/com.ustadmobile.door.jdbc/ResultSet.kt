package com.ustadmobile.door.jdbc

expect interface ResultSet {

    fun next(): Boolean

    fun getString(index: Int): String

    fun close()
}
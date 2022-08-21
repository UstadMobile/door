package com.ustadmobile.door.jdbc

expect interface ResultSetMetaData {

    fun getColumnCount(): Int

    fun getColumnLabel(column: Int): String
}
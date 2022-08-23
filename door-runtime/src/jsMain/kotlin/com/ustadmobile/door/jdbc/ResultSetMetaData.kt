package com.ustadmobile.door.jdbc

actual interface ResultSetMetaData {

    actual fun getColumnCount(): Int

    actual fun getColumnLabel(column: Int): String

}
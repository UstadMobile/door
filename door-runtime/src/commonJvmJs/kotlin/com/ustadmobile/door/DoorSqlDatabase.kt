package com.ustadmobile.door

actual interface DoorSqlDatabase  {

    actual fun execSQL(sql: String)

    fun execSQLBatch(statements: Array<String>)

    val dbTypeInt: Int

}
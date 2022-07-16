package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection

actual interface DoorSqlDatabase  {

    actual fun execSQL(sql: String)

    fun execSQLBatch(statements: Array<String>)

    val dbTypeInt: Int

    val connection: Connection

}
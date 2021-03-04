package com.ustadmobile.door

import java.sql.Connection
import java.sql.PreparedStatement

actual interface DoorQuery {

    actual fun getSql(): String

    actual fun getArgCount(): Int

    val values: Array<out Any?>?

    fun bindToPreparedStmt(stmt: PreparedStatement, db: DoorDatabase, con: Connection)


}
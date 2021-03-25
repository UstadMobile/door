package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual class SimpleDoorQuery actual constructor(private val sql: String, override val values: Array<out Any?>?) :
    DoorQuery {
    override fun getSql() = sql

    override fun getArgCount(): Int {
        TODO("Not yet implemented")
    }


    override fun bindToPreparedStmt(stmt: PreparedStatement, db: DoorDatabase, con: Connection) {}


}
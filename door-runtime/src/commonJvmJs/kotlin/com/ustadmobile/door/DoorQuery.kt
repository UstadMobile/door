package com.ustadmobile.door

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual interface DoorQuery {

    actual val sql: String

    actual val argCount: Int

    val values: Array<out Any?>?

    fun bindToPreparedStmt(stmt: PreparedStatement, db: RoomDatabase)


}
package com.ustadmobile.door

import androidx.room.RoomDatabase
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual interface DoorQuery {

    actual fun getSql(): String

    actual fun getArgCount(): Int

    val values: Array<out Any?>?

    fun bindToPreparedStmt(stmt: PreparedStatement, db: RoomDatabase)


}
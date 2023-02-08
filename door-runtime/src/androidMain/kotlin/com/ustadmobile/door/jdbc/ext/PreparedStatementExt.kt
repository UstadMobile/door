package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import androidx.room.withTransaction
import com.ustadmobile.door.roomjdbc.ConnectionRoomJdbc

actual suspend fun PreparedStatement.executeQueryAsyncKmp(): ResultSet {
    val roomDb = (connection as ConnectionRoomJdbc).roomDb

    return roomDb.withTransaction {
        executeQuery()
    }
}

actual suspend fun PreparedStatement.executeUpdateAsyncKmp(): Int {
    val roomDb = (connection as ConnectionRoomJdbc).roomDb

    return roomDb.withTransaction {
        executeUpdate()
    }
}


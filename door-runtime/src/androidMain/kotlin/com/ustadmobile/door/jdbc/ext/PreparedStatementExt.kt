package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import androidx.room.CoroutinesRoom
import com.ustadmobile.door.roomjdbc.ConnectionRoomJdbc
import java.util.concurrent.Callable

actual suspend fun PreparedStatement.executeQueryAsyncKmp(): ResultSet {
    val roomDb = (connection as ConnectionRoomJdbc).roomDb
    val callable = Callable {
        executeQuery()
    }

    return CoroutinesRoom.execute(roomDb, true, callable)
}

actual suspend fun PreparedStatement.executeUpdateAsyncKmp(): Int {
    val roomDb = (connection as ConnectionRoomJdbc).roomDb
    val callable = Callable {
        executeUpdate()
    }
    return CoroutinesRoom.execute(roomDb, true, callable)
}


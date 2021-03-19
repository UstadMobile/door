package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import wrappers.SQLiteConnectionJs
import wrappers.SQLitePreparedStatementJs

actual suspend fun PreparedStatement.executeQueryAsync() {
    return SQLitePreparedStatementJs().executeQueryAsyncInt()
}
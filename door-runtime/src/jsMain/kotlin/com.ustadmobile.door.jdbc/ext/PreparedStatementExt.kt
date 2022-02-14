package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.Statement

actual suspend fun Statement.executeUpdateAsync(sql: String): Int {
    return executeUpdateAsyncJs(sql)
}

actual suspend fun PreparedStatement.executeQueryAsyncKmp(): ResultSet {
    return executeQueryAsyncInt()
}

actual suspend fun PreparedStatement.executeUpdateAsyncKmp(): Int {
    return executeUpdateAsync()
}

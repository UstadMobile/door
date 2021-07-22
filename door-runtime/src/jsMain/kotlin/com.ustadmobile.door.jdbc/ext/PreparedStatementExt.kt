package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet


actual suspend fun PreparedStatement.executeQueryAsyncKmp(): ResultSet {
    return executeQueryAsyncInt()
}

actual suspend fun PreparedStatement.executeUpdateAsyncKmp(): Int {
    return executeUpdateAsync()
}

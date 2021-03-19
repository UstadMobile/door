package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet


actual suspend fun PreparedStatement.executeQueryAsync(): ResultSet {
    return executeQueryAsync()
}
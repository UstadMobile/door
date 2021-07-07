package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun PreparedStatement.executeQueryAsync(): ResultSet {
    return withContext(Dispatchers.IO) { executeQuery() }
}
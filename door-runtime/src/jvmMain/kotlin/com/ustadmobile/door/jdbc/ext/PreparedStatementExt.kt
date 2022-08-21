package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.Statement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


actual suspend fun PreparedStatement.executeQueryAsyncKmp(): ResultSet {
    return withContext(Dispatchers.IO) { executeQuery() }
}

actual suspend fun PreparedStatement.executeUpdateAsyncKmp(): Int {
    return withContext(Dispatchers.IO) { executeUpdate() }
}



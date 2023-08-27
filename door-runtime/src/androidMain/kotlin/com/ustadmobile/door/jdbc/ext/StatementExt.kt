package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.Statement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun Statement.executeUpdateAsync(sql: String): Int {
    return withContext(Dispatchers.IO) {
        executeUpdate(sql)
    }
}

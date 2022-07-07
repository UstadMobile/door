package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.Statement

expect suspend fun Statement.executeUpdateAsync(sql: String): Int

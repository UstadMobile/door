package com.ustadmobile.door.jdbc.ext

import com.ustadmobile.door.jdbc.PreparedStatement
import com.ustadmobile.door.jdbc.ResultSet
import com.ustadmobile.door.jdbc.Statement

expect suspend fun Statement.executeUpdateAsync(sql: String): Int

expect suspend fun PreparedStatement.executeQueryAsyncKmp(): ResultSet

expect suspend fun PreparedStatement.executeUpdateAsyncKmp(): Int

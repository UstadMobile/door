package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabaseCommon
import com.ustadmobile.door.DoorSqlDatabase

actual fun DoorSqlDatabase.dbType(): Int = (this as DoorDatabaseCommon.DoorSqlDatabaseImpl).jdbcDbType


actual fun DoorSqlDatabase.execSqlBatch(statements: Array<String>) {
    execSQLBatch(statements)
}

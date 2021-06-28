package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorSqlDatabase

actual fun DoorSqlDatabase.dbType(): Int = (this as DoorDatabase.DoorSqlDatabaseImpl).jdbcDbType


actual fun DoorSqlDatabase.execSqlBatch(statements: Array<String>) {
    execSQLBatch(statements)
}

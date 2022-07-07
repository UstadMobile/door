package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorSqlDatabase

actual fun DoorSqlDatabase.dbType(): Int = this.dbTypeInt


actual fun DoorSqlDatabase.execSqlBatch(statements: Array<String>) {
    execSQLBatch(statements)
}

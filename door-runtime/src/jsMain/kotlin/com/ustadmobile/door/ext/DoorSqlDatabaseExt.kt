package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorSqlDatabase


/**
 * Execute a batch of SQL statements. This extension function deliberately uses SQL in lower case to avoid a name clash
 */
actual fun DoorSqlDatabase.execSqlBatch(statements: Array<String>) {
    execSQLBatch(statements)
}

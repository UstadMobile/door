package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorSqlDatabase

expect fun DoorSqlDatabase.dbType(): Int

/**
 * Execute a batch of SQL statements. This extension function deliberately uses SQL in lower case to avoid a name clash
 */
expect fun DoorSqlDatabase.execSqlBatch(statements: Array<String>)

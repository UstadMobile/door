package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorSqlDatabase

actual fun DoorSqlDatabase.dbType(): Int = DoorDbType.SQLITE

actual fun DoorSqlDatabase.execSqlBatch(statements: Array<String>) {
    try {
        beginTransaction()
        statements.forEach {
            execSQL(it)
        }
        setTransactionSuccessful()
    }finally {
        endTransaction()
    }
}
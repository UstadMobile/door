package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDatabase

actual fun DoorDatabase.dbType(): Int = this.jdbcDbType

actual fun DoorDatabase.dbSchemaVersion(): Int = this.dbVersion

actual suspend inline fun <T: DoorDatabase, R> T.doorWithTransaction(crossinline block: suspend(T) -> R): R {
    //TODO: In next version, actually start a transaction here
    return block(this)
}

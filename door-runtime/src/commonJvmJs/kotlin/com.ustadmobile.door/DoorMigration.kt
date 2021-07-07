package com.ustadmobile.door

actual abstract class DoorMigration actual constructor(actual val startVersion: Int, actual val endVersion: Int) {

    actual abstract fun migrate(database: DoorSqlDatabase)

}
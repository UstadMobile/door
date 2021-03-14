package com.ustadmobile.door

actual abstract class DoorMigration {
    actual val startVersion: Int
        get() = TODO("Not yet implemented")
    actual val endVersion: Int
        get() = TODO("Not yet implemented")

    actual constructor(startVersion: Int, endVersion: Int) {
        TODO("Not yet implemented")
    }

    actual abstract fun migrate(database: DoorSqlDatabase)

}
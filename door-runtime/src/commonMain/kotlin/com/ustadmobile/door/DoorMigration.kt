package com.ustadmobile.door

expect abstract class DoorMigration {

    val startVersion: Int

    val endVersion: Int

    constructor(startVersion: Int, endVersion: Int)

    abstract fun migrate(database: DoorSqlDatabase)

}
package com.ustadmobile.door.migration

sealed class DoorMigration {

    abstract val startVersion: Int

    abstract val endVersion: Int

}
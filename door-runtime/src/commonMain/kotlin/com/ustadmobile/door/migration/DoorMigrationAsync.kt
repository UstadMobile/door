package com.ustadmobile.door.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.DoorSqlDatabase

class DoorMigrationAsync(
    override val startVersion: Int,
    override val endVersion: Int,
    val migrateFn: suspend (DoorSqlDatabase) -> Unit
): DoorMigration() {

}
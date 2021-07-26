package com.ustadmobile.door.migration

import com.ustadmobile.door.DoorSqlDatabase

class DoorMigrationSync(
    override val startVersion: Int,
    override val endVersion: Int,
    val migrateFn: (DoorSqlDatabase) -> Unit
): DoorMigration() {

}
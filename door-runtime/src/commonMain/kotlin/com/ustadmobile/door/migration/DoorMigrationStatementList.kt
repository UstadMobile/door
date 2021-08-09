package com.ustadmobile.door.migration

import com.ustadmobile.door.DoorSqlDatabase

class DoorMigrationStatementList(
    override val startVersion: Int,
    override val endVersion: Int,
    val migrateStmts: (DoorSqlDatabase) -> List<String>
) : DoorMigration()

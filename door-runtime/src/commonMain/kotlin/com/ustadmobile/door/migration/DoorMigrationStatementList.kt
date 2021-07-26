package com.ustadmobile.door.migration

class DoorMigrationStatementList(
    override val startVersion: Int,
    override val endVersion: Int,
    val migrateStmts: () -> List<String>
) : DoorMigration(){
}
package com.ustadmobile.door

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.ext.execSqlBatch
import com.ustadmobile.door.migration.DoorMigration
import com.ustadmobile.door.migration.DoorMigrationAsync
import com.ustadmobile.door.migration.DoorMigrationStatementList
import com.ustadmobile.door.migration.DoorMigrationSync
import kotlinx.coroutines.runBlocking

fun DoorMigration.asRoomMigration(): Migration = MigrationAdapter(this)

class MigrationAdapter(
    private val doorMigration: DoorMigration
): Migration(doorMigration.startVersion, doorMigration.endVersion) {
    override fun migrate(database: SupportSQLiteDatabase) {
        when(doorMigration) {
            is DoorMigrationAsync -> {
                runBlocking { doorMigration.migrateFn(database) }
            }
            is DoorMigrationSync -> {
                doorMigration.migrateFn(database)
            }
            is DoorMigrationStatementList -> {
                database.execSqlBatch(*doorMigration.migrateStmts(database).toTypedArray())
            }
        }
    }
}
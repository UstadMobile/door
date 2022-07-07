package androidx.room

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.RoomDatabaseJdbcImplHelper
import com.ustadmobile.door.RoomJdbcImpl
import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.jdbc.SQLException
import kotlinx.coroutines.Runnable

abstract class RoomDatabase() {

    inner class SupportSQLiteDatabaseImpl : SupportSQLiteDatabase {

        override fun execSQL(sql: String) {
            execSQLBatch(sql)
        }

        //val dbType: Int =

    }

    internal val sqlDatabaseImpl = SupportSQLiteDatabaseImpl()

    abstract fun createAllTables(): List<String>

    abstract fun clearAllTables()

    abstract val dbVersion: Int

    abstract val invalidationTracker: InvalidationTracker

    open fun runInTransaction(runnable: Runnable) {

    }

    /**
     * Execute a batch of SQL Statements in a transaction. This is generally much faster
     * than executing statements individually.
     */
    open fun execSQLBatch(vararg sqlStatements: String) {
        val doorRoomImpl = this as RoomJdbcImpl
        doorRoomImpl.jdbcImplHelper.useConnection { connection ->
            connection.setAutoCommit(false)
            connection.createStatement().useStatement { statement ->
                sqlStatements.forEach { sql ->
                    try {
                        statement.executeUpdate(sql)
                    }catch(eInner: SQLException) {
                        throw eInner
                    }
                }
            }
            connection.commit()
        }
    }



}
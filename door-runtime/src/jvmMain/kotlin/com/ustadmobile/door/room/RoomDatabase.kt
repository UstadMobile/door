package com.ustadmobile.door.room

import com.ustadmobile.door.ext.rootDatabase
import com.ustadmobile.door.jdbc.SQLException
import java.util.concurrent.Callable
import com.ustadmobile.door.jdbc.ext.useStatement

actual abstract class RoomDatabase actual constructor() {


    abstract fun createAllTables(): List<String>

    actual abstract fun clearAllTables()

    abstract val dbVersion: Int

    actual open val invalidationTracker: InvalidationTracker
        get() = throw IllegalAccessError("getInvalidationTracker: maybe override this in the generated version")

    open fun runInTransaction(runnable: Runnable) {
        runnable.run()
    }

    open fun <V> runInTransaction(callable: Callable<V>): V {
        return callable.call()
    }


    /**
     * Execute a batch of SQL Statements in a transaction. This is generally much faster
     * than executing statements individually.
     */
    open fun execSQLBatch(vararg sqlStatements: String) {
        val doorRoomImpl = rootDatabase as RoomJdbcImpl
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
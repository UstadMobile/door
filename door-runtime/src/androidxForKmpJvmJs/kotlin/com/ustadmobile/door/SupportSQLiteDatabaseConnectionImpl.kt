package com.ustadmobile.door

import androidx.sqlite.db.SupportSQLiteDatabase
import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.jdbc.Connection

class SupportSQLiteDatabaseConnectionImpl(
    private val connection: Connection
): SupportSQLiteDatabase {
    override fun execSQL(sql: String) {
        connection.createStatement().useStatement {
            it.executeUpdate(sql)
        }
    }

    override fun beginTransaction() {
        connection.setAutoCommit(false)
    }

    override fun setTransactionSuccessful() {
        connection.commit()
    }

    override fun endTransaction() {

    }
}
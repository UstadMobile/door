package com.ustadmobile.door

import com.ustadmobile.door.jdbc.ext.useStatement
import com.ustadmobile.door.jdbc.Connection

class DoorSqlDatabaseConnectionImpl(
    override val connection: Connection,
    override val dbTypeInt: Int,
) : DoorSqlDatabase {

    override fun execSQL(sql: String) {
        connection.createStatement().useStatement {
            it.executeUpdate(sql)
        }
    }

    override fun execSQLBatch(statements: Array<String>) {
        connection.createStatement().useStatement {
            connection.setAutoCommit(false)
            statements.forEach { sql ->
                it.executeUpdate(sql)
            }
            connection.commit()
        }
    }

}
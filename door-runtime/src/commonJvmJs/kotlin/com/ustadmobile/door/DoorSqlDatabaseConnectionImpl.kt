package com.ustadmobile.door

import com.ustadmobile.door.ext.useStatement
import com.ustadmobile.door.jdbc.Connection

class DoorSqlDatabaseConnectionImpl(private val connection: Connection) : DoorSqlDatabase {

    override val dbTypeInt: Int by lazy {
        DoorDbType.typeIntFromProductName(connection.getMetaData().getDatabaseProductName())
    }

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
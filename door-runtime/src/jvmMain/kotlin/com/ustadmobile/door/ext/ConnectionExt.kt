package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.PreparedStatementArrayProxy
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual fun Connection.prepareStatement(
    db: RoomDatabase,
    stmtConfig: PreparedStatementConfig
): PreparedStatement {
    val pgSql = stmtConfig.postgreSql
    val sqlToUse = if(pgSql != null && db.dbType() == DoorDbType.POSTGRES ) {
        pgSql
    }else {
        stmtConfig.sql
    }

    return when {
        !stmtConfig.hasListParams -> prepareStatement(sqlToUse, stmtConfig.generatedKeys)
        db.arraySupported -> prepareStatement(sqlToUse.adjustQueryWithSelectInParam(db.dbType()))
        else -> PreparedStatementArrayProxy(sqlToUse, this)
    }
}

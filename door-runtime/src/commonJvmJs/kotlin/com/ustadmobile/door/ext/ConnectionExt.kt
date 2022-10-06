package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.PreparedStatementArrayProxy
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual fun Connection.prepareStatement(
    db: RoomDatabase,
    stmtConfig: PreparedStatementConfig
): PreparedStatement {
    val sqlToUse = stmtConfig.sqlToUse(db.dbType())

    return when {
        !stmtConfig.hasListParams -> prepareStatement(sqlToUse, stmtConfig.generatedKeys)
        db.arraySupported -> prepareStatement(sqlToUse.adjustQueryWithSelectInParam(db.dbType()),
            stmtConfig.generatedKeys)
        else -> PreparedStatementArrayProxy(sqlToUse, this)
    }
}


actual suspend fun Connection.prepareStatementAsyncOrFallback(
    db: RoomDatabase,
    stmtConfig: PreparedStatementConfig,
): PreparedStatement {
    val sqlToUse = stmtConfig.sqlToUse(db.dbType())

    return when {
        !stmtConfig.hasListParams -> prepareStatementAsyncOrFallback(sqlToUse, stmtConfig.generatedKeys)
        db.arraySupported -> prepareStatementAsyncOrFallback(sqlToUse.adjustQueryWithSelectInParam(db.dbType()),
            stmtConfig.generatedKeys)
        else -> PreparedStatementArrayProxy(sqlToUse, this)
    }
}

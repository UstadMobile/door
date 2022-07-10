package com.ustadmobile.door.ext

import androidx.room.RoomDatabase
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual fun Connection.prepareStatement(
    db: RoomDatabase,
    stmtConfig: PreparedStatementConfig
): PreparedStatement {

    return when {
        !stmtConfig.hasListParams -> prepareStatement(stmtConfig.sql, stmtConfig.generatedKeys)
        else -> TODO("prepareStatement(db, stmtConfig): list params on Android unsupported!")
    }
}

package com.ustadmobile.door.ext

import androidx.room.RoomDatabase
import com.ustadmobile.door.PreparedStatementArrayProxy
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

actual fun Connection.prepareStatement(
    db: RoomDatabase,
    stmtConfig: PreparedStatementConfig
): PreparedStatement {
    return if(stmtConfig.hasListParams) {
        PreparedStatementArrayProxy(stmtConfig.sql, this)
    }else {
        prepareStatement(stmtConfig.sql, stmtConfig.generatedKeys)
    }
}

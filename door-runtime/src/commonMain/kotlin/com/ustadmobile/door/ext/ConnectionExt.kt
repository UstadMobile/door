package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.PreparedStatement

/**
 * Extension function that will produce a PreparedStatement given a database
 * object and PreparedStatementConfig. It will choose the correct SQL to use
 * (e.g. normal SQLite or Postgres if that is specified separately) and it will
 * use the PreparedStatementArrayProxy if needed.
 */
expect fun Connection.prepareStatement(
    db: RoomDatabase,
    stmtConfig: PreparedStatementConfig
): PreparedStatement



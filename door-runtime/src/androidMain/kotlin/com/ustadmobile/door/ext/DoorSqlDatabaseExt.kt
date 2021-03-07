package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorSqlDatabase

actual fun DoorSqlDatabase.dbType(): Int = DoorDbType.SQLITE

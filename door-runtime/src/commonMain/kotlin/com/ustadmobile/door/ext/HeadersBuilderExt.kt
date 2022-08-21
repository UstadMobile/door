package com.ustadmobile.door.ext

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorConstants
import io.ktor.http.HeadersBuilder

fun HeadersBuilder.appendDbVersionHeader(db: RoomDatabase) {
    append(DoorConstants.HEADER_DBVERSION, "${db.dbSchemaVersion()}")
}
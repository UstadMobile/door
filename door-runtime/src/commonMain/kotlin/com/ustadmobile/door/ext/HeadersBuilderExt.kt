package com.ustadmobile.door.ext

import androidx.room.RoomDatabase
import com.ustadmobile.door.DoorConstants
import io.ktor.http.HeadersBuilder

fun HeadersBuilder.appendDbVersionHeader(db: RoomDatabase) {
    append(DoorConstants.HEADER_DBVERSION, "${db.dbSchemaVersion()}")
}
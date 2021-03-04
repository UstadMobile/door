package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDatabase
import io.ktor.http.HeadersBuilder

fun HeadersBuilder.appendDbVersionHeader(db: DoorDatabase) {
    append(DoorConstants.HEADER_DBVERSION, "${db.dbSchemaVersion()}")
}
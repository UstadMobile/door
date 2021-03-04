package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDatabase
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

fun HttpRequestBuilder.dbVersionHeader(db: DoorDatabase) {
    this.header(DoorConstants.HEADER_DBVERSION, db.dbSchemaVersion())
}
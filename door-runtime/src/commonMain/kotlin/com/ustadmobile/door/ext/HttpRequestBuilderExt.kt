package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

fun HttpRequestBuilder.dbVersionHeader(db: DoorDatabase) {
    this.header(DoorConstants.HEADER_DBVERSION, db.dbSchemaVersion())
}

/**
 * Append the client ID header
 */
fun HttpRequestBuilder.doorNodeIdHeader(repo: DoorDatabaseRepository) {
    header(DoorConstants.HEADER_NODE, "${repo.config.nodeId}/${repo.config.auth}")
}

/**
 * Add required headers: version and nodeid / auth
 */
fun HttpRequestBuilder.doorNodeAndVersionHeaders(repo: DoorDatabaseRepository) {
    dbVersionHeader(repo.db)
    doorNodeIdHeader(repo)
}

package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseRepository.Companion.PATH_REPLICATION
import com.ustadmobile.door.ext.*
import io.ktor.client.request.*
import io.ktor.http.*

suspend inline fun DoorDatabaseRepository.put(
    repEndpointName: String,
    tableId: Int,
    block: HttpRequestBuilder.() -> Unit = {}
) {
    config.httpClient.put {
        url {
            takeFrom(this@put.config.endpoint)
            encodedPath = "${encodedPath}$PATH_REPLICATION/$repEndpointName"
        }

        parameter("tableId", tableId)
        doorNodeIdHeader(this@put)
        dbVersionHeader(this@put.db)
        block()
    }
}


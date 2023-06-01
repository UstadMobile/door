package com.ustadmobile.door.replication

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_CHECK_FOR_ENTITIES_ALREADY_RECEIVED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATIONS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_FIND_PENDING_REPLICATION_TRACKERS
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_MARK_REPLICATE_TRACKERS_AS_PROCESSED
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_RECEIVE_ENTITIES
import com.ustadmobile.door.DoorDatabaseRepository.Companion.PATH_REPLICATION
import com.ustadmobile.door.IncomingReplicationEvent
import com.ustadmobile.door.attachments.JsonEntityWithAttachment
import com.ustadmobile.door.attachments.downloadAttachments
import com.ustadmobile.door.attachments.uploadAttachment
import com.ustadmobile.door.ext.*
import io.github.aakira.napier.Napier
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.math.max
import kotlin.math.min

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


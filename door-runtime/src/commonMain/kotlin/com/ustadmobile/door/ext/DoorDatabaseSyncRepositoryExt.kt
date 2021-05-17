//We don't directly use the receiver - but send updates should only be called on DoorDatabaseSyncRepository
@file:Suppress("unused")

package com.ustadmobile.door.ext

import io.github.aakira.napier.Napier
import com.ustadmobile.door.*
import com.ustadmobile.door.attachments.EntityWithAttachment
import com.ustadmobile.door.attachments.downloadAttachments
import com.ustadmobile.door.attachments.uploadAttachment
import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.entities.UpdateNotificationSummary
import com.ustadmobile.door.util.systemTimeInMillis
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * This is used by the generated SyncDao Repository when it is implementing
 * _findDevicesToNotify(EntityName) - the function which is called by dispatchUpdateNotifications
 * in order to generate UpdateNotification entities.
 */
fun DoorDatabaseSyncRepository.sendUpdates(tableId: Int, updateNotificationManager: ServerUpdateNotificationManager?,
                                           findDevicesFn: () -> List<UpdateNotificationSummary>)
        : List<UpdateNotificationSummary> {

    val devicesToNotify = findDevicesFn()
    if(devicesToNotify.isEmpty()) {
        Napier.d("[SyncRepo@${this.doorIdentityHashCode}]: sendUpdates: Table #$tableId has no devices to notify")
        return listOf()
    }

    Napier.v("[SyncRepo@${this.doorIdentityHashCode}]: sendUpdates: Table #$tableId needs to notify ${devicesToNotify.joinToString()}.",
        tag= DoorTag.LOG_TAG)

    val timeNow = systemTimeInMillis()
    val updateNotifications = devicesToNotify.map {
        UpdateNotification(pnDeviceId = it.deviceId, pnTableId = it.tableId, pnTimestamp = timeNow)
    }

    syncHelperEntitiesDao.replaceUpdateNotifications(updateNotifications)
    updateNotificationManager?.onNewUpdateNotifications(updateNotifications)
    Napier.v("[SyncRepo@${this.doorIdentityHashCode}] replaced update notifications " +
            "and informed updatenotificationmanager: $updateNotificationManager", tag = DoorTag.LOG_TAG)


    return devicesToNotify
}

/**
 * Runs a block that syncs the given entity if it is in the list of tablesToSync
 * or tablesToSync is null (which means sync all entities)
 */
suspend inline fun DoorDatabaseSyncRepository.runEntitySyncIfRequired(tablesToSync: List<Int>?, tableId : Int,
                                           allResults: MutableList<SyncResult>,
                                           crossinline block: suspend DoorDatabaseSyncRepository.() -> SyncResult) {
    if(tablesToSync == null || tableId in tablesToSync) {
        allResults += block()
    }
}

/**
 * Records the overall result of the syncrun based on all results from all tables that sync was
 * attempted for.
 */
suspend fun DoorDatabaseSyncRepository.recordSyncRunResult(allResults: List<SyncResult>) {
    val syncStatus = if(allResults.all { it.status == SyncResult.STATUS_SUCCESS}) {
        SyncResult.STATUS_SUCCESS
    }else {
        SyncResult.STATUS_FAILED
    }

    syncHelperEntitiesDao.insertSyncResult(SyncResult(status = syncStatus,
            timestamp = systemTimeInMillis()))
}

/**
 * This is the main entity sync function that is called by generated code. It will use function
 * parameters to get a list of new entities from the remote endpoint, send acknowledgements for
 * entities received, and then send entities that have been changed locally to the remote server.
 *
 * @param tableId the SyncableEntity tableId that the sync is running for
 * @param syncSettings SyncSettings object that provides a few params on running the sync
 * @param receiveRemoteEntitiesFn a function that will retrieve new entities from the server (this
 * will be a function on the generated SyncDao)
 * @param findLocalUnsentEntitiesFn a function that will find entities that have been changed locally
 * that need to be sent to the remote server
 * @param entityToAckFn a function that wil turn a List of the given entity into a list of EntityAck
 * @param entityToTrkFn a function that will convert a list of the entity itself into the tracker entity (_trk)
 * @param storeTrkFn a function that will insert / replace the trk entity locally
 * @param entityToEntityWithAttachmentFn a function that will convert a given entity into an
 * an EntityWithAttachment if the entity has attachments, or null otherwise
 */
suspend inline fun <reified T:Any, reified K: Any> DoorDatabaseSyncRepository.syncEntity(tableId: Int,
    syncSettings: SyncSettings,
    receiveRemoteEntitiesFn: suspend (maxResults: Int) -> List<T>,
    storeEntitiesFn: suspend (List<T>) -> Unit,
    findLocalUnsentEntitiesFn: suspend (maxResults: Int) -> List<T>,
    entityToAckFn: (entities: List<T>, primary: Boolean) -> List<EntityAck>,
    entityToTrkFn: (entities: List<T>, primary: Boolean) -> List<K>,
    storeTrkFn: suspend (List<K>) -> Unit,
    entityToEntityWithAttachmentFn: (T) -> EntityWithAttachment?): SyncResult {

    val dbName = this::class.simpleName?.removeSuffix("_Repo")
    val daoName = dbName + "SyncDao"
    val entityName =  T::class.simpleName

    Napier.d("SyncRepo: start sync of ${T::class.simpleName} ")

    var newEntities: List<T>
    var entitiesReceived = 0
    do {
        newEntities = receiveRemoteEntitiesFn(syncSettings.receiveBatchSize)

        if(newEntities.isNotEmpty()) {
            downloadAttachments(newEntities.mapNotNull { entityToEntityWithAttachmentFn(it) })

            storeEntitiesFn(newEntities)
            handleSyncEntitiesReceived(T::class, newEntities)

            val entityAcks = entityToAckFn(newEntities, true)

            Napier.d("DAONAME=$daoName / ${this::class.simpleName}")
            config.httpClient.postEntityAck(entityAcks, config.endpoint,
                    "$dbPath/${daoName}/_ack${T::class.simpleName}Received", this)
            entitiesReceived += newEntities.size
        }
    }while(newEntities.size == syncSettings.receiveBatchSize)


    var entitiesSent = 0
    var localUnsentEntities: List<T>
    do {
        localUnsentEntities = findLocalUnsentEntitiesFn(syncSettings.sendBatchSize)

        if(localUnsentEntities.isNotEmpty()) {
            //if the entity has attachments, upload those before sending the actual entity data
            val attachmentsToUpload = localUnsentEntities.mapNotNull { entityToEntityWithAttachmentFn(it) }
            attachmentsToUpload.filter { it.attachmentUri != null }.forEach {
                uploadAttachment(it)
            }

            config.httpClient.post<Unit> {
                url {
                    takeFrom(config.endpoint)
                    encodedPath = "$encodedPath$dbPath/$daoName/_replace${entityName}"
                }

                dbVersionHeader(db)
                body = defaultSerializer().write(localUnsentEntities,
                        ContentType.Application.Json.withUtf8Charset())
            }

            storeTrkFn(entityToTrkFn(localUnsentEntities, false))
            entitiesSent += localUnsentEntities.size
        }
    }while(localUnsentEntities.size == syncSettings.sendBatchSize)

    val result = SyncResult(tableId = tableId, received = newEntities.size,
            sent = localUnsentEntities.size, status = SyncResult.STATUS_SUCCESS)

    Napier.d("SyncRepo: ${T::class.simpleName} DONE - Received $entitiesReceived / Sent ${result.sent}")
    syncHelperEntitiesDao.insertSyncResult(result)

    return result
}
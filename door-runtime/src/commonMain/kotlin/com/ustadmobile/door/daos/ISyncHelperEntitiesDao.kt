package com.ustadmobile.door.daos

import com.ustadmobile.door.SyncResult
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.entities.TableSyncStatus
import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.entities.ZombieAttachmentData

/**
 * Interface to describe queries available in SyncHelperEntitiesDao.
 */
interface ISyncHelperEntitiesDao {

    /**
     * This will be implemented by generated code to run the query. It will find a list of all
     * pending UpdateNotification entities for the given deviceId (e.g. used to find the backlog
     * of notifications when a client subscribes to events).
     */
    suspend fun findPendingUpdateNotifications(deviceId: Int): List<UpdateNotification>

    /**
     * This will delete the matching UpdateNotification. It should be called after an update notification
     * has been delivered to the client (via the client making an http request answered by
     * lib-door-runtime RespondUpdateNotifications.respondUpdateNotificationReceived).
     *
     * Note this is not done using the actual notification uid because this is not known when
     * the server sends it live
     *
     * @param deviceId The deviceid as per the UpdateNotification
     * @param tableId The tableId as per the UpdateNotification
     * @param lastModTimestamp The pnTimestamp as per the UpdateNotification
     */
    suspend fun deleteUpdateNotification(deviceId: Int, tableId: Int, lastModTimestamp: Long)

    /**
     * Delete the ChangeLogs for the given table. This should be called after all notifyOnUpdate
     * queries for the table in question have been run
     */
    fun deleteChangeLogs(tableId: Int)

    /**
     * This will be implemented by generated code to run the query. It will find a list of any
     * tableIds that have pending ChangeLog items that should be sent to dispatchUpdateNotifications.
     * This is used on startup to find any changes that happen when ChangeLogMonitor was not running.
     *
     * @return A list of tableIds for which there are pending ChangeLogs
     */
    suspend fun findTablesWithPendingChangeLogs(): List<Int>


    /**
     * Find a list of tables that need to be sync'd (e.g. those that have changed more recently than
     * a sync has been completed)
     */
    fun findTablesToSync(): List<TableSyncStatus>

    /**
     * Mark the given table id as having been changed at the specified time. This will be used by
     * the ClientSyncManager to determine which tables need to be synced.
     */
    suspend fun updateTableSyncStatusLastChanged(tableId: Int, lastChanged: Long)

    suspend fun updateTableSyncStatusLastSynced(tableId: Int, lastSynced: Long)

    /**
     * Get the clientId for this instance of the database
     */
    fun findSyncNodeClientId(): Int

    /**
     * Insert a SyncResult entity (logging success or failure of a sync run)
     */
    suspend fun insertSyncResult(syncResult: SyncResult)

    /**
     * Replace/update UpdateNotification objects. If there is an existing update notification pending
     * for the same device id and table id, then the existing one will be replaced/updated
     */
    fun replaceUpdateNotifications(entities: List<UpdateNotification>)

    /**
     * Find Zombie Attachment data (e.g. where an entity has been updated and the old attachment
     * data is no longer required). This is a list of attachment uris that should be deleted from the
     * disk.
     */
    suspend fun findZombieAttachments(tableName: String, primaryKey: Long) : List<ZombieAttachmentData>

    /**
     * Delete from the Zombie Attachment table. This should be called once the attachment file has
     * been deleted from the disk. This will remove it from the table.
     */
    suspend fun deleteZombieAttachments(zombieList: List<ZombieAttachmentData>)

    /**
     * Get the auth string for the given DoorNode
     * @param nodeId
     */
    suspend fun getDoorNodeAuth(nodeId: Int): String?

    /**
     * Add a new DoorNode
     */
    suspend fun addDoorNode(doorNode: DoorNode)


}
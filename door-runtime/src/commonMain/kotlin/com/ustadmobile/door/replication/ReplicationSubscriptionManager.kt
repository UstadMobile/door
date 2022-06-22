package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorConstants
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorDatabaseRepository.Companion.ENDPOINT_SUBSCRIBE_SSE
import com.ustadmobile.door.DoorDatabaseRepository.Companion.PATH_REPLICATION
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.entities.DoorNode
import com.ustadmobile.door.entities.ReplicationStatus
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp
import com.ustadmobile.door.sse.*
import com.ustadmobile.door.util.systemTimeInMillis
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.serialization.json.Json
import kotlin.jvm.Volatile
import kotlin.math.min
import kotlin.reflect.KClass
import com.ustadmobile.door.ext.toUrlQueryString

//Class that is responsible to listen for local and remote changes, then send/receive replication accordingly.
// There will be one repository instance per endpoint (e.g. for the cloud and any local mirrors).
// ReplicationSubscriptionManager will listen for local changes using the ReplicationNotificationDispatcher, will track
// status using ReplicationStatus entities, and then call sendPendingReplications.

// ReplicationSubscriptionManager will also subscribe for serversentevents from the endpoint to receive notifications
// of remote changes, and will then call fetchPendingReplications as needed.

class ReplicationSubscriptionManager(
    private val dbSchemaVersion: Int,
    private val json: Json,
    private val dbNotificationDispatcher: ReplicationNotificationDispatcher,
    private val repository: DoorDatabaseRepository,
    private val coroutineScope: CoroutineScope,
    private val dbMetadata: DoorDatabaseMetadata<*>,
    private val dbKClass: KClass<out DoorDatabase>,
    private val numProcessors: Int = 5,
    private val eventSourceFactory: DoorEventSourceFactory = DefaultDoorEventSourceFactoryImpl(),
    private val sendReplicationRunner: ReplicateRunner = DefaultReplicationSender(json),
    private val fetchReplicationRunner: ReplicateRunner = DefaultReplicationFetcher(json),
    /**
     * Event handler that will be called when the subscription is initialized. This can be useful to help setup
     * remote replication (e.g. to know the node id of the primary remote sever).
     */
    @Volatile
    var onSubscriptionInitialized : SubscriptionInitializedListener? = null
): DoorEventListener, ReplicationPendingListener {

    val logPrefix: String
        get() = "ReplicationSubscriptionManager for $repository"

    fun interface SubscriptionInitializedListener {
        suspend fun onSubscriptionInitialized(repo: DoorDatabaseRepository, remoteNodeId: Long)
    }

    fun interface ReplicateRunner {
        suspend fun replicate(repo: DoorDatabaseRepository, tableId: Int, remoteNodeId: Long)
    }

    private class DefaultReplicationSender(private val json: Json): ReplicateRunner {
        override suspend fun replicate(repo: DoorDatabaseRepository, tableId: Int, remoteNodeId: Long) {
            repo.sendPendingReplications(json, tableId, remoteNodeId)
        }
    }

    private class DefaultReplicationFetcher(private val json: Json): ReplicateRunner {
        override suspend fun replicate(repo: DoorDatabaseRepository, tableId: Int, remoteNodeId: Long) {
            repo.fetchPendingReplications(json, tableId, remoteNodeId)
        }
    }

    @Volatile
    private var eventSource: DoorEventSource? = null

    private val queueProcessor: AtomicRef<ReceiveChannel<ReplicationStatus>?> = atomic(null)

    private val checkQueueSignal = Channel<Boolean>(Channel.UNLIMITED)

    private val activeTables = concurrentSafeListOf<Int>()

    private val remoteNodeId = atomic(0L)

    @Volatile
    private var initCompletable = CompletableDeferred<Boolean>()

    private var replicationSupervisor: ReplicationSubscriptionSupervisor? = null

    var enabled: Boolean
        get() = eventSource != null
        set(value) {
            if(value) {
                //start the subscription to the endpoint. When the endpoint replies, it can provide the node id as a header or
                // as a message itself.
                Napier.i("$logPrefix : enabling", tag = DoorTag.LOG_TAG)
                val queryParams = mapOf(
                    DoorConstants.HEADER_DBVERSION to dbSchemaVersion.toString(),
                    DoorConstants.HEADER_NODE to "${repository.config.nodeId}/${repository.config.auth}")

                if(eventSource == null) {
                    eventSource = eventSourceFactory.makeNewDoorEventSource(repository.config,
                        "${repository.config.endpoint}$PATH_REPLICATION/$ENDPOINT_SUBSCRIBE_SSE?${queryParams.toUrlQueryString()}",
                        this)
                }

                checkQueueSignal.trySend(true)
            }else {
                Napier.i("$logPrefix : disabling")
                eventSource?.close()
                eventSource = null
            }
        }

    init {
        if(repository.config.replicationSubscriptionMode == ReplicationSubscriptionMode.AUTO) {
            replicationSupervisor = ReplicationSubscriptionSupervisor(this, repository)
        }
    }

    override fun onOpen() {
        initCompletable = CompletableDeferred()
    }

    private suspend fun findTablesToReplicate() : List<ReplicationStatus>{
        return repository.db.prepareAndUseStatementAsync(
            """
            SELECT ReplicationStatus.* 
              FROM ReplicationStatus
             WHERE ((lastRemoteChangeTime > lastFetchReplicationCompleteTime)
                    OR (lastLocalChangeTime > lastSendReplicationCompleteTime))
               AND nodeId = ? 
               AND priority = (
                   SELECT COALESCE((
                           SELECT MIN(RepStatusInternal.priority)
                             FROM ReplicationStatus RepStatusInternal
                            WHERE ((RepStatusInternal.lastRemoteChangeTime > RepStatusInternal.lastFetchReplicationCompleteTime)
                                    OR (RepStatusInternal.lastLocalChangeTime > RepStatusInternal.lastSendReplicationCompleteTime))
                              AND RepStatusInternal.nodeId = ?), ${ReplicateEntity.LOWEST_PRIORITY})
                   ) 
             LIMIT ?   
            """
        ) { stmt ->
            stmt.setLong(1, remoteNodeId.value)
            stmt.setLong(2, remoteNodeId.value)
            stmt.setInt(3, numProcessors * 2)
            stmt.executeQueryAsyncKmp().useResults { it.mapRows { resultSet ->
                ReplicationStatus().apply {
                    repStatusId = resultSet.getInt("repStatusId")
                    tableId = resultSet.getInt("tableId")
                    nodeId = resultSet.getLong("nodeId")
                    lastRemoteChangeTime = resultSet.getLong("lastRemoteChangeTime")
                    lastFetchReplicationCompleteTime = resultSet.getLong("lastFetchReplicationCompleteTime")
                    lastLocalChangeTime = resultSet.getLong("lastLocalChangeTime")
                    lastSendReplicationCompleteTime = resultSet.getLong("lastSendReplicationCompleteTime")
                }
            } }
        }
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.produceJobs() = produce {
        do {
            Napier.d("$logPrefix checking queue")
            checkQueueSignal.receive()
            if(enabled) {
                val numProcessorsAvailable = numProcessors - activeTables.size
                if(numProcessorsAvailable > 0) {
                    val tablesToReplicate = findTablesToReplicate().filter {
                        it.tableId !in activeTables
                    }
                    Napier.d("$logPrefix: produceJobs need to replicate table ids " +
                            "#${tablesToReplicate.joinToString{ it.tableId.toString() }}", tag = DoorTag.LOG_TAG)

                    val numTablesToSend = min(numProcessorsAvailable, tablesToReplicate.size)
                    for(i in 0 until numTablesToSend){
                        activeTables += tablesToReplicate[i].tableId
                        send(tablesToReplicate[i])
                    }
                }
            }
        }while(coroutineContext.isActive)
    }

    /**
     * Create a ReplicationStatus entity for each tableId where it does not yet exist for tracking the given remote node
     * id
     */
    private suspend fun initReplicationStatus() {
        val remoteNodeIdVal = remoteNodeId.value
        repository.db.withDoorTransactionAsync(dbKClass) { transactionDb ->
            transactionDb.prepareAndUseStatementAsync("""
                INSERT INTO ReplicationStatus (tableId, priority, nodeId, lastRemoteChangeTime, lastFetchReplicationCompleteTime, lastLocalChangeTime, lastSendReplicationCompleteTime)
                SELECT ? AS tableId, ? as priority, ? AS nodeId, ? AS lastRemoteChangeTime, ? AS lastFetchReplicationCompleteTime, ? AS lastLocalChangeTime, ? AS lastSendReplicationCompleteTime
                WHERE NOT EXISTS(
                      SELECT RepStatusInternal.tableId 
                        FROM ReplicationStatus RepStatusInternal
                       WHERE RepStatusInternal.tableId = ? 
                         AND RepStatusInternal.nodeId = ?)
            """) { stmt ->
                dbMetadata.replicateEntities.values.forEach { repEntity ->
                    stmt.setInt(1, repEntity.tableId)
                    stmt.setInt(2, repEntity.priority)
                    stmt.setLong(3, remoteNodeIdVal)
                    stmt.setLong(4, 0)
                    stmt.setLong(5, 0)
                    stmt.setLong(6, 0)
                    stmt.setLong(7, 0)
                    stmt.setInt(8, repEntity.tableId)
                    stmt.setLong(9, remoteNodeIdVal)

                    stmt.executeUpdateAsyncKmp()
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.launchProcessor(id: Int, channel: ReceiveChannel<ReplicationStatus>) = launch {
        for(item in channel) {
            Napier.d("$logPrefix processor $id: Processing ${item.tableId}")
            if(item.lastLocalChangeTime > item.lastSendReplicationCompleteTime) {
                Napier.d("$logPrefix processor $id table ${item.tableId} has replications to send",
                    tag = DoorTag.LOG_TAG)
                val timeNow = systemTimeInMillis()
                try {
                    sendReplicationRunner.replicate(repository, item.tableId, item.nodeId)
                    Napier.d("$logPrefix processor $id table ${item.tableId} replications sent")
                    repository.db.prepareAndUseStatementAsync(
                        "UPDATE ReplicationStatus SET lastSendReplicationCompleteTime = ? WHERE tableId = ? AND nodeId = ?") { stmt ->
                        stmt.setLong(1, timeNow)
                        stmt.setInt(2, item.tableId)
                        stmt.setLong(3, remoteNodeId.value)

                        stmt.executeUpdateAsyncKmp()
                    }
                }catch(e: Exception) {
                    Napier.e("$logPrefix processor $id table ${item.tableId} EXCEPTION sending replication", e,
                        tag = DoorTag.LOG_TAG)
                    delay(1000)
                }
            }

            if(item.lastRemoteChangeTime > item.lastFetchReplicationCompleteTime) {
                try {
                    Napier.d("$logPrefix processor $id table ${item.tableId} has replications to fetch",
                        tag = DoorTag.LOG_TAG)
                    val timeNow = systemTimeInMillis()

                    fetchReplicationRunner.replicate(repository, item.tableId, item.nodeId)
                    Napier.d("$logPrefix processor $id table ${item.tableId} replications fetch complete")

                    repository.db.prepareAndUseStatementAsync(
                        "UPDATE ReplicationStatus SET lastFetchReplicationCompleteTime = ? WHERE tableId = ? AND nodeId = ?") { stmt ->
                        stmt.setLong(1, timeNow)
                        stmt.setInt(2, item.tableId)
                        stmt.setLong(3, remoteNodeId.value)

                        stmt.executeUpdateAsyncKmp()
                    }
                }catch(e: Exception) {
                    Napier.e("$logPrefix processor $id table ${item.tableId} EXCEPTION fetching replication", e,
                        tag = DoorTag.LOG_TAG)
                    delay(1000)
                }
            }
            activeTables -= item.tableId
            checkQueueSignal.send(true)
        }
    }

    override fun onMessage(message: DoorServerSentEvent) {
        Napier.d("$logPrefix: received message: #${message.id} ${message.event} - ${message.data}")
        when(message.event) {
            EVT_INIT -> coroutineScope.launch {
                val remoteNodeIdLong = message.data.toLong()
                remoteNodeId.value = remoteNodeIdLong
                initReplicationStatus()
                onSubscriptionInitialized?.onSubscriptionInitialized(repository, remoteNodeIdLong)


                var newNode = false

                repository.db.withDoorTransactionAsync(repository.db::class.doorDatabaseMetadata().dbClass) { transactDb ->
                    if(!transactDb.selectDoorNodeExists(remoteNodeIdLong)) {
                        newNode = true
                        transactDb.insertNewDoorNode(DoorNode().also {
                            it.rel = DoorNode.SUBSCRIBED_TO
                            it.nodeId = remoteNodeIdLong
                            it.auth = null
                        })
                    }
                }



                dbNotificationDispatcher.takeIf { newNode }?.onNewDoorNode(remoteNodeIdLong, "")

                initCompletable.complete(true)
                dbNotificationDispatcher.addReplicationPendingEventListener(remoteNodeId.value,
                    this@ReplicationSubscriptionManager)

                val producer = produceJobs().also {
                    queueProcessor.value = it
                }

                repeat(numProcessors){
                    launchProcessor(it, producer)
                }
                checkQueueSignal.send(true)
            }
            EVT_INVALIDATE -> coroutineScope.launch {
                val tableIdsToInvalidate = message.data.split(",").mapNotNull { it.trim().toIntOrNull() }
                initCompletable.await()
                Napier.d("$logPrefix invalidate table ids: ${tableIdsToInvalidate.joinToString()}")
                val timeNow = systemTimeInMillis()
                repository.db.withDoorTransactionAsync(dbKClass) { transactionDb ->
                    transactionDb.prepareAndUseStatementAsync(
                        """
                    UPDATE ReplicationStatus 
                       SET lastRemoteChangeTime = ? 
                     WHERE nodeId = ? 
                       AND tableId = ?
                    """
                    ) { stmt ->
                        tableIdsToInvalidate.forEach { tableId ->
                            stmt.setLong(1, timeNow)
                            stmt.setLong(2, remoteNodeId.value)
                            stmt.setInt(3, tableId)
                            stmt.executeUpdateAsyncKmp()
                        }
                    }
                }
                checkQueueSignal.send(true)
            }
        }
    }

    //Should be called when a local change is pending for outgoing replication
    override fun onReplicationPending(event: ReplicationPendingEvent) {
        coroutineScope.launch {
            repository.db.withDoorTransactionAsync(dbKClass) { transactionDb ->
                transactionDb.prepareAndUseStatementAsync(
                    """
                    UPDATE ReplicationStatus 
                       SET lastLocalChangeTime = ? 
                     WHERE nodeId = ? 
                       AND tableId = ?
                    """
                ) { stmt ->
                    event.tableIds.forEach { tableId ->
                        stmt.setLong(1, systemTimeInMillis())
                        stmt.setLong(2, remoteNodeId.value)
                        stmt.setInt(3, tableId)
                        stmt.executeUpdateAsyncKmp()
                    }
                }
            }
            checkQueueSignal.send(true)
        }
    }

    override fun onError(e: Exception) {
        e.printStackTrace()
    }

    fun close() {
        eventSource?.close()
    }

    companion object {

        const val EVT_INIT = "INIT"

        const val EVT_INVALIDATE = "INVALIDATE"


    }
}
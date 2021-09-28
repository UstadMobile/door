package com.ustadmobile.door.replication

import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.entities.ReplicationStatus
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.jdbc.ext.executeQueryAsyncKmp
import com.ustadmobile.door.jdbc.ext.executeUpdateAsyncKmp
import com.ustadmobile.door.sse.*
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.Volatile
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KClass

//Class that is responsible to listen for local and remote changes, then send/receive replication accordingly.
// There will be one repository instance per endpoint (e.g. for the cloud and any local mirrors).
// ReplicationSubscriptionManager will listen for local changes using the ReplicationNotificationDispatcher, will track
// status using ReplicationStatus entities, and then call sendPendingReplications.

// ReplicationSubscriptionManager will also subscribe for serversentevents from the endpoint to receive notifications
// of remote changes, and will then call fetchPendingReplications as needed.

class ReplicationSubscriptionManager(
    private val dbNotificationDispatcher: ReplicationNotificationDispatcher,
    private val repository: DoorDatabaseRepository,
    private val coroutineScope: CoroutineScope,
    private val dbMetadata: DoorDatabaseMetadata<*>,
    private val dbKClass: KClass<out DoorDatabase>,
    private val numProcessors: Int = 5,
    private val eventSourceFactory: DoorEventSourceFactory = DefaultDoorEventSourceFactoryImpl(),
    private val sendReplicationRunner: ReplicateRunner = ReplicateRunner { repo, tableId -> repo.sendPendingReplications(tableId) },
    private val fetchReplicationRunner: ReplicateRunner = ReplicateRunner { repo, tableId -> repo.fetchPendingReplications(tableId) }
): DoorEventListener, ReplicationPendingListener {

    fun interface ReplicateRunner {

        suspend fun replicate(repo: DoorDatabaseRepository, tableId: Int)

    }

    private val eventSource: AtomicRef<DoorEventSource?> = atomic(null)

    private val queueProcessor: AtomicRef<ReceiveChannel<ReplicationStatus>?> = atomic(null)

    private val checkQueueSignal = Channel<Boolean>(Channel.UNLIMITED)

    private val activeTables = concurrentSafeListOf<Int>()

    init {
        //start the subscription to the endpoint. When the endpoint replies, it can provide the node id as a header or
        // as a message itself.

        eventSource.value = eventSourceFactory.makeNewDoorEventSource(repository.config,
            repository.config.endpoint + "replication/subscribe",this)

    }

    override fun onOpen() {

    }

    private suspend fun findTablesToReplicate(remoteNodeId: Long) : List<ReplicationStatus>{
        return repository.db.prepareAndUseStatementAsync(
            """
            SELECT ReplicationStatus.* 
              FROM ReplicationStatus
             WHERE ((lastRemoteChangeTime > lastFetchReplicationCompleteTime)
                OR (lastLocalChangeTime > lastSendReplicationCompleteTime))
               AND nodeId = ? 
             LIMIT ?   
            """
        ) { stmt ->
            stmt.setLong(1, remoteNodeId)
            stmt.setInt(2, numProcessors * 2)
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
    private fun CoroutineScope.produceJobs(remoteNodeId: Long) = produce<ReplicationStatus> {
        do {
            checkQueueSignal.receive()
            val numProcessorsAvailable = numProcessors - activeTables.size
            if(numProcessorsAvailable > 0) {
                val tablesToReplicate = findTablesToReplicate(remoteNodeId).filter {
                    it.tableId !in activeTables
                }

                val numTablesToSend = min(numProcessorsAvailable, tablesToReplicate.size)
                for(i in 0 until numTablesToSend){
                    activeTables += tablesToReplicate[i].tableId
                    send(tablesToReplicate[i])
                }
            }
        }while(coroutineContext.isActive)
    }

    /**
     * Create a ReplicationStatus entity for each tableId where it does not yet exist for tracking the given remote node
     * id
     */
    private suspend fun initReplicationStatus(remoteNodeId: Long) {
        val timeNow = systemTimeInMillis()
        repository.db.withDoorTransactionAsync(dbKClass) { transactionDb ->
            transactionDb.prepareAndUseStatementAsync("""
                INSERT INTO ReplicationStatus (tableId, nodeId, lastRemoteChangeTime, lastFetchReplicationCompleteTime, lastLocalChangeTime, lastSendReplicationCompleteTime)
                SELECT ? AS tableId, ? AS nodeId, ? AS lastRemoteChangeTime, ? AS lastFetchReplicationCompleteTime, ? AS lastLocalChangeTime, ? AS lastSendReplicationCompleteTime
                WHERE NOT EXISTS(
                      SELECT RepStatusInternal.tableId 
                        FROM ReplicationStatus RepStatusInternal
                       WHERE RepStatusInternal.tableId = ? 
                         AND RepStatusInternal.nodeId = ?)
            """) { stmt ->
                dbMetadata.replicateEntities.values.forEach { repEntity ->
                    stmt.setInt(1, repEntity.tableId)
                    stmt.setLong(2, remoteNodeId)
                    stmt.setLong(3, timeNow)
                    stmt.setLong(4, 0)
                    stmt.setLong(5, timeNow)
                    stmt.setLong(6, 0)
                    stmt.setInt(7, repEntity.tableId)
                    stmt.setLong(8, remoteNodeId)

                    stmt.executeUpdateAsyncKmp()
                }
            }
        }
    }

    @ExperimentalCoroutinesApi
    private fun CoroutineScope.launchProcessor(id: Int, nodeId: Long, channel: ReceiveChannel<ReplicationStatus>) = launch {
        for(item in channel) {
            if(item.lastLocalChangeTime > item.lastSendReplicationCompleteTime) {
                val timeNow = systemTimeInMillis()
                sendReplicationRunner.replicate(repository, item.tableId)
                repository.db.prepareAndUseStatementAsync(
                    "UPDATE ReplicationStatus SET lastSendReplicationCompleteTime = ? WHERE tableId = ? AND nodeId = ?") { stmt ->
                    stmt.setLong(1, timeNow)
                    stmt.setInt(2, item.tableId)
                    stmt.setLong(3, nodeId)

                    stmt.executeUpdateAsyncKmp()
                }
            }

            if(item.lastRemoteChangeTime > item.lastFetchReplicationCompleteTime) {
                val timeNow = systemTimeInMillis()

                fetchReplicationRunner.replicate(repository, item.tableId)
                repository.db.prepareAndUseStatementAsync(
                    "UPDATE ReplicationStatus SET lastFetchReplicationCompleteTime = ? WHERE tableId = ? AND nodeId = ?") { stmt ->
                    stmt.setLong(1, timeNow)
                    stmt.setInt(2, item.tableId)
                    stmt.setLong(3, nodeId)

                    stmt.executeUpdateAsyncKmp()
                }
            }
            checkQueueSignal.send(true)
        }
    }

    override fun onMessage(message: DoorServerSentEvent) {
        var remoteNodeId: Long = 0
        val initCompletable = CompletableDeferred<Boolean>()
        when(message.event) {
            "INIT" -> coroutineScope.launch {
                remoteNodeId = message.data.toLong()
                initReplicationStatus(remoteNodeId)
                initCompletable.complete(true)
                dbNotificationDispatcher.addReplicationPendingEventListener(remoteNodeId,
                    this@ReplicationSubscriptionManager)

                val producer = produceJobs(remoteNodeId).also {
                    queueProcessor.value = it
                }

                repeat(numProcessors){
                    launchProcessor(it, remoteNodeId, producer)
                }
                checkQueueSignal.send(true)
            }
            "INVALIDATE" -> coroutineScope.launch {
                val tableIdsToInvalidate = message.data.split(",").mapNotNull { it.toIntOrNull() }
                initCompletable.await()
                repository.db.withDoorTransactionAsync(DoorDatabase::class) { transactionDb ->
                    transactionDb.prepareAndUseStatementAsync(
                        """
                    UPDATE ReplicationStatus 
                       SET lastRemoteChangeTime = ? 
                     WHERE nodeId = ? 
                       AND tableId = ?
                    """
                    ) { stmt ->
                        stmt.setLong(1, systemTimeInMillis())
                        stmt.setLong(2, remoteNodeId)
                        tableIdsToInvalidate.forEach { tableId ->
                            stmt.setInt(3, tableId)
                            stmt.executeUpdateAsyncKmp()
                        }
                    }
                }
                checkQueueSignal.send(true)
            }
        }
    }

    override fun onReplicationPending(event: ReplicationPendingEvent) {

        //update ReplicationStatus
        //checkQueue

    }

    override fun onError(e: Exception) {

    }
}
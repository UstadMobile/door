package com.ustadmobile.door

import io.github.aakira.napier.Napier
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.doorIdentityHashCode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicReference

/**
 * This class is used on the primary server to listen for changes to the database and tell the
 * server side repository to generate the required UpdateNotification entities (using the
 * dispatchUpdateNotifications function).
 *
 * When a change is received from the database it will wait up to 100ms before triggering the
 * the repo's dispatchUpdateNotifications function (so that it can batch updates together when
 * bulk changes take place). The dispatchUpdateNotifications function is called using a coroutine
 * fan-out pattern so that multiple tables can be processed concurrently.
 */
class ServerChangeLogMonitor(database: DoorDatabase, private val repo: DoorDatabaseRepository,
                             val numProcessors: Int = 5) {

    private val db = if(database is DoorDatabaseSyncableReadOnlyWrapper) {
        database.realDatabase
    }else {
        database
    }

    private val tablesChangedChannel: Channel<Int> = Channel(capacity = Channel.UNLIMITED)

    private val tablesToProcessChannel: Channel<Int> = Channel(capacity = Channel.UNLIMITED)

    private val dispatchJob: AtomicReference<Job?> = AtomicReference(null)

    private val changeListenerRequest: ChangeListenerRequest

    private val logPrefix: String = "[ServerChangeLogMonitor@${this.doorIdentityHashCode}]"

    init {
        Napier.d("$logPrefix init", tag = DoorTag.LOG_TAG)
        changeListenerRequest = ChangeListenerRequest(listOf(), this::onTablesChanged)
        db.addChangeListener(changeListenerRequest)

        GlobalScope.launch {
            repeat(numProcessors) {
                launchChangeLogProcessor(it, tablesToProcessChannel)
            }

            //Find anything that was changed when the ChangeLogMonitor wasn't running (e.g. repo not
            // yet created or manually changed by SQL)
            (repo as? DoorDatabaseSyncRepository)?.syncHelperEntitiesDao?.findTablesWithPendingChangeLogs()?.also {
                Napier.d("$logPrefix init: tables changed before: ${it.joinToString() }}",
                        tag = DoorTag.LOG_TAG)
                onTablesChangedInternal(it)
            }
        }
    }


    fun CoroutineScope.launchChangeLogProcessor(id: Int, channel: Channel<Int>) = launch {
        for(tableId in channel) {
            Napier.d("$logPrefix Processor #$id dispatchUpdateNotifications for: $tableId",
                    tag = DoorTag.LOG_TAG)
            repo.dispatchUpdateNotifications(tableId)
        }
    }

    fun onTablesChanged(tablesChanged: List<String>) {
        //Napier.d("$logPrefix onTablesChange: names=${tablesChanged.joinToString()}")
        onTablesChangedInternal(tablesChanged.map { repo.tableIdMap[it] ?: 0})
    }

    private fun onTablesChangedInternal(tablesChanged: List<Int>) {
//        Napier.d("$logPrefix tablesChanged: ids=${tablesChanged.joinToString()}",
//                tag = DoorTag.LOG_TAG)
        tablesChanged.filter { it != 0 }.forEach {table ->
            tablesChangedChannel.offer(table)

            if(dispatchJob.get() == null) {
                dispatchJob.set(GlobalScope.async {
                    delay(UPDATE_INTERVAL)
                    dispatchJob.set(null)
                    val itemsToSend = mutableSetOf<Int>()
                    var tableId: Int? = null
                    do {
                        tableId = tablesChangedChannel.poll()?.also {
                            itemsToSend += it
                        }
                    }while(tableId != null)

                    Napier.d("$logPrefix send tables for processing: ${itemsToSend.joinToString()}",
                            tag = DoorTag.LOG_TAG)
                    itemsToSend.forEach {
                        tablesToProcessChannel.send(it)
                    }
                })
            }
        }
    }



    fun close() {
        Napier.d("$logPrefix close", tag = DoorTag.LOG_TAG)
        db.removeChangeListener(changeListenerRequest)
    }

    companion object {

        const val UPDATE_INTERVAL = 100L

    }

}
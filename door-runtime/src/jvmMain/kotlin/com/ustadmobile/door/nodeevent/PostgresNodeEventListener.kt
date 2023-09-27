package com.ustadmobile.door.nodeevent

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.message.DoorMessage
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.postgresql.jdbc.PgConnection

/**
 * Postgres NodeEvent listener. This uses Postgres listen/notify. When a new OutgoingReplication is created, then
 * a trigger function will call pg_notify where the payload will contain the fields required for NodeEvent.
 *
 * The background listen loop will run to collect notifications and emit them from the outgoingEvents flow.
 */
class PostgresNodeEventListener(
    private val dataSource: DataSource,
    private val outgoingEvents: MutableSharedFlow<List<NodeEvent>>,
    private val hasOutgoingReplicationTable: Boolean,
    private val retryDelay: Long = 1_000L,
    private val eventCheckInterval: Long = 20L,
) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    init {
        initTrigger()
        startListenLoop()
    }

    /**
     * Run the listen statement synchronously (to ensure that no events are missed) on startup, then use a coroutine
     * to loop in the background.
     */
    private fun startListenLoop() {
        try {
            val connection = dataSource.connection.unwrap(PgConnection::class.java)
            connection.createStatement().use {
                it.execute("LISTEN $LISTEN_CHANNEL_NAME")
            }
            Napier.v(tag = DoorTag.LOG_TAG) {
                "PostgresNodeEventListener: Listening for $LISTEN_CHANNEL_NAME"
            }

            scope.launch {
                listenForEvents(connection)
            }
        }catch(e: Exception) {
            scope.launch {
                delay(retryDelay)
                startListenLoop()
            }
        }
    }

    private suspend fun CoroutineScope.listenForEvents(connection: PgConnection) {
        try {
            while(isActive && !connection.isClosed) {
                val notifications = connection.notifications
                if(notifications.isNotEmpty()) {
                    val nodeEvents = notifications.mapNotNull {
                        payloadStrToNodeEventOrNull(it.parameter)
                    }

                    outgoingEvents.takeIf { nodeEvents.isNotEmpty() }?.emit(nodeEvents)
                }
                delay(eventCheckInterval)
            }
        }catch(e: Exception) {
            if(isActive) {
                Napier.w(tag = DoorTag.LOG_TAG, throwable = e) {
                    "PostgresNodeEventListener: exception listening for events"
                }
                delay(retryDelay)
                startListenLoop()
            }
        }

    }


    private fun initTrigger() {
        if(!hasOutgoingReplicationTable)
            return

        dataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute("""
                        CREATE OR REPLACE FUNCTION door_outgoingrep_nodeevent_fn() RETURNS TRIGGER AS ${'$'}${'$'}
                        BEGIN
                        PERFORM pg_notify('$LISTEN_CHANNEL_NAME', (SELECT '${DoorMessage.WHAT_REPLICATION_PUSH},' || CAST(NEW.destNodeId AS VARCHAR) ||','|| CAST(NEW.orTableId AS VARCHAR) || ',' || CAST(NEW.orPk1 AS VARCHAR) || ',' || CAST(NEW.orPk2 AS VARCHAR)));
                        RETURN NEW;
                        END ${'$'}${'$'}
                        LANGUAGE plpgsql;
                        """)
                stmt.execute("""
                           DROP TRIGGER IF EXISTS door_outgoingrep_nodeevent_trig ON OutgoingReplication  
                        """)
                stmt.execute("""
                        CREATE TRIGGER door_outgoingrep_nodeevent_trig AFTER UPDATE OR INSERT OR DELETE 
                            ON OutgoingReplication FOR EACH ROW 
                            EXECUTE PROCEDURE door_outgoingrep_nodeevent_fn();
                    """)
            }
        }
    }

    private fun payloadStrToNodeEventOrNull(payload: String) : NodeEvent? {
        val parts = payload.split(",")
        if(parts.size != 5)
            return null //NodeEvent has 5 components

        try {
            return NodeEvent(
                what = parts[0].toInt(),
                toNode = parts[1].toLong(),
                tableId = parts[2].toInt(),
                key1 = parts[3].toLong(),
                key2 = parts[4].toLong(),
            )
        }catch(e: Exception) {
            Napier.w(tag = DoorTag.LOG_TAG) {
                "PostgresNodeEventListener: failed to parse event payload \"$payload\""
            }
        }

        return null
    }

    fun close() {
        scope.cancel()
    }

    companion object {

        const val LISTEN_CHANNEL_NAME = "door_node_evt"

    }

}
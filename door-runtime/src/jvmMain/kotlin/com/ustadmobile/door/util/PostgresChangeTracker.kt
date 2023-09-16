package com.ustadmobile.door.util

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.room.InvalidationTracker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import org.postgresql.jdbc.PgConnection
import java.io.Closeable

/**
 * PostgresChangeTracker uses a Listen/Notify system to track invalidations. See
 * https://www.postgresql.org/docs/13/sql-notify.html
 *
 * Triggers created by setupTriggers execute make a call to notify when a table is changed. This is then monitored
 * by a background job which uses LISTEN to pickup the table change notifications.
 */
class PostgresChangeTracker(
    private val dataSource: DataSource,
    private val invalidationTracker: InvalidationTracker,
    private val tableNames: List<String>,
) : Closeable{

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    init {
        setupTriggers()
        Napier.d(tag = DoorTag.LOG_TAG){
            "PostgresChangeTracker: initialized "
        }
        scope.launch {
            monitorNotifications()
        }
    }

    /**
     * Monitor notifications coming from the postgres database that are generated using the triggers created by
     * setupTriggers.
     */
    private suspend fun CoroutineScope.monitorNotifications() {
        while(isActive) {
            try {
                dataSource.connection.use { connection ->
                    val pgConnection = connection.unwrap(PgConnection::class.java)
                    pgConnection.createStatement().use {
                        it.execute("LISTEN $LISTEN_CHANNEL_NAME")
                    }

                    while(isActive) {
                        val notifications = pgConnection.notifications
                        if(notifications.isNotEmpty()) {
                            val tablesChanged = notifications.map { it.parameter }
                            invalidationTracker.onTablesInvalidated(tablesChanged.toSet())
                        }
                        delay(20)
                    }
                }
            }catch(e: Exception) {
                if(e !is CancellationException) {
                    Napier.e("PostgresChangeTracker: Exception", e, tag = DoorTag.LOG_TAG)
                    delay(1000)
                }
            }
        }
    }

    /**
     * Create a trigger and function for each table on the database.
     */
    private fun setupTriggers() {
        tableNames.forEach { tableName ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("""
                    CREATE OR REPLACE FUNCTION door_mod_fn_$tableName() RETURNS TRIGGER AS ${'$'}${'$'}
                        BEGIN 
                        NOTIFY $LISTEN_CHANNEL_NAME, '$tableName';
                        IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN
                        RETURN NEW;
                        ELSE
                        RETURN OLD;
                        END IF;
                        END ${'$'}${'$'}
                        LANGUAGE plpgsql;
                    """)
                    stmt.execute("""
                       DROP TRIGGER IF EXISTS door_mod_trig_$tableName ON $tableName  
                    """)
                    stmt.execute("""
                    CREATE TRIGGER door_mod_trig_$tableName AFTER UPDATE OR INSERT OR DELETE 
                        ON $tableName FOR EACH STATEMENT 
                        EXECUTE PROCEDURE door_mod_fn_$tableName();
                """)
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        Napier.d(tag = DoorTag.LOG_TAG){
            "PostgresChangeTracker: closed"
        }
    }

    companion object {
        const val LISTEN_CHANNEL_NAME = "doorinvalidations"
    }

}
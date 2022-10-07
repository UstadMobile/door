package com.ustadmobile.door.room

import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.util.InvalidationTrackerDbBuiltListener
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import org.postgresql.jdbc.PgConnection
import java.io.Closeable
import kotlin.coroutines.coroutineContext

/**
 * InvalidationTracker for Postgres. Based on using Postgres listen/notify.
 */
class PostgresInvalidationTracker(
    private val dataSource: DataSource,
    private vararg val tableNames: String,
) : InvalidationTracker(), Closeable, InvalidationTrackerDbBuiltListener {

    private val job: Job

    init {
        job = GlobalScope.launch { monitorNotifications() }
    }

    /**
     * Monitor notifications coming from the postgres database that are generated using the triggers created by
     * setupTriggers.
     */
    private suspend fun monitorNotifications() {
        while(coroutineContext.isActive) {
            try {
                dataSource.connection.use { connection ->
                    val pgConnection = connection.unwrap(PgConnection::class.java)
                    pgConnection.createStatement().use {
                        it.execute("LISTEN $LISTEN_CHANNEL_NAME")
                    }

                    while(coroutineContext.isActive) {
                        val notifications = pgConnection.notifications
                        if(notifications.isNotEmpty()) {
                            val tablesChanged = notifications.map { it.parameter }
                            fireChanges(tablesChanged.toSet())
                        }
                        delay(20)
                    }
                }
            }catch(e: Exception) {
                Napier.e("PostgresChangeTracker: Exception", e, tag = DoorTag.LOG_TAG)
                delay(1000)
            }
        }
    }

    override suspend fun onDatabaseBuilt(connection: Connection) {
        tableNames.forEach { tableName ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        """
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
                    """
                    )
                    stmt.execute(
                        """
                       DROP TRIGGER IF EXISTS door_mod_trig_$tableName ON $tableName  
                    """
                    )
                    stmt.execute(
                        """
                    CREATE TRIGGER door_mod_trig_$tableName AFTER UPDATE OR INSERT OR DELETE 
                        ON $tableName FOR EACH STATEMENT 
                        EXECUTE PROCEDURE door_mod_fn_$tableName();
                """
                    )
                }
            }
        }
    }


    override fun close() {
        job.cancel()
    }

    companion object {
        const val LISTEN_CHANNEL_NAME = "doorinvalidations"
    }

}
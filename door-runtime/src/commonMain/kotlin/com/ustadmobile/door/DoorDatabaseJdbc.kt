package com.ustadmobile.door

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.replication.ReplicationNotificationDispatcher
import com.ustadmobile.door.util.NodeIdAuthCache
import com.ustadmobile.door.util.TransactionDepthCounter
import com.ustadmobile.door.util.DoorInvalidationTracker

/**
 * This interface is implemented by all generated JDBC implementations of a database.
 */
interface DoorDatabaseJdbc {

    /**
     * The JNDI DataSource which will be used to open connections.
     */
    val dataSource: DataSource

    /**
     * Called to open a new database connection
     */
    fun openConnection(): Connection

    /**
     * When this instance is a repository or ReplicationWrapper, the sourceDatabase must be the database that they
     * connect to.
     * When this instance is an actual implementation (e.g. JdbcKt) transaction wrapper, sourceDatabase will point
     * to the underlying (original) database instance.
     * When this instance is the actual original database instance, this will be null.
     */
    val doorJdbcSourceDatabase: DoorDatabase?

    /**
     * If the database instance is being used as a transaction wrapper, and a transaction is ongoing, this will
     * be true. False otherwise (eg. for the root database itself).
     */
    val isInTransaction: Boolean
        get() = transactionDepthCounter.transactionDepth > 0

    /**
     * If this database is the root database, e.g. doorJdbcSourceDatabase == null, then it will hold a primary key
     * manager
     */
    val realPrimaryKeyManager: DoorPrimaryKeyManager

    /**
     * The name as it was provided to the builder function
     */
    val dbName: String

    /**
     * The real replication notification dispatcher (which is generally accessed
     * using multiplatform extension properties)
     */
    val realReplicationNotificationDispatcher: ReplicationNotificationDispatcher

    val realNodeIdAuthCache: NodeIdAuthCache

    val realIncomingReplicationListenerHelper: IncomingReplicationListenerHelper

    val transactionDepthCounter: TransactionDepthCounter

    val invalidationTracker: DoorInvalidationTracker


    val realAttachmentStorageUri: DoorUri?


}
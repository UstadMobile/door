package com.ustadmobile.door

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.attachments.AttachmentFilter
import com.ustadmobile.door.jdbc.DataSource
import com.ustadmobile.door.util.NodeIdAuthCache

/**
 * This interface is implemented by all generated JDBC implementations of a database.
 */
interface DoorDatabaseJdbc {

    /**
     * The JNDI DataSource which will be used to open connections.
     */
    val dataSource: DataSource

    /**
     * When this instance is a repository or ReplicationWrapper, the sourceDatabase must be the database that they
     * connect to.
     * When this instance is an actual implementation (e.g. JdbcKt) transaction wrapper, sourceDatabase will point
     * to the underlying (original) database instance.
     * When this instance is the actual original database instance, this will be null.
     */
    val doorJdbcSourceDatabase: RoomDatabase?

    /**
     * If this database is the root database, e.g. doorJdbcSourceDatabase == null, then it will hold a primary key
     * manager
     */
    val realPrimaryKeyManager: DoorPrimaryKeyManager

    /**
     * The name as it was provided to the builder function
     */
    val dbName: String

    val realNodeIdAuthCache: NodeIdAuthCache

    val realAttachmentStorageUri: DoorUri?

    val realAttachmentFilters: List<AttachmentFilter>

    /**
     * The query timeout to use: in seconds
     */
    val jdbcQueryTimeout: Int

}
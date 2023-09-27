package com.ustadmobile.door.room

import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.door.jdbc.DataSource


/**
 * Contains logic that is used by generated JDBC implementations on JVM and JS. The reason this is not in the parent
 * RoomDatabase class is because we want to be 100% sure that there is only one instance of this class per database
 * instance e.g. one set of invalidation listeners, one map of thread ids to transaction connections, etc.
 */
expect class RoomDatabaseJdbcImplHelper(
    dataSource: DataSource,
    db: RoomDatabase,
    dbUrl: String,
    tableNames: List<String>,
    invalidationTracker: InvalidationTracker,
    dbType: Int,
) : RoomDatabaseJdbcImplHelperCommon {

    /**
     * Use a (blocking) connection. If there is already a connection associated with this thread (e.g. via
     * withDoorTransaction), it will be used, otherwise a new connection will be used.
     *
     * @param readOnly true if only non-modifying (e.g. select queries) will be run using this connection. This helps
     *        improve performance : setting up change catch triggers can be skipped, look for changed tables can be
     *        skipped, and on servers, this could allow the use of read-only replicas.
     */
    fun <R> useConnection(
        readOnly: Boolean,
        block: (Connection) -> R
    ) : R

    /**
     *
     */
    fun <R> useConnection(block: (Connection) -> R) : R

}
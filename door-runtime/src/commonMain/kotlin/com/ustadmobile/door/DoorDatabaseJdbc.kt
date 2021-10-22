package com.ustadmobile.door

/**
 * This interface is implemented by all generated JDBC implementations of a database.
 */
interface DoorDatabaseJdbc {

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

    /**
     * If this database is the root database, e.g. doorJdbcSourceDatabase == null, then it will hold a primary key
     * manager
     */
    val realPrimaryKeyManager: DoorPrimaryKeyManager


}
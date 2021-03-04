package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)

/**
 * Annotation to mark an entity as Syncable. This will
 *
 * 1. Modify the primary key generation to ensure that primary keys don't collide
 * 2. Require that the entity contains a local and master change sequence number, and a last changed
 * by field.
 */
annotation class SyncableEntity(
        /**
         * The table id must be a unique positive integer that is not used by any other table on
         * the database
         */
        val tableId: Int,

        /**
         * This is an array of queries to run on the primary database when this table is updated used
         * to determine which clients need to be notified to sync, and which tables those clients
         * need to sync.
         *
         * The query should return two columns - deviceId and tableId. If the tableId
         * is ClientSyncManager.TABLEID_SYNC_ALL_TABLES (-1) the client will sync all tables in
         * the database (e.g. this could be useful after login, permission changes, etc).
         */
        val notifyOnUpdate: Array<String> = arrayOf(),


        val syncFindAllQuery: String = "",

        /**
         * The number of entities to receive at a time when syncing. If the entity has
         * attachments it might be sensible to reduce this.
         */
        val receiveBatchSize: Int = 1000,

        /**
         * The number of entities to send at a time when syncing. If the entity has
         * attachments it might be sensible to reduce this.
         */
        val sendBatchSize: Int = 1000)

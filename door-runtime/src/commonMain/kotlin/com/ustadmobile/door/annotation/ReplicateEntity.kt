package com.ustadmobile.door.annotation


@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)

/**
 * Indicates that this entity will be replicated
 */
annotation class ReplicateEntity(
    /**
     * The table id must be a unique positive integer that is not used by any other table on
     * the database
     */
    val tableId: Int,

    /**
     * The number of entities to transfer at a time. By default, 1000.
     */
    val batchSize: Int = 1000,

    /**
     * How to handle when this entity is received from a remote node (via pull or push).
     */
    val remoteInsertStrategy: RemoteInsertStrategy = RemoteInsertStrategy.CALLBACK,

    ) {

    enum class RemoteInsertStrategy {
        /**
         * The EventManager will emit an event and nothing more.
         */
        CALLBACK,

        /**
         * Any received entity will be directly inserted. This should be used with caution
         */
        INSERT,

        /**
         * The received entity will be inserted into a View. Triggers can then be used to determine if/how to accept
         * the received data (e.g. permission checks, conflict checks, etc). The ReceiveView is simply:
         *
         * CREATE VIEW Entity_ReceiveView AS
         *      SELECT Entity.*, 0 AS fromNodeId
         *        FROM Entity
         *
         * When the insert into the ReceiveView is run, the fromNodeId will be set as the nodeId of the node from which
         * the entities are being received (e.g. so this can be used as part of permission checks etc).
         */
        INSERT_INTO_RECEIVE_VIEW
    }

}
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
     * Lower priority replications will not proceed if there are any higher priority replications pending. This allows
     * one entity to depend on another (e.g. for permission management purposes etc).
     *
     * Lower values are higher priority.
     */
    val priority: Int = DEFAULT_PRIORITY,

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
        INSERT_DIRECT,
        INSERT_INTO_VIEW
    }

    companion object {
        const val HIGHEST_PRIORITY = 0

        const val DEFAULT_PRIORITY = 100

        const val LOWEST_PRIORITY = 50000
    }
}
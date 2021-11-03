package com.ustadmobile.door.annotation

import kotlin.reflect.KClass


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
     * The replication tracker entity
     */
    val tracker: KClass<*>,

    /**
     * Lower priority replications will not proceed if there are any higher priority replications pending. This allows
     * one entity to depend on another (e.g. for permission management purposes etc).
     *
     * Lower values are higher priority.
     */
    val priority: Int = DEFAULT_PRIORITY
) {
    companion object {
        const val HIGHEST_PRIORITY = 0

        const val DEFAULT_PRIORITY = 100

        const val LOWEST_PRIORITY = 50000
    }
}
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
    val tracker: KClass<*>
)

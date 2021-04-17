package com.ustadmobile.door.annotation

/**
 * This annotation must be on a Long field of a SyncableEntity. It will be used to store the changed timestamp of when a
 * an entity was last changed for sync purposes.
 *
 * This is reserved for future use.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD)
annotation class LastChangedTime()

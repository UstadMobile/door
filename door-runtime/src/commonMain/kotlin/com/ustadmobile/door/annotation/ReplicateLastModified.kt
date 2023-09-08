package com.ustadmobile.door.annotation

/**
 * This annotation may (optionally) be added to one Long property of a ReplicateEntity (the value should be the time in
 * millis). If present it will be used to set the last-modified header on http pull responses that include this entity.
 *
 * This may be applied to the same field that has the ReplicateEtag annotation (e.g. the etag can be the last modified
 * time if desired).
 *
 * @param autoSet if true (as is the default case), then the generated DoorWrapper will automatically set the last
 * modified time field to the current system time when an Insert or Update is performed.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD)
annotation class ReplicateLastModified(val autoSet: Boolean = true)


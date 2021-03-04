package com.ustadmobile.door.annotation

/**
 * Indicates that this is an attachment field. This MUST be a string.
 *
 * Door uses it's own URI scheme
 *
 * door:attachment/TableName/md5
 *
 * The URI can be set with a platform specific URI string (e.g. a URI returned by Android, file URI,
 * etc). The Update and Insert functions on a repository will detect this and copy the data
 * accordingly.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD)
annotation class AttachmentUri()


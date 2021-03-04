package com.ustadmobile.door.annotation

/**
 * Attachment field used to hold the size of the attachment data. Must be annotated on a field
 * which is of type Int. (Seriously, we're not supporting 2GB+ attachments).
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD)
annotation class AttachmentSize()

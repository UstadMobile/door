package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabaseRepository
import com.ustadmobile.door.DoorUri

/**
 * Store an attachment locally. This will be called by the generated update and insert implementation.
 * The platform specific attachmentUri on the entity will be read.
 *
 * @param entityWithAttachment This interface is implemented by a generated adapter class. The
 * attachmentUri must be a readable platform specific uri.
 *
 * The attachmentUri variable will then be set to a uri in the form of:
 *
 * door-attachment://tablename/md5sum
 *
 * If the attachmentUri is already stored (e.g. it is prefixed with door-attachment://) then nothing
 * will be done
 *
 */
actual suspend fun DoorDatabaseRepository.storeAttachment(entityWithAttachment: EntityWithAttachment) {
}

/**
 * Get a platform specific URI for the given attachment URI.
 *
 * @param attachmentUri The attachmentUri: this can be a platform dependent URI string, or it could
 *
 */
actual suspend fun DoorDatabaseRepository.retrieveAttachment(attachmentUri: String): DoorUri {
    TODO("Not yet implemented")
}

/**
 * After an update has been performed on a table that has attachments, this function is called
 * to delete old/unused data by the generated repository code
 */
actual suspend fun DoorDatabaseRepository.deleteZombieAttachments(entityWithAttachment: EntityWithAttachment) {
}

/**
 * Upload the given attachment uri to the endpoint.
 */
actual suspend fun DoorDatabaseRepository.uploadAttachment(entityWithAttachment: EntityWithAttachment) {
}

actual suspend fun DoorDatabaseRepository.downloadAttachments(entityList: List<EntityWithAttachment>) {
}
package com.ustadmobile.door.attachments

import com.ustadmobile.door.room.RoomDatabase
import com.ustadmobile.door.DoorRootDatabase
import com.ustadmobile.door.DoorDatabaseRepository.Companion.DOOR_ATTACHMENT_URI_PREFIX
import com.ustadmobile.door.DoorUri
import com.ustadmobile.door.ext.*
import com.ustadmobile.door.util.systemTimeInMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Paths

actual suspend fun RoomDatabase.storeAttachment(entityWithAttachment: EntityWithAttachment) {
    requireAttachmentStorage().storeAttachment(entityWithAttachment)
}

actual suspend fun RoomDatabase.retrieveAttachment(attachmentUri: String): DoorUri {
    return requireAttachmentStorage().retrieveAttachment(attachmentUri)
}

actual val RoomDatabase.attachmentsStorageUri: DoorUri?
    get() = (rootDatabase as DoorRootDatabase).realAttachmentStorageUri

actual val RoomDatabase.attachmentFilters: List<AttachmentFilter>
    get() = (rootDatabase as DoorRootDatabase).realAttachmentFilters



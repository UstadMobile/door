package com.ustadmobile.door.attachments

import com.ustadmobile.door.DoorDatabaseRepository.Companion.DOOR_ATTACHMENT_URI_PREFIX
import com.ustadmobile.door.entities.ZombieAttachmentData

internal val ZombieAttachmentData.tableNameAndMd5Path: String
    get() = zaMd5?.substringAfter(DOOR_ATTACHMENT_URI_PREFIX) ?: throw IllegalStateException("No uri on Zombie!")

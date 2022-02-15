package com.ustadmobile.door.util

import com.ustadmobile.door.ChangeListenerRequest
import com.ustadmobile.door.DoorDatabase
import com.ustadmobile.door.attachments.deleteZombieAttachments
import com.ustadmobile.door.ext.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Simple invalidationlistener that will listen for changes on the ZombieAttachmentData table, and will then call the
 * deleteZombieAttachments function so unused files get deleted.
 */
class DeleteZombieAttachmentsListener(
    private val db: DoorDatabase,
    coroutineScope: CoroutineScope = GlobalScope,
) {

    val invalidationListener = ChangeListenerRequest(listOf("ZombieAttachmentData")) {
        coroutineScope.launch {
            db.deleteZombieAttachments()
        }
    }

    init {
        if(db::class.doorDatabaseMetadata().hasAttachments) {
            db.addInvalidationListener(invalidationListener)
        }
    }

    fun close() {
        db.removeInvalidationListener(invalidationListener)
    }


}
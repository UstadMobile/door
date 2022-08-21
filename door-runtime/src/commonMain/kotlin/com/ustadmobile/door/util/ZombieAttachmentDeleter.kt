package com.ustadmobile.door.util

import com.ustadmobile.door.room.InvalidationTracker
import com.ustadmobile.door.room.RoomDatabase
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
    private val db: RoomDatabase,
    coroutineScope: CoroutineScope = GlobalScope,
) {

    private val invalidationObserver = object: InvalidationTracker.Observer(arrayOf("ZombieAttachmentData")) {
        override fun onInvalidated(tables: Set<String>) {
            coroutineScope.launch {
                db.deleteZombieAttachments()
            }
        }
    }


    init {
        if(db::class.doorDatabaseMetadata().hasAttachments) {
            db.getInvalidationTracker().addObserver(invalidationObserver)
        }
    }

    fun close() {
        db.getInvalidationTracker().removeObserver(invalidationObserver)
    }


}
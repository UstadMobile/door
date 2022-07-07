package com.ustadmobile.door.util

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.ustadmobile.door.ChangeListenerRequest
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

    val invalidationObserver = object: InvalidationTracker.Observer(arrayOf("ZombieAttachmentData")) {
        override fun onInvalidated(tables: Set<String>) {
            coroutineScope.launch {
                db.deleteZombieAttachments()
            }
        }
    }


    init {
        if(db::class.doorDatabaseMetadata().hasAttachments) {
            db.invalidationTracker.addObserver(invalidationObserver)
        }
    }

    fun close() {
        db.invalidationTracker.removeObserver(invalidationObserver)
    }


}
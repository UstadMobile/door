package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * Represents a Zombie attachment Uri. This hapens when an entity with attachments is updated
 * and the old md5 is no longer used in the table.
 */
@Entity
class ZombieAttachmentData {

    @PrimaryKey(autoGenerate = true)
    var zaUid: Long = 0

    var zaTableId: Int = 0

    var zaPrimaryKey: Long = 0

    var zaMd5: String? = null

}
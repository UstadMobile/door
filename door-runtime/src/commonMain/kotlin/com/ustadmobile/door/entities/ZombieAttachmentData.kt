package com.ustadmobile.door.entities

import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * Represents a Zombie attachment Uri. This hapens when an entity with attachments is updated
 * and the old md5 is no longer used in the table.
 */
@Entity(primaryKeys = ["zaUid"])
class ZombieAttachmentData {

    @PrimaryKey(autoGenerate = true)
    var zaUid: Int = 0

    var zaUri: String? = null

}
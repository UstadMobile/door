package com.ustadmobile.door

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(primaryKeys = ["nodeClientId"])
@Serializable
data class SyncNode(var nodeClientId: Long = 0) {
    companion object {

        @Suppress("unused")
        const val SELECT_LOCAL_NODE_ID_SQL = """
            (SELECT COALESCE(
                    (SELECT nodeClientId 
                       FROM SyncNode 
                      LIMIT 1), 0))
        """
    }
}
package com.ustadmobile.door

import kotlinx.serialization.json.JsonArray

class IncomingReplicationEvent(
    val incomingReplicationData: JsonArray,
    val tableId: Int
) {
}
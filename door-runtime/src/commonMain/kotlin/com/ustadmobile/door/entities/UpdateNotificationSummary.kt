package com.ustadmobile.door.entities

/**
 * This entity is the return type for the query specified in the notifyOnUpdate annotation value of
 * the SyncableEntity annotation.
 */
data class UpdateNotificationSummary (var deviceId: Int = 0, var tableId: Int = 0)
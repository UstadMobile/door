package com.ustadmobile.door

/**
 * Represents settings that are used by syncEntity.
 */
data class SyncSettings(val receiveBatchSize: Int = 1000, val sendBatchSize: Int = 100)

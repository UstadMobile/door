package com.ustadmobile.door

interface IncomingReplicationListener {

    /**
     * This will be called after incoming data has been inserted.
     */
    suspend fun onIncomingReplicationProcessed(incomingReplicationEvent: IncomingReplicationEvent)

}
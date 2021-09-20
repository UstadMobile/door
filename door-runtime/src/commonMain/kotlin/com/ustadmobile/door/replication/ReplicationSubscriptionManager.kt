package com.ustadmobile.door.replication

//Class that is responsible to listen for local and remote changes, then send/receive replication accordingly.
// There will be one repository instance per endpoint (e.g. for the cloud and any local mirrors).
// ReplicationSubscriptionManager will listen for local changes using the ReplicationNotificationDispatcher, will track
// status using ReplicationStatus entities, and then call sendPendingReplications.

// ReplicationSubscriptionManager will also subscribe for serversentevents from the endpoint to receive notifications
// of remote changes, and will then call fetchPendingReplications as needed.

class ReplicationSubscriptionManager(
    private val dbNotificationDispatcher: ReplicationNotificationDispatcher
) {
}
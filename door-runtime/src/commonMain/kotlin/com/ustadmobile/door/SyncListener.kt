package com.ustadmobile.door

/**
 * SyncListener can receive an event when SyncableEntities are received from another device. This can be useful when
 * receiving an entity should trigger some kind of device-side processing (eg. scheduling a task, showing a notification,
 * etc)
 */
interface SyncListener<T: Any> {

    /**
     * Event triggered when entities have been received from another device. This is called after the entities
     * themselves are stored.
     */
    fun onEntitiesReceived(evt: SyncEntitiesReceivedEvent<T>)

}
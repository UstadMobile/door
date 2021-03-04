package com.ustadmobile.door

import com.ustadmobile.door.entities.UpdateNotification

/**
 * This interface is implemented by a manager class on the server. It is used to distribute
 * notifications via HTTP server sent events (SSEs) 
 */
interface ServerUpdateNotificationManager {

    /**
     * After registering for notifications this function will be called when there is a new
     * UpdateNotification for the deviceId that was used on addUpdateNotificationListener. It is
     * recommended that these updates should be dispatched via a channel (events may be sent
     * simultaneously using the default coroutine dispatcher)
     */
    fun onNewUpdateNotifications(notifications: List<UpdateNotification>)

    /**
     * Add a listener that should receive an event whenever there is a new updatenotification
     * for the given deviceId. This would generally be used by the HTTP server when it receives
     * an SSE request so that it will receive events immediately whenever there is an update for
     * the given deviceId client.
     */
    fun addUpdateNotificationListener(deviceId: Int, listener: UpdateNotificationListener)

    /**
     * Remove the given UpdateNotificationListener for the given deviceId. This should normally
     * be called once a SSE request is completed.
     */
    fun removeUpdateNotificationListener(deviceId: Int, listener: UpdateNotificationListener)

}
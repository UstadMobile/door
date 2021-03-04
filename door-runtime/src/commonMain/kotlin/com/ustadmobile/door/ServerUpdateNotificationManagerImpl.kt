package com.ustadmobile.door

import com.github.aakira.napier.Napier
import com.ustadmobile.door.entities.UpdateNotification
import com.ustadmobile.door.ext.DoorTag
import com.ustadmobile.door.ext.doorIdentityHashCode

class ServerUpdateNotificationManagerImpl: ServerUpdateNotificationManager {

    //TODO: Use thread safe versions on JVM via expect / actual
    val notificationListeners: MutableMap<Int, MutableList<UpdateNotificationListener>> = mutableMapOf()

    val logPrefix = "[ServerUpdateNotificationManagerImpl@$doorIdentityHashCode]"

    override fun onNewUpdateNotifications(notifications: List<UpdateNotification>) {
        Napier.d(" newUpdateNotification: ${notifications.joinToString { "table #${it.pnTableId} -> device ${it.pnDeviceId}" }}",
            tag = DoorTag.LOG_TAG)
        notifications.forEach {notification ->
            notificationListeners[notification.pnDeviceId]?.forEach {listener ->
                listener.onNewUpdate(notification)
            }
        }
    }

    override fun addUpdateNotificationListener(deviceId: Int, listener: UpdateNotificationListener) {
        Napier.d("$logPrefix addUpdateNotificationListener for device: $deviceId",
                tag = DoorTag.LOG_TAG)
        notificationListeners.getOrPut(deviceId) { mutableListOf() }.add(listener)
    }

    override fun removeUpdateNotificationListener(deviceId: Int, listener: UpdateNotificationListener) {
        Napier.d("$logPrefix removeUpdateNotificationListener for device: $deviceId",
                tag = DoorTag.LOG_TAG)
        notificationListeners[deviceId]?.remove(listener)
    }


}
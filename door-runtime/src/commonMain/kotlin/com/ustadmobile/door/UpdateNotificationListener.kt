package com.ustadmobile.door

import com.ustadmobile.door.entities.UpdateNotification

interface UpdateNotificationListener {

    fun onNewUpdate(notification: UpdateNotification)

}
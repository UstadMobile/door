package com.ustadmobile.door

import com.ustadmobile.door.ext.concurrentSafeListOf

abstract class DoorDatabaseEventListener {

    val changeListeners = concurrentSafeListOf<ChangeListenerRequest>()

    abstract fun addChangeListener(changeListenerRequest: ChangeListenerRequest): DoorDatabase

    abstract fun removeChangeListener(changeListenerRequest: ChangeListenerRequest): DoorDatabase
}
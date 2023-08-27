package com.ustadmobile.door

import com.ustadmobile.door.ext.currentDoorState
import com.ustadmobile.door.lifecycle.DoorState
import com.ustadmobile.door.lifecycle.LifecycleOwner


actual class RepositoryLoadHelperLifecycleHelper actual constructor(lifecycleOwner: LifecycleOwner) {

    var actLifecycleOwner: LifecycleOwner? = lifecycleOwner

    actual var onActive: (() -> Unit)? = null

    actual var onInactive: (() -> Unit)? = null

    actual var onDestroyed: (() -> Unit)? = null

    actual fun addObserver() {
        //Not implemented on JVM
    }

    actual fun removeObserver() {
        //Not implemented on JVM
    }

    /**
     * Returns the current state as an Int as per DoorLifecycleObserver constants
     */
    actual val currentState: Int
        get() = actLifecycleOwner?.lifecycle?.currentDoorState?.ordinal ?: DoorState.DESTROYED.ordinal

    actual fun dispose() {
        actLifecycleOwner = null
    }


}
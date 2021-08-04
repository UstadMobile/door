package com.ustadmobile.door


actual class RepositoryLoadHelperLifecycleHelper actual constructor(lifecycleOwner: DoorLifecycleOwner) {

    var actLifecycleOwner: DoorLifecycleOwner? = lifecycleOwner

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
        get() = actLifecycleOwner?.currentState ?: DoorLifecycleObserver.DESTROYED

    actual fun dispose() {
        actLifecycleOwner = null
    }


}
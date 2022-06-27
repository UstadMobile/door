package com.ustadmobile.door

import androidx.lifecycle.*

actual class RepositoryLoadHelperLifecycleHelper actual constructor(
    lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {

    var actLifecycleOwner: LifecycleOwner? = lifecycleOwner


    /**
     * Returns the current state as an Int as per DoorLifecycleObserver constants
     */
    actual val currentState: Int
        get() {
            return actLifecycleOwner?.lifecycle?.currentState?.ordinal ?: Lifecycle.State.DESTROYED.ordinal
        }

    actual var onActive: (() -> Unit)? = null

    actual var onInactive: (() -> Unit)? = null

    actual var onDestroyed: (() -> Unit)? = null

    actual fun addObserver() {
        actLifecycleOwner?.lifecycle?.addObserver(this)
    }

    actual fun removeObserver() {
        actLifecycleOwner?.lifecycle?.removeObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        onActive?.invoke()
    }

    override fun onStop(owner: LifecycleOwner) {
        onInactive?.invoke()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dispose()
        onDestroyed?.invoke()
    }

    actual fun dispose() {
        actLifecycleOwner = null
    }


}
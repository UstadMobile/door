package com.ustadmobile.door

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent

actual class RepositoryLoadHelperLifecycleHelper actual constructor(lifecycleOwner: DoorLifecycleOwner) : LifecycleObserver {

    var actLifecycleOwner: LifecycleOwner? = lifecycleOwner


    /**
     * Returns the current state as an Int as per DoorLifecycleObserver constants
     */
    actual val currentState: Int
        get() {
            val actLifecycleVal = actLifecycleOwner
            return if(actLifecycleVal != null) {
                STATE_MAP[actLifecycleVal.lifecycle.currentState] ?: DoorLifecycleObserver.DESTROYED
            }else {
                DoorLifecycleObserver.DESTROYED
            }
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

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        onActive?.invoke()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        onInactive?.invoke()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        dispose()
        onDestroyed?.invoke()
    }


    actual fun dispose() {
        actLifecycleOwner = null
    }

    companion object {

        val STATE_MAP = mapOf(Lifecycle.State.CREATED to DoorLifecycleObserver.CREATED,
                Lifecycle.State.STARTED to DoorLifecycleObserver.STARTED,
                Lifecycle.State.DESTROYED to DoorLifecycleObserver.DESTROYED,
                Lifecycle.State.INITIALIZED to DoorLifecycleObserver.NOT_CREATED,
                Lifecycle.State.RESUMED to DoorLifecycleObserver.RESUMED)

    }


}
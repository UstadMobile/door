package com.ustadmobile.door

abstract class DoorLifecycleObserver {

    open fun onCreate(owner: DoorLifecycleOwner) {

    }

    open fun onStart(owner: DoorLifecycleOwner) {

    }

    open fun onResume(owner: DoorLifecycleOwner) {

    }

    open fun onPause(owner: DoorLifecycleOwner) {

    }

    open fun onStop(owner: DoorLifecycleOwner) {

    }

    open fun onDestroy(owner: DoorLifecycleOwner) {

    }

    companion object {
        const val NOT_CREATED = 0

        const val CREATED = 1

        const val STARTED = 2

        const val RESUMED = 3

        const val PAUSED = 4

        const val STOPPED = 5

        const val DESTROYED = 6
    }

}
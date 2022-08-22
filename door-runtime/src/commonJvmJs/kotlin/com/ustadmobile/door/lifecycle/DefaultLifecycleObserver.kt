package com.ustadmobile.door.lifecycle

actual interface DefaultLifecycleObserver: LifecycleObserver {

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    actual fun onCreate(owner: LifecycleOwner) {

    }

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    actual fun onStart(owner: LifecycleOwner) {

    }

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    actual fun onResume(owner: LifecycleOwner) {

    }

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    actual fun onPause(owner: LifecycleOwner) {

    }

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    actual fun onStop(owner: LifecycleOwner) {

    }

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    actual fun onDestroy(owner: LifecycleOwner) {

    }
}
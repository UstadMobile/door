package com.ustadmobile.door.lifecycle

expect interface DefaultLifecycleObserver {

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    open fun onCreate(owner: LifecycleOwner)

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    open fun onStart(owner: LifecycleOwner)

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    open fun onResume(owner: LifecycleOwner)

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    open fun onPause(owner: LifecycleOwner)

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    open fun onStop(owner: LifecycleOwner)

    @Suppress("NO_ACTUAL_FOR_EXPECT")
    open fun onDestroy(owner: LifecycleOwner)

}
package com.ustadmobile.door.lifecycle

actual interface FullLifecycleObserver : LifecycleObserver{

    actual fun onCreate(owner: LifecycleOwner)

    actual fun onStart(owner: LifecycleOwner)

    actual fun onResume(owner: LifecycleOwner)

    actual fun onPause(owner: LifecycleOwner)

    actual fun onStop(owner: LifecycleOwner)

    actual fun onDestroy(owner: LifecycleOwner)

}
package com.ustadmobile.door.lifecycle

actual abstract class Lifecycle {

    actual abstract fun getCurrentState(): Lifecycle.State

    actual abstract fun addObserver(observer: LifecycleObserver)

    actual abstract fun removeObserver(observer: LifecycleObserver)

    actual enum class State {
        /**
         * Destroyed state for a LifecycleOwner. After this event, this Lifecycle will not dispatch
         * any more events. For instance, for an [android.app.Activity], this state is reached
         * **right before** Activity's [onDestroy][android.app.Activity.onDestroy] call.
         */
        DESTROYED,

        /**
         * Initialized state for a LifecycleOwner. For an [android.app.Activity], this is
         * the state when it is constructed but has not received
         * [onCreate][android.app.Activity.onCreate] yet.
         */
        INITIALIZED,

        /**
         * Created state for a LifecycleOwner. For an [android.app.Activity], this state
         * is reached in two cases:
         *
         *  * after [onCreate][android.app.Activity.onCreate] call;
         *  * **right before** [onStop][android.app.Activity.onStop] call.
         *
         */
        CREATED,

        /**
         * Started state for a LifecycleOwner. For an [android.app.Activity], this state
         * is reached in two cases:
         *
         *  * after [onStart][android.app.Activity.onStart] call;
         *  * **right before** [onPause][android.app.Activity.onPause] call.
         *
         */
        STARTED,

        /**
         * Resumed state for a LifecycleOwner. For an [android.app.Activity], this state
         * is reached after [onResume][android.app.Activity.onResume] is called.
         */
        RESUMED;

        /**
         * Compares if this State is greater or equal to the given `state`.
         *
         * @param state State to compare with
         * @return true if this State is greater or equal to the given `state`
         */
        actual fun isAtLeast(state: State): Boolean {
            return compareTo(state) >= 0
        }
    }

}
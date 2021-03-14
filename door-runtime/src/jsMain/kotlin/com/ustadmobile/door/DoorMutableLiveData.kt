package com.ustadmobile.door

actual open class DoorMutableLiveData<T> : DoorLiveData<T> {

    actual open fun sendValue(value: T) {
    }

    actual open fun setVal(value: T) {
    }

    /**
     * This is to be implemented to be the same as onActive. We can't typealias it because it is
     * a Java protected method (which does not match Kotlin protected).
     *
     * The underlying implementation (e.g. Room LiveData on Android or DoorLiveData JDBC/JS) will
     * take care of calling this.
     */
    protected actual open fun onActive2() {
    }

    /**
     * This is to be implemented to be the same as onActive. We can't typealias it because it is
     * a Java protected method (which does not match Kotlin protected)
     *
     * The underlying implementation (e.g. Room LiveData on Android or DoorLiveData JDBC/JS) will
     * take care of calling this.
     */
    protected actual open fun onInactive2() {
    }

    actual constructor(value: T) {
        TODO("Not yet implemented")
    }

    actual constructor() {
        TODO("Not yet implemented")
    }

}
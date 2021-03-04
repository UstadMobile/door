package com.ustadmobile.door

expect open class DoorMutableLiveData<T>: DoorLiveData<T>  {

    constructor(value: T)

    constructor()

    open fun sendValue(value: T)

    open fun setVal(value: T)

    /**
     * This is to be implemented to be the same as onActive. We can't typealias it because it is
     * a Java protected method (which does not match Kotlin protected).
     *
     * The underlying implementation (e.g. Room LiveData on Android or DoorLiveData JDBC/JS) will
     * take care of calling this.
     */
    protected open fun onActive2()

    /**
     * This is to be implemented to be the same as onActive. We can't typealias it because it is
     * a Java protected method (which does not match Kotlin protected)
     *
     * The underlying implementation (e.g. Room LiveData on Android or DoorLiveData JDBC/JS) will
     * take care of calling this.
     */
    protected open fun onInactive2()

}
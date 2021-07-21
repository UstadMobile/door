package com.ustadmobile.door

import androidx.lifecycle.LiveData

actual open class DoorMutableLiveData<T> : LiveData<T> {

    actual constructor(value: T): super(value)

    actual constructor() : super()

    /**
     * Synonymous with postValue. Unfortunately we can't use a straight typeAlias because the
     * MutableLiveData class is overriding a protected method (onActive and onInactive) and making it public in Java. Because
     * the protected keyword in Kotlin has a different meaning to the keyword in Java, the compiler
     * will reject the function signatures as being incompatible.
     */
    actual open fun sendValue(value: T) = postValue(value)

    actual open fun setVal(value: T) = setValue(value)

    override fun onInactive() {
        super.onInactive()
        onInactive2()
    }

    override fun onActive() {
        super.onActive()
        onActive2()
    }

    protected actual open fun onActive2() {

    }

    protected actual open fun onInactive2() {

    }


}

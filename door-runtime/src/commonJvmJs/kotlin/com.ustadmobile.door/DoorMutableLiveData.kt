package com.ustadmobile.door

actual open class DoorMutableLiveData<T> : DoorLiveData<T> {

    actual constructor(value: T): super(value) {

    }

    actual constructor()

    actual open fun sendValue(value: T) = postValue(value)

    actual open fun setVal(value: T) = postValue(value)


    override fun onActive() {
        super.onActive()
        onActive2()
    }

    override fun onInactive() {
        super.onInactive()
        onInactive2()
    }

    protected actual open fun onActive2() {
    }

    protected actual open fun onInactive2() {
    }


}
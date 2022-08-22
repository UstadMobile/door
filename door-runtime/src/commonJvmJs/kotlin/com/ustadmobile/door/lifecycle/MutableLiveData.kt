package com.ustadmobile.door.lifecycle

actual open class MutableLiveData<T: Any?> : LiveData<T> {

    actual constructor(value: T) : super(value) {

    }

    actual constructor()

    public actual override fun postValue(value: T) {
        super.postValue(value)
    }

    public actual override fun setValue(value: T) = postValue(value)


}
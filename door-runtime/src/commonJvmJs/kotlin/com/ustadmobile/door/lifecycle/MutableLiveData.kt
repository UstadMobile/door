package com.ustadmobile.door.lifecycle

actual open class MutableLiveData<T> : LiveData<T> {

    actual constructor(value: T) : super(value) {

    }

    actual constructor()

    public actual override fun postValue(value: T) {
        super.postValue(value)
    }

    actual open fun setVal(value: T) = postValue(value)


}
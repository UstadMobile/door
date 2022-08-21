package com.ustadmobile.door.lifecycle

expect open class MutableLiveData<T> : LiveData<T> {

    constructor(value: T)

    constructor()

    public override fun postValue(value: T)

    open fun setVal(value: T)

}
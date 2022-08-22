package com.ustadmobile.door.lifecycle

expect open class MutableLiveData<T: Any?> : LiveData<T> {

    constructor(value: T)

    constructor()

    public override fun postValue(value: T)

    public override fun setValue(value: T)

}
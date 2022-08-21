package androidx.lifecycle

open class MutableLiveData<T> : LiveData<T> {

    constructor(value: T) : super(value) {

    }

    constructor()

    public override fun postValue(value: T) {
        super.postValue(value)
    }

    open fun setVal(value: T) = postValue(value)


}
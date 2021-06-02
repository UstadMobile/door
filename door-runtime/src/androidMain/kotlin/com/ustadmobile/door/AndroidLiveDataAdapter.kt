package com.ustadmobile.door

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

/**
 * Because we can't typealias MutableLiveData (due to the difference between protected in Java vs. Kotlin), this class
 * will wrap Android's MutableLiveData as a subclass of DoorMutableLiveData
 */
internal class AndroidLiveDataAdapter<T>(private val srcLiveData: MutableLiveData<T>) : DoorMutableLiveData<T>(){

    override fun sendValue(value: T) {
        srcLiveData.postValue(value)
    }

    override fun setVal(value: T) {
        srcLiveData.value = value
    }

    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        srcLiveData.observe(owner, observer)
    }

    override fun observeForever(observer: Observer<in T>) {
        srcLiveData.observeForever(observer)
    }

    override fun removeObserver(observer: Observer<in T>) {
        srcLiveData.removeObserver(observer)
    }

    override fun removeObservers(owner: LifecycleOwner) {
        srcLiveData.removeObservers(owner)
    }
}
package com.ustadmobile.door

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.ustadmobile.door.ext.concurrentSafeMapOf

/**
 * Implementation similar to Android's MediatorLiveData
 */
@Suppress("unused")
open class DoorMediatorLiveData<T> : MutableLiveData<T>() {

    private class Source<T>(
        private val liveData: LiveData<T>,
        private val mObserver: Observer<T>
    ) : Observer<T> {

        override fun onChanged(t: T) {
            mObserver.onChanged(t)
        }

        fun plug(){
            liveData.observeForever(this)
        }

        fun unplug() {
            liveData.removeObserver(this)
        }
    }

    private val sources = concurrentSafeMapOf<LiveData<*>, Source<*>>()

    fun <S> addSource(source: LiveData<S>, doorObserver: Observer<S>) {
        sources[source]?.unplug()

        val newSource = Source(source, doorObserver)
        sources[source] = newSource
        if(hasActiveObservers()) {
            newSource.plug()
        }
    }

    fun removeSource(source: LiveData<*>) {
        sources.remove(source)?.unplug()
    }

    override fun onActive() {
        super.onActive()
        sources.forEach {
            it.value.plug()
        }
    }

    override fun onInactive() {
        super.onInactive()
        sources.forEach {
            it.value.unplug()
        }
    }
}
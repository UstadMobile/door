package com.ustadmobile.door

import com.ustadmobile.door.ext.concurrentSafeMapOf

/**
 * Implementation similar to Android's MediatorLiveData
 */
open class DoorMediatorLiveData<T> : DoorMutableLiveData<T>() {

    private class Source<T>(
        private val liveData: DoorLiveData<T>,
        private val mObserver: DoorObserver<T>
    ) : DoorObserver<T> {

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

    private val sources = concurrentSafeMapOf<DoorLiveData<*>, Source<*>>()

    fun <S> addSource(source: DoorLiveData<S>, doorObserver: DoorObserver<S>) {
        sources[source]?.unplug()

        val newSource = Source(source, doorObserver)
        sources[source] = newSource
        if(hasActiveObservers()) {
            newSource.plug()
        }
    }

    fun removeSource(source: DoorLiveData<*>) {
        sources.remove(source)?.unplug()
    }

    override fun onActive2() {
        super.onActive2()
        sources.forEach {
            it.value.plug()
        }
    }

    override fun onInactive2() {
        super.onInactive2()
        sources.forEach {
            it.value.unplug()
        }
    }
}
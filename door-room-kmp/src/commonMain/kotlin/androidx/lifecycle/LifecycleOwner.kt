package androidx.lifecycle

interface LifecycleOwner {

    //Should be lifecycle.currentState
    //val currentState: Int

    val lifecycle: Lifecycle

    fun addObserver(observer: LifecycleObserver)

    fun removeObserver(observer: LifecycleObserver)

}
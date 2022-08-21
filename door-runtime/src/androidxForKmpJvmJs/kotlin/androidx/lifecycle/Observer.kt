package androidx.lifecycle

fun interface Observer<T> {

    fun onChanged(t: T)

}

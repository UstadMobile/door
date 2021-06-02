
package com.ustadmobile.door.ext

import androidx.lifecycle.MutableLiveData
import com.ustadmobile.door.AndroidLiveDataAdapter
import com.ustadmobile.door.DoorMutableLiveData

/**
 * Because we can't typealias MutableLiveData (due to the difference between protected in Java vs. Kotlin), this class
 * will wrap Android's MutableLiveData as a subclass of DoorMutableLiveData (e.g. so we can expose Android's
 * MutableLiveData as DoorMutableLiveData for consumption by multiplatform code)
 */
@Suppress("unused")
fun <T> MutableLiveData<T>.asDoorMutableLiveData(): DoorMutableLiveData<T> = AndroidLiveDataAdapter(this)

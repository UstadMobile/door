package com.ustadmobile.door.ext


import androidx.lifecycle.LiveData
import com.ustadmobile.door.RepositoryLoadHelper

fun LiveData<*>.isRepositoryLiveData() = (this is RepositoryLoadHelper<*>.LiveDataWrapper2<*>)
package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorLiveData
import com.ustadmobile.door.RepositoryLoadHelper

fun DoorLiveData<*>.isRepositoryLiveData() = (this is RepositoryLoadHelper<*>.LiveDataWrapper2<*>)
package com.ustadmobile.door.ext

import com.ustadmobile.door.DoorLifecycleObserver
import com.ustadmobile.door.DoorObserver
import com.ustadmobile.door.RepositoryLoadHelperLifecycleHelper

fun RepositoryLoadHelperLifecycleHelper.isActive() = this.currentState == DoorLifecycleObserver.STARTED
        || this.currentState == DoorLifecycleObserver.RESUMED
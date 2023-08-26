package com.ustadmobile.door.ext

import com.ustadmobile.door.RepositoryLoadHelperLifecycleHelper
import com.ustadmobile.door.lifecycle.DoorState

fun RepositoryLoadHelperLifecycleHelper.isActive() = this.currentState >= DoorState.STARTED.ordinal

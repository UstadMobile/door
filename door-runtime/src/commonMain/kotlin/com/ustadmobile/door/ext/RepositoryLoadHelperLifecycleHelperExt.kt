package com.ustadmobile.door.ext

import com.ustadmobile.door.lifecycle.Lifecycle
import com.ustadmobile.door.RepositoryLoadHelperLifecycleHelper

fun RepositoryLoadHelperLifecycleHelper.isActive() = this.currentState >= Lifecycle.State.STARTED.ordinal

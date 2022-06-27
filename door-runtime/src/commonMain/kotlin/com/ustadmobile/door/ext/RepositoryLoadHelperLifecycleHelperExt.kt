package com.ustadmobile.door.ext

import androidx.lifecycle.Lifecycle
import com.ustadmobile.door.RepositoryLoadHelperLifecycleHelper

fun RepositoryLoadHelperLifecycleHelper.isActive() = this.currentState >= Lifecycle.State.STARTED.ordinal

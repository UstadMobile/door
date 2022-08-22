package com.ustadmobile.door.ext

import com.ustadmobile.door.lifecycle.DoorState
import com.ustadmobile.door.lifecycle.Lifecycle

actual val Lifecycle.currentDoorState: DoorState
    get() = realCurrentDoorState

package com.ustadmobile.door.ext

import com.ustadmobile.door.lifecycle.DoorState
import com.ustadmobile.door.lifecycle.Lifecycle

expect val Lifecycle.currentDoorState: DoorState

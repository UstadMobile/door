package com.ustadmobile.door.ext

import androidx.lifecycle.Lifecycle
import com.ustadmobile.door.lifecycle.DoorState

fun Lifecycle.State.toDoorState(): DoorState = when(this) {
    Lifecycle.State.DESTROYED -> DoorState.DESTROYED
    Lifecycle.State.INITIALIZED -> DoorState.INITIALIZED
    Lifecycle.State.CREATED -> DoorState.CREATED
    Lifecycle.State.STARTED -> DoorState.STARTED
    Lifecycle.State.RESUMED -> DoorState.RESUMED
}
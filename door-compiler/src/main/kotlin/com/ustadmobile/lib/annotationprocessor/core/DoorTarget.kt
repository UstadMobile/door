package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.door.DoorDbType


enum class DoorTarget(val supportedDbs: List<Int>) {
    JVM(listOf(DoorDbType.SQLITE, DoorDbType.POSTGRES)),
    ANDROID(listOf(DoorDbType.SQLITE)),
    JS(listOf(DoorDbType.SQLITE))
}

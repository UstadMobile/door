package com.ustadmobile.door.util

import kotlin.random.Random

actual fun generateDoorNodeId(maxNodeId: Int) : Int {
    return Random.nextInt(0, maxNodeId)
}
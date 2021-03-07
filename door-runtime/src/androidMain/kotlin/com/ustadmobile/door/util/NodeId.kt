package com.ustadmobile.door.util

import android.content.Context
import kotlin.random.Random

private lateinit var appContextInternal: Context

fun initDoorId(appContext: Context) {
    appContextInternal = appContext
}

actual fun generateDoorNodeId(maxNodeId: Int) : Int {
    return Random.nextInt(0, maxNodeId)
}
package com.ustadmobile.door.util

import kotlinx.browser.localStorage
import org.w3c.dom.get
import kotlin.math.floor
import kotlin.random.Random

/**
 * Generate an id between 0 and maxNodeId. This is implemented at the platform level. On Android
 * this can be done using the advertising id. On JVM this can be done by hasing the network mac
 * addresses.
 */
actual fun generateDoorNodeId(maxNodeId: Int): Int {
    val nodeIdRef = "nodeId_ref"
    var foundNodeId = localStorage[nodeIdRef]?.toInt() ?: 0
    if(foundNodeId == 0){
        foundNodeId = (floor((Random.nextDouble() * maxNodeId))).toInt()
        localStorage.setItem(nodeIdRef, foundNodeId.toString())
    }
    return foundNodeId
}
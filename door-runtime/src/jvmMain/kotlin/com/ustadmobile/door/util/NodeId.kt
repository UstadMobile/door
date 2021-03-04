package com.ustadmobile.door.util

import java.net.NetworkInterface
import kotlin.random.Random

actual fun generateDoorNodeId(maxNodeId: Int) : Int {
    try {
        var macString = ""
        NetworkInterface.getNetworkInterfaces().toList().forEach {netInterface ->
            netInterface.hardwareAddress.forEach {
                macString += String.format("%02X", it)
            }
        }

        return macString.hashCode() and maxNodeId
    }catch(e: Exception) {
        return Random.nextInt(1, maxNodeId)
    }
}

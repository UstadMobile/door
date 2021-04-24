package com.ustadmobile.door.util

import kotlin.js.Date

actual fun randomUuid(): KmpUuid = KmpUuid(0L,0L)

actual class KmpUuid actual constructor(mostSigBits: Long, leastSigBits: Long) {
    actual override fun toString(): String {
        var timeStamp = Date().getTime()
        return js("'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(mChar) {" +
                "var mRandom = (timeStamp + Math.random()*16)%16 | 0;" +
                "timeStamp = Math.floor(timeStamp/16);" +
                "return (mChar=='x' ? mRandom :(r&0x3|0x8)).toString(16);})") as String
    }

}
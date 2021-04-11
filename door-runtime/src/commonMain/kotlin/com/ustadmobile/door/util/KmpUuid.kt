package com.ustadmobile.door.util

expect fun randomUuid(): KmpUuid

expect class KmpUuid(mostSigBits: Long, leastSigBits: Long) {

    override fun toString(): String

}

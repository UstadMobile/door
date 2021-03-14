package com.ustadmobile.door

actual interface DoorQuery {
    actual fun getSql(): String
    actual fun getArgCount(): Int

}
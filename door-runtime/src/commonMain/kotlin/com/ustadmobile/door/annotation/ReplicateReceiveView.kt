package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)

annotation class ReplicateReceiveView(val name: String, val value: String)

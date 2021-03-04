package com.ustadmobile.door.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class ParamName(val value: String)
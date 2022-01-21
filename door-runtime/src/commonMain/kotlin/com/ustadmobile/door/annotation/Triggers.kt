package com.ustadmobile.door.annotation

/**
 * This annotation exists solely to hold an array of Trigger annotations. Due to Kotlin not supporting @Repeatable
 * for annotation properly ( https://github.com/Kotlin/KEEP/issues/257 ), this is the simplest workaround.
 */
@Target(AnnotationTarget.CLASS)
annotation class Triggers(val value: Array<Trigger> = arrayOf())

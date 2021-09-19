package com.ustadmobile.door.annotation

import kotlin.reflect.KClass

annotation class ReplicationRunOnChange(
    val value: Array<KClass<*>>,


    @Suppress("ReplaceArrayOfWithLiteral") //Using literal [] does not compile on Javascript
    val checkPendingReplicationsFor: Array<KClass<*>> = arrayOf()
)

package com.ustadmobile.door.annotation

import kotlin.reflect.KClass

annotation class ReplicationRunOnChange(
    val value: Array<KClass<*>>,

    val notifyEntity: Array<KClass<*>> = []
)

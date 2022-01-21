package com.ustadmobile.door.annotation

import kotlin.reflect.KClass

annotation class ReplicationCheckPendingNotificationsFor(
    val value: Array<KClass<*>> = arrayOf()
)

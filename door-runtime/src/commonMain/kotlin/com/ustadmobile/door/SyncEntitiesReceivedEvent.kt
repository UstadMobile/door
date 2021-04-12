package com.ustadmobile.door

import kotlin.reflect.KClass

data class SyncEntitiesReceivedEvent<T: Any>(val entityClass: KClass<T>, val entitiesReceived: List<T>)

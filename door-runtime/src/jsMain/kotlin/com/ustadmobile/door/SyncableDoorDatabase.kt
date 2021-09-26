package com.ustadmobile.door

import io.ktor.client.*
import kotlin.reflect.KClass

actual fun <T : DoorDatabase> T.wrap(dbClass: KClass<T>): T {
    TODO("wrap: Not yet implemented")
}

actual fun <T : DoorDatabase> T.unwrap(dbClass: KClass<T>): T {
    TODO("unwrap: Not yet implemented")
}

actual inline fun <reified T : DoorDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T {
    TODO("asRepository: Not yet implemented")
}
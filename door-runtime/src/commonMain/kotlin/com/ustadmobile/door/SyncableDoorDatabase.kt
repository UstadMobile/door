package com.ustadmobile.door

import kotlin.reflect.KClass

interface SyncableDoorDatabase {

    val master: Boolean

}

/**
 * Get a repository for the given database. This should be kept as a singleton.
 *
 * @param repositoryConfig config for the repository to be created
 */
expect inline fun <reified  T: DoorDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T

expect fun <T: DoorDatabase> T.wrap(dbClass: KClass<T>): T

expect fun <T: DoorDatabase> T.unwrap(dbClass: KClass<T>): T

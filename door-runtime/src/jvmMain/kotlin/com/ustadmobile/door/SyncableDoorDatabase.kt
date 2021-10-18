package com.ustadmobile.door

import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
actual inline fun <reified  T: DoorDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T {
    val dbClass = T::class
    val repoImplClass = Class.forName("${dbClass.qualifiedName}_Repo") as Class<T>

    val dbUnwrapped = if(this is DoorDatabaseReplicateWrapper) {
        this.unwrap(dbClass)
    }else {
        this
    }

    val repo = repoImplClass
            .getConstructor(dbClass.java, dbClass.java, RepositoryConfig::class.java)
            .newInstance(this, dbUnwrapped, repositoryConfig)
    return repo
}

/**
 * Wrap a syncable database to prevent accidental use of the database instead of the repo on queries
 * that modify syncable entities. All modification queries (e.g. update, insert etc) must be done on
 * the repo.
 */
@Suppress("UNCHECKED_CAST")
actual fun <T: DoorDatabase> T.wrap(dbClass: KClass<T>) : T {
    val wrapperClass = Class.forName("${dbClass.qualifiedName}${DoorDatabaseReplicateWrapper.SUFFIX}") as Class<T>
    return wrapperClass.getConstructor(dbClass.java).newInstance(this)
}

@Suppress("UNCHECKED_CAST")
actual fun <T: DoorDatabase> T.unwrap(dbClass: KClass<T>): T {
    return (this as DoorDatabaseReplicateWrapper).realDatabase as T
}

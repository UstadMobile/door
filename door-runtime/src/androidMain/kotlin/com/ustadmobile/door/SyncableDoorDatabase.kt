package com.ustadmobile.door

import kotlin.reflect.KClass

//There is no alternative to the unchecked cast here. The cast is operating on generated code, so it will always
//succeed
@Suppress("UNCHECKED_CAST")
actual inline fun <reified  T: DoorDatabase> T.asRepository(repositoryConfig: RepositoryConfig): T {
    val dbUnwrapped = if(this is DoorDatabaseReplicateWrapper) {
        this.unwrap(T::class)
    }else {
        this
    }

    val dbClass = T::class
    val repoImplClass = Class.forName("${dbClass.qualifiedName}_Repo") as Class<T>
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
    if(this is DoorDatabaseReplicateWrapper) {
        return this.realDatabase as T
    }else {
        return this
    }
}

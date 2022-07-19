package com.ustadmobile.lib.annotationprocessor.core.ext

import androidx.room.Dao
import androidx.room.Database
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.annotation.Repository
import kotlin.reflect.KClass


/**
 * Assuming that this KSClassDeclaration represents a Database, get a list of
 * all the DAOs that it references
 */
fun KSClassDeclaration.dbEnclosedDaos(): List<KSClassDeclaration> {

    @OptIn(KspExperimental::class)
    fun Iterable<KSTypeReference>.daos() = filter {
        it.resolve().declaration.isAnnotationPresent(Dao::class)
    }.map {
        it.resolve().declaration as KSClassDeclaration
    }


    val functionDaos =  getAllFunctions().toList().mapNotNull { it.returnType }
        .daos()
    val propertyDaos = getAllProperties().toList().map { it.type }
        .daos()

    return functionDaos + propertyDaos
}

/**
 * Get a list of the KSClassDeclarations for all entities on the given database
 */
@Suppress("UNCHECKED_CAST") // No other way, and it will always work
@OptIn(KspExperimental::class)
fun KSClassDeclaration.allDbEntities(): List<KSClassDeclaration> {
    val dbAnnotation = getKSAnnotationsByType(Database::class).first()
    val annotationVal = dbAnnotation.arguments.first { it.name?.asString() == "entities" }
    val entityKsTypeList = annotationVal.value as List<KSType>
    return entityKsTypeList.map {
        it.declaration as KSClassDeclaration
    }
}

/**
 * Indicates whether or not this database should have a read only wrapper generated. The ReadOnlyWrapper should be
 * be generated for databses that have repositories and replicated entities.
 *
 */
fun KSClassDeclaration.dbHasReplicateWrapper() : Boolean {
    val hasRepos = dbEnclosedDaos().any { it.hasAnnotation(Repository::class) }
    val hasReplicatedEntities = allDbEntities().any { it.hasAnnotation(ReplicateEntity::class) }
    return hasRepos && hasReplicatedEntities
}

fun KSClassDeclaration.allDbClassDaoGetters(): List<KSDeclaration> {
    return getAllFunctions().filter {
        it.returnType?.resolve()?.declaration?.hasAnnotation(Dao::class) == true
    }.toList() + getAllProperties().filter {
        it.type.resolve().declaration.hasAnnotation(Dao::class)
    }.toList()
}

/**
 * Use this in place of getAllSuperTypes when looking for all supertypes...
 *
 * It will resolve typealiases
 */
fun KSClassDeclaration.getAllSuperTypeClassDeclarations(): List<KSClassDeclaration> {
    return getAllSuperTypes().map {
        if(it is KSTypeAlias) {
            it.findActualTypeClassDecl()
        }else{
            it.declaration as KSClassDeclaration
        }
    }.toList()
}

/**
 * Get a list of all functions (recursive, including all supertypes).
 */
fun KSClassDeclaration.getAllFunctionsIncSuperTypes(
    incSuperTypes: Boolean = true,
    filterFn: (KSFunctionDeclaration) -> Boolean = {true},
) : List<KSFunctionDeclaration> {
    val allClassDecls = listOf(this) + if(incSuperTypes) {
        getAllSuperTypeClassDeclarations()
    }else {
        emptyList()
    }

    return allClassDecls.flatMap { superType ->
        superType.getAllFunctions().filter(filterFn)
    }
}

/**
 * Find all functions that have a given annotation class (by default including all supertypes).
 */
fun <T: Annotation> KSClassDeclaration.getAllFunctionsIncSuperTypesWithAnnotation(
    annotationKClass: KClass<T>,
    incSuperTypes: Boolean = true,
): List<KSFunctionDeclaration> = getAllFunctionsIncSuperTypes(incSuperTypes) {
    it.hasAnnotation(annotationKClass)
}
fun KSClassDeclaration.isListDeclaration(): Boolean {
    return simpleName.asString() in listOf("List", "MutableList") &&
        qualifiedName?.asString() == "kotlin"
}


fun KSClassDeclaration.toClassNameWithSuffix(
    suffix: String
) = ClassName(packageName.asString(), simpleName.asString() + suffix)


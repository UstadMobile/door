package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.*
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ustadmobile.door.DoorDbType
import javax.lang.model.element.ExecutableElement
import com.ustadmobile.door.RepositoryConnectivityListener
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.CLASSNAME_ILLEGALSTATEEXCEPTION
import com.ustadmobile.lib.annotationprocessor.core.ext.getAnnotation
import com.ustadmobile.lib.annotationprocessor.core.ext.propertyOrReturnType
import com.ustadmobile.lib.annotationprocessor.core.ext.toPropertyOrEmptyFunctionCaller
import kotlin.IllegalArgumentException

/**
 * Add a method or property that overrides the given accessor. The ExecutableElement could be a
 * getter method - in which case we need to add a Kotlin property with a getter method. Otherwise we
 * add an overriding function
 */
fun TypeSpec.Builder.addAccessorOverride(methodName: String, returnType: TypeName, codeBlock: CodeBlock) {
    if(methodName.startsWith("get")) {
        val propName = methodName.substring(3, 4).lowercase() + methodName.substring(4)
        val getterFunSpec = FunSpec.getterBuilder().addCode(codeBlock)
        addProperty(PropertySpec.builder(propName, returnType,
                KModifier.OVERRIDE).getter(getterFunSpec.build()).build())
    }else {
        addFunction(FunSpec.builder(methodName)
                .addModifiers(KModifier.OVERRIDE)
                .returns(returnType)
                .addCode(codeBlock)
                .build())
    }
}

fun TypeSpec.Builder.addDaoPropOrGetterOverride(
    daoPropOrGetterDeclaration: KSDeclaration,
    codeBlock: CodeBlock,
): TypeSpec.Builder {
    val daoType = daoPropOrGetterDeclaration.propertyOrReturnType()?.resolve()
        ?: throw IllegalArgumentException("Dao getter given ${daoPropOrGetterDeclaration.simpleName.asString()} has no type!")

    if(daoPropOrGetterDeclaration is KSFunctionDeclaration) {
        addFunction(FunSpec.builder(daoPropOrGetterDeclaration.simpleName.asString())
            .addModifiers(KModifier.OVERRIDE)
            .returns(daoType.toTypeName())
            .addCode(codeBlock)
            .build())
    }else if(daoPropOrGetterDeclaration is KSPropertyDeclaration) {
        addProperty(PropertySpec.builder(daoPropOrGetterDeclaration.simpleName.asString(),
                daoType.toTypeName())
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                .addCode(codeBlock)
                .build())
            .build())
    }else {
        throw IllegalArgumentException("${daoPropOrGetterDeclaration.simpleName} is not property or function!")
    }

    return this
}

fun TypeSpec.Builder.addDaoPropOrGetterDelegate(
    daoPropOrGetterDeclaration: KSDeclaration,
    prefix: String
): TypeSpec.Builder = addDaoPropOrGetterOverride(daoPropOrGetterDeclaration,
    CodeBlock.of("return $prefix${daoPropOrGetterDeclaration.toPropertyOrEmptyFunctionCaller()}\n"))

/**
 * Implement the DoorDatabaseRepository methods for add/remove mirror etc. by delegating to a
 * RepositoryHelper.
 */
internal fun TypeSpec.Builder.addRepositoryHelperDelegateCalls(delegatePropName: String): TypeSpec.Builder {
    addProperty(PropertySpec.builder("connectivityStatus", INT)
            .addModifiers(KModifier.OVERRIDE)
            .mutable(true)
            .getter(FunSpec.getterBuilder()
                    .addCode("return $delegatePropName.connectivityStatus\n")
                    .build())
            .setter(FunSpec.setterBuilder()
                    .addParameter("newValue", INT)
                    .addCode("$delegatePropName.connectivityStatus = newValue\n")
                    .build())
            .build())
    addFunction(FunSpec.builder("addWeakConnectivityListener")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("listener", RepositoryConnectivityListener::class)
            .addCode("$delegatePropName.addWeakConnectivityListener(listener)\n")
            .build())
    addFunction(FunSpec.builder("removeWeakConnectivityListener")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("listener", RepositoryConnectivityListener::class)
            .addCode("$delegatePropName.removeWeakConnectivityListener(listener)\n")
            .build())

    return this
}

/**
 * Add a property
 */
fun TypeSpec.Builder.addDbVersionProperty(dbTypeElement: TypeElement): TypeSpec.Builder {
    addProperty(PropertySpec.builder("dbVersion", INT)
            .addModifiers(KModifier.OVERRIDE)
            .getter(FunSpec.getterBuilder()
                    .addCode("return ${dbTypeElement.getAnnotation(Database::class.java).version}")
                    .build())
            .build())

    return this
}

fun TypeSpec.Builder.addDbVersionProperty(dbClassDecl: KSClassDeclaration): TypeSpec.Builder {
    return addProperty(PropertySpec.builder("dbVersion", INT)
        .addModifiers(KModifier.OVERRIDE)
        .getter(FunSpec.getterBuilder()
            .addCode("return ${dbClassDecl.getAnnotation(Database::class)?.version}")
            .build())
        .build())
}

/**
 * Add an override for the room database createOpenHelper function which is required for any
 * child class of the database on Android
 */
fun TypeSpec.Builder.addRoomDatabaseCreateOpenHelperFunction() : TypeSpec.Builder {
    addFunction(FunSpec.builder("createOpenHelper")
            .addParameter("config", ClassName("androidx.room", "DatabaseConfiguration"))
            .returns(ClassName("androidx.sqlite.db", "SupportSQLiteOpenHelper"))
            .addModifiers(KModifier.OVERRIDE, KModifier.PROTECTED)
            .addCode("throw IllegalAccessException(%S)\n", "Cannot use open helper on repository")
            .build())

    return this
}

/**
 * Add an override for the room database createInvalidationTracker which is required for any
 * child class of the database on Android
 */
fun TypeSpec.Builder.addRoomCreateInvalidationTrackerFunction() : TypeSpec.Builder {
    addFunction(FunSpec.builder("createInvalidationTracker")
            .returns(ClassName("androidx.room", "InvalidationTracker"))
            .addModifiers(KModifier.OVERRIDE, KModifier.PROTECTED)
            .addCode("return %T.createDummyInvalidationTracker(this)\n",
                    ClassName("com.ustadmobile.door","DummyInvalidationTracker"))
            .build())

    return this
}

/**
 * If code actually wants to use the invalidation tracker, it should use the real database invalidation tracker, not
 * a dummy.
 */
fun TypeSpec.Builder.addOverrideGetRoomInvalidationTracker(realDbVarName: String) : TypeSpec.Builder {
    addFunction(FunSpec.builder("getInvalidationTracker")
        .returns(ClassName("androidx.room", "InvalidationTracker"))
        .addModifiers(KModifier.OVERRIDE)
        .addCode("return $realDbVarName.getInvalidationTracker()\n")
        .build())
    return this
}

fun TypeSpec.Builder.addOverrideGetInvalidationTrackerVal(realDbVarName: String) : TypeSpec.Builder {
    addProperty(PropertySpec.builder("invalidationTracker", InvalidationTracker::class,
            KModifier.OVERRIDE)
        .getter(FunSpec.getterBuilder()
            .addCode("return $realDbVarName.invalidationTracker")
            .build())
        .build())
    return this
}

fun TypeSpec.Builder.addThrowExceptionOverride(
    funName: String = "clearAllTables",
    suspended: Boolean = false,
    exceptionMessage: String = "Cannot use this to run $funName",
    exceptionClass: ClassName = CLASSNAME_ILLEGALSTATEEXCEPTION
): TypeSpec.Builder {
    addFunction(FunSpec.builder(funName)
        .applyIf(suspended) {
            addModifiers(KModifier.SUSPEND)
        }
        .addModifiers(KModifier.OVERRIDE)
        .addCode("throw %T(%S)\n", exceptionClass,  exceptionMessage)
        .build())

    return this
}

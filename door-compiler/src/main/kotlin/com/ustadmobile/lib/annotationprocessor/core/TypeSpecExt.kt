package com.ustadmobile.lib.annotationprocessor.core

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ustadmobile.door.annotation.DoorDatabase
import com.ustadmobile.door.replication.DoorRepositoryReplicationClient
import com.ustadmobile.door.room.InvalidationTracker
import com.ustadmobile.lib.annotationprocessor.core.AbstractDbProcessor.Companion.CLASSNAME_ILLEGALSTATEEXCEPTION
import com.ustadmobile.lib.annotationprocessor.core.ext.getAnnotation
import com.ustadmobile.lib.annotationprocessor.core.ext.propertyOrReturnType
import com.ustadmobile.lib.annotationprocessor.core.ext.toPropertyOrEmptyFunctionCaller
import kotlinx.coroutines.flow.Flow

fun TypeSpec.Builder.addDaoPropOrGetterOverride(
    daoPropOrGetterDeclaration: KSDeclaration,
    codeBlock: CodeBlock,
): TypeSpec.Builder {
    val daoType = daoPropOrGetterDeclaration.propertyOrReturnType()?.resolve()
        ?: throw IllegalArgumentException("Dao getter given ${daoPropOrGetterDeclaration.simpleName.asString()} has no type!")

    when (daoPropOrGetterDeclaration) {
        is KSFunctionDeclaration -> {
            addFunction(FunSpec.builder(daoPropOrGetterDeclaration.simpleName.asString())
                .addModifiers(KModifier.OVERRIDE)
                .returns(daoType.toTypeName())
                .addCode(codeBlock)
                .build())
        }

        is KSPropertyDeclaration -> {
            addProperty(PropertySpec.builder(daoPropOrGetterDeclaration.simpleName.asString(),
                daoType.toTypeName())
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder()
                    .addCode(codeBlock)
                    .build())
                .build())
        }

        else -> {
            throw IllegalArgumentException("${daoPropOrGetterDeclaration.simpleName} is not property or function!")
        }
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
    addProperty(
        PropertySpec.builder(
        "clientState",
            Flow::class.parameterizedBy(DoorRepositoryReplicationClient.ClientState::class),
            KModifier.OVERRIDE
        ).getter(FunSpec.getterBuilder()
            .addCode("return $delegatePropName.clientState\n")
            .build())
        .build()
    )

    addFunction(FunSpec.builder("close")
        .addModifiers(KModifier.OVERRIDE)
        .addCode("$delegatePropName.close()\n")
        .build())

    addFunction(
        FunSpec.builder("remoteNodeIdOrNull")
            .addModifiers(KModifier.OVERRIDE)
            .returns(LONG.copy(nullable = true))
            .addCode("return ${delegatePropName}.remoteNodeIdOrNull()\n")
            .build()
    )
    addFunction(
        FunSpec.builder("remoteNodeIdOrFake")
            .addModifiers(KModifier.OVERRIDE)
            .returns(LONG)
            .addCode("return ${delegatePropName}.remoteNodeIdOrFake()\n")
            .build()
    )

    return this
}

fun TypeSpec.Builder.addDbVersionProperty(dbClassDecl: KSClassDeclaration): TypeSpec.Builder {
    return addProperty(PropertySpec.builder("dbVersion", INT)
        .addModifiers(KModifier.OVERRIDE)
        .getter(FunSpec.getterBuilder()
            .addCode("return ${dbClassDecl.getAnnotation(DoorDatabase::class)?.version}")
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
            .addCode("return %M()\n",
                MemberName("com.ustadmobile.door.util", "makeDummyInvalidationHandler"))
            .build())

    return this
}

fun TypeSpec.Builder.addOverrideInvalidationTracker(realDbVarName: String) : TypeSpec.Builder {
    addProperty(PropertySpec.builder("invalidationTracker", InvalidationTracker::class,
            KModifier.OVERRIDE)
        .getter(FunSpec
            .getterBuilder()
            .addCode("return $realDbVarName.invalidationTracker\n")
            .build()
        )
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

fun TypeSpec.Builder.addSuperClassOrInterface(superKSClassDeclaration: KSClassDeclaration) : TypeSpec.Builder {
    if(superKSClassDeclaration.classKind == ClassKind.CLASS) {
        superclass(superKSClassDeclaration.toClassName())
    }else {
        addSuperinterface(superKSClassDeclaration.toClassName())
    }

    return this
}

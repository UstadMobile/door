package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Entity
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ustadmobile.door.annotation.ReplicateEntity
import com.ustadmobile.door.replication.DoorReplicationEntity
import com.ustadmobile.lib.annotationprocessor.core.ext.*

/**
 * Generate a delegation style function call, e.g.
 * varName.callMethod(param1, param2, param3)
 *
 * @param varName the variable name for the object that has the desired function
 * @param funSpec the function spec that we are generating a delegated call for
 */
fun CodeBlock.Builder.addDelegateFunctionCall(varName: String, funSpec: FunSpec) : CodeBlock.Builder {
    return add("$varName.${funSpec.name}(")
            .add(funSpec.parameters.joinToString { it.name })
            .add(")")
}


/**
 * Add SQL to the output. This could be done as appending to a list variable, or this can be done as a function call
 */
fun CodeBlock.Builder.addSql(
    execSqlFn: String?,
    sqlListVar: String? = null,
    sql: String
) : CodeBlock.Builder {
    if(sqlListVar != null) {
        add("$sqlListVar += %S\n", sql)
    }else {
        add("$execSqlFn(%S)\n", sql)
    }

    return this
}

/**
 * Add code that will create the table using a function that executes SQL (e.g. stmt.executeUpdate or
 * db.execSQL) to create a table and any indices specified. Indices can be specified by through
 * the indices argument (e.g. for those that come from the Entity annotation) and via the
 * entityTypeSpec for those that are specified using ColumnInfo(index=true) annotation.
 *
 * @param entityKSClass a KSClassDeclaration that represents the entity a table is being created for
 * @param execSqlFn the literal string that should be added to call a function that runs SQL
 * @param dbProductType DoorDbType.SQLITE or POSTGRES

 */
fun CodeBlock.Builder.addCreateTableCode(
    entityKSClass: KSClassDeclaration,
    execSqlFn: String,
    dbProductType: Int,
    sqlListVar: String? = null,
    resolver: Resolver,
) : CodeBlock.Builder {
    addSql(execSqlFn, sqlListVar, entityKSClass.toCreateTableSql(dbProductType, resolver))
    val entity = entityKSClass.getKSAnnotationByType(Entity::class)?.toEntity()

    entity?.indices?.forEach { index ->
        val indexName = if(index.name != "") {
            index.name
        }else {
            "index_${entityKSClass.entityTableName}_${index.value.joinToString(separator = "_", postfix = "", prefix = "")}"
        }

        addSql(execSqlFn, sqlListVar, "CREATE ${if(index.unique){ "UNIQUE " } else { "" } }INDEX $indexName" +
                " ON ${entityKSClass.entityTableName} (${index.value.joinToString()})")
    }

    return this
}


enum class PreparedStatementOp {
    GET, SET;

    override fun toString() = if(this == GET) {
        "get"
    }else {
        "set"
    }
}

/**
 * Generate code that will get a value back from the ResultSet of the desired type
 * e.g. getInt, getString, etc. or generate code that will set a parameter on a preparedstatement
 * e.g. setInt, setString etc.
 *
 * @param type the property type that is being used
 * @param operation GET or SET
 * @param resolver KSP resolver
 *
 * @return CodeBlock with the correct get/set call. This does not include brackets. It will use extension
 * functions to handle nullable fields as required.
 */
fun CodeBlock.Builder.addGetResultOrSetQueryParamCall(
    type: KSType,
    operation: PreparedStatementOp,
    resolver: Resolver
): CodeBlock.Builder {
    val builtIns = resolver.builtIns
    val extPkgName = "com.ustadmobile.door.jdbc.ext"
    when {
        type == builtIns.intType -> add("${operation}Int")
        type == builtIns.intType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}IntNullable"))
        type == builtIns.shortType -> add("${operation}Short")
        type == builtIns.shortType.makeNullable() -> MemberName
        type == builtIns.byteType -> add("${operation}Byte")
        type == builtIns.byteType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}ByteNullable"))
        type == builtIns.longType -> add("${operation}Long")
        type == builtIns.longType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}LongNullable"))
        type == builtIns.floatType -> add("${operation}Float")
        type == builtIns.floatType.makeNullable() -> add("%M",
            MemberName(extPkgName,"${operation}FloatNullable"))
        type == builtIns.doubleType -> add("${operation}Double")
        type == builtIns.doubleType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}DoubleNullable"))
        type == builtIns.booleanType -> add("${operation}Boolean")
        type == builtIns.booleanType.makeNullable() -> add("%M",
            MemberName(extPkgName, "${operation}BooleanNullable"))
        (type == builtIns.stringType.makeNullable() ||
            (operation == PreparedStatementOp.SET && type == builtIns.stringType.makeNotNullable())
        ) -> {
            //If a string is nullable, we can use the normal JDBC for both get and set
            //If a string is not nullable, we can use the normal JDBC setString but we cant use getString for a
            //non-nullable string value
            add("${operation}String")
        }

        type == builtIns.stringType && operation == PreparedStatementOp.GET -> {
            add("%M", MemberName(extPkgName, "getStringNonNull"))
        }

        type == builtIns.arrayType -> add("${operation}Array")
        (type.declaration as? KSClassDeclaration)?.isListDeclaration() == true -> add("${operation}Array")
        else -> add("ERR_UNKNOWN_TYPE /* $type*/")
    }

    return this
}

/**
 * Create a PreparedStatement set param call for the given variable type
 */
fun CodeBlock.Builder.addPreparedStatementSetCall(
    type: KSType,
    resolver: Resolver
) = addGetResultOrSetQueryParamCall(type, PreparedStatementOp.SET, resolver)



/**
 * Generate a code block that will create a DoorReplicationEntity for a given type e.g.
 * Given
 * \@ReplicateEntity
 * class MyReplicateEntity { ... }
 *
 * val anEntity: MyReplicateEntity
 *
 * Can generate:
 *
 * DoorReplicationEntity(
 *    tableId = TABLEID,
 *    orUid = 0,
 *    entity = json.encodeToJsonElement(MyReplicateEntity.serializer(), anEntity).jsonObject
 * )
 *
 * @param entityKSClass The KSClassDeclaration for the entity that is annotated with @ReplicateEntity
 * @param entityValName the name of the entity in the codeblock
 * @param outgoingReplicationUidValue the value to assign for orUid on DoorReplicationEntity
 * @param jsonVarName the kotlinx serialization Json object val name in the codeblock
 */
fun CodeBlock.Builder.addCreateDoorReplicationCodeBlock(
    entityKSClass: KSClassDeclaration,
    entityValName: String,
    entityNullable: Boolean = false,
    outgoingReplicationUidValue: String = "0",
    jsonVarName: String,
): CodeBlock.Builder {
    val repEntityAnnotation = entityKSClass.getAnnotation(ReplicateEntity::class)
        ?: throw IllegalArgumentException("addToDoorReplicationEntityExtensionFn : " +
                "${entityKSClass.qualifiedName?.asString()} does not have ReplicateEntity annotation")

    var effectiveEntityValName = entityValName
    if(entityNullable) {
        beginControlFlow("$entityValName?.let")
        effectiveEntityValName = "it"
    }

    add("%T(", DoorReplicationEntity::class)
    indent()
    add("tableId = %L,\n", repEntityAnnotation.tableId)
    add("orUid = %L,\n", outgoingReplicationUidValue)
    add("entity = $jsonVarName.encodeToJsonElement(%T.serializer(), $effectiveEntityValName).%M,\n",
        entityKSClass.toClassName(),
        MemberName("kotlinx.serialization.json", "jsonObject"))
    unindent()
    add(")")

    if(entityNullable) {
        add("\n")
        endControlFlow()
    }

    return this
}

/**
 * Add a Kotlinx Serialization strategy to the code block for the given type e.g.
 *
 * ksType = Int, then add Int.serializer()
 * ksType = Int?, then add Int.serializer().nullable
 * ksType = List<Int> - then add ListSerializer(Int.serializer())
 * ksType = List<Int?> - then add ListSerializer(Int.serializer().nullable)
 * ksType = LongArray - then add LongArraySerializer()
 * ksType = String - then add String.serializer()
 * ksType = MyEntity - then add MyEntity.serializer()
 *
 * When ksType is not handled by Kotlinx serialization builtins (e.g. not a string, primitive type), then add
 * Type.serializer() e.g. for an entity class. The class MUST be annotated as @Serializable
 */
fun CodeBlock.Builder.addKotlinxSerializationStrategy(
    ksType: KSType,
    resolver: Resolver,
) : CodeBlock.Builder {
    fun addNullableExtensionIfRequired() {
        if(ksType.isMarkedNullable)
            add(".%M", MemberName("kotlinx.serialization.builtins", "nullable"))
    }


    when {
        //Use builtin serializers for builtin supported types e.g. Int, IntArray, String etc.
        ksType.isKotlinxSerializationBuiltInType(resolver) -> {
            add("%T.%M()", ksType.makeNotNullable().toTypeName(),
                MemberName("kotlinx.serialization.builtins", "serializer"))
            addNullableExtensionIfRequired()
        }

        //Use ListSerializer / ArraySerializer for kotlin.Array and List
        ksType.isListOrArrayType(resolver) -> {
            val componentType = ksType.unwrapComponentTypeIfListOrArray(resolver)
            val builtInTypeName = if(ksType.isArrayType())  "ArraySerializer"  else "ListSerializer"
            add("%M(", MemberName("kotlinx.serialization.builtins", builtInTypeName))
            add(CodeBlock.builder()
                .addKotlinxSerializationStrategy(componentType, resolver)
                .build())
            add(")")
            addNullableExtensionIfRequired()
        }

        //Use Type.serializer() otherwise e.g. for entity classes.
        else -> {
            add("%T.serializer()", ksType.makeNotNullable().toTypeName())
            addNullableExtensionIfRequired()
        }
    }

    return this
}

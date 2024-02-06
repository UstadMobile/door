package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Entity
import androidx.room.Query
import androidx.room.RawQuery
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorDbType.Companion.productNameForDbType
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.ext.prepareStatement
import com.ustadmobile.door.ext.sqlToPostgresSql
import com.ustadmobile.door.jdbc.Connection
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException

/**
 * This processor will create all tables on all databases for a given round of processing. It will also attempt
 * to validate the Entities and DAOs. If this processor reports any errors, SymbolProcessorWrapper will not proceed.
 *
 * This is done here in one place instead of in all processors to avoid reporting the same error to the user repeatedly.
 */
class DoorValidatorProcessor(
    private val logger: KSPLogger,
    private val environment: SymbolProcessorEnvironment,
): SymbolProcessor {

    val sqliteConnection: Connection

    var pgConnection: Connection?

    var connectionMap: Map<Int, Connection?>

    init {
        val sqliteTmpFile = File.createTempFile("dbprocessorkt", ".db")
        val sqliteDataSource = SQLiteDataSource().also {
            it.url = "jdbc:sqlite:${sqliteTmpFile.absolutePath}"
        }

        val postgresDbUrl: String? = environment.options[AnnotationProcessorWrapper.OPTION_POSTGRES_TESTDB]

        sqliteConnection = sqliteDataSource.connection
        pgConnection = postgresDbUrl?.takeIf { it.isNotBlank() }?.let { dbUrl ->
            try {
                Class.forName("org.postgresql.Driver")
                DriverManager.getConnection(dbUrl, environment.options[AnnotationProcessorWrapper.OPTION_POSTGRES_TESTUSER],
                    environment.options[AnnotationProcessorWrapper.OPTION_POSTGRES_TESTPASS])
            } catch(e: SQLException) {
                logger.error("Door: Postgres check database supplied using doordb_postgres_url, but could not connect: ${e.message}")
                null
            }
        }

        connectionMap = mapOf(DoorDbType.SQLITE to sqliteConnection, DoorDbType.POSTGRES to pgConnection)
    }

    private fun createAllTables(resolver: Resolver) {
        val dbs = resolver.getDatabaseSymbolsToProcess()

        dbs.flatMap { it.allDbEntities() }.toSet().forEach { entity ->
            if(!entity.hasAnnotation(Entity::class)) {
                logger.error("Class used as entity on database does not have @Entity annotation",
                    entity)
            }

            val allEntityProps = entity.getAllProperties().toList()
            val missingPkFields = entity.getKSAnnotationByType(Entity::class)?.toEntity()?.primaryKeys?.filter { pkFieldName ->
                !allEntityProps.any { it.simpleName.asString() == pkFieldName }
            } ?: emptyList()

            if(missingPkFields.isNotEmpty()) {
                logger.error("Entity annotation primary key " +
                        "fields not found: ${missingPkFields.joinToString()}", entity)
            }

            if(entity.entityPrimaryKeyProps.isEmpty()) {
                logger.error(
                    "Class ${entity.simpleName.asString()} used as entity does not have field(s) annotated @PrimaryKey or primaryKeys set",
                    entity)
            }

            val sqliteStmt = sqliteConnection.createStatement()
            val pgStmt = pgConnection?.createStatement()
            val stmts = mapOf(DoorDbType.SQLITE to sqliteStmt, DoorDbType.POSTGRES to pgStmt)
            stmts.forEach {  stmtEntry ->
                val dbType = stmtEntry.key
                stmtEntry.value?.use { stmt ->
                    val createTableSql = entity.toCreateTableSql(dbType, resolver)
                    try {
                        stmt.execute(createTableSql)
                    }catch(sqle: SQLException) {
                        logger.error("SQLException creating table for:" +
                                "${entity.simpleName.asString()} : ${sqle.message}. SQL was \"$createTableSql\"", entity)
                    }
                }
            }
        }
    }

    private fun validateReplicateEntities(resolver: Resolver) {
        val dbs = resolver.getDatabaseSymbolsToProcess()

        val allReplicateEntities = dbs.flatMap { it.allDbEntities() }.toSet()
            .filter { it.hasAnnotation(ReplicateEntity::class) }

        val replicableEntitiesGroupedById = allReplicateEntities
            .groupBy { it.getAnnotation(ReplicateEntity::class)?.tableId ?: 0 }

        replicableEntitiesGroupedById.filter { it.value.size > 1 }.forEach { duplicates ->
            logger.error(
                "Duplicate replicate tableId ${duplicates.key} : ${duplicates.value.joinToString { it.simpleName.asString() }} ",
                duplicates.value.first())
        }

        //Check entities with the ReplicateEntity annotation have all the required fields
        allReplicateEntities.forEach { entity ->
            try {

                val entityVersionIdField = entity.getAllProperties().filter { it.hasAnnotation(ReplicateEtag::class) }.toList()
                if(entityVersionIdField.size != 1) {
                    logger.error(
                        "@ReplicateEntity ${entity.qualifiedName?.asString()} must have exactly one field annotated @ReplicateEtag",
                        entity
                    )
                }
            }catch(e: IllegalArgumentException){
                logger.error("ReplicateEntity ${entity.qualifiedName?.asString()} must have a tracker entity specified",
                    entity)
            }
        }
    }

    private fun validateTriggers(
        resolver: Resolver,
        target: DoorTarget
    ) {
        val dbs = resolver.getDatabaseSymbolsToProcess()
        //After all tables have been created, check that the SQL in all triggers is actually valid
        dbs.flatMap { it.allDbEntities() }.toSet()
            .filter { it.hasAnnotation(Triggers::class) }
            .forEach { entity ->
                entity.getAnnotations(Triggers::class).firstOrNull()?.value?.forEach { trigger ->
                    val stmt = sqliteConnection.createStatement()!!
                    val pgStmt = pgConnection?.createStatement()
                    val stmtsMap = mapOf(DoorDbType.SQLITE to stmt, DoorDbType.POSTGRES to pgStmt)

                    //When the trigger SQL runs it will have access to NEW.(fieldName). We won't have that when we try and
                    //test the validity of the SQL statement here. Therefor NEW.(fieldname) and OLD.(fieldname) will be
                    //replaced with 0, null, or false based on the column type.
                    fun String.substituteTriggerPrefixes(dbProductType: Int) : String {
                        //Add space on both sides: replaceColNameWithDefaultValue will only match if next to
                        //whitespace, brackets, or a comma e.g. to avoid matching substrings by accident. The regex does
                        //not have an end of line, so the easiest workaround here is to simply add additional whitespace
                        //at the start and end.
                        var sqlFormatted = " $this "

                        val availablePrefixes = mutableListOf<String>()
                        if(!trigger.events.any { it == Trigger.Event.DELETE })
                            availablePrefixes += "NEW"

                        if(!trigger.events.any { it == Trigger.Event.INSERT })
                            availablePrefixes += "OLD"

                        availablePrefixes.forEach { prefix ->
                            entity.getAllColumnProperties(resolver).forEach { field ->
                                sqlFormatted = sqlFormatted.replaceColNameWithDefaultValueInSql(
                                    "$prefix.${field.simpleName.asString()}",
                                    field.type.resolve().defaultSqlQueryVal(resolver, dbProductType))
                            }
                        }

                        return sqlFormatted
                    }


                    stmtsMap.forEach {entry ->
                        var sqlToRun = trigger.conditionSql.expandSqlTemplates(
                            entity, resolver, target, dbType = entry.key,
                        ).substituteTriggerPrefixes(entry.key)
                        try {
                            if(entry.key == DoorDbType.POSTGRES)
                                sqlToRun = sqlToRun.sqlToPostgresSql()

                            entry.value.takeIf { trigger.conditionSql != "" }?.executeQuery(sqlToRun)
                        }catch(e: SQLException) {
                            logger.error("Trigger ${trigger.name} condition SQL " +
                                    "using ${productNameForDbType(entry.key)} " +
                                    "running SQL '${sqlToRun.trim()}' " +
                                    "error on: ${e.message}",
                                entity)
                        }
                    }


                    if(trigger.sqlStatements.isEmpty())
                        logger.error("Trigger ${trigger.name} has no SQL statements", entity)


                    var dbType = 0
                    var sqlToRun: String? = null
                    trigger.sqlStatements.forEach { sql ->
                        try {
                            stmtsMap.forEach { stmtEntry ->
                                dbType = stmtEntry.key
                                sqlToRun = sql.expandSqlTemplates(entity, resolver, target, dbType)
                                    .substituteTriggerPrefixes(stmtEntry.key)
                                if(dbType == DoorDbType.POSTGRES)
                                    sqlToRun = sqlToRun?.sqlToPostgresSql()

                                stmtEntry.value?.takeIf { sqlToRun != null }?.executeUpdate(sqlToRun)
                            }
                        }catch(e: SQLException) {
                            logger.error("Trigger ${trigger.name} (${productNameForDbType(dbType)}) exception: ${e.message} " +
                                    "running SQL '$sqlToRun'",
                                entity)
                        }
                    }
                }
            }
    }

    private fun validateHttpServerFunctionCall(
        funTypeName: String,//e.g. "pulQueriesToReplicate" or "authQuery"
        daoFun: KSFunctionDeclaration,
        dao: KSClassDeclaration,
        httpServerFunCall: KSAnnotation,
        resolver: Resolver,
    ) {
        val functionName = httpServerFunCall.getArgumentValueByNameAsString("functionName")

        val matchingFunctions = httpServerFunCall.getHttpServerFunctionCallDaoKSClass(
            dao, resolver
        ).getAllFunctions().filter {
            it.simpleName.asString() == functionName && !it.isOverridden(dao, resolver)
        }.toList()

        httpServerFunCall.getArgumentValueByNameAsAnnotationList("functionArgs")?.findDuplicates { httpServerFunctionParam, httpServerFunctionParam2 ->
            httpServerFunctionParam.getArgumentValueByNameAsString("name") == httpServerFunctionParam2.getArgumentValueByNameAsString("name")
        }.takeIf { it?.isNotEmpty() == true }?.also { duplicateParams ->
            logger.error("@HttpAccessible $funTypeName -" +
                    " \"$functionName\" - has duplicate parameters: " +
                    duplicateParams.map { it.getArgumentValueByNameAsString("name") }.toSet().joinToString(), daoFun)
        }

        if(matchingFunctions.size != 1) {
            logger.error("@HttpAccessible $funTypeName: referenced function " +
                    "\"$functionName\" must match one and only one function in DAO. " +
                    "This matches ${matchingFunctions.size}. Name must be unique. Overloading by parameter types" +
                    " is not supported.", daoFun)
        }else {
            val matchingFunction = matchingFunctions.first() //Eg the function that is being referred to by HttpServerFunctionCall

            httpServerFunCall.getArgumentValueByNameAsAnnotationList("functionArgs")?.mapNotNull { serverFunctionParam ->
                serverFunctionParam.getArgumentValueByNameAsString("name")
            }?.filter { serverFunctionParamName ->
                !matchingFunction.parameters.any { it.name?.asString() == serverFunctionParamName }
            }?.forEach { serverFunctionParamName ->
                logger.error("HttpServerFunction \"${matchingFunction.simpleName.asString()}\" has no parameter $serverFunctionParamName",
                    daoFun)
            }


            matchingFunction.parameters.forEach { matchingFunParam ->
                val daoFunMatchingParam = daoFun.parameters.firstOrNull { daoFunParam ->
                    daoFunParam.name?.asString() == matchingFunParam.name?.asString()
                }

                val annotatedParamValue = httpServerFunCall.getArgumentValueByNameAsAnnotationList("functionArgs")?.firstOrNull {
                    it.getArgumentValueByNameAsString("name") == matchingFunParam.name?.asString()
                }

                if(annotatedParamValue == null && daoFunMatchingParam != null
                    && daoFunMatchingParam.type.resolve() != matchingFunParam.type.resolve()) {
                    logger.error("@HttpAccessible - $funTypeName " +
                            "\"$functionName\" parameter type for: " +
                            "${matchingFunParam.name?.asString()} does not match", daoFun)
                }

                if(daoFunMatchingParam == null && annotatedParamValue == null){
                    logger.error("@HttpAccessible - $funTypeName " +
                            "\"$functionName\" cannot find parameter: " +
                            "${matchingFunParam.name?.asString()} - must match a parameter of the same naame in " +
                            " @HttpAccessible function or specify using @HttpServerFunctionParam", daoFun)
                }

                if(annotatedParamValue != null) {
                    val paramValName = annotatedParamValue.getArgumentValueByNameAsString("name")
                    val loggerPrefix = "@HttpAccessible - $funTypeName - \"$functionName\" - param $paramValName"
                    val argType = annotatedParamValue.getArgumentValueByNameAsKSType("argType")?.let {
                        HttpServerFunctionParam.ArgType.valueOf(it.declaration.simpleName.asString())
                    } ?: HttpServerFunctionParam.ArgType.LITERAL

                    when(argType) {
                        HttpServerFunctionParam.ArgType.LITERAL -> {
                            val literalValue = annotatedParamValue.getArgumentValueByNameAsString("literalValue")
                            if(literalValue.isNullOrEmpty()) {
                                logger.error("$loggerPrefix - type is literal,but literalValue is empty",
                                    daoFun)
                            }
                        }
                        HttpServerFunctionParam.ArgType.REQUESTER_NODE_ID -> {
                            if(matchingFunParam.type.resolve() != resolver.builtIns.longType)
                                logger.error("$loggerPrefix - type is requester node id, but param " +
                                        "type is not long.", daoFun)
                        }
                        HttpServerFunctionParam.ArgType.PAGING_KEY,
                        HttpServerFunctionParam.ArgType.PAGING_LOAD_SIZE-> {
                            if(matchingFunParam.type.resolve() != resolver.builtIns.intType ||
                                daoFun.returnType?.resolve()?.isPagingSource() != true) {
                                logger.error("$loggerPrefix - type is paging key/loadsize - but param " +
                                        "type is not int or function itself does not return pagingsource", daoFun)
                            }
                        }
                        HttpServerFunctionParam.ArgType.MAP_OTHER_PARAM -> {
                            val fromParamName = annotatedParamValue.getArgumentValueByNameAsString("fromName")
                            if(!daoFun.parameters.any { it.name?.asString() ==  fromParamName }) {
                                logger.error("$loggerPrefix function with HttpAccessible function has no parameter $fromParamName",
                                    daoFun)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun validateDaoHttpAccessibleFunctions(
        dao: KSClassDeclaration,
        resolver: Resolver,
    ) {
        val target = environment.doorTarget(resolver)
        //Don't run this validation on Kotlin/JS target
        if(target == DoorTarget.JS)
            return


        val allHttpAccessibleFunctions = dao.getAllFunctions().toList()
            .filter { it.hasAnnotation(HttpAccessible::class) }

        val duplicateNameFunctions = allHttpAccessibleFunctions.filter { daoFun ->
            allHttpAccessibleFunctions.any { otherDaoFun ->
                otherDaoFun != daoFun && otherDaoFun.simpleName.asString() == daoFun.simpleName.asString()
            }
        }.map { it.simpleName.asString() }.toSet()
        if(duplicateNameFunctions.isNotEmpty()) {
            logger.error("RepoHttpAccessible DAO functions must have unique names. They cannot be overriden " +
                    "by parameters. Duplicates are: ${duplicateNameFunctions.joinToString()}", dao)
        }

        allHttpAccessibleFunctions.forEach { daoFun ->
            val httpAccessibleAnnotation = daoFun.getAnnotation(HttpAccessible::class)
            if(daoFun.hasAnnotation(RawQuery::class)) {
                logger.error("HttpAccessible function cannot use RawQuery! This would be a major security risk",
                    daoFun)
            }

            if(daoFun.parameters.count { it.hasAnnotation(RepoHttpBodyParam::class) } > 1) {
                logger.error("HttpAccessible function: only one parameter can be annotated @RepoHttpBodyParam",
                    daoFun)
            }

            if(daoFun.parameters.any { it.hasAnnotation(RepoHttpBodyParam::class) } &&
                httpAccessibleAnnotation?.httpMethod == HttpAccessible.HttpMethod.GET
            ) {
                logger.error("HttAccessible function: if @RepoHttpBodyParam then http method MUST be AUTO or POST",
                    daoFun)
            }

            httpAccessibleAnnotation?.pullQueriesToReplicate?.toList()?.findDuplicates { item1, item2 ->
                item1.functionName == item2.functionName
            }?.takeIf { it.isNotEmpty() }?.also {
                logger.error("pullQueriesToReplicate: duplicate calls: ${it.joinToString { it.functionName }}",
                        daoFun)
            }


            val httpAccessibleKSAnnotation = daoFun.getKSAnnotationByType(HttpAccessible::class)

            httpAccessibleKSAnnotation?.getArgumentValueByNameAsAnnotationList("pullQueriesToReplicate")?.forEach { pullQueryToReplicateFn ->
                validateHttpServerFunctionCall("pullQueriesToReplicate", daoFun, dao,
                    pullQueryToReplicateFn, resolver)
            }
            httpAccessibleKSAnnotation?.getArgumentValueByNameAsAnnotationList("authQueries")?.forEach { authFun ->
                validateHttpServerFunctionCall("authQueries", daoFun, dao, authFun, resolver)
            }
        }
    }

    private fun validateDaos(
        resolver: Resolver,
    ) {
        val daos = resolver.getDaoSymbolsToProcess()
        daos.forEach { dao ->

            dao.getAllFunctions().filter { it.hasAnnotation(Query::class) }.forEach { queryFunDecl ->
                val queryAnnotation = queryFunDecl.getAnnotation(Query::class)!!
                val queryFun = queryFunDecl.asMemberOf(dao.asType(emptyList()))
            //dao.enclosedElementsWithAnnotation(Query::class.java).map { it as ExecutableElement }.forEach { queryFun ->

                //check that the query parameters on both versions match
                val sqliteQueryParams = queryAnnotation.value.getSqlQueryNamedParameters()
                val postgresQueryParams = (queryFunDecl.getAnnotation(PostgresQuery::class)?.value ?:
                    queryAnnotation.value.sqlToPostgresSql()).getSqlQueryNamedParameters()

                if(sqliteQueryParams != postgresQueryParams) {
                    logger.error("Query parameters don't match: " +
                                "Query parameters must feature the same names in the same order on both platforms " +
                                "SQLite params=${sqliteQueryParams.joinToString()} " +
                                "Postgres params=${postgresQueryParams.joinToString()}", queryFunDecl)
                }


                connectionMap.filter {
                    it.value != null && !(it.key == DoorDbType.POSTGRES && queryFunDecl.hasAnnotation(SqliteOnly::class))
                }.forEach { connectionEntry ->
                    val query = queryAnnotation.value.let {
                        if(connectionEntry.key == DoorDbType.POSTGRES) {
                            queryFunDecl.getAnnotation(PostgresQuery::class)?.value ?: it.sqlToPostgresSql()
                        }else {
                            it
                        }
                    }

                    val queryNamedParams = query.getSqlQueryNamedParameters()
                    val queryWithQuestionPlaceholders = query.replaceQueryNamedParamsWithQuestionMarks(queryNamedParams)
                    val connectionEntryCon = connectionEntry.value!! //This is OK because of the filter above.

                    try {
                        val preparedStatementConfig = PreparedStatementConfig(queryWithQuestionPlaceholders,
                            hasListParams = queryFunDecl.hasAnyListOrArrayParams(resolver))
                        connectionEntryCon.prepareStatement(preparedStatementConfig, connectionEntry.key).use { statement ->
                            queryNamedParams.forEachIndexed { index, paramName ->
                                val paramIndex = queryFunDecl.parameters.indexOfFirst { it.name?.asString() == paramName }
                                val paramType = queryFun.parameterTypes[paramIndex]
                                    ?: throw IllegalArgumentException("Could not find type for $paramName")
                                statement.setDefaultParamValue(resolver, index + 1, paramName,
                                    paramType, connectionEntry.key)
                            }

                            if(!query.isSQLAModifyingQuery()) {
                                statement.executeQuery()
                            }else {
                                statement.executeUpdate()
                            }
                        }
                    }catch(e: Exception) {
                        logger.error("Error running query for DAO function: $e", queryFunDecl)
                        if(e !is SQLException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            validateDaoHttpAccessibleFunctions(dao, resolver)
        }
    }


    override fun process(resolver: Resolver): List<KSAnnotated> {
        //Only do postgres validations etc when running on JVM
        if(environment.doorTarget(resolver) != DoorTarget.JVM) {
            pgConnection = null
            connectionMap = mapOf(DoorDbType.SQLITE to sqliteConnection, DoorDbType.POSTGRES to pgConnection)
        }

        createAllTables(resolver)
        validateReplicateEntities(resolver)
        validateTriggers(resolver, environment.doorTarget(resolver))
        validateDaos(resolver)

        return emptyList()
    }

    /**
     *
     */
    fun cleanup(resolver: Resolver) {
        val dbs = resolver.getDatabaseSymbolsToProcess()
        pgConnection?.createStatement()?.use { pgStmt ->
            dbs.flatMap { it.allDbEntities() }.toSet().forEach { entity ->
                pgStmt.executeUpdate("DROP TABLE IF EXISTS ${entity.entityTableName}")
            }
        }
    }

}
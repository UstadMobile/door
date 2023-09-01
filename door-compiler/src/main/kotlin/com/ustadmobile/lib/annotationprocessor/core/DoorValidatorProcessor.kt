package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Entity
import androidx.room.Query
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.metadata.toKmClass
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorDbType.Companion.productNameForDbType
import com.ustadmobile.door.PreparedStatementConfig
import com.ustadmobile.door.annotation.*
import com.ustadmobile.door.entities.ZombieAttachmentData
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
            /*
            val missingPkFields = entity.getAnnotation(Entity::class)?.primaryKeys?.filter { pkFieldName ->
                !allEntityProps.any { it.simpleName.asString() == pkFieldName }
            } ?: emptyList()

            if(missingPkFields.isNotEmpty()) {
                logger.error("Entity annotation primary key " +
                        "fields not found: ${missingPkFields.joinToString()}", entity)
            }*/

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

                val entityVersionIdField = entity.getAllProperties().filter { it.hasAnnotation(ReplicationVersionId::class) }.toList()
                if(entityVersionIdField.size != 1) {
                    logger.error(
                        "@ReplicateEntity ${entity.qualifiedName?.asString()} must have exactly one field annotated @ReplicationVersionId",
                        entity
                    )
                }
            }catch(e: IllegalArgumentException){
                logger.error("ReplicateEntity ${entity.qualifiedName?.asString()} must have a tracker entity specified",
                    entity)
            }
        }
    }

    private fun validateTriggers(resolver: Resolver) {
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
                        var sqlFormatted = this

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
                        var sqlToRun = trigger.conditionSql.substituteTriggerPrefixes(entry.key)
                        try {
                            if(entry.key == DoorDbType.POSTGRES)
                                sqlToRun = sqlToRun.sqlToPostgresSql()

                            entry.value.takeIf { trigger.conditionSql != "" }?.executeQuery(sqlToRun)
                        }catch(e: SQLException) {
                            logger.error("Trigger ${trigger.name} condition SQL using ${productNameForDbType(entry.key)} error on: ${e.message}",
                                entity)
                        }
                    }


                    if(trigger.sqlStatements.isEmpty())
                        logger.error("Trigger ${trigger.name} has no SQL statements", entity)


                    var dbType = 0
                    var sqlToRun: String
                    trigger.sqlStatements.forEach { sql ->
                        try {
                            stmtsMap.forEach { stmtEntry ->
                                dbType = stmtEntry.key
                                sqlToRun = sql.substituteTriggerPrefixes(stmtEntry.key)
                                if(dbType == DoorDbType.POSTGRES)
                                    sqlToRun = sqlToRun.sqlToPostgresSql()

                                stmtEntry.value?.executeUpdate(sqlToRun)
                            }
                        }catch(e: SQLException) {
                            logger.error("Trigger ${trigger.name} ${productNameForDbType(dbType)} exception running ${e.message} running '$sql'}",
                                entity)
                        }
                    }
                }
            }
    }

    private fun validateEntitiesWithAttachments(resolver: Resolver) {
        val entities = resolver.getSymbolsWithAnnotation("androidx.room.Entity")
            .filterIsInstance<KSClassDeclaration>()
        entities.filter { it.getAllProperties().any { it.hasAnnotation(AttachmentUri::class) } }.forEach { entity ->
            if(entity.getAllProperties().filter { it.hasAnnotation(AttachmentMd5::class) }.toList().size != 1)
                logger.error("Has AttachmentUri field, must have exactly one AttachmentMd5 field",
                    entity)

            if(entity.getAllProperties().filter { it.hasAnnotation(AttachmentSize::class) }.toList().size != 1) {
                logger.error("Has AttachmentUri field, must have exactly one AttachmentSize field", entity)
            }

            if(!entity.hasAnnotation(ReplicateEntity::class)) {
                logger.error("Has AttachmentUri field, MUST be annotated with @ReplicateEntity", entity)
            }
        }

        val dbs = resolver.getDatabaseSymbolsToProcess()
        dbs.filter { db ->
           db.allDbEntities().any { it.entityHasAttachments() } &&
                   !db.allDbEntities().any { it.qualifiedName?.asString() == ZombieAttachmentData::class.qualifiedName }
        }.forEach { db ->
            logger.error("Database has entities with attachments, must have ZombieAttachmentData entity", db)
        }
    }

    private fun validateDaos(resolver: Resolver) {
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
        validateTriggers(resolver)
        validateEntitiesWithAttachments(resolver)
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
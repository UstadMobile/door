package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Entity
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.lib.annotationprocessor.core.ext.*
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection
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

    val pgConnection: Connection?

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
    }

    private fun createAllTables(resolver: Resolver) {
        val dbs = resolver.getSymbolsWithAnnotation("androidx.room.Database")
            .filterIsInstance<KSClassDeclaration>()
        dbs.flatMap { it.allDbEntities() }.toSet().forEach { entity ->
            if(!entity.hasAnnotation(Entity::class)) {
                logger.error("Class used as entity on database does not have @Entity annotation",
                    entity)
            }

            val allEntityProps = entity.getAllProperties().toList()
            val missingPkFields = entity.getAnnotation(Entity::class)?.primaryKeys?.filter { pkFieldName ->
                !allEntityProps.any { it.simpleName.asString() == pkFieldName }
            } ?: emptyList()

            if(missingPkFields.isNotEmpty()) {
                logger.error("Entity annotation primary key " +
                        "fields not found: ${missingPkFields.joinToString()}", entity)
            }

            if(entity.entityPrimaryKeyProps.isEmpty()) {
                logger.error(
                    "Class ${entity.simpleName.asString()} used as entity does not have a field(s) annotated @PrimaryKey or primaryKeys set",
                    entity)
            }

            val sqliteStmt = sqliteConnection.createStatement()
            val pgStmt = pgConnection?.createStatement()
            val stmts = mapOf(DoorDbType.SQLITE to sqliteStmt, DoorDbType.POSTGRES to pgStmt)
            stmts.forEach {  stmtEntry ->
                val dbType = stmtEntry.key
                stmtEntry.value?.use { stmt ->
                    //val typeEntitySpec: TypeSpec = entity.asEntityTypeSpec()
                    val createTableSql = entity.toCreateTableSql(dbType, resolver)
                    try {
                        stmt.execute(createTableSql)
                    }catch(sqle: SQLException) {
                        logger.error("SQLException creating table for:" +
                                "${entity.simpleName.asString()} : ${sqle.message}. SQL was \"$createTableSql\"", entity)
                    }

//                    if(dbType == DoorDbType.SQLITE) {
//                        allKnownEntityNames.add(typeEntitySpec.name!!)
//                        allKnownEntityTypesMap[typeEntitySpec.name!!] = entity
//                    }
                }

            }
        }
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        createAllTables(resolver)
        return emptyList()
    }

    /**
     *
     */
    fun cleanup(resolver: Resolver) {
        val dbs = resolver.getSymbolsWithAnnotation("androidx.room.Database")
            .filterIsInstance<KSClassDeclaration>()
        pgConnection?.createStatement()?.use { pgStmt ->
            dbs.flatMap { it.allDbEntities() }.toSet().forEach { entity ->
                pgStmt.executeUpdate("DROP TABLE IF EXISTS ${entity.entityTableName}")
            }
        }
    }

}
package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JS_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JVM_DIRS
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_KTOR_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_MIGRATIONS_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_SOURCE_PATH
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.metadata.toImmutableKmClass
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.DoorDbType.Companion.PRODUCT_INT_TO_NAME_MAP
import com.ustadmobile.door.annotation.*
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import javax.tools.Diagnostic

/**
 * This is the annotation processor as far as the compiler sees it. It will delegate the actual
 * work to classes that are children of AbstractDbProcessor. It will create a shared SQLite database
 * where all tables have been created (so that child processors can check queries etc).
 */
@SupportedAnnotationTypes("androidx.room.Database", "androidx.room.Dao")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedOptions(value = [OPTION_JVM_DIRS, OPTION_ANDROID_OUTPUT, OPTION_KTOR_OUTPUT,
    OPTION_JS_OUTPUT, OPTION_SOURCE_PATH, OPTION_MIGRATIONS_OUTPUT, "kapt.kotlin.generated"])
class AnnotationProcessorWrapper: AbstractProcessor() {

    val processors = listOf(DbProcessorJdbcKotlin(), DbProcessorKtorServer(),
            DbProcessorRepository(), DbProcessorAndroid(), DbProcessorReplicateWrapper())

    lateinit var messager: MessagerWrapper

    private var dbConnection: Connection? = null

    /**
     * When we generate the code for a Query annotation function that performs an update or delete,
     * we use this so that we can match the case of the table name.
     */
    protected var allKnownEntityNames = mutableListOf<String>()

    /**
     * Provides a map that can be used to find the TypeElement for a given table name.
     */
    protected var allKnownEntityTypesMap = mutableMapOf<String, TypeElement>()

    private val Int.dbProductName: String
        get() = PRODUCT_INT_TO_NAME_MAP[this] ?: throw IllegalArgumentException("Not a valid db constant")


    override fun init(p0: ProcessingEnvironment) {
        messager = MessagerWrapper(p0.messager)
        processors.forEach { it.init(p0) }
        processingEnv = p0
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if(annotations.isEmpty())
            return true

        setupDb(roundEnv)

        //Check if any errors were emitted when setting up the database. If so, it is not valid, and we should not
        // proceed
        if(messager.hasError) {
            return true
        }

        val dbConnection = dbConnection ?: throw IllegalStateException("Could not connect to db")
        processors.forEach {
            it.processDb(annotations, roundEnv, dbConnection, allKnownEntityNames, allKnownEntityTypesMap)
        }

        dbConnection.close()

        return true
    }

    /**
     * This creates an instance of the database in SQLite that is used by the annotation
     * processors to check queries etc.
     */
    internal fun setupDb(roundEnv: RoundEnvironment) {
        allKnownEntityNames.clear()
        allKnownEntityTypesMap.clear()

        val dbs = roundEnv.getElementsAnnotatedWith(Database::class.java).map { it as TypeElement }
        val dataSource = SQLiteDataSource()
        val dbTmpFile = File.createTempFile("dbprocessorkt", ".db")
        println("Db tmp file: ${dbTmpFile.absolutePath}")
        dataSource.url = "jdbc:sqlite:${dbTmpFile.absolutePath}"
        val messager = processingEnv.messager
        val connectionVal = dataSource.connection!!
        dbConnection = connectionVal

        val postgresDbUrl: String? = processingEnv.options[OPTION_POSTGRES_TESTDB]

        val pgConnection: Connection? = postgresDbUrl?.takeIf { it.isNotBlank() }?.let { dbUrl ->
            try {
                Class.forName("org.postgresql.Driver")
                DriverManager.getConnection(dbUrl, processingEnv.options[OPTION_POSTGRES_TESTUSER],
                    processingEnv.options[OPTION_POSTGRES_TESTPASS])
            } catch(e: SQLException) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Postgres check database supplied, but could not connect: ${e.message}")
                null
            }
        }

        val allReplicateEntities = dbs.flatMap { it.allDbEntities(processingEnv) }.toSet()
            .filter { it.hasAnnotation(ReplicateEntity::class.java) }

        val replicableEntitiesGroupedById = allReplicateEntities
            .groupBy { it.getAnnotation(ReplicateEntity::class.java).tableId }

        replicableEntitiesGroupedById.filter { it.value.size > 1 }.forEach { duplicates ->
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Duplicate replicate tableId ${duplicates.key} : ${duplicates.value.joinToString { it.simpleName }} ",
                duplicates.value.first())
        }

        //Check entities with the ReplicateEntity annotation have all the required fields
        allReplicateEntities.forEach { entity ->
            val entityKmClass = entity.getAnnotation(Metadata::class.java).toImmutableKmClass()

            try {
                val entityRepTrkr = entity.getReplicationTracker(processingEnv)

                val entityVersionIdField = entity.enclosedElementsWithAnnotation(ReplicationVersionId::class.java)
                if(entityVersionIdField.size != 1)
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "@ReplicateEntity must have exactly one field annotated @ReplicationVersionId",
                        entity)


                if(!entityRepTrkr.hasAnnotation(Entity::class.java))
                    messager.printMessage(Diagnostic.Kind.ERROR, "Replication tracker entity does not have @Entity annotation",
                        entityRepTrkr)

                val trkrForeignKey = entityRepTrkr.enclosedElementsWithAnnotation(ReplicationEntityForeignKey::class.java)
                entityKmClass.properties.first().returnType


                if(trkrForeignKey.size != 1 ||
                    entity.kmPropertiesWithAnnotation(PrimaryKey::class.java).first().returnType !=
                    entityRepTrkr.kmPropertiesWithAnnotation(ReplicationEntityForeignKey::class.java).first().returnType)

                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "Replication tracker must have exactly one field annotated @ReplicationEntityForeignKey of the same type as the entity's primary key")



                val trkrVersionId = entityRepTrkr.enclosedElementsWithAnnotation(ReplicationVersionId::class.java)
                if(trkrVersionId.size != 1 ||
                        trkrVersionId.first().asType().asTypeName() != entityVersionIdField.firstOrNull()?.asType()?.asTypeName()) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "Replication tracker must have exactly one field annotated @ReplicationVersionId " +
                            "and it must be the same type as the field annotated @ReplicationVersionId on the main entity",
                        entityRepTrkr)
                }

                val trkrNodeId = entityRepTrkr.enclosedElementsWithAnnotation(ReplicationDestinationNodeId::class.java)
                if(trkrNodeId.size != 1 || trkrNodeId.first().asType().asTypeName() != LONG) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Replication tracker must have exactly one field" +
                            "annotated @ReplicationDestinationNodeId and it must be of type long", entityRepTrkr)
                }

                val trkrProcessed = entityRepTrkr.enclosedElementsWithAnnotation(ReplicationTrackerProcessed::class.java)
                if(trkrProcessed.size != 1 || trkrProcessed.first().asType().asTypeName() != BOOLEAN) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Replication Tracker must have exactly one field " +
                            "annotated @ReplicationTrackerProcessed and it must be of type boolean", entityRepTrkr)
                }

                //check for duplicate fields between tracker and entity
                val entityFieldNames = entity.entityFields
                val trkrFieldNames = entityRepTrkr.entityFields.map { it.simpleName.toString() }

                val duplicateFieldElements = entityFieldNames.filter { it.simpleName.toString() in trkrFieldNames }
                duplicateFieldElements.forEach { fieldEl ->
                    messager.printMessage(Diagnostic.Kind.ERROR, "Same field names used in both entity and tracker. " +
                            "This is not allowed as it will lead to a conflict in SQL query row names",
                        fieldEl)
                }

            }catch(e: IllegalArgumentException){
                messager.printMessage(Diagnostic.Kind.ERROR, "ReplicateEntity must have a tracker entity specified",
                    entity)
            }

        }


        dbs.flatMap { it.allDbEntities(processingEnv) }.toSet().forEach { entity ->
            if(entity.getAnnotation(Entity::class.java) == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Class ${entity.simpleName} used as entity on database does not have @Entity annotation",
                    entity)
            }

            if(entity.entityPrimaryKeys.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    "Class ${entity.simpleName} used as entity does not have a field(s) annotated @PrimaryKey or primaryKeys set",
                    entity)
            }

            val sqliteStmt = dbConnection!!.createStatement()
            val pgStmt = pgConnection?.createStatement()
            val stmts = mapOf(DoorDbType.SQLITE to sqliteStmt, DoorDbType.POSTGRES to pgStmt)
            stmts.forEach {  stmtEntry ->
                val dbType = stmtEntry.key
                stmtEntry.value?.use { stmt ->
                    val typeEntitySpec: TypeSpec = entity.asEntityTypeSpec()
                    val createTableSql = typeEntitySpec.toCreateTableSql(dbType, entity.packageName, processingEnv)
                    try {
                        stmt.execute(createTableSql)
                    }catch(sqle: SQLException) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "SQLException creating table for:" +
                                "${entity.simpleName} : ${sqle.message}. SQL was \"$createTableSql\"")
                    }

                    if(dbType == DoorDbType.SQLITE) {
                        allKnownEntityNames.add(typeEntitySpec.name!!)
                        allKnownEntityTypesMap[typeEntitySpec.name!!] = entity
                    }
                }
            }
        }

        //After all tables have been created, check that the SQL in all triggers is actually valid
        dbs.flatMap { it.allDbEntities(processingEnv) }.toSet()
        .filter { it.hasAnnotation(Triggers::class.java) }
        .forEach { entity ->
            entity.getAnnotationsByType(Triggers::class.java).firstOrNull()?.value?.forEach { trigger ->
                val stmt = connectionVal.createStatement()!!
                val pgStmt = pgConnection?.createStatement()
                val stmtsMap = mapOf(DoorDbType.SQLITE to stmt, DoorDbType.POSTGRES to pgStmt)
                val repTrkr = entity.getReplicationTracker(processingEnv)

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
                        entity.entityFields.forEach { field ->
                            sqlFormatted = sqlFormatted.replace("$prefix.${field.simpleName}",
                                field.asType().asTypeName().defaultSqlValue(dbProductType))
                        }
                        repTrkr.takeIf { trigger.on == Trigger.On.RECEIVEVIEW }?.entityFields?.forEach { field ->
                            sqlFormatted = sqlFormatted.replace("$prefix.${field.simpleName}",
                                field.asType().asTypeName().defaultSqlValue(dbProductType))
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
                        messager.printMessage(Diagnostic.Kind.ERROR,
                            "Trigger ${trigger.name} condition SQL using ${entry.key.dbProductName} error on: ${e.message}",
                            entity)
                    }
                }


                if(trigger.sqlStatements.isEmpty())
                    messager.printMessage(Diagnostic.Kind.ERROR, "Trigger ${trigger.name} has no SQL statements", entity)


                var dbType = 0
                trigger.sqlStatements.forEach { sql ->
                    var sqlToRun = sql.substituteTriggerPrefixes(dbType)
                    try {
                        stmtsMap.forEach { stmtEntry ->
                            dbType = stmtEntry.key
                            if(dbType == DoorDbType.POSTGRES)
                                sqlToRun = sqlToRun.sqlToPostgresSql()

                            stmtEntry.value?.executeUpdate(sqlToRun)
                        }
                    }catch(e: SQLException) {
                        messager.printMessage(Diagnostic.Kind.ERROR,
                            "Trigger ${trigger.name} ${dbType.dbProductName} exception running ${e.message} running '$sql'}",
                            entity)
                    }
                }
            }
        }



        //cleanup postgres so it can be used next time:
        pgConnection?.createStatement()?.use { pgStmt ->
            dbs.flatMap { it.allDbEntities(processingEnv) }.toSet().forEach { entity ->
                pgStmt.executeUpdate("DROP TABLE IF EXISTS ${entity.entityTableName}")
            }
        }
    }

    companion object {

        const val OPTION_SOURCE_PATH = "doordb_source_path"

        const val OPTION_JVM_DIRS = "doordb_jvm_out"

        const val OPTION_ANDROID_OUTPUT = "doordb_android_out"

        const val OPTION_KTOR_OUTPUT = "doordb_ktor_out"

        const val OPTION_NANOHTTPD_OUTPUT = "doordb_nanohttpd_out"

        const val OPTION_JS_OUTPUT = "doordb_js_out"

        const val OPTION_MIGRATIONS_OUTPUT = "doordb_migrations_out"

        const val OPTION_POSTGRES_TESTDB = "doordb_postgres_url"

        const val OPTION_POSTGRES_TESTUSER = "doordb_postgres_user"

        const val OPTION_POSTGRES_TESTPASS = "doordb_postgres_password"

    }

}
package com.ustadmobile.lib.annotationprocessor.core

import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JS_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_JVM_DIRS
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_KTOR_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_MIGRATIONS_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_SOURCE_PATH
import com.ustadmobile.lib.annotationprocessor.core.migrations.DbProcessorSyncPushMigration
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.kotlinpoet.TypeSpec
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.door.annotation.SyncableEntity
import org.sqlite.SQLiteDataSource
import java.io.File
import java.sql.Connection
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
            DbProcessorRepository(), DbProcessorSync(), DbProcessorAndroid(), DbProcessorJs(),
            DbProcessorSyncableReadOnlyWrapper(), DbProcessorSyncPushMigration())

    lateinit var messager: Messager

    protected var dbConnection: Connection? = null

    /**
     * When we generate the code for a Query annotation function that performs an update or delete,
     * we use this so that we can match the case of the table name.
     */
    protected var allKnownEntityNames = mutableListOf<String>()

    /**
     * Provides a map that can be used to find the TypeElement for a given table name.
     */
    protected var allKnownEntityTypesMap = mutableMapOf<String, TypeElement>()


    override fun init(p0: ProcessingEnvironment) {
        messager = p0.messager
        processors.forEach { it.init(p0) }
        processingEnv = p0
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if(annotations.isNotEmpty()) {
            setupDb(processingEnv, roundEnv)

            val dbConnection = dbConnection ?: throw IllegalStateException("Could not connect to db")
            processors.forEach {
                it.processDb(annotations, roundEnv, dbConnection, allKnownEntityNames,
                        allKnownEntityTypesMap)
            }
            dbConnection.close()
        }

        return true
    }

    /**
     * This creates an instance of the database in SQLite that is used by the annotation
     * processors to check queries etc.
     */
    internal fun setupDb(processingenv: ProcessingEnvironment, roundEnv: RoundEnvironment) {
        allKnownEntityNames.clear()
        allKnownEntityTypesMap.clear()

        val dbs = roundEnv.getElementsAnnotatedWith(Database::class.java)
        val dataSource = SQLiteDataSource()
        val dbTmpFile = File.createTempFile("dbprocessorkt", ".db")
        println("Db tmp file: ${dbTmpFile.absolutePath}")
        dataSource.url = "jdbc:sqlite:${dbTmpFile.absolutePath}"
        val messager = processingEnv.messager
        dbConnection = dataSource.connection
        dbs.flatMap { entityTypesOnDb(it as TypeElement, processingEnv) }.forEach {entity ->
            if(entity.getAnnotation(Entity::class.java) == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Class ${entity.simpleName} used as entity on database does not have @Entity annotation",
                        entity)
            }

            if(!entity.enclosedElements.any { it.getAnnotation(PrimaryKey::class.java) != null }) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Class ${entity.simpleName} used as entity does not have a field annotated @PrimaryKey",
                        entity)

            }

            val stmt = dbConnection!!.createStatement()
            stmt.use {
                val typeEntitySpec: TypeSpec = entity.asEntityTypeSpec()
                val createTableSql = typeEntitySpec.toCreateTableSql(DoorDbType.SQLITE)
                try {
                    stmt.execute(createTableSql)
                }catch(sqle: SQLException) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "SQLException creating table for:" +
                            "${entity.simpleName} : ${sqle.message}. SQL was \"$createTableSql\"")
                }

                allKnownEntityNames.add(typeEntitySpec.name!!)
                allKnownEntityTypesMap[typeEntitySpec.name!!] = entity

                if(entity.getAnnotation(SyncableEntity::class.java) != null) {
                    val trackerEntitySpec = generateTrackerEntity(entity, processingEnv)
                    stmt.execute(trackerEntitySpec.toCreateTableSql(DoorDbType.SQLITE))
                    allKnownEntityNames.add(trackerEntitySpec.name!!)
                }
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

    }

}
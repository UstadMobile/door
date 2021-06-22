package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Database
import com.squareup.kotlinpoet.*
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_SOURCE_PATH
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorSync.Companion.SUFFIX_SYNCDAO_ABSTRACT
import io.ktor.util.extension
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import com.ustadmobile.door.DoorDatabaseCallback
import com.ustadmobile.door.DoorDbType
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_LOCAL
import com.ustadmobile.lib.annotationprocessor.core.DbProcessorKtorServer.Companion.SUFFIX_KTOR_HELPER_MASTER
import java.util.*
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType

class DbProcessorAndroid: AbstractDbProcessor() {

    private fun findSourceFile(packageName: String, className: String): File? {
        val srcPaths = processingEnv.options[OPTION_SOURCE_PATH]
        if(srcPaths == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "DoorDbProcessorAndroid: must set ${OPTION_SOURCE_PATH}")
            return null
        }

        val packageRelPath = "${packageName.replace(".", File.separator)}${File.separator}${className}.kt"
        return srcPaths.split(File.pathSeparator).map { File(it, packageRelPath)}
                .firstOrNull { it.exists() }
    }

    private fun adjustDbFile(dbTypeEl: TypeElement, inFile: File, outDirs: List<String>) {
        val relativePath = pkgNameOfElement(dbTypeEl, processingEnv).replace(".", File.separator) +
                "/${dbTypeEl.simpleName}.kt"

        val versionClassSimpleName = "${dbTypeEl.simpleName}_DoorVersion"
        val dbVersion = dbTypeEl.getAnnotation(Database::class.java).version
        val fileSpec = FileSpec.builder(dbTypeEl.asClassName().packageName, versionClassSimpleName)
                .addType(TypeSpec.classBuilder(versionClassSimpleName)
                        .superclass(ClassName("com.ustadmobile.door", "DoorDatabaseVersion"))
                        .addProperty(PropertySpec.builder("dbVersion", INT)
                                .addModifiers(KModifier.OVERRIDE)
                                .initializer(dbVersion.toString())
                                .build())
                        .build())
                .build()

        outDirs.forEach {
            fileSpec.writeTo(File(it))
        }


        val outFiles = outDirs.map { File(it, relativePath) }
        outFiles.filter { !it.parentFile.exists() }.forEach {
            if(!it.parentFile.mkdirs()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Could not create android output " +
                        "dir for ${dbTypeEl.qualifiedName} : ${it.parentFile.absolutePath}")
                throw IOException("Could not create android output dir ${it.parentFile.absolutePath}")
            }
        }

        val outStreams = outFiles.map { BufferedWriter(FileWriter(it)) }

        BufferedReader(FileReader(inFile)).use {

            for(line in it.lines()) {
                var lineOut = ""
                if (line.contains("//#DOORDB_TRACKER_ENTITIES")) {
                    lineOut += "//Generated section: add tracker entities to room database\n"
                    lineOut += syncableEntityTypesOnDb(dbTypeEl,processingEnv)
                            .joinToString(prefix = ",") { "${it.simpleName}_trk::class" }
                    lineOut += "\n//End of generated section: add tracker entities to room database\n"
                } else if(line.contains("//#DOORDB_SYNCDAO")) {
                    lineOut += "//Generated section: add SyncDao getter, boundary callback getters, and http (ktor) helper DAO getters\n"
                    lineOut += "abstract fun _syncDao(): ${dbTypeEl.simpleName}${SUFFIX_SYNCDAO_ABSTRACT}\n\n"
                    lineOut += "abstract fun _syncHelperEntitiesDao(): com.ustadmobile.door.daos.SyncHelperEntitiesDao\n\n"

                    //add boundary callbacks and http sync (KTOR) helper DAOs
                    methodsToImplement(dbTypeEl, dbTypeEl.asType() as DeclaredType, processingEnv).forEach {
                        val execEl = it as ExecutableElement
                        val returnTypeEl = processingEnv.typeUtils.asElement(execEl.returnType)

                        if(returnTypeEl is TypeElement && returnTypeEl.isDaoWithRepository) {
                            listOf(SUFFIX_KTOR_HELPER_MASTER, SUFFIX_KTOR_HELPER_LOCAL).forEach {suffix ->
                                lineOut += "abstract fun _${returnTypeEl.simpleName}$suffix() : ${returnTypeEl.qualifiedName}$suffix\n\n"
                            }
                        }
                    }

                    lineOut += "//End of generated section: add SyncDao getter and boundary callback getters\n"
                }else if(line.contains("@JsName") || line.contains("kotlin.js.JsName"))
                    lineOut = ""
                else {
                    lineOut = line
                }

                outStreams.forEach {
                    it.write(lineOut)
                    it.newLine()
                }
            }
        }

        outStreams.forEach {
            it.flush()
            it.close()
        }

    }

    override fun process(elements: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        //find all databases on the source path
        val outDirsArg = processingEnv.options[OPTION_ANDROID_OUTPUT]
        if(outDirsArg == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "DoorDb Android output not specified: please provide $OPTION_ANDROID_OUTPUT " +
                            "annotation processor argument")
            return true
        }

        val srcPathsArg = processingEnv.options[OPTION_SOURCE_PATH]
        if(srcPathsArg == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "DoorDbProcessorAndroid: must set ${OPTION_SOURCE_PATH}")
            return false
        }

        val srcPaths = srcPathsArg.split(File.pathSeparator)
        val outDirs = outDirsArg.split(File.pathSeparator)
        val outPaths = outDirs.map { Paths.get(File(it).toURI()) }

        srcPaths.map { Paths.get(File(it).toURI()) }.forEach{srcPath ->
            Files.walk(srcPath).forEach {srcFilePath ->
                if(srcFilePath.extension == "kt"){
                    val srcRelativePath = srcPath.relativize(srcFilePath)
                    val outFilePaths = outPaths.map { it.resolve(srcRelativePath)}
                    if(outFilePaths.map {it.toFile() }
                                    .any { !it.exists() || it.lastModified() < srcFilePath.toFile().lastModified()}) {
                        val lines = srcFilePath.toFile().readLines()
                        if(!lines.any { it.contains("@Database" )} ) {
                            outFilePaths.forEach {
                                val parentDirFile = it.parent.toFile()
                                if(!parentDirFile.isDirectory){
                                    parentDirFile.mkdirs()
                                }

                                var srcFileIn = null as BufferedReader?
                                var fileOut = null as BufferedWriter?

                                try {
                                    srcFileIn = BufferedReader(FileReader(srcFilePath.toFile()))
                                    fileOut = BufferedWriter(FileWriter(it.toFile()))


                                    for(line in srcFileIn.lines()) {
                                        if(!line.contains("@JsName") && !line.contains("kotlin.js.JsName"))
                                            fileOut.write(line)

                                        fileOut.newLine()
                                    }
                                }catch(e: IOException) {
                                    messager.printMessage(Diagnostic.Kind.ERROR, "IOException " +
                                            "copying db source file$srcFilePath to $it : $e")
                                }finally {
                                    srcFileIn?.close()
                                    fileOut?.flush()
                                    fileOut?.close()
                                }
                            }
                        }
                    }
                }

            }
        }

        val dbElements = roundEnv.getElementsAnnotatedWith(Database::class.java).map {it as TypeElement}
        dbElements.forEach {dbEl ->
            val sourceFile = findSourceFile(pkgNameOfElement(dbEl, processingEnv), dbEl.simpleName.toString())

            if(sourceFile != null) {
                adjustDbFile(dbEl, sourceFile, outDirs)
            }

            if(isSyncableDb(dbEl, processingEnv)) {
                writeFileSpecToOutputDirs(generateSyncSetupCallback(dbEl), OPTION_ANDROID_OUTPUT, false)
            }
        }


        return true
    }

    fun generateSyncSetupCallback(dbTypeEl: TypeElement) : FileSpec {
        val dbTypeclassName = dbTypeEl.asClassName()
        val callbackFileSpec = FileSpec.builder(dbTypeclassName.packageName,
                "${dbTypeclassName.simpleName}$SUFFIX_SYNCCALLBACK")
        val callbackTypeSpec = TypeSpec.classBuilder("${dbTypeclassName.simpleName}$SUFFIX_SYNCCALLBACK")
                .addFunction(FunSpec.builder("onOpen")
                        .addParameter("db", ClassName("com.ustadmobile.door", "DoorSqlDatabase"))
                        .addModifiers(KModifier.OVERRIDE)
                        .build())
                .addProperty(PropertySpec.builder("master", BOOLEAN).initializer("false").build())
                .addSuperinterface(DoorDatabaseCallback::class)
                .addSuperinterface(ClassName("com.ustadmobile.door", "DoorSyncCallback"))

        val onCreateFunSpec = FunSpec.builder("onCreate")
                .addParameter("db", ClassName("com.ustadmobile.door", "DoorSqlDatabase"))
                .addModifiers(KModifier.OVERRIDE)
        val codeBlock = CodeBlock.builder()

        syncableEntityTypesOnDb(dbTypeEl, processingEnv).forEach {
            codeBlock.add(generateSyncTriggersCodeBlock(it.asClassName(), "db.execSQL",
                    DoorDbType.SQLITE))
        }
        codeBlock.add("initSyncablePrimaryKeys(db)\n")

        dbTypeEl.allEntitiesWithAttachments(processingEnv).forEach {
            codeBlock.addGenerateAttachmentTriggerSqlite(it, "db.execSQL")
        }


        callbackTypeSpec.addFunction(FunSpec.builder("initSyncablePrimaryKeys")
                .addParameter("db",
                        ClassName("androidx.sqlite.db","SupportSQLiteDatabase"))
                .addModifiers(KModifier.OVERRIDE)
                .addCode(CodeBlock.builder().addInsertTableSyncStatuses(dbTypeEl,
                        "db.execSQL", processingEnv).build())
                .build())

        onCreateFunSpec.addCode(codeBlock.build())
        callbackTypeSpec.addFunction(onCreateFunSpec.build())
        callbackFileSpec.addType(callbackTypeSpec.build())

        return callbackFileSpec.build()
    }

    companion object{
        const val SUFFIX_SYNCCALLBACK = "_SyncCallback"
    }

}
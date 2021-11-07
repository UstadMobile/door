package com.ustadmobile.lib.annotationprocessor.core

import androidx.room.Database
import com.squareup.kotlinpoet.*
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_ANDROID_OUTPUT
import com.ustadmobile.lib.annotationprocessor.core.AnnotationProcessorWrapper.Companion.OPTION_SOURCE_PATH
import io.ktor.util.extension
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import com.ustadmobile.door.DoorDatabaseCallback

class DbProcessorAndroid: AbstractDbProcessor() {

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

        return true
    }

}
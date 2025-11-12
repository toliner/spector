package dev.toliner.spector.indexer

import dev.toliner.spector.scanner.ClassScanner
import dev.toliner.spector.scanner.KotlinMetadataEnricher
import dev.toliner.spector.storage.TypeIndexer
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Indexes Java standard library classes from jrt:/ (Java Runtime Image).
 *
 * Java 9+ uses a modular runtime image instead of rt.jar. The jrt:/ filesystem
 * provides access to modules and their classes.
 */
class JavaStdLibIndexer(
    private val typeIndexer: TypeIndexer
) {
    private val logger = LoggerFactory.getLogger(JavaStdLibIndexer::class.java)
    private val classScanner = ClassScanner()
    private val kotlinEnricher = KotlinMetadataEnricher()

    /**
     * Index all classes from the Java standard library.
     *
     * @param parallel Whether to use parallel processing for scanning
     */
    fun indexJavaStdLib(parallel: Boolean = true) {
        logger.info("Starting indexing of Java standard library from jrt:/")

        val fs = FileSystems.getFileSystem(URI.create("jrt:/"))
        val modulesPath = fs.getPath("/modules")

        val classFiles = mutableListOf<ClassFileData>()

        // Walk through all modules and collect class files
        Files.walk(modulesPath).use { paths ->
            paths
                .filter { it.extension == "class" }
                .filter { it.name != "module-info.class" } // Skip module descriptors
                .forEach { classPath ->
                    try {
                        // Read the class file bytes immediately
                        val classBytes = Files.readAllBytes(classPath)
                        val pathString = classPath.toString()
                        classFiles.add(ClassFileData(pathString, classBytes))
                    } catch (e: Exception) {
                        logger.warn("Failed to read class file: $classPath", e)
                    }
                }
        }

        logger.info("Found ${classFiles.size} class files in Java standard library")

        if (parallel) {
            scanAndIndexParallel(classFiles)
        } else {
            scanAndIndexSequential(classFiles)
        }

        logger.info("Java standard library indexing complete")
    }

    private fun scanAndIndexSequential(classFiles: List<ClassFileData>) {
        var processed = 0
        for (classFile in classFiles) {
            try {
                scanAndIndexClass(classFile)
                processed++
                if (processed % 100 == 0) {
                    logger.info("Processed $processed/${classFiles.size} classes from Java stdlib")
                }
            } catch (e: Exception) {
                logger.error("Failed to scan class: ${classFile.path}", e)
            }
        }
    }

    private fun scanAndIndexParallel(classFiles: List<ClassFileData>) {
        val threadCount = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(threadCount)

        val processed = java.util.concurrent.atomic.AtomicInteger(0)
        val total = classFiles.size

        for (classFile in classFiles) {
            executor.submit {
                try {
                    scanAndIndexClass(classFile)
                    val count = processed.incrementAndGet()
                    if (count % 100 == 0) {
                        logger.info("Processed $count/$total classes from Java stdlib")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to scan class: ${classFile.path}", e)
                }
            }
        }

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.HOURS)
    }

    private fun scanAndIndexClass(classFile: ClassFileData) {
        // Scan with ASM
        val classInfo = classScanner.scanClass(classFile.bytes) ?: return

        // Enrich with Kotlin metadata if available (though Java stdlib won't have it)
        val enrichedClassInfo = kotlinEnricher.enrichClassInfo(classFile.bytes, classInfo)

        // Index into database
        typeIndexer.indexClass(enrichedClassInfo)
    }

    /**
     * Data class to hold class file data from jrt:/
     */
    private data class ClassFileData(
        val path: String,
        val bytes: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ClassFileData

            if (path != other.path) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = path.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }
}

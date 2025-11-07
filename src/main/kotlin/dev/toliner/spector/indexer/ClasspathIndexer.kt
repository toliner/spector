package dev.toliner.spector.indexer

import dev.toliner.spector.scanner.ClassScanner
import dev.toliner.spector.scanner.KotlinMetadataEnricher
import dev.toliner.spector.storage.TypeIndexer
import java.io.File
import java.util.jar.JarFile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Orchestrates scanning of entire classpaths and indexing into the database.
 */
class ClasspathIndexer(
    private val typeIndexer: TypeIndexer
) {
    private val logger = LoggerFactory.getLogger(ClasspathIndexer::class.java)
    private val classScanner = ClassScanner()
    private val kotlinEnricher = KotlinMetadataEnricher()

    fun indexClasspath(classpathEntries: List<File>, parallel: Boolean = true) {
        logger.info("Starting indexing of ${classpathEntries.size} classpath entries")

        val classFiles = mutableListOf<ClassFileSource>()

        // Collect all class files from JARs and directories
        for (entry in classpathEntries) {
            when {
                !entry.exists() -> {
                    logger.warn("Classpath entry does not exist: $entry")
                }
                entry.isDirectory -> {
                    collectClassFilesFromDirectory(entry, classFiles)
                }
                entry.isFile && entry.extension == "jar" -> {
                    collectClassFilesFromJar(entry, classFiles)
                }
                else -> {
                    logger.warn("Unsupported classpath entry: $entry")
                }
            }
        }

        logger.info("Found ${classFiles.size} class files to scan")

        // Scan and index
        if (parallel) {
            scanAndIndexParallel(classFiles)
        } else {
            scanAndIndexSequential(classFiles)
        }

        logger.info("Indexing complete")
    }

    private fun collectClassFilesFromDirectory(dir: File, output: MutableList<ClassFileSource>) {
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .forEach { file ->
                output.add(ClassFileSource.FileSystem(file))
            }
    }

    private fun collectClassFilesFromJar(jarFile: File, output: MutableList<ClassFileSource>) {
        try {
            JarFile(jarFile).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .forEach { entry ->
                        output.add(ClassFileSource.JarEntry(jarFile, entry.name))
                    }
            }
        } catch (e: Exception) {
            logger.error("Failed to read JAR file: $jarFile", e)
        }
    }

    private fun scanAndIndexSequential(classFiles: List<ClassFileSource>) {
        var processed = 0
        for (source in classFiles) {
            try {
                scanAndIndexClass(source)
                processed++
                if (processed % 100 == 0) {
                    logger.info("Processed $processed/${classFiles.size} classes")
                }
            } catch (e: Exception) {
                logger.error("Failed to scan class: $source", e)
            }
        }
    }

    private fun scanAndIndexParallel(classFiles: List<ClassFileSource>) {
        val threadCount = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(threadCount)

        val processed = java.util.concurrent.atomic.AtomicInteger(0)
        val total = classFiles.size

        for (source in classFiles) {
            executor.submit {
                try {
                    scanAndIndexClass(source)
                    val count = processed.incrementAndGet()
                    if (count % 100 == 0) {
                        logger.info("Processed $count/$total classes")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to scan class: $source", e)
                }
            }
        }

        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.HOURS)
    }

    private fun scanAndIndexClass(source: ClassFileSource) {
        val classBytes = source.readBytes()

        // Scan with ASM
        val classInfo = classScanner.scanClass(classBytes) ?: return

        // Enrich with Kotlin metadata if available
        val enrichedClassInfo = kotlinEnricher.enrichClassInfo(classBytes, classInfo)

        // Index into database
        typeIndexer.indexClass(enrichedClassInfo)

        // TODO: Extract and index members (fields, methods, properties)
        // For now we'll skip member indexing to keep the PoC simple
    }

    sealed class ClassFileSource {
        abstract fun readBytes(): ByteArray

        data class FileSystem(val file: File) : ClassFileSource() {
            override fun readBytes(): ByteArray = file.readBytes()
            override fun toString(): String = file.path
        }

        data class JarEntry(val jarFile: File, val entryName: String) : ClassFileSource() {
            override fun readBytes(): ByteArray {
                return JarFile(jarFile).use { jar ->
                    jar.getInputStream(jar.getEntry(entryName)).readBytes()
                }
            }
            override fun toString(): String = "$jarFile!$entryName"
        }
    }
}

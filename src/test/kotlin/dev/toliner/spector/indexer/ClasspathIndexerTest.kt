package dev.toliner.spector.indexer

import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files

class ClasspathIndexerTest : FunSpec({

    test("should index classes from directory") {
        val tempDb = Files.createTempFile("test-dir-index", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            // Get the directory containing our test classes
            val testClassesDir = File(System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .first { it.contains("test") && File(it).isDirectory })

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(listOf(testClassesDir), parallel = false)

            // Verify that some test classes were indexed
            val classes = indexer.findClassesByPackage(
                "dev.toliner.spector",
                recursive = true,
                publicOnly = false
            )

            classes.shouldNotBeEmpty()
            classes.map { it.fqcn } shouldContain "dev.toliner.spector.indexer.ClasspathIndexerTest"
        }
    }

    test("should index classes from JAR file") {
        val tempDb = Files.createTempFile("test-jar-index", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            // Find a JAR file from the classpath (e.g., kotlin-stdlib)
            val jarFiles = System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }
                .filter { it.exists() && it.isFile && it.extension == "jar" }

            if (jarFiles.isNotEmpty()) {
                val kotlinStdlib = jarFiles.firstOrNull { it.name.contains("kotlin-stdlib") }
                if (kotlinStdlib != null) {
                    val classpathIndexer = ClasspathIndexer(indexer)
                    classpathIndexer.indexClasspath(listOf(kotlinStdlib), parallel = false)

                    // Verify that Kotlin stdlib classes were indexed
                    val kotlinClasses = indexer.findClassesByPackage(
                        "kotlin",
                        recursive = true,
                        publicOnly = true
                    )

                    kotlinClasses.shouldNotBeEmpty()
                    kotlinClasses.size shouldBeGreaterThan 10
                }
            }
        }
    }

    test("should handle non-existent classpath entry gracefully") {
        val tempDb = Files.createTempFile("test-nonexistent", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val nonExistentFile = File("/path/to/nonexistent/file.jar")

            val classpathIndexer = ClasspathIndexer(indexer)
            // Should not throw, just log warning
            classpathIndexer.indexClasspath(listOf(nonExistentFile), parallel = false)

            // Verify no classes were indexed
            val classes = indexer.findClassesByPackage(
                "",
                recursive = true,
                publicOnly = false
            )
            // Could be empty or contain other classes, just verify no crash
        }
    }

    test("should index multiple classpath entries") {
        val tempDb = Files.createTempFile("test-multi", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val classpathEntries = System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }
                .filter { it.exists() }
                .take(3) // Just take first few to keep test fast

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(classpathEntries, parallel = false)

            // Just verify some classes were indexed
            val javaClasses = indexer.findClassesByPackage(
                "java",
                recursive = true,
                publicOnly = true
            )

            javaClasses.shouldNotBeEmpty()
        }
    }

    test("should index in parallel mode") {
        val tempDb = Files.createTempFile("test-parallel", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val classpathEntries = System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }
                .filter { it.exists() }
                .take(2)

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(classpathEntries, parallel = true)

            // Verify classes were indexed even in parallel mode
            val classes = indexer.findClassesByPackage(
                "java",
                recursive = true,
                publicOnly = true
            )

            classes.shouldNotBeEmpty()
        }
    }

    test("should enrich Kotlin classes with metadata during indexing") {
        val tempDb = Files.createTempFile("test-kotlin-enrich", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            // Index our own test classes which are Kotlin
            val testClassesDir = File(System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .first { it.contains("test") && File(it).isDirectory })

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(listOf(testClassesDir), parallel = false)

            // Find this test class itself
            val thisClass = indexer.findClassByFqcn("dev.toliner.spector.indexer.ClasspathIndexerTest")
            thisClass shouldNotBe null
            // This is a Kotlin class, should have Kotlin metadata
            thisClass!!.kotlin shouldNotBe null
        }
    }

    test("should detect different class kinds") {
        val tempDb = Files.createTempFile("test-kinds", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val runtimeClasspath = System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }
                .filter { it.exists() }

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(runtimeClasspath, parallel = false)

            // Check for interfaces
            val interfaces = indexer.findClassesByPackage(
                "java.util",
                recursive = false,
                kinds = setOf(ClassKind.INTERFACE),
                publicOnly = true
            )
            interfaces.shouldNotBeEmpty()
            interfaces.map { it.fqcn } shouldContain "java.util.List"

            // Check for classes
            val classes = indexer.findClassesByPackage(
                "java.util",
                recursive = false,
                kinds = setOf(ClassKind.CLASS),
                publicOnly = true
            )
            classes.shouldNotBeEmpty()
            classes.map { it.fqcn } shouldContain "java.util.ArrayList"
        }
    }

    test("ClassFileSource.FileSystem should read file bytes") {
        val testFile = Files.createTempFile("test", ".class").toFile()
        testFile.deleteOnExit()
        testFile.writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))

        val source = ClasspathIndexer.ClassFileSource.FileSystem(testFile)
        val bytes = source.readBytes()

        bytes.size shouldBe 4
        bytes[0] shouldBe 0xCA.toByte()
        bytes[1] shouldBe 0xFE.toByte()
    }

    test("ClassFileSource.FileSystem toString should return file path") {
        val testFile = File("/tmp/test.class")
        val source = ClasspathIndexer.ClassFileSource.FileSystem(testFile)

        source.toString() shouldBe testFile.path
    }

    test("ClassFileSource.JarEntry toString should include JAR and entry name") {
        val jarFile = File("/tmp/test.jar")
        val entryName = "com/example/Test.class"
        val source = ClasspathIndexer.ClassFileSource.JarEntry(jarFile, entryName)

        source.toString() shouldBe "$jarFile!$entryName"
    }
})

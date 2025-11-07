package dev.toliner.spector.indexer

import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File
import java.nio.file.Files
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class ClasspathIndexerTest : FunSpec({

    test("should collect class files from directory") {
        val tempDir = Files.createTempDirectory("test-classes").toFile()
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        try {
            // Create a test class file in the directory
            val classBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()
            val classFile = File(tempDir, "TestClass.class")
            classFile.writeBytes(classBytes)

            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                classpathIndexer.indexClasspath(listOf(tempDir), parallel = false)

                // Should have indexed at least one class
                val classes = indexer.findClassesByPackage("java.lang", publicOnly = false, recursive = false)
                classes.shouldNotBeEmpty()
            }
        } finally {
            tempDir.deleteRecursively()
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("should collect class files from JAR") {
        val tempJar = Files.createTempFile("test", ".jar").toFile()
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        try {
            // Create a JAR with a test class file
            JarOutputStream(tempJar.outputStream()).use { jar ->
                val classBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()
                jar.putNextEntry(ZipEntry("java/lang/String.class"))
                jar.write(classBytes)
                jar.closeEntry()
            }

            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                classpathIndexer.indexClasspath(listOf(tempJar), parallel = false)

                // Should have indexed the String class
                val stringClass = indexer.findClassByFqcn("java.lang.String")
                stringClass.shouldNotBeNull()
                stringClass.fqcn shouldBe "java.lang.String"
            }
        } finally {
            tempJar.delete()
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("should handle non-existent classpath entry") {
        val tempDb = Files.createTempFile("test-db", ".db").toString()
        val nonExistentFile = File("/nonexistent/path")

        try {
            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                // Should not throw exception
                classpathIndexer.indexClasspath(listOf(nonExistentFile), parallel = false)

                // Should have indexed nothing
                val classes = indexer.findClassesByPackage("", publicOnly = false)
                classes.shouldBeEmpty()
            }
        } finally {
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("should index multiple classes from directory") {
        val tempDir = Files.createTempDirectory("test-classes").toFile()
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        try {
            // Create multiple test class files
            val stringBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()
            val integerBytes = Integer::class.java.getResourceAsStream("/java/lang/Integer.class")!!.readBytes()

            File(tempDir, "String.class").writeBytes(stringBytes)
            File(tempDir, "Integer.class").writeBytes(integerBytes)

            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                classpathIndexer.indexClasspath(listOf(tempDir), parallel = false)

                val stringClass = indexer.findClassByFqcn("java.lang.String")
                stringClass.shouldNotBeNull()

                val integerClass = indexer.findClassByFqcn("java.lang.Integer")
                integerClass.shouldNotBeNull()
            }
        } finally {
            tempDir.deleteRecursively()
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("should work with parallel processing") {
        val tempDir = Files.createTempDirectory("test-classes").toFile()
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        try {
            // Create multiple test class files
            val stringBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()
            val integerBytes = Integer::class.java.getResourceAsStream("/java/lang/Integer.class")!!.readBytes()
            val listBytes = List::class.java.getResourceAsStream("/java/util/List.class")!!.readBytes()

            File(tempDir, "String.class").writeBytes(stringBytes)
            File(tempDir, "Integer.class").writeBytes(integerBytes)
            File(tempDir, "List.class").writeBytes(listBytes)

            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                classpathIndexer.indexClasspath(listOf(tempDir), parallel = true)

                // All classes should be indexed
                indexer.findClassByFqcn("java.lang.String").shouldNotBeNull()
                indexer.findClassByFqcn("java.lang.Integer").shouldNotBeNull()
                indexer.findClassByFqcn("java.util.List").shouldNotBeNull()
            }
        } finally {
            tempDir.deleteRecursively()
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("should index nested directory structure") {
        val tempDir = Files.createTempDirectory("test-classes").toFile()
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        try {
            // Create nested directories
            val subDir = File(tempDir, "com/example")
            subDir.mkdirs()

            val classBytes = String::class.java.getResourceAsStream("/java/lang/String.class")!!.readBytes()
            File(subDir, "Test.class").writeBytes(classBytes)

            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                classpathIndexer.indexClasspath(listOf(tempDir), parallel = false)

                val classes = indexer.findClassesByPackage("java.lang", publicOnly = false, recursive = false)
                classes.shouldNotBeEmpty()
            }
        } finally {
            tempDir.deleteRecursively()
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("should handle corrupted class files gracefully") {
        val tempDir = Files.createTempDirectory("test-classes").toFile()
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        try {
            // Create a corrupted class file
            val corruptedFile = File(tempDir, "Corrupted.class")
            corruptedFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                // Should not throw exception
                classpathIndexer.indexClasspath(listOf(tempDir), parallel = false)

                // Should have indexed nothing
                val classes = indexer.findClassesByPackage("", publicOnly = false, recursive = true)
                classes.shouldBeEmpty()
            }
        } finally {
            tempDir.deleteRecursively()
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("should enrich Kotlin classes during indexing") {
        val tempDir = Files.createTempDirectory("test-classes").toFile()
        val tempDb = Files.createTempFile("test-db", ".db").toString()

        try {
            // Use a Kotlin stdlib class (Pair is a data class)
            val pairBytes = Pair::class.java.getResourceAsStream("/kotlin/Pair.class")!!.readBytes()
            File(tempDir, "Pair.class").writeBytes(pairBytes)

            TypeIndexer(tempDb).use { indexer ->
                val classpathIndexer = ClasspathIndexer(indexer)
                classpathIndexer.indexClasspath(listOf(tempDir), parallel = false)

                val pairClass = indexer.findClassByFqcn("kotlin.Pair")
                pairClass.shouldNotBeNull()
                pairClass.kotlin.shouldNotBeNull()
                pairClass.kotlin!!.isData shouldBe true
            }
        } finally {
            tempDir.deleteRecursively()
            Files.deleteIfExists(java.nio.file.Paths.get(tempDb))
        }
    }

    test("ClassFileSource.FileSystem should read bytes correctly") {
        val tempFile = Files.createTempFile("test", ".class").toFile()
        try {
            val testData = byteArrayOf(1, 2, 3, 4, 5)
            tempFile.writeBytes(testData)

            val source = ClasspathIndexer.ClassFileSource.FileSystem(tempFile)
            val readBytes = source.readBytes()

            readBytes shouldBe testData
        } finally {
            tempFile.delete()
        }
    }

    test("ClassFileSource.JarEntry should read bytes correctly") {
        val tempJar = Files.createTempFile("test", ".jar").toFile()
        try {
            val testData = byteArrayOf(1, 2, 3, 4, 5)
            JarOutputStream(tempJar.outputStream()).use { jar ->
                jar.putNextEntry(ZipEntry("test.class"))
                jar.write(testData)
                jar.closeEntry()
            }

            val source = ClasspathIndexer.ClassFileSource.JarEntry(tempJar, "test.class")
            val readBytes = source.readBytes()

            readBytes shouldBe testData
        } finally {
            tempJar.delete()
        }
    }
})

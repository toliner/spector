package dev.toliner.spector.integration

import dev.toliner.spector.indexer.ClasspathIndexer
import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files

class IntegrationTest : FunSpec({

    test("should index and query classes from classpath") {
        val tempDb = Files.createTempFile("test-index", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            // Index the current runtime classpath (includes kotlin-stdlib and test dependencies)
            // Note: Java standard library classes are in modules (jrt:/) in Java 9+ and not in classpath
            val runtimeClasspath = System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }
                .filter { it.exists() }

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(runtimeClasspath, parallel = false)

            // Query for classes in kotlin.collections package
            val classes = indexer.findClassesByPackage(
                packageName = "kotlin.collections",
                recursive = false,
                publicOnly = true
            )

            classes.shouldNotBeEmpty()

            // Test recursive query
            val kotlinClasses = indexer.findClassesByPackage(
                packageName = "kotlin",
                recursive = true,
                publicOnly = false
            )

            kotlinClasses.shouldNotBeEmpty()

            // Test specific class lookup using a class we know exists
            // Pick the first class from our query results and verify we can look it up by FQCN
            val firstClass = classes.first()
            val lookedUpClass = indexer.findClassByFqcn(firstClass.fqcn)
            lookedUpClass shouldNotBe null
            lookedUpClass!!.fqcn shouldBe firstClass.fqcn
        }
    }

    test("should detect and enrich Kotlin metadata") {
        val tempDb = Files.createTempFile("test-kotlin", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            val runtimeClasspath = System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }
                .filter { it.exists() }

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(runtimeClasspath, parallel = false)

            // Query for Kotlin stdlib classes
            // Tests that Kotlin metadata is properly detected and enriched
            val kotlinClasses = indexer.findClassesByPackage(
                packageName = "kotlin",
                recursive = true,
                publicOnly = true
            )

            kotlinClasses.shouldNotBeEmpty()

            // Many Kotlin classes should have Kotlin metadata
            val kotlinClassesWithMetadata = kotlinClasses.filter { it.kotlin != null }
            kotlinClassesWithMetadata.shouldNotBeEmpty()

            // Test specific Kotlin class with metadata
            val pairClass = indexer.findClassByFqcn("kotlin.Pair")
            if (pairClass != null) {
                // Pair is a data class, so it should have Kotlin metadata
                pairClass.kotlin shouldNotBe null
            }
        }
    }
})

package dev.toliner.spector.integration

import dev.toliner.spector.IntegrationTag
import dev.toliner.spector.SlowTag
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

/**
 * Integration tests for full classpath indexing and querying.
 *
 * These tests index the entire runtime classpath once at the beginning
 * and share the index across all tests for performance.
 *
 * Tagged with IntegrationTag and SlowTag to allow selective test execution.
 */
class IntegrationTest : FunSpec({

    // Apply tags to all tests in this spec
    tags(IntegrationTag, SlowTag)

    // Shared resources across all tests in this spec
    lateinit var indexer: TypeIndexer
    lateinit var tempDb: File

    beforeSpec {
        // Create temporary database and indexer once for all tests
        tempDb = Files.createTempFile("test-integration", ".db").toFile()
        tempDb.deleteOnExit()
        indexer = TypeIndexer(tempDb.absolutePath)

        // Index the current runtime classpath once (includes kotlin-stdlib and test dependencies)
        // Note: Java standard library classes are in modules (jrt:/) in Java 9+ and not in classpath
        val runtimeClasspath = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { File(it) }
            .filter { it.exists() }

        val classpathIndexer = ClasspathIndexer(indexer)
        classpathIndexer.indexClasspath(runtimeClasspath, parallel = false)
    }

    afterSpec {
        // Clean up resources after all tests complete
        indexer.close()
    }

    test("should index and query classes from classpath") {
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

    test("should detect and enrich Kotlin metadata") {
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
})

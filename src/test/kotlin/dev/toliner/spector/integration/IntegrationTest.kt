package dev.toliner.spector.integration

import dev.toliner.spector.Integration
import dev.toliner.spector.Slow
import dev.toliner.spector.indexer.ClasspathIndexer
import dev.toliner.spector.indexer.JavaStdLibIndexer
import dev.toliner.spector.model.ClassKind
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.core.annotation.RequiresTag
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files

/**
 * Integration tests for Kotlin standard library and Java standard library indexing and querying.
 *
 * These tests index Kotlin stdlib and Java standard library once at the beginning
 * and share the index across all tests for performance.
 *
 * Tagged with IntegrationTag and SlowTag to allow selective test execution.
 */
class IntegrationTest : FunSpec({

    // Shared resources across all tests in this spec
    lateinit var indexer: TypeIndexer
    lateinit var tempDb: File

    beforeSpec {
        // Create temporary database and indexer once for all tests
        tempDb = Files.createTempFile("test-integration", ".db").toFile()
        tempDb.deleteOnExit()
        indexer = TypeIndexer(tempDb.absolutePath)

        // Index Kotlin standard library only for faster test execution
        val kotlinStdlib = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { File(it) }
            .firstOrNull { it.name.matches(Regex("kotlin-stdlib-\\d.*\\.jar")) }

        if (kotlinStdlib != null && kotlinStdlib.exists()) {
            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(listOf(kotlinStdlib), parallel = false)
        }

        // Index Java standard library from jrt:/ (Java 9+ modular runtime)
        val javaStdLibIndexer = JavaStdLibIndexer(indexer)
        javaStdLibIndexer.indexJavaStdLib(parallel = false)
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

    test("should index and query Java standard library classes") {
        // Query for classes in java.lang package
        val javaLangClasses = indexer.findClassesByPackage(
            packageName = "java.lang",
            recursive = false,
            publicOnly = true
        )

        javaLangClasses.shouldNotBeEmpty()

        val fqcn = javaLangClasses.map { it.fqcn }
        fqcn shouldContain "java.lang.String"
        fqcn shouldContain "java.lang.Object"

        // Verify java.lang.String
        val stringClass = indexer.findClassByFqcn("java.lang.String")
        stringClass shouldNotBe null
        stringClass!!.fqcn shouldBe "java.lang.String"
        stringClass.kind shouldBe ClassKind.CLASS
        // superClass is null for String because java.lang.Object is the implicit parent (filtered out by ClassScanner)
        stringClass.superClass.shouldBeNull()

        // Query for java.util package
        val javaUtilClasses = indexer.findClassesByPackage(
            packageName = "java.util",
            recursive = false,
            publicOnly = true
        )

        javaUtilClasses.shouldNotBeEmpty()
        javaUtilClasses.map { it.fqcn } shouldContain "java.util.List"
        javaUtilClasses.map { it.fqcn } shouldContain "java.util.ArrayList"

        // Verify java.util.List interface
        val listInterface = indexer.findClassByFqcn("java.util.List")
        listInterface shouldNotBe null
        listInterface!!.kind shouldBe ClassKind.INTERFACE

        // Verify java.util.ArrayList class
        val arrayListClass = indexer.findClassByFqcn("java.util.ArrayList")
        arrayListClass shouldNotBe null
        arrayListClass!!.kind shouldBe ClassKind.CLASS
        arrayListClass.interfaces shouldContain "java.util.List"
    }
})

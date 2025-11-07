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

    test("should index and query Java standard library classes") {
        val tempDb = Files.createTempFile("test-index", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            // Index the current runtime classpath (includes kotlin-stdlib and test dependencies)
            val runtimeClasspath = System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it) }
                .filter { it.exists() }

            val classpathIndexer = ClasspathIndexer(indexer)
            classpathIndexer.indexClasspath(runtimeClasspath, parallel = false)

            // Query for classes in java.util package
            val classes = indexer.findClassesByPackage(
                packageName = "java.util",
                recursive = false,
                publicOnly = true
            )

            classes.shouldNotBeEmpty()
            classes.map { it.fqcn } shouldContain "java.util.ArrayList"
            classes.map { it.fqcn } shouldContain "java.util.HashMap"

            // Find ArrayList specifically
            val arrayList = indexer.findClassByFqcn("java.util.ArrayList")
            arrayList shouldNotBe null
            arrayList!!.kind shouldBe ClassKind.CLASS
            arrayList.superClass shouldBe "java.util.AbstractList"
            arrayList.interfaces shouldContain "java.util.List"
        }
    }

    test("should detect Kotlin classes") {
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
            val kotlinClasses = indexer.findClassesByPackage(
                packageName = "kotlin",
                recursive = true,
                publicOnly = true
            )

            kotlinClasses.shouldNotBeEmpty()

            // Many Kotlin classes should have Kotlin metadata
            val kotlinClassesWithMetadata = kotlinClasses.filter { it.kotlin != null }
            kotlinClassesWithMetadata.shouldNotBeEmpty()
        }
    }
})

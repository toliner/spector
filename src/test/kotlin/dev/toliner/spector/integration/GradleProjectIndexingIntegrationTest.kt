package dev.toliner.spector.integration

import dev.toliner.spector.indexer.ClasspathIndexer
import dev.toliner.spector.indexer.GradleRuntimeClasspathResolver
import dev.toliner.spector.storage.TypeIndexer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

class GradleProjectIndexingIntegrationTest : FunSpec({
    val resolver = GradleRuntimeClasspathResolver()

    test("should resolve and index a Gradle project without printRuntimeCp") {
        val fixtureDir = prepareFixtureProject()
        val classpathEntries = resolver.resolve(fixtureDir)

        classpathEntries.map { it.name }.shouldContain("external.jar")
        classpathEntries.any { it.path.contains("build/classes/java/main") } shouldBe true

        val tempDb = Files.createTempFile("gradle-project-indexing", ".db").toFile()
        tempDb.deleteOnExit()

        TypeIndexer(tempDb.absolutePath).use { indexer ->
            ClasspathIndexer(indexer).indexClasspath(classpathEntries, parallel = false)

            indexer.findClassByFqcn("com.example.app.FixtureApp") shouldNotBe null
            indexer.findClassByFqcn("com.example.external.ExternalDependency") shouldNotBe null
        }
    }

    test("should reject a directory that is not a Gradle project") {
        val tempDir = Files.createTempDirectory("spector-non-gradle").toFile()
        tempDir.deleteOnExit()

        shouldThrow<IllegalArgumentException> {
            resolver.resolve(tempDir)
        }
    }
})

private fun prepareFixtureProject(): File {
    val sourceDir = File("src/test/fixtures/gradle-simple").absoluteFile
    val workingDir = Files.createTempDirectory("spector-gradle-fixture").toFile()
    copyRecursively(sourceDir, workingDir)
    writeExternalJar(File(workingDir, "libs/external.jar"))
    return workingDir
}

private fun copyRecursively(source: File, target: File) {
    Files.walk(source.toPath()).use { paths ->
        paths.forEach { path ->
            val relativePath = source.toPath().relativize(path)
            val destination = target.toPath().resolve(relativePath)

            if (Files.isDirectory(path)) {
                Files.createDirectories(destination)
            } else {
                Files.createDirectories(destination.parent)
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun writeExternalJar(jarFile: File) {
    val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) {
        "JDK compiler is required to build the Gradle fixture dependency."
    }
    val sourceRoot = Files.createTempDirectory("spector-external-src").toFile()
    val packageDir = File(sourceRoot, "com/example/external")
    packageDir.mkdirs()

    val sourceFile = File(packageDir, "ExternalDependency.java")
    sourceFile.writeText(
        """
        package com.example.external;

        public class ExternalDependency {
            public String value() {
                return "fixture";
            }
        }
        """.trimIndent()
    )

    val classesDir = Files.createTempDirectory("spector-external-classes").toFile()
    val compilationResult = compiler.run(null, null, null, "-d", classesDir.absolutePath, sourceFile.absolutePath)
    require(compilationResult == 0) {
        "Failed to compile fixture dependency source."
    }

    jarFile.parentFile.mkdirs()
    JarOutputStream(jarFile.outputStream().buffered()).use { jar ->
        val classFile = File(classesDir, "com/example/external/ExternalDependency.class")
        jar.putNextEntry(JarEntry("com/example/external/ExternalDependency.class"))
        classFile.inputStream().use { input -> input.copyTo(jar) }
        jar.closeEntry()
    }
}

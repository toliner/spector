package dev.toliner.spector.indexer

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Resolves a Gradle JVM project's main runtime classpath without editing the target build files.
 */
class GradleRuntimeClasspathResolver {
    fun resolve(projectDir: File): List<File> {
        val normalizedProjectDir = projectDir.absoluteFile.normalize()
        require(normalizedProjectDir.isDirectory) {
            "Gradle project not found: ${normalizedProjectDir.path}"
        }
        require(isGradleProject(normalizedProjectDir)) {
            "Not a valid Gradle project: ${normalizedProjectDir.path}"
        }

        val initScript = createInitScript()
        try {
            return connect(normalizedProjectDir).use { connection ->
                val output = ByteArrayOutputStream()
                connection.newBuild()
                    .forTasks("spectorPrintRuntimeClasspath")
                    .withArguments("-q", "-I", initScript.absolutePath)
                    .setStandardOutput(output)
                    .setStandardError(output)
                    .run()

                parseClasspathEntries(output.toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            throw IllegalStateException(buildFailureMessage(normalizedProjectDir, e), e)
        } finally {
            initScript.delete()
        }
    }

    private fun connect(projectDir: File): ProjectConnection {
        return GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .connect()
    }

    private fun isGradleProject(projectDir: File): Boolean {
        return listOf(
            "settings.gradle.kts",
            "settings.gradle",
            "build.gradle.kts",
            "build.gradle",
        ).any { File(projectDir, it).isFile }
    }

    private fun parseClasspathEntries(output: String): List<File> {
        val begin = output.indexOf(BEGIN_MARKER)
        val end = output.indexOf(END_MARKER)
        require(begin >= 0 && end > begin) {
            "Failed to parse Gradle runtime classpath from build output:\n$output"
        }

        return output
            .substring(begin + BEGIN_MARKER.length, end)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::File)
            .distinctBy { it.absoluteFile.normalize().path }
            .toList()
    }

    private fun buildFailureMessage(projectDir: File, error: Exception): String {
        return "Failed to resolve Gradle runtime classpath for ${projectDir.path}: ${error.message}"
    }

    private fun createInitScript(): File {
        val file = File.createTempFile("spector-gradle-runtime-classpath", ".gradle")
        file.writeText(
            """
            import org.gradle.api.GradleException

            def beginMarker = "${BEGIN_MARKER}"
            def endMarker = "${END_MARKER}"

            gradle.rootProject { rootProject ->
                rootProject.tasks.register("spectorPrintRuntimeClasspath") {
                    doLast {
                        def sourceSets = rootProject.extensions.findByName("sourceSets")
                        Set<File> runtimeClasspathFiles = null

                        if (sourceSets != null && sourceSets.findByName("main") != null) {
                            runtimeClasspathFiles = sourceSets.getByName("main").runtimeClasspath.files
                        } else if (rootProject.configurations.findByName("runtimeClasspath") != null) {
                            runtimeClasspathFiles = rootProject.configurations.getByName("runtimeClasspath").files
                        }

                        if (runtimeClasspathFiles == null) {
                            throw new GradleException("Project must expose either sourceSets.main.runtimeClasspath or a runtimeClasspath configuration.")
                        }

                        println(beginMarker)
                        runtimeClasspathFiles.each { println(it.absolutePath) }
                        println(endMarker)
                    }
                }
            }

            gradle.projectsEvaluated {
                def task = rootProject.tasks.named("spectorPrintRuntimeClasspath").get()
                if (rootProject.tasks.findByName("classes") != null) {
                    task.dependsOn(rootProject.tasks.named("classes"))
                }
            }
            """.trimIndent()
        )
        return file
    }

    companion object {
        private const val BEGIN_MARKER = "__SPECTOR_RUNTIME_CLASSPATH_BEGIN__"
        private const val END_MARKER = "__SPECTOR_RUNTIME_CLASSPATH_END__"
    }
}

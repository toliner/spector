import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("io.kotest") version "6.0.0"
    application
}

group = "dev.toliner"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // ASM for bytecode analysis
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-util:9.7")

    // Kotlin metadata parsing
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")

    // SQLite for storage
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")

    // Ktor for HTTP server
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.8")

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:6.0.0")
    testImplementation("io.kotest:kotest-framework-engine:6.0.0")
    testImplementation("io.kotest:kotest-assertions-core:6.0.0")
    testImplementation("io.ktor:ktor-server-test-host:2.3.12")

    // Test dependencies for API testing
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

tasks {
    named<UpdateDaemonJvm>("updateDaemonJvm") {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks.test {
    useJUnitPlatform()

    // Exclude integration and slow tests by default for fast feedback
    systemProperty("kotest.tags.exclude", "integration,slow")
}

// Task to run only integration tests
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (tagged with 'integration')"
    group = "verification"

    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    systemProperty("kotest.tags.include", "integration")

    // Use the same classpath as the test task
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
}

// Task to run all tests including integration tests
tasks.register<Test>("allTests") {
    description = "Runs all tests including integration and slow tests"
    group = "verification"

    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    // No tag filtering - run everything

    // Use the same classpath as the test task
    testClassesDirs = tasks.test.get().testClassesDirs
    classpath = tasks.test.get().classpath
}

// Tasks to print runtime classpaths for indexing
tasks.register("printRuntimeCp") {
    doLast {
        val runtimeClasspath = configurations.getByName("runtimeClasspath")
        println(runtimeClasspath.asPath)
    }
}

tasks.register("printTestRuntimeCp") {
    doLast {
        val testRuntimeClasspath = configurations.getByName("testRuntimeClasspath")
        println(testRuntimeClasspath.asPath)
    }
}

application {
    mainClass.set("dev.toliner.spector.MainKt")
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xnested-type-aliases", "-Xskip-prerelease-check"))
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}
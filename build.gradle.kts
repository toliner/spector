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

    // Test runtime dependencies - add some real libraries to scan
    testRuntimeOnly("com.squareup.okhttp3:okhttp:4.12.0")
    testRuntimeOnly("com.fasterxml.jackson.core:jackson-databind:2.17.2")
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
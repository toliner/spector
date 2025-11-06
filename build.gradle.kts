plugins {
    kotlin("jvm") version "2.2.0"
    id("io.kotest") version "6.0.0"
}

group = "dev.toliner"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-framework-engine:6.0.0")
    testImplementation("io.kotest:kotest-assertions-core:6.0.0")
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
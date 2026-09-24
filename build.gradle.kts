plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

group = "gorefest"
version = "0.1.0"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }
}

application {
    mainClass = "gorefest.MainKt"
}

ktor {
    fatJar {
        archiveFileName = "gorefest.jar"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.webjars)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.kotlinx.html)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.logback)
    runtimeOnly(libs.webjar.htmx)
    runtimeOnly(libs.webjar.hyperscript)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.test {
    useJUnitPlatform()
}

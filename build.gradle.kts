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

// JDK 25 warns when dependencies load native code (jansi, via ktor call-logging) or use Unsafe (classgraph,
// via webjars-locator). Both are fine for us, so say so rather than log four warnings on every start.
val jvmWarningFlags = listOf("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")

application {
    mainClass = "gorefest.MainKt"
    applicationDefaultJvmArgs = jvmWarningFlags
}

ktor {
    fatJar {
        archiveFileName = "gorefest.jar"
    }
}

// Native access can be granted from the manifest; the Unsafe flag can't, so `java -jar` still needs it (see deploy/gorefest.service).
tasks.withType<Jar>().configureEach {
    manifest { attributes("Enable-Native-Access" to "ALL-UNNAMED") }
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
    implementation(libs.ktor.server.forwarded.header)
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
    testImplementation(libs.jsoup)
}

// The design system in docs/design is the single source for the app's CSS and icons.
tasks.processResources {
    from("docs/design/css") { into("static/css") }
    from("docs/design/fonts") { include("*.woff2"); into("static/fonts") }
    from("docs/design/icons") { include("*.svg", "favicon.ico", "apple-touch-icon.png"); into("static/icons") }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(jvmWarningFlags)
    // Tests share one database (TestDatabase), so test classes must not run in parallel forks.
    maxParallelForks = 1
}

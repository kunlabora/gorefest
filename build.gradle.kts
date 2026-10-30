plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.detekt)
}

group = "gorefest"
version = "2.2.1"

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

val pitest by configurations.creating

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.webjars)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.conditional.headers)
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
    testImplementation(libs.konsist)

    pitest(libs.pitest.command.line)
    pitest(libs.pitest.junit5.plugin)
}

// The design system in docs/design is the single source for the app's CSS and icons.
tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("version.properties") { expand("version" to project.version.toString()) }
    from("docs/design/css") { into("static/css") }
    from("docs/design/fonts") { include("*.woff2", "OFL.txt"); into("static/fonts") }
    from("docs/design/icons") { include("*.svg", "favicon.ico", "apple-touch-icon.png"); into("static/icons") }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
    baseline = file("config/detekt/baseline.xml")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(jvmWarningFlags)
    // Tests share one database (TestDatabase), so test classes must not run in parallel forks.
    maxParallelForks = 1
}

// Mutation testing on the core (IMP's kill matrix): which changes to the code no test notices.
// Not part of `check`: run `./gradlew mutationTest` and read build/reports/pitest/index.html.
val mutationTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs pitest on gorefest.core with the core tests."
    dependsOn(tasks.testClasses)
    val reports = layout.buildDirectory.dir("reports/pitest")
    outputs.dir(reports)
    mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport"
    classpath = pitest + sourceSets.test.get().runtimeClasspath
    jvmArgs(jvmWarningFlags)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--targetClasses", "gorefest.core.*",
            "--targetTests", "gorefest.core.*",
            // Code Kotlin generates (serializers, data class members) isn't ours to test.
            "--excludedClasses", "*\$\$serializer,*\$Companion,*Test,*Test\$*,*FixturesKt,*.eventsourcing.DeciderTestingKt*",
            "--excludedMethods", "copy,copy\$default,component*,equals,hashCode,toString,write\$Self*,serializer,<clinit>",
            "--sourceDirs", file("src/main/kotlin").path,
            "--classPath", sourceSets.test.get().runtimeClasspath.joinToString(","),
            "--reportDir", reports.get().asFile.path,
            "--outputFormats", "HTML,XML",
            "--timestampedReports", "false",
            "--threads", "4",
            "--fullMutationMatrix", "true",
            "--jvmArgs", jvmWarningFlags.joinToString(","),
        )
    })
}

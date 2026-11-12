package gorefest.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * Stop hook: Kotlin changed since the last `./gradlew check` (or test) run? Say so once.
 *
 * Compares the newest changed .kt or contract file with the last test results. It nudges once per stop
 * (`stop_hook_active` lets the second stop through), so it never traps the session.
 */
object CheckReminder {
    private const val GIT_TIMEOUT_SECONDS = 10L

    fun run() {
        val event = Json.parseToJsonElement(System.`in`.readBytes().decodeToString()).jsonObject
        val secondStop = event["stop_hook_active"]?.jsonPrimitive?.booleanOrNull == true
        val newestChange = if (secondStop) null else newestChange()
        if (newestChange != null && newestChange > lastTestRun()) {
            println(
                buildJsonObject {
                    put("decision", "block")
                    put(
                        "reason",
                        "Kotlin or Contract files changed since the last test run. Run `./gradlew check` " +
                            "(tests, contract coverage, architecture rules, detekt) before calling this done, " +
                            "or say plainly that it wasn't run.",
                    )
                },
            )
        }
    }

    /** When the newest uncommitted .kt, .kts or Contract file changed (epoch millis), or null if none did. */
    private fun newestChange(): Long? =
        gitStatus()?.lines()
            ?.filter { it.length > 3 }
            ?.map { it.substring(3).substringAfterLast(" -> ") }
            ?.filter { it.endsWith(".kt") || it.endsWith(".kts") || it.startsWith("docs/contract/") }
            ?.map { root.resolve(it) }
            ?.filter { it.isRegularFile() }
            ?.maxOfOrNull { it.getLastModifiedTime().toMillis() }

    private fun lastTestRun(): Long {
        val results = root.resolve("build/test-results/test")
        if (!results.isDirectory()) return 0
        return results.listDirectoryEntries().filter { it.isRegularFile() }.maxOfOrNull { it.getLastModifiedTime().toMillis() } ?: 0
    }

    private fun gitStatus(): String? = try {
        val process = ProcessBuilder("git", "status", "--porcelain")
            .directory(root.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        output.takeIf { process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
    } catch (_: IOException) {
        null
    }
}

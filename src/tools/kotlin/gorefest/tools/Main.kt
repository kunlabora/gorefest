package gorefest.tools

import java.nio.file.Path
import java.time.Clock
import kotlin.system.exitProcess

/**
 * Developer tools, not part of the app. Each has a Gradle task (build.gradle.kts, group "tools") that names it in the
 * `gorefest.tool` property and passes `--args` through: `./gradlew -q flow --args="--when 8"`.
 */
fun main(args: Array<String>) {
    val rest = args.toList()
    when (System.getProperty("gorefest.tool")) {
        "flow" -> Flow.run(rest, Clock.systemDefaultZone())
        "killMatrix" -> KillMatrix.run(rest)
        "guard" -> Guard.run()
        "checkReminder" -> CheckReminder.run()
        else -> {
            System.err.println("Run a tool through its Gradle task: ./gradlew -q flow|killMatrix|guard|checkReminder")
            exitProcess(USAGE)
        }
    }
}

private const val USAGE = 64

/** The repository root: Claude Code's project dir for hooks, else the Gradle project dir, else here. */
internal val root: Path =
    Path.of(System.getenv("CLAUDE_PROJECT_DIR") ?: System.getProperty("gorefest.root") ?: ".").toAbsolutePath().normalize()

package gorefest.tools

import java.nio.file.Path
import java.time.Clock
import kotlin.system.exitProcess

/**
 * Developer tools, not part of the app: run through `scripts/tool` (or its links `scripts/flow` and
 * `scripts/kill-matrix`), which builds `build/tools/tools.jar` when its sources changed.
 */
fun main(args: Array<String>) {
    val rest = args.drop(1)
    when (args.firstOrNull()) {
        "flow" -> Flow.run(rest, Clock.systemDefaultZone())
        "kill-matrix" -> KillMatrix.run(rest)
        "guard" -> Guard.run()
        "check-reminder" -> CheckReminder.run()
        else -> {
            System.err.println("Usage: scripts/tool flow|kill-matrix|guard|check-reminder [args]")
            exitProcess(USAGE)
        }
    }
}

private const val USAGE = 64

/** The repository root: Claude Code's project dir for hooks, else what `scripts/tool` passes, else here. */
internal val root: Path =
    Path.of(System.getenv("CLAUDE_PROJECT_DIR") ?: System.getProperty("gorefest.root") ?: ".").toAbsolutePath().normalize()

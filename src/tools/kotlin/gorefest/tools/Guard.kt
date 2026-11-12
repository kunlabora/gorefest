package gorefest.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * PreToolUse guard (IMP: guardrails on the agent itself). Warn and ask, never block.
 *
 * Asks the person before a change that is hard to undo or breaks a promise of the codebase:
 * - pushing (pushing main deploys),
 * - editing a Flyway migration that already exists (it has run in production),
 * - removing or renaming a stored event type (`@SerialName` is forever),
 * - dropping a Contract obligation id (ids are forever; strike it through instead),
 * - growing the detekt baseline (new code meets the rules).
 * Everything else passes silently.
 */
object Guard {
    fun run() {
        val event = Json.parseToJsonElement(System.`in`.readBytes().decodeToString()).jsonObject
        val reason = check(event, root) ?: return
        println(
            buildJsonObject {
                putJsonObject("hookSpecificOutput") {
                    put("hookEventName", "PreToolUse")
                    put("permissionDecision", "ask")
                    put("permissionDecisionReason", reason)
                }
            },
        )
    }

    /** Why to ask the person before this tool call, or null to let it pass. */
    fun check(event: JsonObject, root: Path): String? {
        val input = event["tool_input"] as? JsonObject ?: JsonObject(emptyMap())
        fun field(name: String) = input[name]?.jsonPrimitive?.contentOrNull
        return when (val tool = event["tool_name"]?.jsonPrimitive?.contentOrNull) {
            "Bash" -> checkBash(field("command").orEmpty())
            "Edit", "Write" -> {
                val path = root.relativize(root.resolve(field("file_path").orEmpty()).normalize()).toString()
                val before = root.resolve(path).takeIf { it.isRegularFile() }?.readText()
                val after = when {
                    tool == "Write" -> field("content").orEmpty()
                    before == null -> null
                    input["replace_all"]?.jsonPrimitive?.booleanOrNull == true ->
                        before.replace(field("old_string").orEmpty(), field("new_string").orEmpty())
                    else -> before.replaceFirst(field("old_string").orEmpty(), field("new_string").orEmpty())
                }
                checkFile(path, before, after)
            }
            else -> null
        }
    }

    private val PUSH = Regex("""\bgit\b[^;&|]*\bpush\b""")
    private val MIGRATION_IN_COMMAND = Regex("""db/migration/V\d+""")
    private val CHANGES_FILES = Regex("""\bsed\s+-i|>\s*\S*db/migration|\bmv\b|\brm\b""")
    private val MIGRATION = Regex("""src/main/resources/db/migration/V\d+__.*\.sql""")
    private val SERIAL_NAME = Regex("""@SerialName\("([^"]+)"\)""")
    private val CONTRACT = Regex("""docs/contract/[^/]+\.md""")
    private val OBLIGATION = Regex("""\*\*([A-Z]+-\d+)\*\*""")

    private fun checkBash(command: String): String? = when {
        PUSH.containsMatchIn(command) ->
            "This pushes, and pushing main deploys to production. Only the release skill pushes, when you asked for a release."
        "detektBaseline" in command ->
            "This rewrites the detekt baseline, which would hide new findings. New code should meet the rules instead."
        MIGRATION_IN_COMMAND.containsMatchIn(command) && CHANGES_FILES.containsMatchIn(command) ->
            "This changes or removes an existing Flyway migration, which has already run in production. " +
                "Add a new V<n> migration instead."
        else -> null
    }

    private fun checkFile(path: String, before: String?, after: String?): String? {
        return when {
            before == null -> null
            MIGRATION.matches(path) ->
                "$path already exists and has run in production; changing it breaks Flyway's checksum. " +
                    "Add a new V<n> migration instead."
            after == null -> null
            path == "config/detekt/baseline.xml" && after.occurrences("<ID>") > before.occurrences("<ID>") ->
                "This adds findings to the detekt baseline. New code should meet the rules instead."
            path.startsWith("src/main/kotlin/") && path.endsWith(".kt") ->
                gone(SERIAL_NAME, before, after)?.let {
                    "This removes or renames the stored event type(s) $it. " +
                        "Stored events decode by that name on every replay: add a new event instead (CLAUDE.md, Event evolution)."
                }
            CONTRACT.matches(path) ->
                gone(OBLIGATION, before, after)?.let {
                    "This drops Contract obligation(s) $it. Ids are forever: strike a dropped one through " +
                        "(`- ~~**XX-n**~~ …, dropped <date>: why`) instead of deleting or renumbering."
                }
            else -> null
        }
    }

    private fun String.occurrences(text: String) = split(text).size - 1

    /** The names [pattern] finds in [before] but no longer in [after], comma-separated, or null if none went. */
    private fun gone(pattern: Regex, before: String, after: String): String? {
        fun names(text: String) = pattern.findAll(text).map { it.groupValues[1] }.toSet()
        return (names(before) - names(after)).sorted().takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}

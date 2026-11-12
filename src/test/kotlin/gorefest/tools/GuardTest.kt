package gorefest.tools

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class GuardTest {
    @TempDir
    lateinit var root: Path

    private fun call(tool: String, vararg input: Pair<String, Any>): String? = Guard.check(
        buildJsonObject {
            put("tool_name", tool)
            put("tool_input", JsonObject(input.associate { (k, v) -> k to (if (v is Boolean) JsonPrimitive(v) else JsonPrimitive(v.toString())) }))
        },
        root,
    )

    private fun file(path: String, text: String) = root.resolve(path).createParentDirectories().writeText(text)

    @Test
    fun `asks before a push, not before other git commands`() {
        call("Bash", "command" to "git push origin main").shouldNotBeNull() shouldContain "deploys"
        call("Bash", "command" to "git status && git log").shouldBeNull()
    }

    @Test
    fun `asks before rewriting the detekt baseline or moving a migration`() {
        call("Bash", "command" to "./gradlew detektBaseline").shouldNotBeNull()
        call("Bash", "command" to "rm src/main/resources/db/migration/V3__x.sql").shouldNotBeNull()
        call("Bash", "command" to "cat src/main/resources/db/migration/V3__x.sql").shouldBeNull()
    }

    @Test
    fun `asks before changing an existing migration, not before adding one`() {
        file("src/main/resources/db/migration/V1__init.sql", "create table x();")
        call("Edit", "file_path" to "$root/src/main/resources/db/migration/V1__init.sql", "old_string" to "x", "new_string" to "y")
            .shouldNotBeNull() shouldContain "checksum"
        call("Write", "file_path" to "src/main/resources/db/migration/V2__more.sql", "content" to "select 1;").shouldBeNull()
    }

    @Test
    fun `asks before a stored event type is renamed, not when one is added`() {
        file("src/main/kotlin/gorefest/core/Event.kt", """@SerialName("GameBrought") class A""")
        call("Edit", "file_path" to "src/main/kotlin/gorefest/core/Event.kt", "old_string" to "GameBrought", "new_string" to "GameTaken")
            .shouldNotBeNull() shouldContain "GameBrought"
        call("Edit", "file_path" to "src/main/kotlin/gorefest/core/Event.kt", "old_string" to "class A", "new_string" to """class A @SerialName("New") class B""")
            .shouldBeNull()
    }

    @Test
    fun `asks before a Contract id disappears, not when it is struck through`() {
        file("docs/contract/gameday.md", "- **GD-1** one\n- **GD-2** two\n")
        call("Edit", "file_path" to "docs/contract/gameday.md", "old_string" to "- **GD-2** two\n", "new_string" to "")
            .shouldNotBeNull() shouldContain "GD-2"
        call("Edit", "file_path" to "docs/contract/gameday.md", "old_string" to "- **GD-2** two", "new_string" to "- ~~**GD-2**~~ two, dropped")
            .shouldBeNull()
    }

    @Test
    fun `asks when the detekt baseline grows, not when it shrinks`() {
        file("config/detekt/baseline.xml", "<ID>a</ID><ID>b</ID>")
        call("Write", "file_path" to "config/detekt/baseline.xml", "content" to "<ID>a</ID><ID>b</ID><ID>c</ID>").shouldNotBeNull()
        call("Edit", "file_path" to "config/detekt/baseline.xml", "old_string" to "<ID>b</ID>", "new_string" to "", "replace_all" to true)
            .shouldBeNull()
    }
}

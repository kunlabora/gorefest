package gorefest.shell

import gorefest.core.gameday.gorefest7
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InviteLinksTest {
    private val invites = InviteLinks("test-key")

    @Test
    fun `a token names its game day and version`() {
        invites.parse(invites.token(gorefest7, 3)) shouldBe (gorefest7 to 3)
    }

    @Test
    fun `tampered or foreign tokens don't parse`() {
        val token = invites.token(gorefest7, 0)
        invites.parse(token.replace(".0.", ".1.")).shouldBeNull()
        invites.parse(InviteLinks("other-key").token(gorefest7, 0)).shouldBeNull()
        invites.parse("nonsense").shouldBeNull()
    }
}

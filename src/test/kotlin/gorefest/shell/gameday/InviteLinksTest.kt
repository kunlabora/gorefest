package gorefest.shell.gameday

import gorefest.core.DatePollId
import gorefest.core.gameday.gorefest7
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

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

    @Test
    fun `a date poll token names its poll, and is not interchangeable with a game day token`() {
        val poll = DatePollId(Uuid.parse(gorefest7.toString()))
        val token = invites.token(poll)

        invites.parsePoll(token) shouldBe poll
        invites.parsePoll(InviteLinks("other-key").token(poll)).shouldBeNull()
        invites.parsePoll(token.replaceFirst('.', '0')).shouldBeNull()
        invites.parsePoll("nonsense").shouldBeNull()
        // Same id, same key: still can't be replayed across kinds.
        invites.parse(token).shouldBeNull()
        invites.parsePoll(invites.token(gorefest7, 0)).shouldBeNull()
    }
}

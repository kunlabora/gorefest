package gorefest.core.player

import gorefest.contract.Obligation
import gorefest.core.LoggedIn
import gorefest.core.PlayerRegistered
import gorefest.core.eventsourcing.given
import gorefest.core.gameday.anna
import gorefest.core.gameday.bram
import org.junit.jupiter.api.Test

class LogInTest {
    @Test
    @Obligation("ACC-10")
    fun `first login registers the player`() {
        LogInDecider.given().whenever(LogIn(anna)).thenEmits(PlayerRegistered(anna), LoggedIn(anna))
    }

    @Test
    @Obligation("ACC-10")
    fun `later logins only log in`() {
        LogInDecider.given(PlayerRegistered(anna), LoggedIn(anna)).whenever(LogIn(anna)).thenEmits(LoggedIn(anna))
    }

    @Test
    @Obligation("ACC-10")
    fun `other players' registrations don't count`() {
        LogInDecider.given(PlayerRegistered(bram)).whenever(LogIn(anna)).thenEmits(PlayerRegistered(anna), LoggedIn(anna))
    }
}

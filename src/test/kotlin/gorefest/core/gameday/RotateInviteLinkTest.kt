package gorefest.core.gameday

import gorefest.contract.Obligation
import gorefest.core.Rejection
import gorefest.core.GameDayEnded
import gorefest.core.InviteLinkRotated
import gorefest.core.eventsourcing.given
import org.junit.jupiter.api.Test

class RotateInviteLinkTest {
    @Test
    @Obligation("GD-9")
    fun `the host bumps the invite version`() {
        RotateInviteLinkDecider.given(gameDayCreated()).whenever(RotateInviteLink(gorefest7, anna)).thenEmits(InviteLinkRotated(gorefest7, 1))
        RotateInviteLinkDecider
            .given(gameDayCreated(), InviteLinkRotated(gorefest7, 1))
            .whenever(RotateInviteLink(gorefest7, anna))
            .thenEmits(InviteLinkRotated(gorefest7, 2))
    }

    @Test
    @Obligation("GD-8", "GD-9")
    fun `only the host, and only while the game day is on`() {
        RotateInviteLinkDecider.given(gameDayCreated()).whenever(RotateInviteLink(gorefest7, bram)).thenRejects(Rejection.OnlyHostCanRotateInvite)
        RotateInviteLinkDecider.given(gameDayCreated(), GameDayEnded(gorefest7)).whenever(RotateInviteLink(gorefest7, anna)).thenRejects(Rejection.GameDayOver)
    }
}

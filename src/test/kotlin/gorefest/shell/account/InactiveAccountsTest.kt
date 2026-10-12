package gorefest.shell.account

import gorefest.core.GameDayCreated
import gorefest.core.GameDayId
import gorefest.core.PlayerId
import gorefest.core.PlayerRegistered
import gorefest.shell.AppConfig
import gorefest.shell.auth.AccountEraser
import gorefest.shell.web.TestApp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import kotlin.uuid.Uuid

class InactiveAccountsTest {
    private val test = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com", inactiveAccountMonths = 24))
    private val app = test.app
    private val kim = PlayerId(Uuid.random())
    private val bram = PlayerId(Uuid.random())

    @BeforeEach
    fun reset(): Unit = runBlocking {
        test.reset()
        listOf("Kim" to kim, "Bram" to bram, "Tim" to PlayerId(Uuid.random())).forEach { (name, id) ->
            test.loggedIn(name, id)
            app.db.transaction { app.login.players.touchLogin(id, test.clock.instant()) }
        }
        app.db.transaction { app.store.append(listOf(PlayerRegistered(kim), PlayerRegistered(bram))) }
        app.catchUp()
    }

    private fun exists(id: PlayerId) = runBlocking { app.db.transaction { app.login.players.find(id) } != null }
    private fun days(n: Long) = test.clock.advance(Duration.ofDays(n))

    @Test
    fun `after 24 months without signing in there is a warning, and 30 days later the account is erased`() = runBlocking {
        days(24 * 31)

        app.inactiveAccounts.sweep() shouldBe (3 - 1 to 0) // the admin is left alone
        test.mailbox.latestTo("kim@example.com")!!.subject shouldBe "Your Boardfest account will be deleted"
        exists(kim) shouldBe true

        days(29)
        app.inactiveAccounts.sweep() shouldBe (0 to 0)
        exists(kim) shouldBe true

        days(2)
        app.inactiveAccounts.sweep() shouldBe (0 to 2)
        exists(kim) shouldBe false
        exists(bram) shouldBe false
        test.mailbox.latestTo("kim@example.com")!!.text shouldContain "hadn't signed in"
    }

    @Test
    fun `signing in after the warning keeps the account`() = runBlocking<Unit> {
        days(24 * 31)
        app.inactiveAccounts.sweep()

        days(10)
        app.db.transaction { app.login.players.touchLogin(kim, test.clock.instant()) }
        days(25)
        app.inactiveAccounts.sweep() shouldBe (0 to 1) // only Bram, who stayed away

        exists(kim) shouldBe true
        exists(bram) shouldBe false
    }

    @Test
    fun `somebody hosting a game day still to come is left alone, and the admin always is`() = runBlocking<Unit> {
        val day = GameDayId(Uuid.random())
        days(24 * 31)
        app.db.transaction {
            app.store.append(listOf(GameDayCreated(day, kim, "Gorefest #9", LocalDate.now().plusDays(5), "Kim's", "Europe/Brussels")))
        }
        app.catchUp()

        app.inactiveAccounts.sweep() shouldBe (1 to 0) // Bram only
        days(31)
        app.inactiveAccounts.sweep()

        exists(kim) shouldBe true
        exists(bram) shouldBe false
    }

    @Test
    fun `it can be switched off`() = runBlocking<Unit> {
        val off = TestApp(AppConfig.dev().copy(inactiveAccountMonths = 0))
        off.reset()
        off.loggedIn("Kim", kim)
        off.clock.advance(Duration.ofDays(24 * 40))

        off.app.inactiveAccounts.sweep() shouldBe (0 to 0)
    }

    @Test
    fun `an erasure is announced by id only, and re-applying after a restore erases again`() = runBlocking<Unit> {
        app.accountEraser.erase(kim) shouldBe AccountEraser.Result.Erased
        val note = test.mailbox.latestTo("tim@example.com")!!
        note.subject shouldBe "Account erased"
        note.text shouldContain kim.toString()

        // A restore brings the account back (the backup is older than the erasure).
        test.loggedIn("Kim", kim)
        exists(kim) shouldBe true

        app.accountEraser.reapply(listOf(kim, PlayerId(Uuid.random()))) shouldBe 1
        exists(kim) shouldBe false
    }
}

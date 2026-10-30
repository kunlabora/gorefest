package gorefest.shell.auth

import gorefest.contract.Obligation
import gorefest.core.PlayerId
import gorefest.shell.AppConfig
import gorefest.shell.IdGenerator
import gorefest.shell.TestDatabase
import gorefest.shell.web.TestApp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.http.HttpRequest
import java.util.concurrent.Flow
import kotlin.uuid.Uuid

class AdminNotifierTest {
    private val hook = "https://discord.com/api/webhooks/123456789/abc-DEF_ghi"
    private val test = TestApp(AppConfig.dev().copy(adminEmail = "tim@example.com"))

    @BeforeEach
    fun reset() = runBlocking { test.reset() }

    private fun HttpRequest.body(): String {
        val out = StringBuilder()
        bodyPublisher().get().subscribe(object : Flow.Subscriber<java.nio.ByteBuffer> {
            override fun onSubscribe(s: Flow.Subscription) = s.request(Long.MAX_VALUE)
            override fun onNext(item: java.nio.ByteBuffer) { out.append(Charsets.UTF_8.decode(item)) }
            override fun onError(t: Throwable) = Unit
            override fun onComplete() = Unit
        })
        return out.toString()
    }

    @Test
    @Obligation("PR-20")
    fun `the Discord post carries the message and can't ping anyone`() {
        val request = DiscordWebhook(hook).request("Fresh wants into Boardfest", "Waiting for your approval: https://boardfest.be/admin/approvals")

        request.uri().toString() shouldBe hook
        val body = request.body()
        body shouldContain "Fresh wants into Boardfest"
        body shouldContain "/admin/approvals"
        body shouldContain """"allowed_mentions":{"parse":[]}"""
    }

    @Test
    @Obligation("PR-20")
    fun `only a Discord webhook address is accepted`() {
        shouldThrow<IllegalArgumentException> { DiscordWebhook("https://example.com/hook") }
        shouldThrow<IllegalArgumentException> { DiscordWebhook("http://discord.com/api/webhooks/1/x") }
        DiscordWebhook.fromEnv { null } shouldBe null
    }

    @Test
    @Obligation("PR-21")
    fun `the fallback hears about it when the first channel fails`() = runBlocking {
        val heard = mutableListOf<String>()
        FallbackNotifier({ _, _ -> error("discord down") }, { subject, _ -> heard += subject }).notify("hello", "text")

        heard shouldBe listOf("hello")
    }

    @Test
    @Obligation("ACC-22")
    fun `a notification that fails never stops someone from signing up`() = runBlocking {
        val login = Login(TestDatabase.db, test.app.commands, test.mailbox, test.app.config, test.clock, IdGenerator.sequential(), admin = { _, _ -> error("down") })
        val id = PlayerId(Uuid.random())
        TestDatabase.db.transaction { Players().findOrCreate("fresh@example.com", id, approved = false) }

        login.rename(id, "Fresh")

        login.names(listOf(id))[id] shouldBe "Fresh"
    }

    @Test
    @Obligation("PR-19")
    fun `the default is a mail to the admin, without the newcomer's email address`() = runBlocking {
        val id = PlayerId(Uuid.random())
        test.app.db.transaction { Players().findOrCreate("fresh@example.com", id, approved = false) }

        test.app.login.rename(id, "Fresh")

        val mail = test.mailbox.latestTo("tim@example.com")!!
        mail.subject shouldContain "Fresh"
        mail.text shouldContain "/admin/approvals"
        mail.text shouldNotContain "fresh@example.com"
    }
}

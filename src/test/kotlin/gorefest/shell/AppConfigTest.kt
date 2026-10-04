package gorefest.shell

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class AppConfigTest {
    private fun conflict(config: AppConfig, host: String = "127.0.0.1", mail: Boolean = false) =
        AppConfig.devModeConflict(config, host, mail)

    @Test
    fun `dev on localhost is fine`() {
        conflict(AppConfig.dev()) shouldBe null
        conflict(AppConfig.dev("http://127.0.0.1:8080"), host = "localhost") shouldBe null
    }

    @Test
    fun `dev with an https or public base url is refused`() {
        conflict(AppConfig.dev("https://games.example.org"))!! shouldContain "BASE_URL"
        conflict(AppConfig.dev("http://games.example.org"))!! shouldContain "BASE_URL"
    }

    @Test
    fun `dev bound beyond loopback or with real mail is refused`() {
        conflict(AppConfig.dev(), host = "0.0.0.0")!! shouldContain "HOST"
        conflict(AppConfig.dev(), mail = true)!! shouldContain "mail provider"
    }

    @Test
    fun `production is unaffected`() {
        conflict(AppConfig.dev("https://games.example.org").copy(dev = false), host = "0.0.0.0", mail = true) shouldBe null
    }
}

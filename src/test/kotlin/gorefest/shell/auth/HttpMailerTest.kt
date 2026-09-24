package gorefest.shell.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.Flow

class HttpMailerTest {
    private val mail = Mail("kim@example.com", "Your Gorefest code: 391740", "Your code is 391740.", Instant.EPOCH)

    @Test
    fun `postmark gets its token header and a text body`() {
        val request = PostmarkMailer("pm-token", "Gorefest <hi@gorefest.app>").request(mail)

        request.uri().toString() shouldBe "https://api.postmarkapp.com/email"
        request.headers().firstValue("X-Postmark-Server-Token").get() shouldBe "pm-token"
        val body = body(request)
        body.getValue("To").jsonPrimitive.content shouldBe "kim@example.com"
        body.getValue("From").jsonPrimitive.content shouldBe "Gorefest <hi@gorefest.app>"
        body.getValue("TextBody").jsonPrimitive.content shouldBe "Your code is 391740."
    }

    @Test
    fun `resend gets a bearer token and a list of recipients`() {
        val request = ResendMailer("re-key", "hi@gorefest.app").request(mail)

        request.uri().toString() shouldBe "https://api.resend.com/emails"
        request.headers().firstValue("Authorization").get() shouldBe "Bearer re-key"
        body(request).getValue("to").jsonArray.single().jsonPrimitive.content shouldBe "kim@example.com"
    }

    @Test
    fun `the provider comes from the environment`() {
        val env = mapOf("MAIL_PROVIDER" to "resend", "MAIL_API_KEY" to "k", "MAIL_FROM" to "hi@gorefest.app")
        HttpMailer.fromEnv(env::get).shouldBeInstanceOf<ResendMailer>()
        HttpMailer.fromEnv(emptyMap<String, String>()::get) shouldBe null
    }

    /** Reads a request's body publisher back into JSON. */
    private fun body(request: HttpRequest): JsonObject {
        val bytes = java.io.ByteArrayOutputStream()
        val done = java.util.concurrent.CountDownLatch(1)
        request.bodyPublisher().get().subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(s: Flow.Subscription) = s.request(Long.MAX_VALUE)
            override fun onNext(item: ByteBuffer) { bytes.write(ByteArray(item.remaining()).also(item::get)) }
            override fun onError(t: Throwable) = done.countDown()
            override fun onComplete() = done.countDown()
        })
        done.await()
        return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as JsonObject
    }
}

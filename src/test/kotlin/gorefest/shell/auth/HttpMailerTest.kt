package gorefest.shell.auth

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.Flow

class HttpMailerTest {
    private val mail = Mail("kim@example.com", "Your Boardfest code: 391740", "Your code is 391740.", Instant.EPOCH)

    @Test
    fun `postmark gets its token header and a text body`() {
        val request = PostmarkMailer("pm-token", "Boardfest <hi@gorefest.app>").request(mail)

        request.uri().toString() shouldBe "https://api.postmarkapp.com/email"
        request.headers().firstValue("X-Postmark-Server-Token").get() shouldBe "pm-token"
        val body = body(request)
        body.getValue("To").jsonPrimitive.content shouldBe "kim@example.com"
        body.getValue("From").jsonPrimitive.content shouldBe "Boardfest <hi@gorefest.app>"
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
    fun `scaleway gets its token, a from object, recipients and the project`() {
        val request = ScalewayMailer("scw-secret", "Boardfest <hello@gorefest.example.org>", "project-123").request(mail)

        request.uri().toString() shouldBe "https://api.scaleway.com/transactional-email/v1alpha1/regions/fr-par/emails"
        request.headers().firstValue("X-Auth-Token").get() shouldBe "scw-secret"
        val body = body(request)
        body.getValue("from").jsonObject.let { (it.getValue("name").jsonPrimitive.content to it.getValue("email").jsonPrimitive.content) } shouldBe
            ("Boardfest" to "hello@gorefest.example.org")
        body.getValue("to").jsonArray.single().jsonObject.getValue("email").jsonPrimitive.content shouldBe "kim@example.com"
        body.getValue("project_id").jsonPrimitive.content shouldBe "project-123"
        body.getValue("text").jsonPrimitive.content shouldBe "Your code is 391740."
    }

    @Test
    fun `a bare from address has no name`() {
        val body = body(ScalewayMailer("k", "hello@gorefest.example.org", "p").request(mail))
        body.getValue("from").jsonObject.keys shouldBe setOf("email")
    }

    @Test
    fun `the provider comes from the environment`() {
        val env = mapOf("MAIL_PROVIDER" to "resend", "MAIL_API_KEY" to "k", "MAIL_FROM" to "hi@gorefest.app")
        HttpMailer.fromEnv(env::get).shouldBeInstanceOf<ResendMailer>()
        HttpMailer.fromEnv((env + ("MAIL_PROVIDER" to "scaleway") + ("SCW_PROJECT_ID" to "p"))::get).shouldBeInstanceOf<ScalewayMailer>()
        HttpMailer.fromEnv(emptyMap<String, String>()::get) shouldBe null
        HttpMailer.fromEnv(mapOf("MAIL_API_KEY" to "", "MAIL_FROM" to "").let { m -> { k: String -> m[k]?.ifBlank { null } } }) shouldBe null
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

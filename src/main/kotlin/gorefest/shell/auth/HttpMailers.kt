package gorefest.shell.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A transactional mail provider reached over HTTP. [request] is separate so it can be tested without sending. */
abstract class HttpMailer(private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) : Mailer {
    abstract fun request(mail: Mail): HttpRequest

    override suspend fun send(mail: Mail) {
        val response = withContext(Dispatchers.IO) { http.send(request(mail), HttpResponse.BodyHandlers.ofString()) }
        check(response.statusCode() in 200..299) { "Mail provider answered ${response.statusCode()}: ${response.body().take(200)}" }
    }

    companion object {
        /** `MAIL_PROVIDER` = postmark or resend, with `MAIL_API_KEY` and `MAIL_FROM`. */
        fun fromEnv(env: (String) -> String? = System::getenv): Mailer? {
            val key = env("MAIL_API_KEY") ?: return null
            val from = env("MAIL_FROM") ?: error("MAIL_FROM is required with MAIL_API_KEY")
            return when (env("MAIL_PROVIDER")) {
                "postmark" -> PostmarkMailer(key, from)
                "resend" -> ResendMailer(key, from)
                else -> error("MAIL_PROVIDER must be postmark or resend")
            }
        }
    }
}

class PostmarkMailer(private val token: String, private val from: String) : HttpMailer() {
    override fun request(mail: Mail): HttpRequest = HttpRequest.newBuilder(URI("https://api.postmarkapp.com/email"))
        .header("X-Postmark-Server-Token", token)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .timeout(Duration.ofSeconds(20))
        .POST(HttpRequest.BodyPublishers.ofString(buildJsonObject {
            put("From", from)
            put("To", mail.to)
            put("Subject", mail.subject)
            put("TextBody", mail.text)
            put("MessageStream", "outbound")
        }.toString()))
        .build()
}

class ResendMailer(private val key: String, private val from: String) : HttpMailer() {
    override fun request(mail: Mail): HttpRequest = HttpRequest.newBuilder(URI("https://api.resend.com/emails"))
        .header("Authorization", "Bearer $key")
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(20))
        .POST(HttpRequest.BodyPublishers.ofString(buildJsonObject {
            put("from", from)
            putJsonArray("to") { add(mail.to) }
            put("subject", mail.subject)
            put("text", mail.text)
        }.toString()))
        .build()
}

package gorefest.shell.auth

import gorefest.shell.AppConfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
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
        // Only the status: error bodies often echo the recipient, and this message ends up in the log.
        check(response.statusCode() in 200..299) { "Mail provider answered ${response.statusCode()}" }
    }

    companion object {
        /**
         * `MAIL_PROVIDER` = scaleway, postmark or resend, with `MAIL_API_KEY` and `MAIL_FROM`
         * ("Name <address>" or a bare address). Scaleway also needs `SCW_PROJECT_ID`.
         */
        fun fromEnv(env: (String) -> String? = AppConfig::setting): Mailer? {
            val key = env("MAIL_API_KEY") ?: return null
            val from = env("MAIL_FROM") ?: error("MAIL_FROM is required with MAIL_API_KEY")
            return when (env("MAIL_PROVIDER")) {
                "scaleway" -> ScalewayMailer(key, from, env("SCW_PROJECT_ID") ?: error("SCW_PROJECT_ID is required for Scaleway mail"))
                "postmark" -> PostmarkMailer(key, from)
                "resend" -> ResendMailer(key, from)
                else -> error("MAIL_PROVIDER must be scaleway, postmark or resend")
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

/**
 * Scaleway Transactional Email. [secretKey] is an IAM API key allowed to send mail in [projectId];
 * the sending domain must be verified in TEM. Only the fr-par region offers TEM.
 */
class ScalewayMailer(private val secretKey: String, from: String, private val projectId: String, private val region: String = "fr-par") : HttpMailer() {
    private val fromName = from.substringBefore('<', "").trim()
    private val fromEmail = from.substringAfter('<').substringBefore('>').trim()

    override fun request(mail: Mail): HttpRequest =
        HttpRequest.newBuilder(URI("https://api.scaleway.com/transactional-email/v1alpha1/regions/$region/emails"))
            .header("X-Auth-Token", secretKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(buildJsonObject {
                putJsonObject("from") {
                    put("email", fromEmail)
                    if (fromName.isNotEmpty()) put("name", fromName)
                }
                putJsonArray("to") { add(buildJsonObject { put("email", mail.to) }) }
                put("subject", mail.subject)
                put("text", mail.text)
                put("project_id", projectId)
            }.toString()))
            .build()
}

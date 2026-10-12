package gorefest.shell.auth

import gorefest.shell.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration

/** Tells the admin something needs their attention. Keep [text] free of email addresses: it may leave for a chat service. */
fun interface AdminNotifier {
    suspend fun notify(subject: String, text: String)
}

/** The default: a mail to `ADMIN_EMAIL`. */
class MailAdminNotifier(private val mailer: Mailer, private val config: AppConfig, private val clock: Clock) : AdminNotifier {
    override suspend fun notify(subject: String, text: String) {
        val admin = config.adminEmail ?: return
        mailer.send(Mail(to = admin, subject = subject, text = text, sentAt = clock.instant()))
    }
}

/** Posts to a Discord channel through a webhook (`NOTIFY_DISCORD_WEBHOOK`). [request] is separate so it can be tested without sending. */
class DiscordWebhook(
    private val url: String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : AdminNotifier {
    init {
        require(WEBHOOK.matches(url)) { "NOTIFY_DISCORD_WEBHOOK must be a Discord webhook URL (https://discord.com/api/webhooks/…)" }
    }

    fun request(subject: String, text: String): HttpRequest = HttpRequest.newBuilder(URI(url))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(15))
        .POST(HttpRequest.BodyPublishers.ofString(buildJsonObject {
            put("content", "**$subject**\n$text".take(MAX_CONTENT))
            // A name is typed by a stranger: it must never be able to ping @everyone or a role.
            put("allowed_mentions", buildJsonObject { putJsonArray("parse") {} })
        }.toString()))
        .build()

    override suspend fun notify(subject: String, text: String) {
        val response = withContext(Dispatchers.IO) { http.send(request(subject, text), HttpResponse.BodyHandlers.ofString()) }
        check(response.statusCode() in 200..299) { "Discord answered ${response.statusCode()}" }
    }

    companion object {
        private const val MAX_CONTENT = 1900
        private val WEBHOOK = Regex("""^https://(discord|discordapp)\.com/api/webhooks/\d+/[\w-]+$""")

        fun fromEnv(env: (String) -> String? = AppConfig::setting): DiscordWebhook? = env("NOTIFY_DISCORD_WEBHOOK")?.let(::DiscordWebhook)
    }
}

/** Tries [primary]; if that fails, [fallback] gets it, so the admin hears either way. */
class FallbackNotifier(private val primary: AdminNotifier, private val fallback: AdminNotifier) : AdminNotifier {
    private val log = LoggerFactory.getLogger(FallbackNotifier::class.java)

    override suspend fun notify(subject: String, text: String) {
        try {
            primary.notify(subject, text)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Admin notification failed, falling back: {}", e.message)
            fallback.notify(subject, text)
        }
    }
}

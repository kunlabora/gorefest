package gorefest.shell.web.privacy

import gorefest.shell.AppConfig
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** The notice is public: people read it before they give us an email address. */
context(config: AppConfig)
fun Route.privacyRoutes() {
    get("/privacy") {
        call.respondHtml { privacyPage(PrivacyView(contactEmail = config.adminEmail, mailProvider = MailProvider.of(config.mailProvider), discord = config.discordNotifications)) }
    }
}

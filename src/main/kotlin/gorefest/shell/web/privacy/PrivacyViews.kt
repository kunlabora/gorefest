package gorefest.shell.web.privacy

import gorefest.shell.web.components.crumb
import gorefest.shell.web.components.page
import gorefest.shell.web.components.wordmark
import gorefest.shell.web.components.wrap
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul

/** What the notice says about itself: how to reach me, and who sends the mail. Built from config in [privacyRoutes]. */
data class PrivacyView(val contactEmail: String?, val mailProvider: MailProvider, val discord: Boolean = false)

/** The mail provider in use, named and placed, so the notice never claims one that isn't. */
enum class MailProvider(val label: String, val where: String) {
    SCALEWAY("Scaleway Transactional Email", "in the EU"),
    POSTMARK("Postmark", "in the US"),
    RESEND("Resend", "in the US"),
    NONE("No mail provider", "");

    companion object {
        fun of(setting: String?) = entries.firstOrNull { it.name.equals(setting, ignoreCase = true) && it != NONE } ?: NONE
    }
}

/** A plain-words notice for a private site: public (no login), still `noindex`. */
fun HTML.privacyPage(view: PrivacyView) = page("Privacy", appBar = { wordmark() }, wide = true, mainClasses = "gf-privacy") {
    wrap {
        crumb("/", "Back")
        h1("gf-h1") { +"What Boardfest keeps about you" }
        p("gf-lede") {
            +"Boardfest is a private site for one group of friends who play board games together. I run it for them, not as a business. This is what it keeps, who can see it, and how to get rid of it."
        }

        privacySection("who", "Who runs it") {
            p {
                +"I do, a friend in the group. "
                if (view.contactEmail != null) {
                    +"To ask a question, see your data, or have your account deleted, write to "
                    a("mailto:${view.contactEmail}", classes = "gf-link") { +view.contactEmail }
                    +", or ask from "
                    a("/account", classes = "gf-link") { +"your account page" }
                    +"."
                } else {
                    +"To ask a question, see your data, or have your account deleted, ask whoever invited you."
                }
            }
        }

        privacySection("ask", "What you can ask for") {
            p { +"On your account page you can ask me to send you everything Boardfest holds about you, or to delete your account. I do it by hand and send the file to your own address, so allow a few days. The file names the games and game days, never other people." }
            p { +"You can change your email address yourself (a code goes to the new address first), and unlink your BGG account, which removes your collection." }
        }

        privacySection("keep", "What it keeps") {
            ul {
                li { +"Your email address and the name you picked. The email is only used to sign you in and to send you the mails you'd expect (login codes, a recap after a game day)." }
                li { +"The BGG username you link, and your collection, which is read from BoardGameGeek. Nothing about you is sent to BGG except that username in the request." }
                li { +"Your taste: which game you'd rather play when asked, games you haven't played, and the games you've hearted." }
                li { +"Which game days you joined, the games you brought or asked someone to bring, what you played and with whom, and your answers in date polls." }
                li { +"The title, date and place of game days. The place is shown only to people who have joined, never on the invite page or in its preview." }
            }
        }

        privacySection("sees", "Who sees what") {
            p { +"People on the same game day see your name, the games you brought and the games you've hearted. They can also see your collection on the screen where someone asks you to bring a game, unless you switched that off. Nobody outside the group sees anything: you need an invite, and I approve every new account by hand." }
        }

        privacySection("others", "Who else handles it") {
            ul {
                li { +"Scaleway, in France, hosts the site and keeps the nightly backups." }
                li {
                    if (view.mailProvider == MailProvider.NONE) +"No mail goes out: this is a development copy, and mail stays on this machine."
                    else +"${view.mailProvider.label}, ${view.mailProvider.where}, sends the mail."
                }
                if (view.discord) li { +"A Discord channel only I can read gets a short message when someone signs up or asks to have their data or account handled. It holds a name and a link, never an email address. When an account is deleted, the message holds only its number, so I can delete it again if I ever have to restore a backup." }
                else li { +"When someone signs up or asks to have their data or account handled, I get a mail with their name and a link." }
            }
        }

        privacySection("cookies", "Cookies") {
            p { +"Two, both needed for the site to work: one keeps you signed in, one remembers light or dark. No tracking, no ads, nothing from third parties, and no banner because of it." }
        }

        privacySection("long", "How long") {
            p { +"For as long as your account exists. If you don't sign in for two years you get a mail, and a month later the account is deleted. Login codes are gone after a day. Server logs keep the time, the page and the result of a request, never a link or an email address, for 14 days. Backups are kept for 30 days, so a deleted account leaves them at the end of that." }
            p { +"When I delete your account, your email, name, collection and sign-ins are removed. Your games and requests leave game days still to come. What you answered stays in the game history, under a number that no longer points at anyone." }
        }
    }
}

private fun FlowContent.privacySection(id: String, heading: String, body: FlowContent.() -> Unit) = section("gf-privacy__section") {
    this.id = id
    h2("gf-h2") { +heading }
    div { body() }
}

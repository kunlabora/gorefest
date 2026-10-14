package gorefest.shell.auth

import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class Mail(val to: String, val subject: String, val text: String, val sentAt: Instant)

fun interface Mailer {
    suspend fun send(mail: Mail)
}

/** Keeps mail in memory for `/dev/mailbox` and tests. A real provider comes with the deploy story. */
class DevMailbox : Mailer {
    private val log = LoggerFactory.getLogger(DevMailbox::class.java)
    private val mails = CopyOnWriteArrayList<Mail>()

    override suspend fun send(mail: Mail) {
        mails += mail
        // No address and no subject: the subject carries the login code. The mail itself is on /dev/mailbox.
        log.info("Mail delivered to the dev mailbox")
    }

    /** Newest first. */
    val all: List<Mail> get() = mails.reversed()

    fun latestTo(email: String): Mail? = all.firstOrNull { it.to == email }
}

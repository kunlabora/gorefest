package gorefest.shell.account

import gorefest.core.PlayerId
import gorefest.shell.AppConfig
import gorefest.shell.auth.AccountEraser
import gorefest.shell.auth.AdminNotifier
import gorefest.shell.auth.Mail
import gorefest.shell.auth.Mailer
import gorefest.shell.auth.Player
import gorefest.shell.auth.Players
import gorefest.shell.db.Database
import gorefest.shell.db.Tx
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

enum class RequestKind { DELETE, EXPORT }

data class OpenRequest(val player: Player, val kind: RequestKind, val at: Instant)

/**
 * "Delete my account" and "send me my data": a person asks, the admin hears (the same channel as new sign-ups),
 * and the admin does it from `/admin/requests`. Asking twice is one request.
 */
class AccountRequests(
    private val db: Database,
    private val players: Players,
    private val admin: AdminNotifier,
    private val eraser: AccountEraser,
    private val mailer: Mailer,
    private val config: AppConfig,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(AccountRequests::class.java)
    private val rows = Rows()

    /** True if this is a new request; false if it was already open, or there is no such player. */
    suspend fun ask(id: PlayerId, kind: RequestKind): Boolean {
        val (player, fresh) = db.transaction { players.find(id) to rows.insert(id, kind, clock.instant()) }
        if (player == null || !fresh) return false
        val what = if (kind == RequestKind.DELETE) "delete their account" else "get their data"
        notify("${player.name ?: "Someone"} wants to $what", "Open requests: ${config.baseUrl}/admin/requests")
        return true
    }

    suspend fun pending(id: PlayerId): Set<RequestKind> = db.transaction { rows.of(id) }

    suspend fun open(): List<OpenRequest> = db.transaction { rows.open() }

    /** The admin did it (sent the data): the request is closed. */
    suspend fun done(id: PlayerId, kind: RequestKind) = db.transaction { rows.delete(id, kind) }

    /** Erases the account, then tells the person (their address is gone afterwards). False if there was no such account. */
    suspend fun erase(id: PlayerId): Boolean {
        val player = db.transaction { players.find(id) } ?: return false
        if (eraser.erase(id) !is AccountEraser.Result.Erased) return false
        mailer.send(
            Mail(
                player.email, "Your Boardfest account is deleted",
                "As you asked, your Boardfest account is deleted: your email, name, collection and sign-ins are gone.\nIf you ever want back in, you can sign up again.\n",
                clock.instant(),
            ),
        )
        return true
    }

    /** Never lets a notification problem get in the way of the person asking. */
    private suspend fun notify(subject: String, text: String) {
        try {
            admin.notify(subject, text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("Could not tell the admin about an account request", e)
        }
    }

    private class Rows {
        context(tx: Tx)
        fun insert(id: PlayerId, kind: RequestKind, at: Instant): Boolean =
            tx.connection.prepareStatement(
                "insert into account_requests (player_id, kind, requested_at) select id, ?, ? from players where id = ? on conflict do nothing",
            ).use {
                it.setString(1, kind.name)
                it.setTimestamp(2, Timestamp.from(at))
                it.setObject(3, id.value.toJavaUuid())
                it.executeUpdate() > 0
            }

        context(tx: Tx)
        fun of(id: PlayerId): Set<RequestKind> =
            tx.connection.prepareStatement("select kind from account_requests where player_id = ?").use {
                it.setObject(1, id.value.toJavaUuid())
                it.executeQuery().use { rs -> buildSet { while (rs.next()) add(RequestKind.valueOf(rs.getString(1))) } }
            }

        context(tx: Tx)
        fun open(): List<OpenRequest> =
            tx.connection.prepareStatement(
                "select p.id, p.email, p.name, p.approved_at is not null as approved, r.kind, r.requested_at " +
                    "from account_requests r join players p on p.id = r.player_id order by r.requested_at",
            ).use {
                it.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(
                            OpenRequest(
                                Player(PlayerId(rs.getObject(1, java.util.UUID::class.java).toKotlinUuid()), rs.getString(2), rs.getString(3), rs.getBoolean(4)),
                                RequestKind.valueOf(rs.getString(5)),
                                rs.getTimestamp(6).toInstant(),
                            ),
                        )
                    }
                }
            }

        context(tx: Tx)
        fun delete(id: PlayerId, kind: RequestKind) {
            tx.connection.prepareStatement("delete from account_requests where player_id = ? and kind = ?").use {
                it.setObject(1, id.value.toJavaUuid())
                it.setString(2, kind.name)
                it.executeUpdate()
            }
        }
    }
}

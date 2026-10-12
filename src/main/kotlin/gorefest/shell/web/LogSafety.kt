package gorefest.shell.web

/** Invite and poll links carry their secret in the path: `/i/{token}`, `/p/{token}/join`. */
private val PATH_SECRET = Regex("^/(i|p)/[^/?#]+")

/**
 * A request target that is safe to write to a log: no query string (login links, return paths and
 * join codes travel there) and no secret path segment. Anything that logs a URL goes through this.
 */
fun String.loggable(): String = substringBefore('?').substringBefore('#').replace(PATH_SECRET) { "/${it.groupValues[1]}/{token}" }

package gorefest.shell

/**
 * Settings from the environment. `GOREFEST_ENV=dev` (the default until S1 adds production)
 * enables the dev mailbox and allows a built-in secret key.
 */
data class AppConfig(val baseUrl: String, val secretKey: String, val dev: Boolean) {
    val secureCookies get() = baseUrl.startsWith("https://")

    companion object {
        fun dev(baseUrl: String = "http://localhost:8080") = AppConfig(baseUrl, DEV_SECRET, dev = true)

        fun fromEnv(env: (String) -> String? = ::setting): AppConfig {
            val dev = (env("GOREFEST_ENV") ?: "dev") == "dev"
            return AppConfig(
                baseUrl = (env("BASE_URL") ?: "http://localhost:8080").trimEnd('/'),
                secretKey = env("SECRET_KEY") ?: if (dev) DEV_SECRET else error("SECRET_KEY is required outside dev"),
                dev = dev,
            )
        }

        /**
         * Dev mode uses a public secret and exposes every login code at `/dev/mailbox`, so it must
         * never run on a real deployment. Returns what gives the deployment away, or null when fine.
         */
        fun devModeConflict(config: AppConfig, host: String, realMailConfigured: Boolean): String? {
            if (!config.dev) return null
            val signs = buildList {
                val baseHost = runCatching { java.net.URI(config.baseUrl).host }.getOrNull()
                if (config.secureCookies || baseHost !in LOOPBACK) add("BASE_URL is ${config.baseUrl}")
                if (host !in LOOPBACK) add("HOST is $host")
                if (realMailConfigured) add("a mail provider is configured")
            }
            return if (signs.isEmpty()) null
            else "Refusing to start in dev mode (GOREFEST_ENV is unset or 'dev') on what looks like a real deployment: " +
                signs.joinToString("; ") + ". Set GOREFEST_ENV=prod."
        }

        private val LOOPBACK = setOf("localhost", "127.0.0.1", "[::1]", "::1")

        /**
         * An environment variable, with empty meaning unset: the env file has lines like
         * `BGG_TOKEN=` that systemd passes on as empty strings.
         */
        fun setting(name: String): String? = System.getenv(name)?.ifBlank { null }

        private const val DEV_SECRET = "dev-only-secret-do-not-use-in-production"
    }
}

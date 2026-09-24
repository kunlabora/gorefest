package gorefest.shell

/**
 * Settings from the environment. `GOREFEST_ENV=dev` (the default until S1 adds production)
 * enables the dev mailbox and allows a built-in secret key.
 */
data class AppConfig(val baseUrl: String, val secretKey: String, val dev: Boolean) {
    val secureCookies get() = baseUrl.startsWith("https://")

    companion object {
        fun dev(baseUrl: String = "http://localhost:8080") = AppConfig(baseUrl, DEV_SECRET, dev = true)

        fun fromEnv(env: (String) -> String? = System::getenv): AppConfig {
            val dev = (env("GOREFEST_ENV") ?: "dev") == "dev"
            return AppConfig(
                baseUrl = (env("BASE_URL") ?: "http://localhost:8080").trimEnd('/'),
                secretKey = env("SECRET_KEY") ?: if (dev) DEV_SECRET else error("SECRET_KEY is required outside dev"),
                dev = dev,
            )
        }

        private const val DEV_SECRET = "dev-only-secret-do-not-use-in-production"
    }
}

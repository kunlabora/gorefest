package gorefest.shell.web

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.outputStream

/**
 * In production `/static` is served from the fat jar, where every file carries the same 1980 timestamp. Tests normally
 * serve it from `build/resources`, with real file dates, so the deploy case is rebuilt here from two jars.
 */
class StaticAssetCachingTest {
    private val test = TestApp()

    @TempDir
    lateinit var dir: Path

    @BeforeEach
    fun reset() = kotlinx.coroutines.runBlocking { test.reset() }

    @Test
    fun `after a deploy the browser gets the new file, not a 304 for its old one`() {
        val before = jar("v1", ".probe { padding: 0 }")
        val after = jar("v2", ".probe { padding: 16px }")

        // The browser's first visit: it keeps the body and every validator the server gave it.
        var etag = ""
        var lastModified: String? = null
        test.webTest(classLoader = before) { client ->
            val first = client.get(PROBE)
            first.bodyAsText() shouldBe ".probe { padding: 0 }"
            etag = first.headers[HttpHeaders.ETag].shouldNotBeNull()
            lastModified = first.headers[HttpHeaders.LastModified]
        }

        // Next visit, after the deploy: it revalidates with what it kept, plus the jar's date in case it holds none.
        test.webTest(classLoader = after) { client ->
            val revalidated = client.get(PROBE) {
                header(HttpHeaders.IfNoneMatch, etag)
                header(HttpHeaders.IfModifiedSince, lastModified ?: JAR_DATE_HTTP)
            }
            revalidated.status shouldBe HttpStatusCode.OK
            revalidated.bodyAsText() shouldBe ".probe { padding: 16px }"

            val newEtag = revalidated.headers[HttpHeaders.ETag].shouldNotBeNull()
            client.get(PROBE) { header(HttpHeaders.IfNoneMatch, newEtag) }.status shouldBe HttpStatusCode.NotModified
        }
    }

    @Test
    fun `assets served from a jar carry no Last-Modified`() = test.webTest(classLoader = jar("v1", ".probe {}")) { client ->
        val response = client.get(PROBE)
        response.status shouldBe HttpStatusCode.OK
        response.headers[HttpHeaders.LastModified] shouldBe null
    }

    /** A jar like the fat jar: `static/` holds the probe, and its entry has the reproducible-build timestamp. */
    private fun jar(name: String, css: String): ClassLoader {
        val path = dir.resolve("$name.jar")
        JarOutputStream(path.outputStream()).use { out ->
            out.putNextEntry(JarEntry("static/$PROBE_FILE").apply { lastModifiedTime = FileTime.from(JAR_DATE) })
            out.write(css.toByteArray())
            out.closeEntry()
        }
        return URLClassLoader(arrayOf(path.toUri().toURL()), javaClass.classLoader)
    }

    private companion object {
        const val PROBE_FILE = "deploy-probe.css"
        const val PROBE = "/static/$PROBE_FILE"
        val JAR_DATE: Instant = Instant.parse("1980-02-01T00:00:00Z")
        const val JAR_DATE_HTTP = "Fri, 01 Feb 1980 00:00:00 GMT"
    }
}

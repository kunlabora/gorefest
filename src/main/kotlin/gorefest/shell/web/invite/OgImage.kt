package gorefest.shell.web.invite

import gorefest.shell.web.components.of
import gorefest.shell.web.gameday.invite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** What the invite preview in WhatsApp/Signal shows. */
data class InvitePreview(val host: String, val title: String, val date: String, val place: String, val going: Int, val shelf: Int)

/**
 * Renders the 1200×630 Open Graph image from the design (docs/design/mockups/screen-open-graph.html)
 * with Java2D, so it needs no browser. Uses the JVM's logical fonts; the server needs fontconfig.
 */
object OgImage {
    private val lightYellow = Color(0xfffbdb)
    private val charcoal = Color(0x30362f)
    private val olive = Color(0x625834)
    private val bronze = Color(0xa59132)
    private val chocolate = Color(0xda7422)
    private val onBrand = Color(0x1e2119)

    fun png(preview: InvitePreview): ByteArray {
        val image = BufferedImage(1200, 630, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = lightYellow
        g.fillRect(0, 0, 1200, 630)
        dice(g)

        var y = 72 + 30
        g.color = olive
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 30)
        g.drawString("${preview.host} invites you", 72, y)

        g.color = charcoal
        val titleLines = fit(g, preview.title, maxWidth = 620, sizes = listOf(120, 96, 80, 64), maxLines = 2)
        y += 24
        titleLines.second.forEach { line ->
            y += (titleLines.first * 0.95).toInt()
            g.drawString(line, 68, y)
        }

        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 36)
        y += 64
        g.drawString(ellipsize(g, preview.date, 640), 72, y)
        if (preview.place.isNotBlank()) {
            y += 48
            g.drawString(ellipsize(g, preview.place, 640), 72, y)
        }

        g.font = Font(Font.SANS_SERIF, Font.BOLD, 34)
        val chip = "${preview.shelf} ${if (preview.shelf == 1) "game" else "games"}"
        val chipWidth = g.fontMetrics.stringWidth(chip) + 24
        val baseline = 630 - 72 - 8
        g.color = chocolate
        g.fill(RoundRectangle2D.Double(72.0, (baseline - 34).toDouble(), chipWidth.toDouble(), 46.0, 20.0, 20.0))
        g.color = onBrand
        g.drawString(chip, 84, baseline)
        g.color = charcoal
        g.drawString(" on the shelf · ${preview.going} going", 72 + chipWidth, baseline)

        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    /** Three tumbling dice on the right, like the mockup. */
    private fun dice(g: Graphics2D) {
        data class Die(val x: Int, val y: Int, val size: Int, val angle: Double, val fill: Color, val pips: Int)
        listOf(
            Die(820, 70, 230, -0.2, chocolate, 5),
            Die(930, 330, 200, 0.25, olive, 3),
            Die(720, 380, 150, 0.1, charcoal, 2),
            Die(1080, 60, 120, 0.35, bronze, 1),
        ).forEach { d ->
            val t = g.transform
            g.rotate(d.angle, d.x + d.size / 2.0, d.y + d.size / 2.0)
            g.color = d.fill
            g.fill(RoundRectangle2D.Double(d.x.toDouble(), d.y.toDouble(), d.size.toDouble(), d.size.toDouble(), d.size * 0.22, d.size * 0.22))
            g.color = lightYellow
            g.stroke = BasicStroke(1f)
            val r = d.size / 12
            val c = d.size / 2
            val o = d.size / 4
            val spots = when (d.pips) {
                1 -> listOf(c to c)
                2 -> listOf(o to o, d.size - o to d.size - o)
                3 -> listOf(o to o, c to c, d.size - o to d.size - o)
                else -> listOf(o to o, d.size - o to o, c to c, o to d.size - o, d.size - o to d.size - o)
            }
            spots.forEach { (px, py) -> g.fillOval(d.x + px - r, d.y + py - r, 2 * r, 2 * r) }
            g.transform = t
        }
    }

    /** The largest size that fits [text] on one line, else on up to [maxLines] lines of [maxWidth]. */
    private fun fit(g: Graphics2D, text: String, maxWidth: Int, sizes: List<Int>, maxLines: Int): Pair<Int, List<String>> {
        for (lineLimit in 1..maxLines) for (size in sizes) {
            g.font = Font(Font.SANS_SERIF, Font.BOLD, size)
            val lines = wrap(g, text, maxWidth)
            if (lines.size <= lineLimit && lines.all { g.fontMetrics.stringWidth(it) <= maxWidth }) return size to lines
        }
        val size = sizes.last()
        g.font = Font(Font.SANS_SERIF, Font.BOLD, size)
        val all = wrap(g, text, maxWidth)
        val lines = all.take(maxLines)
        // Cut anything wider than the canvas (one long word), and mark the last line when text was dropped.
        return size to lines.mapIndexed { i, l ->
            if (i == maxLines - 1 && all.size > maxLines) ellipsize(g, "$l…", maxWidth) else ellipsize(g, l, maxWidth)
        }
    }

    private fun wrap(g: Graphics2D, text: String, maxWidth: Int): List<String> = text.split(' ').fold(mutableListOf<String>()) { lines, word ->
        val candidate = (lines.lastOrNull()?.let { "$it $word" } ?: word)
        if (lines.isNotEmpty() && g.fontMetrics.stringWidth(candidate) <= maxWidth) lines[lines.size - 1] = candidate else lines += word
        lines
    }

    private fun ellipsize(g: Graphics2D, text: String, maxWidth: Int): String {
        if (g.fontMetrics.stringWidth(text) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && g.fontMetrics.stringWidth("$cut…") > maxWidth) cut = cut.dropLast(1)
        return "$cut…"
    }
}

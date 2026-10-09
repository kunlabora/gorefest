package gorefest.shell.web.invite

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

class OgImageTest {
    private fun render(title: String) = ImageIO.read(OgImage.png(InvitePreview("Tim", title, "Sunday 27 September", "Anna's place", 1, 0)).inputStream())

    @Test
    fun `a title of one endless word still renders inside the canvas`() {
        val image = render("A".repeat(5000))
        (image.width to image.height) shouldBe (1200 to 630)
    }
}

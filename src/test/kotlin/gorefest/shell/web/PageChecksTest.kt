package gorefest.shell.web

import gorefest.contract.Obligation
import io.kotest.assertions.throwables.shouldThrow
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

/** The checks every web test runs on what it parses (`page()`, `fragment()`) catch what they say they catch. */
class PageChecksTest {
    private fun html(body: String) = Jsoup.parse("<html lang=en><body>$body</body></html>")

    @Test
    @Obligation("A11Y-1", "A11Y-4")
    fun `an unnamed control or an image without an alt fails the page`() {
        shouldThrow<AssertionError> { html("""<input name="q">""").shouldNameEveryControl() }
        shouldThrow<AssertionError> { html("""<button></button>""").shouldNameEveryControl() }
        shouldThrow<AssertionError> { html("""<img src="a.webp">""").shouldNameEveryControl() }
        html("""<label for="q">Search</label><input id="q" name="q"><button aria-label="Close"></button><img src="a.webp" alt="">""").shouldNameEveryControl()
    }

    @Test
    @Obligation("A11Y-2")
    fun `a duplicate id or a description pointing at nothing fails the page`() {
        shouldThrow<AssertionError> { html("""<p id="email-hint">A</p><p id="email-hint">B</p>""").shouldHaveSoundIds() }
        shouldThrow<AssertionError> { html("""<input id="email" aria-describedby="email-hint">""").shouldHaveSoundIds() }
        shouldThrow<AssertionError> { html("""<label for="mail">Email</label><input id="email">""").shouldHaveSoundIds() }
        html("""<label for="email">Email</label><input id="email" aria-describedby="email-hint"><p id="email-hint">A</p>""").shouldHaveSoundIds()
    }

    @Test
    @Obligation("A11Y-3")
    fun `choices that share a name outside a named group fail the page`() {
        shouldThrow<AssertionError> { html("""<label><input type="radio" name="game">Azul</label><label><input type="radio" name="game">Brass</label>""").shouldGroupEveryChoice() }
        html("""<fieldset><legend>Who's at the table?</legend><label><input type="checkbox" name="players">A</label><label><input type="checkbox" name="players">B</label></fieldset>""")
            .shouldGroupEveryChoice()
        html("""<div role="group" aria-label="Which game?"><label><input type="radio" name="game">A</label><label><input type="radio" name="game">B</label></div>""")
            .shouldGroupEveryChoice()
    }
}

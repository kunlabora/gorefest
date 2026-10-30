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
    @Obligation("A11Y-2", "A11Y-9")
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

    @Test
    @Obligation("A11Y-9")
    fun `an htmx target that isn't on the page fails it`() {
        shouldThrow<AssertionError> { html("""<button hx-target="#results">Search</button>""").shouldHaveResolvableHxTargets() }
        html("""<button hx-target="#results">Search</button><div id="results"></div>""").shouldHaveResolvableHxTargets()
    }

    @Test
    @Obligation("A11Y-7")
    fun `a page without a language, a main heading or a main region fails`() {
        val good = """<nav aria-label="Main"></nav><main><h1>Gorefest #7</h1></main>"""
        html(good).shouldHaveSoundStructure()
        shouldThrow<AssertionError> { Jsoup.parse("<html><body>$good</body></html>").shouldHaveSoundStructure() }
        shouldThrow<AssertionError> { html("""<main><h2>Coming up</h2></main>""").shouldHaveSoundStructure() }
        shouldThrow<AssertionError> { html("""<main><h1>A</h1><h1>B</h1></main>""").shouldHaveSoundStructure() }
        shouldThrow<AssertionError> { html("""<h1>A</h1>""").shouldHaveSoundStructure() }
        shouldThrow<AssertionError> { html("""<nav></nav><main><h1>A</h1></main>""").shouldHaveSoundStructure() }
    }

    @Test
    @Obligation("A11Y-8")
    fun `a heading that skips a level fails, unless it sits in hidden decoration`() {
        shouldThrow<AssertionError> { html("""<main><h1>Your collection</h1><h3>7 Wonders</h3></main>""").shouldHaveSoundStructure() }
        html("""<main><h1>Your collection</h1><h2>7 Wonders</h2><h3>Facts</h3><h2>Azul</h2></main>""").shouldHaveSoundStructure()
        html("""<main><h1>Sign in</h1><div aria-hidden="true"><h3>Cascadia</h3></div></main>""").shouldHaveSoundStructure()
    }

    @Test
    @Obligation("A11Y-10")
    fun `a fragment that neither announces itself, alerts nor refreshes quietly fails`() {
        fun fragment(html: String) = Jsoup.parseBodyFragment(html).body().child(0)
        shouldThrow<AssertionError> { fragment("""<div id="results"><p>3 games</p></div>""").shouldAnnounceItself() }
        fragment("""<div id="results" data-announce="3 games found"></div>""").shouldAnnounceItself()
        fragment("""<form id="f"><p role="alert">That doesn't look like an email address.</p></form>""").shouldAnnounceItself()
        fragment("""<div id="summary" data-quiet="true"></div>""").shouldAnnounceItself()
    }

    @Test
    @Obligation("A11Y-4")
    fun `buttons that share a name but act on different things fail the page`() {
        shouldThrow<AssertionError> {
            html("""<form action="/polls/1/games"><input type="hidden" name="game" value="1"><button>Add</button></form>
                    <form action="/polls/1/games"><input type="hidden" name="game" value="2"><button>Add</button></form>""").shouldTellAlikeControlsApart()
        }
        html("""<form action="/polls/1/games"><input type="hidden" name="game" value="1"><button aria-label="Add Azul to the poll">Add</button></form>
                <form action="/polls/1/games"><input type="hidden" name="game" value="2"><button aria-label="Add Brass to the poll">Add</button></form>""")
            .shouldTellAlikeControlsApart()
        // The same action twice (the dock repeats the page's main button) is fine.
        html("""<a href="/bring">Bring games</a><a href="/bring">Bring games</a>""").shouldTellAlikeControlsApart()
    }
}

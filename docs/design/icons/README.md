# Icons

Gorefest's own line icons: 24×24, 2px stroke, round caps and joins, no fill (the star gets `fill: var(--accent)` when it means "great"). The files use a single `charcoal-brown` (#30362f) ink; in the app, inline the SVG with `stroke="currentColor"` and class `gf-icon`. `unplayed` = haven't played, `meh` = both meh, `spark` = novelty / unplayed, `heart` = someone wants to play it, `dice` = generic game, `sun` / `moon` = the light/dark toggle (it shows the theme you switch to).

`favicon.svg` is the app icon (a chocolate die face); `favicon.ico` (16/32/48) and `apple-touch-icon.png` (180, full-bleed) are raster copies of it, drawn from the same shapes, for browsers that ask for `/favicon.ico` and for iOS home screens. They aren't line icons, so the app never inlines them.

#!/usr/bin/env bash
# Assembles the onboarding site (site/) with the app's own design system into build/site, for GitHub Pages.
# docs/design stays the single source for the CSS, fonts and icons, as it is for the app (build.gradle.kts).
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$root/build/site"

rm -rf "$out"
mkdir -p "$out/css" "$out/fonts" "$out/icons"
cp "$root"/site/*.html "$root"/site/*.css "$root"/site/*.js "$out/"
cp "$root"/docs/design/css/tokens.css "$root"/docs/design/css/gorefest.css "$out/css/"
cp "$root"/docs/design/fonts/*.woff2 "$root"/docs/design/fonts/OFL.txt "$out/fonts/"
cp "$root"/docs/design/icons/favicon.svg "$root"/docs/design/icons/apple-touch-icon.png "$out/icons/"
touch "$out/.nojekyll"
echo "Site built in $out"

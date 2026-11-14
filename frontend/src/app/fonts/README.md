# Pinned build font assets

The existing Inter, Plus Jakarta Sans and Noto Nastaliq Urdu families are loaded
through `next/font/local`. Their CSS variables, normal styles, existing weight
ranges and swap display remain. No new font family or UI redesign is introduced.

`manifest.json` pins the exact Google Fonts repository commit, upstream download
URLs, byte sizes, SHA-256 hashes and OFL licenses. The six upstream files are
unmodified, including all three license notices. Tests verify their integrity
and reject new live Google Fonts imports in application source. These full
variable fonts total about 1.7 MB before transport compression; measure actual
network/first-render impact during staging. No new font subsetting tool/dependency
or generated substitute is required from a clean clone.

Hosted run `43601102295` preserved a Next 16.4.1 Google-font loader URL-extension
parsing failure in the separate browser build while the frontend build passed.
The fix removes that external build input; no retry, loader monkeypatch, fake
font response or browser assertion change is used. Fonts are now served by the
application's static assets and builds require no Google Fonts network access.
